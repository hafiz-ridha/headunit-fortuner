// Membuat aset doa naik kendaraan darat (QS. Az-Zukhruf 43:13-14) di assets/doa/:
// - doa_naik_kendaraan.json : teks Arab, Latin, dan terjemahan, dipotong APA ADANYA dari assets/quran/043.json,
//                              plus daftar audio dan titik mulainya.
// - 043013.mp3, 043014.mp3   : murattal Misyari Rasyid Al-Afasi (cdn.equran.id) apa adanya. Aplikasi memutar
//                              ayat 13 mulai START_MS (awal "subḥānal-lażī…", tanpa "wa taqūlū"), lalu ayat 14.
// Jalankan: node tools/build_doa.mjs   (butuh assets/quran dari tools/build_quran.mjs)
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, "assets", "doa");
// Awal bunyi "s" pada "subḥāna" di 043013.mp3: 13,09 detik. Diukur dari rasio energi frekuensi tinggi (>4 kHz)
// terhadap rendah (<1,2 kHz) per 10 ms: "wa taqūlū" = 11,82–13,07 dtk (bersuara), lalu desis "s" 13,09–13,19 dtk.
const START_MS = 13080;

const surah = JSON.parse(await readFile(join(root, "assets", "quran", "043.json"), "utf8"));
const [a13, a14] = [surah.ayat[12], surah.ayat[13]];

function from(text, marker) {
  const i = text.indexOf(marker);
  if (i < 0) throw new Error(`Penanda "${marker}" tidak ditemukan`);
  return text.slice(i);
}

async function download(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`);
  return Buffer.from(await r.arrayBuffer());
}

const arabic = from(a13[0], "سُبْحٰنَ") + " " + a14[0];
const latin13 = from(a13[1], "subḥānal");
const latin = latin13.charAt(0).toUpperCase() + latin13.slice(1) + " " + a14[1];
const translation = (from(a13[2], "“Maha Suci").slice(1) + " " + a14[2]).replace(/”$/, "");

await mkdir(out, { recursive: true });
await writeFile(join(out, "043013.mp3"), await download(a13[3]));
await writeFile(join(out, "043014.mp3"), await download(a14[3]));
await writeFile(join(out, "doa_naik_kendaraan.json"), JSON.stringify({
  title: "Doa Naik Kendaraan",
  arabic, latin, translation,
  source: "QS. Az-Zukhruf [43]: 13–14 · Terjemahan Kemenag RI",
  reciter: "Misyari Rasyid Al-Afasi",
  audio: [{ file: "043013.mp3", startMs: START_MS }, { file: "043014.mp3", startMs: 0 }],
}, null, 1));
console.log(`OK\n${arabic}\n${latin}\n${translation}`);
