// Memastikan font Arab (assets/fonts/NotoNaskhArabic.ttf) punya glyph untuk SEMUA karakter teks Arab
// di assets/quran, supaya tidak ada kotak kosong (tofu). Membaca tabel cmap format 4 dan 12.
// Jalankan: node tools/check_font.mjs [font.ttf]
import { readFileSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const fontPath = process.argv[2] ?? join(root, "assets", "fonts", "NotoNaskhArabic.ttf");
const buf = readFileSync(fontPath);

function cmapCodepoints(b) {
  const numTables = b.readUInt16BE(4);
  let cmap = -1;
  for (let i = 0; i < numTables; i++) {
    const rec = 12 + i * 16;
    if (b.toString("latin1", rec, rec + 4) === "cmap") cmap = b.readUInt32BE(rec + 8);
  }
  if (cmap < 0) throw new Error("tabel cmap tidak ada");
  const set = new Set();
  const n = b.readUInt16BE(cmap + 2);
  for (let i = 0; i < n; i++) {
    const off = cmap + b.readUInt32BE(cmap + 4 + i * 8 + 4);
    const format = b.readUInt16BE(off);
    if (format === 4) {
      const segX2 = b.readUInt16BE(off + 6);
      const ends = off + 14, starts = ends + segX2 + 2, deltas = starts + segX2, ranges = deltas + segX2;
      for (let s = 0; s < segX2 / 2; s++) {
        const end = b.readUInt16BE(ends + s * 2), start = b.readUInt16BE(starts + s * 2);
        const delta = b.readInt16BE(deltas + s * 2), ro = b.readUInt16BE(ranges + s * 2);
        for (let c = start; c <= end && c !== 0xffff; c++) {
          let g;
          if (ro === 0) g = (c + delta) & 0xffff;
          else {
            const gi = b.readUInt16BE(ranges + s * 2 + ro + (c - start) * 2);
            g = gi === 0 ? 0 : (gi + delta) & 0xffff;
          }
          if (g !== 0) set.add(c);
        }
      }
    } else if (format === 12) {
      const groups = b.readUInt32BE(off + 12);
      for (let g = 0; g < groups; g++) {
        const p = off + 16 + g * 12;
        const start = b.readUInt32BE(p), end = b.readUInt32BE(p + 4);
        for (let c = start; c <= end; c++) set.add(c);
      }
    }
  }
  return set;
}

const covered = cmapCodepoints(buf);
const used = new Map();
const dir = join(root, "assets", "quran");
for (const f of readdirSync(dir).filter((x) => /^\d{3}\.json$/.test(x))) {
  const s = JSON.parse(readFileSync(join(dir, f), "utf8"));
  for (const a of s.ayat) for (const ch of a[0]) used.set(ch.codePointAt(0), (used.get(ch.codePointAt(0)) ?? 0) + 1);
}
const missing = [...used.keys()].filter((c) => !covered.has(c) && c !== 0x20);
console.log(`${fontPath}\n${used.size} karakter dipakai, ${missing.length} tidak ada di font`);
for (const c of missing) console.log(`  U+${c.toString(16).toUpperCase().padStart(4, "0")}  x${used.get(c)}`);
process.exit(missing.length ? 1 : 0);
