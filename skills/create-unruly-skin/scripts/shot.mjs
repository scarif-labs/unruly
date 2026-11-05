#!/usr/bin/env node
// Screenshot a skin in a headless Chromium-family browser, phone-sized, against the mock player.
//   node shot.mjs <skin-dir> <out.png> [--size 412x915] [--query "library=empty"]
//                 [--run "js"]... [--wait 1500] [--step 700] [--transparent]
// Each --run (repeatable) is evaluated in the page in order, --step ms apart, starting --wait ms
// after load; the shot is taken --step ms after the last one (or --wait ms after load if none).
// Page console output and uncaught errors are printed: read them as well as the image.
//
// No network is used at all - the browser is driven over a pipe and the skin is served by
// intercepting its requests - so this works inside agent sandboxes that forbid sockets.
// Needs Node 18+ and Chrome, Chromium, Brave or Edge (or CHROME=/path/to/browser).
import { spawn } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { serve, findChromium, PREVIEW_CSP } from "./lib.mjs";

const ORIGIN = "https://preview.skin.unruly";
const args = process.argv.slice(2);
const opt = (name, dflt) => (args.includes(name) ? args[args.indexOf(name) + 1] : dflt);
const runs = args.flatMap((a, i) => (a === "--run" ? [args[i + 1]] : []));
const flags = new Set(["--transparent"]);
const positional = args.filter((a, i) => !a.startsWith("--") && !(i > 0 && args[i - 1].startsWith("--") && !flags.has(args[i - 1])));
const [dir, out] = positional;
if (!dir || !out) {
  console.error('usage: node shot.mjs <skin-dir> <out.png> [--size 412x915] [--query "k=v&..."] [--run "js"]... [--wait ms] [--step ms]');
  process.exit(2);
}
const browser = findChromium();
if (!browser) {
  console.error("No Chrome/Chromium/Brave/Edge found. Set CHROME=/path/to/browser.");
  process.exit(2);
}
const [w, h] = opt("--size", "412x915").split("x").map(Number);
const wait = Number(opt("--wait", "1500"));
const step = Number(opt("--step", "700"));
const query = opt("--query", "");
const target = `${ORIGIN}/${query ? "?" + query : ""}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// A throwaway browser profile. Sandboxes often forbid the temp dir, so fall back to the folder the
// screenshot goes to, which has to be writable anyway.
let profile;
try {
  profile = mkdtempSync(join(tmpdir(), "unruly-shot-"));
} catch {
  profile = mkdtempSync(join(dirname(resolve(out)), ".unruly-shot-"));
}
// Chrome's own sandbox can't start inside another sandbox (Codex, Claude Code...). The page is the
// user's own skin on a fake origin with all other requests blocked, so running without it is fine.
const child = spawn(
  browser,
  [
    "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run", "--no-default-browser-check",
    "--disable-extensions", "--no-sandbox", "--disable-background-networking", "--disable-component-update",
    "--disable-sync", `--user-data-dir=${profile}`, "--remote-debugging-pipe", "about:blank",
  ],
  { stdio: ["ignore", "ignore", "pipe", "pipe", "pipe"] },
);
let stderr = "";
child.stderr.on("data", (d) => (stderr += d));
const toBrowser = child.stdio[3], fromBrowser = child.stdio[4];

let id = 0, buffer = "", failed = false;
const waiting = new Map(), handlers = [];
fromBrowser.on("data", (chunk) => {
  buffer += chunk;
  let end;
  while ((end = buffer.indexOf("\0")) >= 0) {
    const msg = JSON.parse(buffer.slice(0, end));
    buffer = buffer.slice(end + 1);
    if (msg.id && waiting.has(msg.id)) {
      const { ok, fail } = waiting.get(msg.id);
      waiting.delete(msg.id);
      msg.error ? fail(new Error(msg.error.message)) : ok(msg.result);
    } else if (msg.method) handlers.forEach((fn) => fn(msg));
  }
});
const send = (method, params = {}, sessionId) =>
  new Promise((ok, fail) => {
    const mid = ++id;
    waiting.set(mid, { ok, fail });
    toBrowser.write(JSON.stringify({ id: mid, method, params, ...(sessionId ? { sessionId } : {}) }) + "\0");
  });

function finish(code) {
  try { child.kill(); } catch {}
  setTimeout(() => rmSync(profile, { recursive: true, force: true }), 300);
  process.exitCode = code;
}
const timer = setTimeout(() => {
  console.error("Timed out." + (stderr ? "\nBrowser said:\n" + stderr.split("\n").slice(-8).join("\n") : ""));
  finish(1);
  process.exit(1);
}, 30000 + wait + step * (runs.length + 1));
child.on("error", (e) => { console.error("Couldn't start the browser:", e.message); finish(1); process.exit(1); });

try {
  // A browser that can't start (typically: an agent sandbox that blocks it) never answers.
  const started = setTimeout(() => {
    console.error(
      "The browser didn't start. If you are an AI agent in a sandbox, it is blocking the browser:\n" +
        "re-run this exact command with sandbox escalation / outside the sandbox (in Codex: ask for\n" +
        "approval to run it unsandboxed; with `codex exec`, the user must allow full access).",
    );
    finish(3);
    process.exit(3);
  }, 15000);
  const { targetId } = await send("Target.createTarget", { url: "about:blank" });
  clearTimeout(started);
  const { sessionId } = await send("Target.attachToTarget", { targetId, flatten: true });
  const s = (m, p) => send(m, p, sessionId);
  handlers.push((msg) => {
    if (msg.sessionId !== sessionId) return;
    const p = msg.params;
    if (msg.method === "Fetch.requestPaused") {
      const url = new URL(p.request.url);
      if (url.origin !== ORIGIN) {
        // The app refuses every other request, so the preview does too.
        console.log(`blocked: ${p.request.url} (skins have no network; bundle it)`);
        s("Fetch.failRequest", { requestId: p.requestId, errorReason: "BlockedByClient" });
        return;
      }
      const r = serve(dir, decodeURIComponent(url.pathname));
      if (r.status === 404 && url.pathname !== "/favicon.ico") console.log(`404: ${url.pathname}`);
      s("Fetch.fulfillRequest", {
        requestId: p.requestId,
        responseCode: r.status,
        responseHeaders: [
          { name: "Content-Type", value: r.type },
          { name: "Content-Security-Policy", value: PREVIEW_CSP },
        ],
        body: Buffer.from(r.body).toString("base64"),
      });
    } else if (msg.method === "Runtime.consoleAPICalled") {
      const text = p.args.map((a) => a.value ?? a.description ?? a.type).join(" ");
      console.log(`console.${p.type}: ${text}`.slice(0, 500));
    } else if (msg.method === "Runtime.exceptionThrown") {
      const d = p.exceptionDetails;
      console.log(`UNCAUGHT: ${d.exception?.description || d.text} (${d.url || ""}:${d.lineNumber})`.slice(0, 800));
      failed = true;
    } else if (msg.method === "Log.entryAdded") {
      const e = p.entry;
      if (e.level === "error" && !/favicon/.test(e.url || "")) console.log(`browser ${e.level}: ${e.text} ${e.url || ""}`.slice(0, 500));
    }
  });
  await s("Fetch.enable", { patterns: [{ urlPattern: "*" }] });
  await s("Runtime.enable");
  await s("Log.enable");
  await s("Page.enable");
  await s("Emulation.setDeviceMetricsOverride", { width: w, height: h, deviceScaleFactor: 2, mobile: true });
  await s("Emulation.setTouchEmulationEnabled", { enabled: true, maxTouchPoints: 5 });
  // --transparent: keep the page's transparency in the PNG (for icons and other assets).
  if (args.includes("--transparent")) {
    await s("Emulation.setDefaultBackgroundColorOverride", { color: { r: 0, g: 0, b: 0, a: 0 } });
  }
  await s("Page.navigate", { url: target });
  await sleep(wait);
  for (const code of runs) {
    const r = await s("Runtime.evaluate", { expression: code, awaitPromise: true, userGesture: true });
    if (r.exceptionDetails) {
      console.log(`--run threw: ${r.exceptionDetails.exception?.description || r.exceptionDetails.text}`);
      failed = true;
    }
    await sleep(step);
  }
  const shot = await s("Page.captureScreenshot", { format: "png" });
  writeFileSync(resolve(out), Buffer.from(shot.data, "base64"));
  console.log(`Wrote ${out} (${w}x${h} @2x)${query ? " with ?" + query : ""}${failed ? " - with errors above" : ""}`);
  clearTimeout(timer);
  finish(failed ? 1 : 0);
} catch (e) {
  console.error("shot failed:", e.message);
  clearTimeout(timer);
  finish(1);
}
