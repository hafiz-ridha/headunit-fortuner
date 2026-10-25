# Fortuner Launcher

Launcher Android untuk headunit mobil (layar landscape): tema gelap premium, panel Toyota Fortuner TRD 2010
putih, kartu YouTube/Navigasi/Al-Qur'an, widget Al-Qur'an, dock aplikasi headunit, dan panel kontrol.

Launcher ini **tambahan**. Launcher bawaan (Dofun) tidak diubah atau dihapus. Kedua launcher bisa dipakai
bergantian kapan saja.

Diuji di headunit Unisoc UMS512 (TS10/UIS7862), Android 10, 1280×720. Layout juga dicek di 1024×600 dan
1920×1080 (240 dpi).

## Fitur

| Bagian | Isi | Sumber data |
|---|---|---|
| Status bar | Jam & tanggal, Bluetooth, Wi-Fi, sinyal seluler, cuaca, tombol pengaturan | Jam sistem, status perangkat, [Open-Meteo](https://open-meteo.com) |
| Lokasi otomatis | Saat tersambung internet, setelan Lokasi Android dinyalakan bila mati (butuh `pm grant … WRITE_SECURE_SETTINGS` lewat ADB). Cuaca memakai GPS, lalu lokasi jaringan, lalu perkiraan dari IP ([geojs.io](https://www.geojs.io)) | LocationManager, get.geojs.io |
| Sidebar | Beranda, Aplikasi, Pengaturan | – |
| Panel mobil | Fortuner putih 360° (berputar mengikuti jari, berhenti saat jari dilepas, kembali ke sudut awal setelah 8 detik), kecepatan, status GPS, Trip GPS (tekan lama untuk reset) | 72 gambar render dari model 3D, GPS headunit |
| Kartu utama | YouTube (aplikasi YouTube; bila belum terpasang, menawarkan Play Store), Navigasi (Maps/Waze/dll.), Al-Qur'an | Aplikasi terpasang |
| Widget Al-Qur'an | Ayat Arab (RTL), transliterasi, terjemahan, ayat sebelum/berikut, putar/jeda, buka mushaf, pilihan tampilan | `assets/quran` (equran.id / Kemenag RI) |
| Halaman Al-Qur'an | 114 surah, 6.236 ayat, murattal per ayat yang lanjut otomatis, unduh per surah atau semua | idem, audio dari cdn.equran.id |
| Murattal offline | Audio diunduh ke `Android/data/id.headunit.fortuner/files/murattal` (±1,5–2 GB untuk semua). Unduh otomatis di Wi-Fi rumah; bisa dijeda/dihapus di Pengaturan | cdn.equran.id |
| Doa naik kendaraan | Saat headunit menyala (boot, atau layar menyala lagi setelah mati ≥1 menit): overlay doa QS. Az-Zukhruf 13–14 + audio di speaker, tanpa internet | `assets/doa` |
| Dock | Musik, Bluetooth, Radio, Video, File Manager, Equalizer, Car Link, DSP | Aplikasi TW headunit |
| Panel kontrol | Pengaturan kendaraan (CarSetting), volume | AudioManager |

## Keterbatasan (jujur)

- **Kecepatan dan trip berasal dari GPS, bukan dari kendaraan.** Trip dihitung hanya selama launcher
  berjalan di depan. BBM dan posisi transmisi tidak ditampilkan karena datanya tidak tersedia.
- **Cuaca** butuh lokasi dan internet. Tanpa keduanya tertulis "Menunggu lokasi" atau "Cuaca offline",
  bukan angka contoh.
- **Ayat panjang di widget Beranda** ditampilkan sebanyak yang muat: transliterasi disembunyikan dulu, lalu
  terjemahan dipotong rapi dengan "…". Ketuk ayat untuk membaca lengkap di halaman Al-Qur'an.
- **Murattal** surah yang belum diunduh masih di-stream (butuh internet). Unduh otomatis tidak berjalan di
  hotspot HP (jaringan berbayar); tombol "Unduh semua" tetap bisa dipakai di jaringan apa pun.
- **Audio doa** adalah tilawah QS. Az-Zukhruf 43:13–14 oleh Misyari Rasyid Al-Afasi. Ayat 13 diputar mulai
  detik 13,08 (awal "subḥānal-lażī", diukur dari analisis audio) sehingga "wa taqūlū" tidak ikut, lalu ayat 14
  menyambung tanpa jeda.
- **Pemicu doa** = headunit restart, atau layar/ACC menyala setelah headunit tidur minimal 1 menit. Riwayat
  pemicu tampil di **Pengaturan › Doa perjalanan** untuk memeriksa apakah doa terpicu.
- **Overlay doa** butuh izin "tampil di atas aplikasi lain" (sudah diberikan lewat ADB; bisa juga lewat
  Pengaturan). Tanpa izin itu doa hanya diputar dan teksnya tampil di notifikasi.
- **Car Link**: di headunit ini belum ada aplikasinya (Zlink/EasyConnection/Carbit/AutoKit), jadi
  pintasannya redup dan menjelaskan hal itu. **Equalizer** dan **DSP** membuka aplikasi `com.tw.eq` yang sama.
- **Tombol setir** baru mengendalikan murattal saat murattal diputar. Saat dijeda atau dihentikan,
  kendali kembali ke musik/radio bawaan.

## Build

Butuh JDK 17 dan Android SDK (`build-tools/34.0.0` + `platforms/android-34`). Gradle tidak diperlukan.
Script mencari JDK di `%LOCALAPPDATA%\Programs\jdk-17*` dan SDK di `%LOCALAPPDATA%\Android\Sdk`; bisa
diganti lewat variabel `JAVA_HOME` dan `ANDROID_HOME`.

Jalankan di Git Bash:

```bash
./build.sh
```

APK: `build/FortunerLauncher-1.0.0.apk`. Build pertama membuat kunci tanda tangan di `keystore/`.
**Simpan folder itu**: update berikutnya harus ditandatangani dengan kunci yang sama.

Pasang ke headunit lewat ADB (`DEVICE` default `192.168.18.136:5555`):

```bash
./build.sh install
```

Setelah memasang launcher apa pun, Android bisa menanyakan ulang launcher default saat tombol Home ditekan.
Untuk tetap memakai Dofun sebagai default:

```bash
adb -s 192.168.18.136:5555 shell cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher
```

## Pengaturan khusus headunit TW (sekali, lewat ADB)

Layanan bawaan headunit (`com.tw.service`) mematikan paksa semua aplikasi saat kunci kontak OFF, kecuali yang
tercantum di `/sdcard/.Tcfg/sleepKillPackage.cfg`. Dofun juga membuka launchernya sendiri saat headunit menyala.
Agar doa naik kendaraan terpicu dan Fortuner Launcher yang tampil setelah menyala, jalankan:

```bash
adb -s 192.168.18.136:5555 shell "echo id.headunit.fortuner > /sdcard/.Tcfg/sleepKillPackage.cfg"
adb -s 192.168.18.136:5555 shell appops set id.headunit.fortuner SYSTEM_ALERT_WINDOW allow
adb -s 192.168.18.136:5555 shell appops set id.headunit.fortuner GET_USAGE_STATS allow
adb -s 192.168.18.136:5555 shell pm grant id.headunit.fortuner android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.18.136:5555 shell cmd package set-home-activity id.headunit.fortuner/.LauncherActivity
```

- `sleepKillPackage.cfg`: launcher tidak dimatikan saat OFF (satu nama paket per baris).
- `SYSTEM_ALERT_WINDOW`: overlay doa tampil di atas aplikasi lain, dan launcher boleh kembali ke depan.
- `GET_USAGE_STATS`: mengenali launcher Dofun yang muncul sendiri setelah menyala. Hanya launcher Dofun yang
  diganti; kamera mundur, musik, Maps, dll. tidak pernah ditimpa.
- `WRITE_SECURE_SETTINGS`: menyalakan Lokasi otomatis saat tersambung internet.

Launcher menjalankan layanan siaga kecil (notifikasi "Fortuner Launcher siaga", prioritas minimum) agar pendengar
"headunit menyala" selalu aktif.

## Berpindah launcher

- **Membuka Fortuner Launcher tanpa menjadikannya default:** buka ikon *Fortuner Launcher* dari daftar
  aplikasi Dofun.
- **Menjadikannya default:** di Fortuner Launcher buka **Pengaturan › Jadikan launcher utama › Pilih**, lalu
  pilih Fortuner Launcher.
- **Kembali ke Dofun:** **Pengaturan › Buka launcher bawaan**. Untuk mengubah default lagi, pakai
  *Jadikan launcher utama* lalu pilih Dofun.

## Struktur

```
AndroidManifest.xml        HOME + LAUNCHER, izin, <queries>, layanan murattal
build.sh                   aapt2 → javac → d8 → zipalign → apksigner
res/drawable/ic_*.xml      ikon vektor (dibuat oleh tools/gen_icons.mjs)
assets/img/car360/         72 gambar Fortuner 360° (dibuat oleh tools/car360)
assets/quran/              teks Al-Qur'an (dibuat oleh tools/build_quran.mjs)
assets/fonts/              Noto Naskh Arabic (Google Fonts, SIL OFL 1.1) untuk teks Arab
assets/doa/                teks & audio doa naik kendaraan (dibuat oleh tools/build_doa.mjs)
src/id/headunit/fortuner/
  LauncherActivity         susunan halaman, lifecycle, receiver status, izin
  HomeDashboard            halaman Beranda
  TopStatusBar, NavigationSidebar, VehiclePanel, AppShortcutCard,
  QuranWidget, BottomAppDock, VehicleControlBar   komponen Beranda
  AppsPage, SettingsPage, QuranPage               halaman lain
  AppLauncherRepository    kandidat aplikasi, intent, fallback, pesan "tidak terpasang"
  QuranRepository, QuranPlayer, QuranAudioService data, audio, foreground service + MediaSession
  QuranAudioStore, QuranDownloadService           murattal offline (lokasi file, unduhan latar)
  FortunerApp, DoaTrigger, BootReceiver, DoaService  pemicu saat headunit menyala, overlay + audio doa
  LocationTracker, WeatherRepository              GPS dan cuaca
  Ui, Icons, Prefs         tema/skala, ikon & ornamen yang digambar, preferensi
```

Semua ukuran ditulis untuk layar acuan 1280×680, lalu diskalakan ke ukuran jendela sebenarnya. UI
dibangun ulang otomatis bila ukuran layar berubah.

## Data Al-Qur'an

`node tools/build_quran.mjs` mengunduh ulang 114 surah dari API equran.id v2, lalu memeriksa nomor dan
urutan ayat serta total 6.236 ayat. Teks Arab, Latin, dan terjemahan disimpan apa adanya, tanpa diubah.

Teks Arab ditampilkan dengan Noto Naskh Arabic versi terbaru dari repositori Google Fonts (lisensi di
`assets/fonts/OFL-NotoNaskhArabic.txt`). Font bawaan Android 10 belum punya tanda waqaf U+08D6 (muncul 562 kali),
sehingga tanda itu tampil sebagai kotak. `node tools/check_font.mjs` memastikan font mencakup semua
karakter yang dipakai teks Al-Qur'an.

## Mobil 360°

Panel mobil memakai 72 gambar WebP (tiap 5°, latar transparan, total ±2 MB) yang dirender dari model 3D:

> **"2015 Toyota Fortuner"** oleh **BHP3D** ([sketchfab.com/BHP3D](https://sketchfab.com/BHP3D)),
> [sumber](https://sketchfab.com/3d-models/2015-toyota-fortuner-663a6ab1ca4248acb3d0468cb23ad52b),
> lisensi [CC BY 4.0](http://creativecommons.org/licenses/by/4.0/). Diubah: cat bodi putih pearl dengan
> clearcoat, velg gunmetal gelap, kaca jendela digelapkan, lalu dirender menjadi 72 gambar.

Kredit yang sama tampil di **Pengaturan › Tentang**, sesuai syarat lisensi CC BY.

Render ulang (misalnya untuk mengganti warna atau model), tanpa Blender:

```bash
node tools/car360/server.mjs "E:\Downloads\2015_toyota_fortuner.glb"
```

Lalu buka `http://127.0.0.1:8768/?mode=sheet` di browser untuk pratinjau 8 sudut, dan
`http://127.0.0.1:8768/?mode=full&n=72&q=0.85&exp=0.85&paint=%23eeede9&wheel=%234b4f55&w=900&h=520&fill=0.9`
untuk menulis ulang `assets/img/car360/`. Parameter `paint` (warna cat), `wheel` (warna velg), `exp`
(kecerahan), dan `elev` (tinggi kamera) bisa diubah. Model ini Fortuner facelift 2015 (generasi pertama),
bukan persis TRD Sportivo 2010.

## Debloat headunit

Catatan dan skrip debloat headunit (aplikasi yang dinonaktifkan, backup daftar paket, skrip pemulihan) ada di
[debloat/README.md](debloat/README.md).
