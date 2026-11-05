#!/usr/bin/env node
// Serve a skin folder with a mock Unruly player, for a desktop browser.
//   node preview.mjs <skin-dir> [--port 5173]
// Open the printed URL at phone size (devtools device mode, ~412x915). Scenario parameters such as
// ?library=empty&lyrics=disabled are listed at the top of mock-native.js.
import { startPreview, validate } from "./lib.mjs";

const args = process.argv.slice(2);
const dir = args.find((a) => !a.startsWith("--"));
if (!dir) {
  console.error("usage: node preview.mjs <skin-dir> [--port 5173]");
  process.exit(2);
}
const port = Number(args[args.indexOf("--port") + 1]) || 5173;
const { errors, warnings } = validate(dir);
for (const w of warnings) console.warn("warning:", w);
for (const e of errors) console.error("error:", e);
const { url } = await startPreview(dir, port);
console.log(`Previewing ${dir} at ${url}`);
console.log("Scenarios: ?library=empty|scanning  ?lyrics=disabled|notFound  ?playing=0  ?insets=48,0,32,0");
console.log("Press system back: __unrulyPreview.back() in the console. Ctrl-C to stop.");
