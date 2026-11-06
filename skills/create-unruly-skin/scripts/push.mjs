#!/usr/bin/env node
// Install a packed skin on a connected phone over adb and switch to it (Unruly DEBUG builds only;
// release builds load skins from settings -> skin -> Load skin).
//   node push.mjs <file.unruly> [--serial <adb serial>] [--package com.scarif.unruly.debug]
import { execFileSync } from "node:child_process";
import { basename, resolve } from "node:path";

const args = process.argv.slice(2);
const opt = (n, d) => (args.includes(n) ? args[args.indexOf(n) + 1] : d);
const file = args.find((a, i) => !a.startsWith("--") && !(i > 0 && args[i - 1].startsWith("--")));
if (!file) {
  console.error("usage: node push.mjs <file.unruly> [--serial X] [--package com.scarif.unruly.debug]");
  process.exit(2);
}
const pkg = opt("--package", "com.scarif.unruly.debug");
const serial = opt("--serial");
const adb = (...a) => execFileSync("adb", [...(serial ? ["-s", serial] : []), ...a], { encoding: "utf8" });

const remoteDir = `/sdcard/Android/data/${pkg}/files`;
const remote = `${remoteDir}/${basename(file)}`;
adb("shell", "mkdir", "-p", remoteDir);
adb("push", resolve(file), remote);
console.log(
  adb("shell", "am", "start", "-n", `${pkg}/com.merklegroot.unruly.unruly.host.UnrulySettingsActivity`,
    "-a", "com.merklegroot.unruly.INSTALL_SKIN", "-d", `file://${remote}`).trim(),
);
console.log(`Installed ${basename(file)}. If it didn't switch: adb logcat -d | grep -iE "skin|WebSkin" | tail`);
