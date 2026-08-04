import sharp from "sharp";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const fb = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "brand-fallback");

await sharp(path.join(fb, "splash-icon.svg"), { density: 288 })
  .resize(1152, 1152, { fit: "fill" })
  .webp({ lossless: true })
  .toFile(path.join(fb, "splash-icon.webp"));

const instanceSvg = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="640" height="160">
  <rect width="640" height="160" rx="8" fill="#333333"/>
  <rect x="48" y="56" width="120" height="48" rx="6" fill="#888888"/>
  <rect x="192" y="64" width="360" height="16" rx="4" fill="#AAAAAA"/>
  <rect x="192" y="92" width="240" height="12" rx="3" fill="#666666"/>
</svg>`);

await sharp(instanceSvg).webp({ quality: 90 }).toFile(path.join(fb, "instance-logo.webp"));

console.log("placeholders:", fs.readdirSync(fb).join(", "));
