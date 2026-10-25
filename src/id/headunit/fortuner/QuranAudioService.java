package id.headunit.fortuner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Layanan foreground agar murattal tetap berbunyi saat Maps atau aplikasi lain dibuka. */
public class QuranAudioService extends Service implements QuranPlayer.Listener {
    private static final String CHANNEL = "quran";
    private static final int ID = 7;
    private static final String ACTION_TOGGLE = "id.headunit.fortuner.TOGGLE";
    private static final String ACTION_NEXT = "id.headunit.fortuner.NEXT";
    private static final String ACTION_STOP = "id.headunit.fortuner.STOP";

    private QuranPlayer player;

    @Override
    public void onCreate() {
        super.onCreate();
        player = QuranPlayer.get(this);
        player.addListener(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Murattal Al-Qur'an", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) player.toggle();
        else if (ACTION_NEXT.equals(action)) player.next();
        else if (ACTION_STOP.equals(action)) {
            player.stop();
            return START_NOT_STICKY;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, build());
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onQuranChanged() {
        if (!player.isPlaying()) {
            stopForeground(false);
        }
        getSystemService(NotificationManager.class).notify(ID, build());
    }

    @SuppressWarnings("deprecation")
    private Notification build() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, LauncherActivity.class).setAction(LauncherActivity.ACTION_OPEN_QURAN)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
        boolean playing = player.isPlaying();
        return b.setSmallIcon(R.drawable.ic_book)
                .setContentTitle(player.title())
                .setContentText(player.error() != null ? player.error() : playing ? "Murattal · Misyari Rasyid Al-Afasi" : "Dijeda")
                .setContentIntent(open)
                .setOngoing(playing)
                .setShowWhen(false)
                .addAction(new Notification.Action.Builder(null, playing ? "Jeda" : "Putar", action(ACTION_TOGGLE, 1)).build())
                .addAction(new Notification.Action.Builder(null, "Berikutnya", action(ACTION_NEXT, 2)).build())
                .addAction(new Notification.Action.Builder(null, "Stop", action(ACTION_STOP, 3)).build())
                .build();
    }

    private PendingIntent action(String a, int code) {
        return PendingIntent.getService(this, code, new Intent(this, QuranAudioService.class).setAction(a),
                PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        player.removeListener(this);
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
