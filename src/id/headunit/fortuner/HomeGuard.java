package id.headunit.fortuner;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

/**
 * Setelah headunit menyala, aplikasi Dofun membuka launchernya sendiri (bukan lewat tombol Home), sehingga
 * yang tampil launcher bawaan walaupun Fortuner Launcher adalah launcher utama. Kelas ini memeriksa aplikasi
 * yang sedang di depan beberapa kali setelah menyala; HANYA bila yang tampil launcher Dofun, Fortuner Launcher
 * dibawa ke depan. Kamera mundur, musik, Maps, dll. tidak pernah diganggu.
 * Butuh izin akses penggunaan (diberikan lewat ADB: appops set ... GET_USAGE_STATS allow).
 */
final class HomeGuard {
    private static final String DOFUN = "com.dofun.variety";
    private static final long[] CHECKS_MS = {1500, 4000, 8000, 15000};
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private HomeGuard() { }

    static void afterWake(Context c) {
        Context app = c.getApplicationContext();
        if (!new AppLauncherRepository(app).isDefaultHome() || !hasUsageAccess(app)) return;
        for (long d : CHECKS_MS) MAIN.postDelayed(() -> check(app), d);
    }

    private static void check(Context c) {
        String[] top = foreground(c);
        if (top == null || !DOFUN.equals(top[0]) || !top[1].endsWith("Launcher")) return;
        // Jangan pernah menyela kamera mundur/samping, termasuk yang baru saja dibuka atau ditutup.
        if (CarCamera.activeOrRecent(c, 20_000)) return;
        Log.i(AppLauncherRepository.TAG, "Launcher Dofun muncul sendiri setelah menyala; kembali ke Fortuner Launcher");
        try {
            c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClass(c, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Tidak bisa kembali ke Fortuner Launcher", e);
        }
    }

    /** Paket dan kelas activity yang terakhir pindah ke depan, atau null. */
    private static String[] foreground(Context c) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return null;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 60_000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String[] last = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = new String[]{e.getPackageName(), String.valueOf(e.getClassName())};
            }
        }
        return last;
    }

    @SuppressWarnings("deprecation")
    static boolean hasUsageAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(),
                c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
}
