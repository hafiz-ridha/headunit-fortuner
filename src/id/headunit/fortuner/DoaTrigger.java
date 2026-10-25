package id.headunit.fortuner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Menentukan kapan headunit "dinyalakan" lalu memutar doa naik kendaraan sekali.
 * Headunit ini tidak mati total saat kunci kontak (ACC) dimatikan: layanan TW menidurkan Android (deep sleep),
 * lalu membangunkannya lagi saat ACC hidup. Doa diputar bila salah satu terjadi:
 * - BOOT_COMPLETED (headunit benar-benar dihidupkan ulang);
 * - layar menyala / ACC_ON setelah kunci kontak OFF minimal {@link #MIN_OFF_MS};
 * - proses launcher baru dimulai setelah tidur (mis. launcher ditutup sistem saat tidur).
 * "Sudah tidur" dihitung dari dua sumber agar tidak terlewat: waktu SCREEN_OFF terakhir, dan total waktu
 * deep sleep (elapsedRealtime − uptimeMillis) yang bertambah sejak doa terakhir. Riwayat pemicu disimpan
 * dan tampil di Pengaturan.
 */
final class DoaTrigger {
    static final String ENABLED = "doa_enabled";
    private static final String OFF_AT = "doa_screen_off_at";
    private static final String LAST_AT = "doa_last_at";
    private static final String ON_AT = "doa_screen_on_at";
    private static final String SLEEP_BASE = "doa_sleep_base";
    private static final String LOG = "doa_log";
    private static final long MIN_OFF_MS = 30_000;
    private static final long MIN_GAP_MS = 2 * 60_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean registered;

    private DoaTrigger() { }

    /** Dipanggil dari Application.onCreate: daftarkan pendengar dan cek pemicu yang terlewat. */
    static void install(Context c) {
        Context app = c.getApplicationContext();
        if (!registered) {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            f.addAction(Intent.ACTION_USER_PRESENT);
            // Siaran ACC dari layanan headunit (didengarkan juga oleh com.tw.core di unit ini).
            f.addAction("com.unisound.intent.action.ACC_ON");
            f.addAction("com.unisound.intent.action.ACC_OFF");
            f.addAction("com.unisound.intent.action.DO_WAKEUP");
            f.addAction("com.unisound.intent.action.DO_SLEEP");
            f.addAction("com.unisound.intent.action.DO_SHUTDOWN"); // dikirim layanan TW saat kunci kontak OFF
            app.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context ctx, Intent i) {
                    String a = String.valueOf(i.getAction());
                    if (a.endsWith("SCREEN_OFF") || a.endsWith("ACC_OFF") || a.endsWith("DO_SLEEP")
                            || a.endsWith("DO_SHUTDOWN")) onOff(ctx, a);
                    else onOn(ctx, a);
                }
            }, f);
            registered = true;
        }
        SharedPreferences sp = Prefs.get(app);
        if (sp.getLong(SLEEP_BASE, -1) < 0) sp.edit().putLong(SLEEP_BASE, slept()).apply();
        long offAt = sp.getLong(OFF_AT, 0), lastAt = sp.getLong(LAST_AT, 0);
        boolean missed = offAt > lastAt && System.currentTimeMillis() - offAt >= MIN_OFF_MS;
        if (missed || sleptSinceLastDoa(app)) schedule(app, 4000, "launcher dimulai setelah tidur");
    }

    static void onBoot(Context c) {
        Prefs.get(c).edit().putLong(SLEEP_BASE, 0).apply();
        schedule(c.getApplicationContext(), 8000, "headunit dinyalakan (boot)");
    }

    /** Total waktu deep sleep sejak boot. */
    private static long slept() { return SystemClock.elapsedRealtime() - SystemClock.uptimeMillis(); }

    private static boolean sleptSinceLastDoa(Context c) {
        long base = Prefs.get(c).getLong(SLEEP_BASE, -1);
        long now = slept();
        return base >= 0 && (now < base || now - base >= MIN_OFF_MS); // now < base: sudah boot ulang
    }

    private static void onOff(Context c, String action) {
        // Catat saat PERTAMA kali mati (DO_SHUTDOWN datang ±10 dtk sebelum SCREEN_OFF), jangan ditimpa.
        SharedPreferences sp = Prefs.get(c);
        long offAt = sp.getLong(OFF_AT, 0), onAt = sp.getLong(ON_AT, 0);
        if (offAt <= onAt) sp.edit().putLong(OFF_AT, System.currentTimeMillis()).apply();
        log(c, "mati: " + shortName(action));
    }

    private static void onOn(Context c, String action) {
        long offAt = Prefs.get(c).getLong(OFF_AT, 0);
        if (!action.endsWith("USER_PRESENT")) {
            Prefs.get(c).edit().putLong(ON_AT, System.currentTimeMillis()).apply();
            HomeGuard.afterWake(c);
        }
        boolean longOff = offAt != 0 && System.currentTimeMillis() - offAt >= MIN_OFF_MS;
        if (action.endsWith("USER_PRESENT") && !longOff && !sleptSinceLastDoa(c)) return;
        log(c, "nyala: " + shortName(action));
        if (longOff || sleptSinceLastDoa(c)) schedule(c, 3000, "headunit menyala (" + shortName(action) + ")");
    }

    private static String shortName(String action) {
        return action.substring(action.lastIndexOf('.') + 1);
    }

    private static void schedule(Context c, long delayMs, String reason) {
        if (!Prefs.flag(c, ENABLED, true)) return;
        SharedPreferences sp = Prefs.get(c);
        long now = System.currentTimeMillis();
        if (now - sp.getLong(LAST_AT, 0) < MIN_GAP_MS) return;
        sp.edit().putLong(LAST_AT, now).putLong(SLEEP_BASE, slept()).apply();
        log(c, "doa diputar: " + reason);
        Log.i(AppLauncherRepository.TAG, "Doa naik kendaraan dijadwalkan: " + reason);
        MAIN.postDelayed(() -> start(c), delayMs);
    }

    /** Putar doa sekarang (juga dipakai tombol "Coba doa sekarang" di Pengaturan). */
    static void start(Context c) {
        Intent i = new Intent(c, DoaService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Doa tidak bisa dimulai", e);
            log(c, "gagal memulai doa: " + e.getMessage());
        }
    }

    /** Riwayat singkat (maks. 8 baris terbaru) untuk ditampilkan di Pengaturan. */
    private static void log(Context c, String line) {
        SharedPreferences sp = Prefs.get(c);
        String stamp = new SimpleDateFormat("dd/MM HH:mm", new Locale("id", "ID")).format(new Date());
        String all = stamp + "  " + line + "\n" + sp.getString(LOG, "");
        String[] lines = all.split("\n");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(8, lines.length); i++) {
            if (lines[i].isEmpty()) continue;
            if (b.length() > 0) b.append('\n');
            b.append(lines[i]);
        }
        sp.edit().putString(LOG, b.toString()).apply();
    }

    static String history(Context c) {
        String s = Prefs.get(c).getString(LOG, "");
        return s.isEmpty() ? "Belum ada catatan. Riwayat muncul setelah headunit tidur lalu menyala." : s;
    }
}
