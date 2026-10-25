package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Halaman Pengaturan launcher. */
final class SettingsPage extends ScrollView {
    private final LauncherActivity a;
    private final AppLauncherRepository repo;
    private final LocationTracker tracker;
    private final LinearLayout list;

    @SuppressLint("ViewConstructor")
    SettingsPage(LauncherActivity a, AppLauncherRepository repo, LocationTracker tracker) {
        super(a);
        this.a = a;
        this.repo = repo;
        this.tracker = tracker;
        list = Ui.column(a);
        list.setPadding(Ui.px(24), Ui.px(8), Ui.px(28), Ui.px(24));
        addView(list);
        rebuild();
    }

    void rebuild() {
        list.removeAllViews();
        list.addView(Ui.text(a, "Pengaturan", 30, Ui.TEXT, Ui.MEDIUM));

        section("LAUNCHER");
        boolean isDefault = repo.isDefaultHome();
        action("Jadikan launcher utama", isDefault ? "Fortuner Launcher sudah menjadi tampilan utama."
                        : "Buka pilihan aplikasi Home lalu pilih Fortuner Launcher. Bisa dikembalikan kapan saja.",
                isDefault ? "Ubah" : "Pilih", () -> {
                    Intent i = new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (i.resolveActivity(a.getPackageManager()) == null) {
                        i = new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    }
                    repo.start(a, i);
                });
        final ResolveInfo other = repo.otherHome();
        if (other != null) {
            final PackageManager pm = a.getPackageManager();
            final String name = other.loadLabel(pm).toString();
            action("Buka launcher bawaan", "Pindah ke " + name + " tanpa mengubah launcher utama.", "Buka", () ->
                    repo.start(a, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .setComponent(new ComponentName(other.activityInfo.packageName, other.activityInfo.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        toggle("Layar penuh", "Sembunyikan status bar sistem. Tombol navigasi sistem tetap tampil.",
                Prefs.FULLSCREEN, false, a::applyWindowMode);

        section("AL-QUR'AN");
        toggle("Transliterasi Latin", null, Prefs.SHOW_LATIN, true, a::refreshQuran);
        toggle("Terjemahan Indonesia", "Terjemahan Kemenag RI.", Prefs.SHOW_TRANSLATION, true, a::refreshQuran);
        info("Sumber teks", "equran.id (Al-Qur'an & terjemahan Kemenag RI), disimpan offline: 114 surah, 6.236 ayat. "
                + "Murattal Misyari Rasyid Al-Afasi dari cdn.equran.id.");

        section("MURATTAL OFFLINE");
        int done = QuranAudioStore.completeSurahs(a);
        String status = QuranDownloadService.running ? "Sedang mengunduh. Progres tampil di halaman Al-Qur'an dan notifikasi."
                : done + " dari 114 surah tersimpan (" + (QuranAudioStore.bytesUsed(a) / (1024 * 1024)) + " MB). "
                + (done == 114 ? "Semua surah bisa diputar tanpa internet." : "Surah yang belum diunduh butuh internet.");
        if (QuranDownloadService.running) {
            action("Unduh semua surah", status, "Jeda", () -> {
                QuranDownloadService.stop(a);
                rebuild();
            });
        } else if (done < 114) {
            action("Unduh semua surah", status + " Total sekitar 1,5–2 GB.", "Unduh", () -> {
                QuranDownloadService.downloadAll(a);
                rebuild();
            });
        } else {
            info("Unduh semua surah", status);
        }
        toggle("Unduh otomatis saat Wi-Fi", "Melanjutkan unduhan sendiri bila tersambung Wi-Fi rumah. "
                + "Tidak berjalan di hotspot HP agar kuota aman.", QuranDownloadService.AUTO, true,
                () -> QuranDownloadService.resumeIfWanted(a));
        if (done > 0 && !QuranDownloadService.running) {
            action("Hapus murattal offline", "Mengosongkan ruang penyimpanan. Teks Al-Qur'an tetap ada.", "Hapus",
                    () -> new AlertDialog.Builder(a).setTitle("Hapus murattal offline?")
                            .setPositiveButton("Hapus", (d, w) -> {
                                Prefs.set(a, QuranDownloadService.ALL, false);
                                Prefs.set(a, QuranDownloadService.AUTO, false);
                                QuranAudioStore.deleteAll(a);
                                rebuild();
                            }).setNegativeButton("Batal", null).show());
        }

        section("DOA PERJALANAN");
        toggle("Doa naik kendaraan", "Saat headunit menyala (setelah mati/tidur minimal 1 menit, atau restart), "
                + "tampilkan doa (QS. Az-Zukhruf: 13–14) dan putar bacaannya di speaker. Berjalan tanpa internet.",
                DoaTrigger.ENABLED, true, () -> { });
        boolean overlay = android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(a);
        if (!overlay) {
            action("Izin tampil di atas aplikasi lain", "Dibutuhkan agar doa tampil di atas Maps, musik, dan lainnya. "
                    + "Tanpa izin ini doa hanya diputar.", "Izinkan", () -> repo.start(a,
                    new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + a.getPackageName()))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        action("Coba doa sekarang", "Menampilkan dan memutar doa naik kendaraan.", "Putar", () -> DoaTrigger.start(a));
        info("Riwayat pemicu doa", DoaTrigger.history(a));

        section("APLIKASI");
        action("Pengaturan kendaraan", "Membuka aplikasi CarSetting headunit.", "Buka",
                () -> repo.open(a, AppLauncherRepository.Shortcut.VEHICLE));

        section("LOKASI & DATA");
        String perm = tracker.hasPermission() ? "Diizinkan. Dipakai untuk kecepatan, trip, dan cuaca."
                : "Belum diizinkan. Kecepatan, trip, dan cuaca tidak bisa tampil.";
        action("Izin lokasi", perm, tracker.hasPermission() ? "Info" : "Izinkan", () -> {
            if (tracker.hasPermission()) {
                repo.start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + a.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else {
                a.requestLocationPermission();
            }
        });
        action("Reset Trip GPS", String.format(Locale.US, "Trip saat ini %.1f km. Dihitung dari GPS selama launcher berjalan.",
                tracker.tripKm()), "Reset", () -> new AlertDialog.Builder(a).setTitle("Reset Trip GPS?")
                .setPositiveButton("Reset", (d, w) -> {
                    tracker.resetTrip();
                    rebuild();
                }).setNegativeButton("Batal", null).show());
        toggle("Cuaca", "Suhu dan kondisi aktual dari Open-Meteo sesuai lokasi headunit.",
                Prefs.WEATHER, true, a::refreshWeather);

        section("TENTANG");
        String version = "1.0";
        try {
            version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            // versi tetap default
        }
        info("Fortuner Launcher " + version, "Launcher tambahan; launcher bawaan tidak diubah atau dihapus. "
                + "Kecepatan dan trip berasal dari GPS, bukan dari kendaraan. Data BBM dan transmisi belum "
                + "terhubung karena headunit tidak menyediakan API untuk aplikasi biasa.");
        info("Model 3D mobil", "\"2015 Toyota Fortuner\" oleh BHP3D (sketchfab.com/BHP3D), lisensi CC BY 4.0. "
                + "Diubah: cat putih, velg gelap, kaca gelap; dirender menjadi 72 gambar 360°.");
    }

    private void section(String title) {
        TextView t = Ui.text(a, title, 14, Ui.ACCENT, Ui.MEDIUM);
        t.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(26);
        lp.bottomMargin = Ui.px(8);
        list.addView(t, lp);
    }

    private LinearLayout rowBase(String title, String subtitle) {
        LinearLayout row = Ui.row(a);
        row.setPadding(Ui.px(20), Ui.px(16), Ui.px(16), Ui.px(16));
        row.setBackground(Ui.panel(16));
        row.setMinimumHeight(Ui.px(76));
        LinearLayout texts = Ui.column(a);
        texts.addView(Ui.text(a, title, 20, Ui.TEXT, Ui.MEDIUM));
        if (subtitle != null) {
            TextView s = Ui.text(a, subtitle, 15, Ui.TEXT2, Ui.REGULAR);
            s.setLineSpacing(Ui.px(3), 1f);
            texts.addView(s);
            ((LinearLayout.LayoutParams) s.getLayoutParams()).topMargin = Ui.px(6);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(10);
        list.addView(row, lp);
        return row;
    }

    private void action(String title, String subtitle, String button, Runnable run) {
        LinearLayout row = rowBase(title, subtitle);
        TextView b = Ui.text(a, button, 17, Ui.TEXT, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        b.setBackground(Ui.rounded(Ui.ACCENT, 12));
        b.setMinWidth(Ui.px(120));
        row.addView(Ui.space(a, 16, 1));
        row.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.px(52)));
        Ui.pressable(b);
        b.setOnClickListener(v -> run.run());
    }

    private void toggle(String title, String subtitle, String key, boolean def, Runnable onChange) {
        LinearLayout row = rowBase(title, subtitle);
        Switch s = new Switch(a);
        s.setChecked(Prefs.flag(a, key, def));
        s.setScaleX(Math.max(1f, Ui.px(100) / 75f));
        s.setScaleY(Math.max(1f, Ui.px(100) / 75f));
        row.addView(Ui.space(a, 16, 1));
        row.addView(s);
        s.setOnCheckedChangeListener((v, on) -> {
            Prefs.set(a, key, on);
            onChange.run();
        });
        row.setOnClickListener(v -> s.toggle());
    }

    private void info(String title, String subtitle) {
        rowBase(title, subtitle);
    }
}
