# Tutorial Singkat: Pasang Fortuner Launcher lewat ADB

Untuk headunit Android TS10 (UMS512, Android 10). Semua perintah dijalankan di **Command Prompt/PowerShell**
di PC Windows. PC dan headunit harus tersambung ke **Wi-Fi yang sama**.

> Lakukan saat mobil **diam/parkir**.

## 1. Siapkan PC

1. Unduh **Android SDK Platform-Tools** dari situs resmi Google:
   <https://developer.android.com/tools/releases/platform-tools>, lalu ekstrak, misalnya ke
   `C:\platform-tools`.
2. Buka Command Prompt di folder itu, atau tambahkan folder itu ke `PATH`, lalu cek:

   ```bat
   adb version
   ```

3. Siapkan APK `FortunerLauncher-1.0.0.apk` (hasil `./build.sh` ada di folder `build/`).

## 2. Nyalakan USB debugging di headunit

1. **Pengaturan › Tentang** lalu ketuk **Build number** 7 kali sampai muncul *"Anda sekarang developer"*.
2. **Pengaturan › Opsi developer**, lalu nyalakan **USB debugging**. Headunit ini meminta **kode pabrik**; pakai
   kode yang berhasil sebelumnya (kode umum headunit China: `7890`, `8888`, `1617`, `3368`, `1234`).

## 3. Buka ADB lewat Wi-Fi (port 5555)

USB debugging saja hanya berlaku lewat kabel. Agar bisa lewat Wi-Fi, port **5555** harus dibuka:

- **Lewat Termux di headunit**: pakai cara yang sudah berhasil sebelumnya.
- **Lewat kabel USB** (bila port USB headunit mendukung mode device/OTG): colok ke PC lalu jalankan
  `adb tcpip 5555`, setelah itu kabel boleh dicabut.

Catatan: port ini biasanya tertutup lagi setelah headunit **restart penuh**, jadi ulangi langkah ini bila perlu.

## 4. Sambungkan PC ke headunit

1. Lihat IP headunit di **Pengaturan › Wi-Fi › (jaringan yang tersambung)**, mis. `192.168.18.136`.
2. Sambungkan:

   ```bat
   adb connect 192.168.18.136:5555
   adb devices
   ```

3. Saat pertama kali, di layar headunit muncul **"Allow USB debugging?"**: centang **Always allow**, lalu
   ketuk **Allow**. Jalankan `adb devices` lagi; statusnya harus **`device`**.

| Status / pesan | Artinya | Solusi |
|---|---|---|
| `actively refused` | Port 5555 belum terbuka | Ulangi langkah 3 |
| `unauthorized` | Belum diizinkan | Ketuk **Allow** di headunit |
| `offline` | Koneksi putus | `adb disconnect` lalu `adb connect` lagi |
| `Destination host unreachable` | IP berubah / headunit mati | Cek IP di langkah 4.1 |

Agar IP tidak berubah-ubah, atur **IP statis/DHCP reservation** untuk headunit di router.

## 5. Pasang launcher

```bat
adb -s 192.168.18.136:5555 install -r FortunerLauncher-1.0.0.apk
```

Harus muncul `Success`.

## 6. Berikan izin & pengaturan khusus (sekali saja)

```bat
adb -s 192.168.18.136:5555 shell "echo id.headunit.fortuner > /sdcard/.Tcfg/sleepKillPackage.cfg"
adb -s 192.168.18.136:5555 shell appops set id.headunit.fortuner SYSTEM_ALERT_WINDOW allow
adb -s 192.168.18.136:5555 shell appops set id.headunit.fortuner GET_USAGE_STATS allow
adb -s 192.168.18.136:5555 shell pm grant id.headunit.fortuner android.permission.WRITE_SECURE_SETTINGS
adb -s 192.168.18.136:5555 shell pm grant id.headunit.fortuner android.permission.ACCESS_FINE_LOCATION
adb -s 192.168.18.136:5555 shell cmd package set-home-activity id.headunit.fortuner/.LauncherActivity
adb -s 192.168.18.136:5555 shell cmd notification allow_listener id.headunit.fortuner/id.headunit.fortuner.NavNotificationListener
adb -s 192.168.18.136:5555 shell appops set com.google.android.apps.maps PICTURE_IN_PICTURE allow
```

| Perintah | Fungsinya |
|---|---|
| `sleepKillPackage.cfg` | Launcher tidak dimatikan sistem saat kunci kontak OFF |
| `SYSTEM_ALERT_WINDOW` | Overlay doa tampil di atas aplikasi lain |
| `GET_USAGE_STATS` | Launcher kembali ke depan bila Dofun muncul sendiri setelah menyala |
| `WRITE_SECURE_SETTINGS` | Lokasi menyala otomatis saat tersambung internet |
| `ACCESS_FINE_LOCATION` | Kecepatan, trip GPS, dan cuaca |
| `set-home-activity` | Fortuner Launcher jadi launcher utama |
| `allow_listener` | Panel navigasi Google Maps/Waze menggantikan gambar mobil saat navigasi |
| `PICTURE_IN_PICTURE` | Peta Google Maps bisa dipasang di panel mobil (sekali saja: **Pengaturan › Navigasi › ADB lokal untuk peta › Hubungkan**, centang *Always allow*, **Allow**) |

## 7. Cek hasilnya

1. Tekan **Home**: yang tampil harus Fortuner Launcher.
2. **Pengaturan › Doa perjalanan › Coba doa sekarang**: overlay dan bacaan doa harus muncul.
3. **Al-Qur'an › Unduh semua** (±1,5–2 GB, sebaiknya lewat Wi-Fi rumah) agar murattal bisa diputar tanpa internet.
4. Matikan mobil minimal 30 detik lalu nyalakan: Fortuner Launcher tampil dan doa diputar. Riwayatnya ada di
   **Pengaturan › Doa perjalanan › Riwayat pemicu doa**.

### Kamera mundur bergaris/terpotong-potong

Bila gambar kamera mundur rusak (bergaris, terpotong, berulang), format video kamera tidak cocok. Buka
**Pengaturan Kendaraan › Reverse setting › HD selection** dan pilih **NTSC** (format yang cocok untuk kamera mobil
ini), lalu masukkan gigi R untuk mengecek.

## 8. Update dan kembali ke launcher bawaan

- **Update**: ulangi langkah 4 dan 5. Pengaturan dan murattal yang sudah diunduh tetap ada.
  APK update harus ditandatangani dengan kunci yang sama (folder `keystore/`).
- **Kembali ke Dofun**:

  ```bat
  adb -s 192.168.18.136:5555 shell cmd package set-home-activity com.dofun.variety/com.dofun.overseasvariety.Launcher
  ```

## 9. Selesai: tutup kembali ADB Wi-Fi

Setelah semua beres, **restart headunit** tanpa membuka port 5555 lagi, supaya perangkat lain di jaringan yang
sama tidak bisa mengakses headunit lewat ADB.

---

Opsional: catatan debloat (aplikasi bawaan yang dinonaktifkan dan cara mengembalikannya) ada di
[debloat/README.md](../debloat/README.md).
