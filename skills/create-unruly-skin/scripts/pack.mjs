#!/usr/bin/env node
// Validate a skin folder exactly as the app will, then zip it into <id>-<version>.unruly.
//   node pack.mjs <skin-dir> [out-dir]
import { readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { deflateRawSync, crc32 } from "node:zlib";
import { validate } from "./lib.mjs";

const [dirArg, outArg] = process.argv.slice(2);
if (!dirArg) {
  console.error("usage: node pack.mjs <skin-dir> [out-dir]");
  process.exit(2);
}
const dir = resolve(dirArg);
const { manifest, errors, warnings, files } = validate(dir);
for (const w of warnings) console.warn("warning:", w);
if (errors.length) {
  for (const e of errors) console.error("error:", e);
  process.exit(1);
}

// A plain zip writer (deflate, no extras) - enough for java.util.zip and every unzip tool.
const locals = [], centrals = [];
let offset = 0;
for (const name of files) {
  const data = readFileSync(join(dir, name));
  const packed = deflateRawSync(data, { level: 9 });
  const useDeflate = packed.length < data.length;
  const body = useDeflate ? packed : data;
  const crc = crc32(data);
  const nameBuf = Buffer.from(name, "utf8");
  const head = Buffer.alloc(30);
  head.writeUInt32LE(0x04034b50, 0);
  head.writeUInt16LE(20, 4);
  head.writeUInt16LE(0x0800, 6); // UTF-8 names
  head.writeUInt16LE(useDeflate ? 8 : 0, 8);
  head.writeUInt32LE(0x00210000, 10); // 1980-01-01, so builds are reproducible
  head.writeUInt32LE(crc, 14);
  head.writeUInt32LE(body.length, 18);
  head.writeUInt32LE(data.length, 22);
  head.writeUInt16LE(nameBuf.length, 26);
  head.writeUInt16LE(0, 28);
  locals.push(head, nameBuf, body);
  const cen = Buffer.alloc(46);
  cen.writeUInt32LE(0x02014b50, 0);
  cen.writeUInt16LE(20, 4);
  cen.writeUInt16LE(20, 6);
  cen.writeUInt16LE(0x0800, 8);
  cen.writeUInt16LE(useDeflate ? 8 : 0, 10);
  cen.writeUInt32LE(0x00210000, 12);
  cen.writeUInt32LE(crc, 16);
  cen.writeUInt32LE(body.length, 20);
  cen.writeUInt32LE(data.length, 24);
  cen.writeUInt16LE(nameBuf.length, 28);
  cen.writeUInt32LE(offset, 42);
  centrals.push(cen, nameBuf);
  offset += 30 + nameBuf.length + body.length;
}
const central = Buffer.concat(centrals);
const end = Buffer.alloc(22);
end.writeUInt32LE(0x06054b50, 0);
end.writeUInt16LE(files.length, 8);
end.writeUInt16LE(files.length, 10);
end.writeUInt32LE(central.length, 12);
end.writeUInt32LE(offset, 16);
const zip = Buffer.concat([...locals, central, end]);

const out = join(resolve(outArg || "."), `${manifest.id}-${manifest.version || "0"}.unruly`);
writeFileSync(out, zip);
console.log(`Packed ${files.length} files (${(zip.length / 1024).toFixed(0)} KB) -> ${out}`);
