package id.headunit.fortuner;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

/**
 * Mendeteksi kamera mundur / kamera samping headunit (activity com.tw.reverse dan com.tw.rightview) dari
 * riwayat aplikasi di depan. Dipakai agar launcher TIDAK PERNAH menutupi atau menyela kamera:
 * overlay doa disembunyikan dan launcher tidak dibawa ke depan selama kamera aktif.
 * Butuh izin akses penggunaan (appops GET_USAGE_STATS); tanpa izin dianggap tidak bisa dipastikan.
 */
final class CarCamera {
    private static final String[] CAMERA_PACKAGES = {"com.tw.reverse", "com.tw.rightview", "com.baony.tw360"};

    private CarCamera() { }

    private static boolean isCamera(String pkg) {
        for (String p : CAMERA_PACKAGES) if (p.equals(pkg)) return true;
        return false;
    }

    /** True bila kamera sedang tampil, atau baru saja tampil/tertutup dalam {@code recentMs} terakhir. */
    static boolean activeOrRecent(Context c, long recentMs) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null || !HomeGuard.hasUsageAccess(c)) return false;
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - 10 * 60_000L, now);
        UsageEvents.Event e = new UsageEvents.Event();
        boolean inFront = false;
        long lastSeen = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            if (type != UsageEvents.Event.MOVE_TO_FOREGROUND && type != UsageEvents.Event.MOVE_TO_BACKGROUND) continue;
            if (isCamera(e.getPackageName())) {
                inFront = type == UsageEvents.Event.MOVE_TO_FOREGROUND;
                lastSeen = e.getTimeStamp();
            } else if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                inFront = false;
            }
        }
        return inFront || (lastSeen != 0 && now - lastSeen < recentMs);
    }

    static boolean active(Context c) { return activeOrRecent(c, 0); }
}
