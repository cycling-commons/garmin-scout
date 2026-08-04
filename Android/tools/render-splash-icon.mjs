#!/usr/bin/env node
/**
 * Rasterise Brand/splash-icon.svg (or brand-fallback) for the API 31+ splash icon.
 *
 * Android shows the icon at 288×288 **dp** on screen. The SVG artboard should stay
 * 288×288 (design units); this script outputs a higher-pixel bitmap so phones
 * don't upscale a tiny image. 4× covers xxxhdpi; lower densities downscale cleanly.
 */
import { readFileSync } from "node:fs";
import sharp from "sharp";

const [input, output] = process.argv.slice(2);
if (!input || !output) {
  console.error("Usage: node render-splash-icon.mjs <input.svg> <output.webp>");
  process.exit(1);
}

/** Splash icon size in dp (Android spec). */
const DP = 288;
/** Raster scale for xxxhdpi (4×). */
const SCALE = 4;
const SIZE = DP * SCALE;

const svg = readFileSync(input);
await sharp(svg, { density: 72 * SCALE })
  .resize(SIZE, SIZE, {
    fit: "fill",
    background: { r: 0, g: 0, b: 0, alpha: 0 },
  })
  .webp({ lossless: true })
  .toFile(output);
