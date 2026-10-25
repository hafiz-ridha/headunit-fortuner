# Debloat headunit

Catatan debloat headunit UMS512 (Android 10) yang dilakukan sebelum Fortuner Launcher dibuat.
Semua aplikasi hanya **dinonaktifkan** (`pm disable-user`), tidak di-uninstall.

- `debloat_list.txt`: daftar Tier 1 (sudah dinonaktifkan) dan Tier 2 (opsional, belum dinonaktifkan) beserta alasannya.
- `tier1.txt`: daftar paket Tier 1 saja.
- `backup/`: daftar paket sebelum debloat, log penonaktifan, dan pemakaian RAM sebelum debloat.
- `restore_tier1.sh`: mengaktifkan kembali semua paket Tier 1 dan mengembalikan skala animasi.

Mengembalikan semuanya:

```bash
adb -s 192.168.18.136:5555 shell < debloat/restore_tier1.sh
```

## Status

25/10/2026: semua 22 paket Tier 1 kembali dinonaktifkan. Sempat diaktifkan semua untuk menguji kamera mundur;
hasilnya debloat bukan penyebab (penyebabnya format video kamera, harus NTSC).
