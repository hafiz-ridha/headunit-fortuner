// Mengunduh teks Al-Qur'an (Arab, Latin, terjemahan Kemenag) dari API equran.id v2
// lalu menyimpannya ringkas di assets/quran/ agar launcher bisa membaca offline.
// Teks tidak diubah sama sekali selain trim spasi di ujung.
// Jalankan: node tools/build_quran.mjs
import { mkdir, writeFile } from "node:fs/promises";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const out = join(root, "assets", "quran");
const QARI = "05"; // Misyari Rasyid Al-Afasi

async function get(url, tries = 4) {
  for (let i = 1; ; i++) {
    try {
      const r = await fetch(url);
      if (!r.ok) throw new Error(`HTTP ${r.status}`);
      const j = await r.json();
      if (j.code !== 200) throw new Error(`code ${j.code}`);
      return j.data;
    } catch (e) {
      if (i >= tries) throw new Error(`${url}: ${e.message}`);
      await new Promise((res) => setTimeout(res, 1000 * i));
    }
  }
}

await mkdir(out, { recursive: true });
const index = [];
let totalAyat = 0;
for (let n = 1; n <= 114; n++) {
  const d = await get(`https://equran.id/api/v2/surat/${n}`);
  if (d.nomor !== n || d.ayat.length !== d.jumlahAyat) {
    throw new Error(`Surah ${n}: jumlah ayat tidak cocok (${d.ayat.length} vs ${d.jumlahAyat})`);
  }
  d.ayat.forEach((a, i) => {
    if (a.nomorAyat !== i + 1) throw new Error(`Surah ${n}: urutan ayat ${a.nomorAyat} di posisi ${i + 1}`);
  });
  const surah = {
    n: d.nomor,
    arab: d.nama,
    latin: d.namaLatin,
    arti: d.arti,
    tempat: d.tempatTurun,
    // [teksArab, teksLatin, teksIndonesia, urlAudio]
    ayat: d.ayat.map((a) => [a.teksArab.trim(), a.teksLatin.trim(), a.teksIndonesia.trim(), a.audio?.[QARI] ?? ""]),
  };
  await writeFile(join(out, String(n).padStart(3, "0") + ".json"), JSON.stringify(surah));
  index.push([d.nomor, d.namaLatin, d.nama, d.arti, d.jumlahAyat, d.tempatTurun]);
  totalAyat += d.jumlahAyat;
  process.stdout.write(`\r${n}/114`);
}
if (totalAyat !== 6236) throw new Error(`Total ayat ${totalAyat}, seharusnya 6236`);
await writeFile(join(out, "index.json"), JSON.stringify(index));
console.log(`\nOK: 114 surah, ${totalAyat} ayat -> ${out}`);
