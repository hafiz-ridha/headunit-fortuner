package id.headunit.fortuner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Layanan siaga ringan (tanpa kerja berkala) yang menjaga proses launcher tetap hidup, supaya pendengar
 * layar menyala/ACC di {@link DoaTrigger} selalu aktif: doa naik kendaraan diputar dan launcher ini kembali
 * ke depan setiap headunit menyala. Bersama /sdcard/.Tcfg/sleepKillPackage.cfg (daftar pengecualian layanan TW),
 * proses tidak ikut dimatikan saat kunci kontak OFF.
 */
public class KeepAliveService extends Service {
    private static final String CHANNEL = "siaga";
    private static final int ID = 6;

    static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Layanan siaga tidak bisa dimulai", e);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Launcher siaga", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this).setPriority(Notification.PRIORITY_MIN);
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        startForeground(ID, b.setSmallIcon(R.drawable.ic_car)
                .setContentTitle("Fortuner Launcher siaga")
                .setContentText("Doa naik kendaraan diputar saat headunit menyala")
                .setContentIntent(open)
                .setShowWhen(false)
                .build());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
