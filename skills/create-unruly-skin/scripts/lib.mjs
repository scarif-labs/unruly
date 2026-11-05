// Shared by preview/shot/pack/push. Node 18+, no dependencies.
import { createServer } from "node:http";
import { readFileSync, existsSync, statSync, readdirSync } from "node:fs";
import { join, resolve, extname, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";

export const KIT = resolve(fileURLToPath(new URL("..", import.meta.url)));
export const UNRULY_API = 1;
export const GEAR_CORNERS = ["top-start", "top-end", "bottom-start", "bottom-end"];
const ID = /^[a-z0-9]+([._-][a-z0-9]+)*$/;

/**
 * The same checks the app runs on install (UserSkinStore.parseManifest + extract). Returns
 * { manifest, errors, warnings }. A skin with no errors here installs.
 */
export function validate(dir) {
  const errors = [], warnings = [];
  const mPath = join(dir, "skin.json");
  if (!existsSync(mPath)) return { errors: ["skin.json is missing"], warnings };
  let m;
  try {
    m = JSON.parse(readFileSync(mPath, "utf8"));
  } catch (e) {
    return { errors: ["skin.json isn't valid JSON: " + e.message], warnings };
  }
  const str = (k) => (typeof m[k] === "string" ? m[k].trim() : "");
  if (!str("id")) errors.push('skin.json is missing "id"');
  else if (str("id").length > 100 || !ID.test(str("id")))
    errors.push('"id" must be lowercase letters, digits, and single ".", "-" or "_" between them (e.g. "com.yourname.neon")');
  if (!str("name")) errors.push('skin.json is missing "name"');
  else if (str("name").length > 40) warnings.push('"name" is over 40 characters and will be cut');
  if (!(m.unrulyApi >= 1)) errors.push('skin.json is missing "unrulyApi" (use 1)');
  else if (m.unrulyApi > UNRULY_API) errors.push(`"unrulyApi" ${m.unrulyApi} is newer than this kit (${UNRULY_API})`);
  const entry = str("entry") || "index.html";
  if (entry.startsWith("/") || entry.split("/").includes("..")) errors.push('"entry" must be a path inside the skin');
  else if (!existsSync(join(dir, entry))) errors.push(`entry "${entry}" doesn't exist`);
  const gear = str("settingsGear") || "top-end";
  if (!GEAR_CORNERS.includes(gear)) errors.push(`"settingsGear" must be one of ${GEAR_CORNERS.join(", ")}`);
  if (!str("version")) warnings.push('no "version"; updates are easier to tell apart with one');
  if (!str("author")) warnings.push('no "author"; it is shown under the skin in settings');
  if (m.developer === true) warnings.push('"developer": true shows an error panel on the phone; remove it before sharing the skin');

  const files = listFiles(dir);
  if (files.length > 2000) errors.push(`too many files (${files.length} > 2000)`);
  const total = files.reduce((s, f) => s + statSync(join(dir, f)).size, 0);
  if (total > 50 * 1024 * 1024) errors.push(`too big (${(total / 1048576).toFixed(1)} MB > 50 MB)`);
  for (const f of files) {
    if (![".html", ".htm", ".css", ".js", ".mjs", ".svg", ".json"].includes(extname(f).toLowerCase())) continue;
    const text = readFileSync(join(dir, f), "utf8");
    const remote = text.match(/(?:src|href|url\()\s*=?\s*["']?(https?:)?\/\/[^"')\s]+/gi);
    if (remote) warnings.push(`${f}: remote URL ${remote[0]} - skins have no network; bundle it instead`);
    if (/unruly-bridge\.js/.test(text) && f !== "unruly-bridge.js")
      warnings.push(`${f}: don't include unruly-bridge.js; the app injects it`);
  }
  return { manifest: { ...m, id: str("id"), entry, settingsGear: gear }, errors, warnings, files };
}

export function listFiles(dir, base = dir) {
  const out = [];
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    if (e.name === ".DS_Store" || e.name === "node_modules" || e.name.startsWith(".git")) continue;
    const p = join(dir, e.name);
    if (e.isDirectory()) out.push(...listFiles(p, base));
    else if (e.isFile() && !e.name.endsWith(".unruly")) out.push(relative(base, p).split(sep).join("/"));
  }
  return out;
}

const TYPES = {
  ".html": "text/html; charset=utf-8", ".htm": "text/html; charset=utf-8", ".js": "text/javascript", ".mjs": "text/javascript",
  ".css": "text/css", ".json": "application/json", ".svg": "image/svg+xml", ".png": "image/png", ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg", ".webp": "image/webp", ".gif": "image/gif", ".woff": "font/woff", ".woff2": "font/woff2",
  ".ttf": "font/ttf", ".otf": "font/otf", ".mp3": "audio/mpeg", ".ogg": "audio/ogg", ".wav": "audio/wav",
};

// Same policy the app sends (WebSkin.POLICY_HEADERS), so network use fails here too.
const CSP =
  "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
  "style-src 'self' 'unsafe-inline'; connect-src 'self'; form-action 'none'; frame-src 'self'";

/** Album art for the mock library: a deterministic gradient + monogram per album. */
function art(id) {
  let h = 0;
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const a = h % 360, b = (a + 40 + (h >> 8) % 140) % 360;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${a},70%,55%)"/><stop offset="1" stop-color="hsl(${b},65%,25%)"/></linearGradient></defs>
<rect width="600" height="600" fill="url(#g)"/><circle cx="${150 + (h % 300)}" cy="${150 + ((h >> 4) % 300)}" r="${80 + (h % 120)}" fill="rgba(255,255,255,.18)"/>
<text x="40" y="560" font-family="Helvetica,Arial" font-size="72" font-weight="700" fill="rgba(255,255,255,.85)">${id.toUpperCase()}</text></svg>`;
}

/** The policy header, for callers that serve responses themselves. */
export const PREVIEW_CSP = CSP;

/**
 * One response for `path` on the preview origin: the skin's files the way the app serves them,
 * with the mock player injected before anything else in the entry page.
 * Returns { status, type, body }.
 */
export function serve(dir, path) {
  dir = resolve(dir);
  if (path === "/favicon.ico") return { status: 404, type: "text/plain", body: "" };
  if (path === "/__unruly/mock-native.js") return { status: 200, type: TYPES[".js"], body: readFileSync(join(KIT, "scripts/mock-native.js")) };
  if (path === "/__unruly/unruly-bridge.js") return { status: 200, type: TYPES[".js"], body: readFileSync(join(KIT, "runtime/unruly-bridge.js")) };
  if (path.startsWith("/__unruly/art/")) return { status: 200, type: TYPES[".svg"], body: art(path.slice(15)) };
  if (path === "/") path = "/" + (validate(dir).manifest?.entry || "index.html");
  const file = resolve(join(dir, path));
  if (!file.startsWith(dir) || !existsSync(file) || statSync(file).isDirectory()) return { status: 404, type: "text/plain", body: "Not found" };
  let body = readFileSync(file);
  const type = TYPES[extname(file).toLowerCase()] || "application/octet-stream";
  if (type.startsWith("text/html")) {
    // The app injects the bridge at document start; mirror that with the first scripts in <head>.
    const inject = '<script src="/__unruly/mock-native.js"></script><script src="/__unruly/unruly-bridge.js"></script>';
    const html = body.toString("utf8");
    body = /<head[^>]*>/i.test(html) ? html.replace(/<head[^>]*>/i, (m) => m + inject) : inject + html;
  }
  return { status: 200, type, body };
}

/** Serves a skin folder over HTTP on localhost, for a desktop browser (preview.mjs). */
export function startPreview(dir, port = 0) {
  const server = createServer((req, res) => {
    const { status, type, body } = serve(dir, decodeURIComponent(new URL(req.url, "http://x").pathname));
    res.writeHead(status, { "Content-Type": type, "Content-Security-Policy": CSP, "Cache-Control": "no-store" });
    res.end(body);
  });
  return new Promise((ok) => server.listen(port, "127.0.0.1", () => ok({ server, url: `http://127.0.0.1:${server.address().port}/` })));
}

/** A Chromium-family browser for headless screenshots: $CHROME, else the usual install paths. */
export function findChromium() {
  const candidates = [
    process.env.CHROME,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
    "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable", "/usr/bin/chromium", "/usr/bin/chromium-browser",
    "/usr/bin/brave-browser", "/usr/bin/microsoft-edge",
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  ];
  return candidates.find((p) => p && existsSync(p));
}
