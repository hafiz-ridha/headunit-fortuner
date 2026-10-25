package id.headunit.fortuner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Menampilkan overlay doa naik kendaraan di atas aplikasi apa pun dan memutar audionya lewat speaker.
 * Audio tersimpan di APK (assets/doa), jadi tetap jalan tanpa internet. Ayat 13 diputar mulai dari
 * "subḥānal-lażī" (titik mulai di doa_naik_kendaraan.json), lalu ayat 14 menyambung tanpa jeda. Overlay butuh izin "tampil di atas
 * aplikasi lain"; tanpa izin itu audio tetap diputar dan teks doa tampil di notifikasi.
 */
public class DoaService extends Service {
    private static final String CHANNEL = "doa";
    private static final int ID = 9;
    private static final long CLOSE_AFTER_AUDIO_MS = 5000;

    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private final java.util.List<MediaPlayer> players = new java.util.ArrayList<>();
    private View overlay;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private JSONObject doa;

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        doa = load();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Doa perjalanan", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(ID, notification());
        }
        if (player == null) {
            QuranPlayer.get(this).pause();
            showOverlay();
            play();
        }
        return START_NOT_STICKY;
    }

    private JSONObject load() {
        try (InputStream in = getAssets().open("doa/doa_naik_kendaraan.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(AppLauncherRepository.TAG, "Teks doa tidak terbaca", e);
            return new JSONObject();
        }
    }

    @SuppressWarnings("deprecation")
    private Notification notification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_car)
                .setContentTitle(doa.optString("title", "Doa Naik Kendaraan"))
                .setContentText(doa.optString("latin"))
                .setStyle(new Notification.BigTextStyle().bigText(doa.optString("latin") + "\n\n" + doa.optString("translation")))
                .setShowWhen(false)
                .build();
    }

    @SuppressWarnings("deprecation")
    private void play() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes()).build();
                audio.requestAudioFocus(focus);
            } else {
                audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
            // Potongan audio diputar berurutan tanpa jeda: ayat 13 mulai dari "subḥānal-lażī" lalu ayat 14.
            org.json.JSONArray parts = doa.optJSONArray("audio");
            MediaPlayer previous = null;
            for (int k = 0; parts != null && k < parts.length(); k++) {
                JSONObject part = parts.getJSONObject(k);
                MediaPlayer mp = new MediaPlayer();
                mp.setAudioAttributes(attributes());
                try (AssetFileDescriptor afd = getAssets().openFd("doa/" + part.getString("file"))) {
                    mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                }
                mp.setOnErrorListener((m, what, extra) -> {
                    Log.w(AppLauncherRepository.TAG, "Audio doa error " + what + "/" + extra);
                    main.postDelayed(this::finish, CLOSE_AFTER_AUDIO_MS);
                    return true;
                });
                mp.prepare();
                int start = part.optInt("startMs", 0);
                if (start > 0) {
                    if (Build.VERSION.SDK_INT >= 26) mp.seekTo(start, MediaPlayer.SEEK_CLOSEST);
                    else mp.seekTo(start);
                }
                if (previous == null) player = mp;
                else previous.setNextMediaPlayer(mp);
                players.add(mp);
                previous = mp;
            }
            if (previous == null) throw new IllegalStateException("audio doa kosong");
            previous.setOnCompletionListener(mp -> main.postDelayed(this::finish, CLOSE_AFTER_AUDIO_MS));
            final MediaPlayer first = player;
            if (parts.getJSONObject(0).optInt("startMs", 0) > 0) {
                first.setOnSeekCompleteListener(mp -> {
                    mp.setOnSeekCompleteListener(null);
                    mp.start();
                });
            } else {
                first.start();
            }
        } catch (Exception e) {
            Log.e(AppLauncherRepository.TAG, "Audio doa gagal diputar", e);
            main.postDelayed(this::finish, 15000);
        }
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    @SuppressWarnings("deprecation")
    private void showOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Log.w(AppLauncherRepository.TAG, "Izin overlay belum ada; doa hanya diputar dan tampil di notifikasi");
            return;
        }
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        Ui.initOnce(dm.widthPixels, dm.heightPixels);

        LinearLayout card = Ui.column(this);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Icons.quranCardBackground(Ui.px(26), false));
        card.setPadding(Ui.px(40), Ui.px(26), Ui.px(40), Ui.px(24));
        card.setElevation(Ui.px(12));

        LinearLayout head = Ui.row(this);
        head.addView(Ui.iconView(this, Icons.medallion(), 46));
        head.addView(Ui.space(this, 14, 1));
        head.addView(Ui.text(this, doa.optString("title", "Doa Naik Kendaraan"), 28, Ui.TEXT, Ui.MEDIUM));
        card.addView(head);

        TextView arabic = Ui.text(this, doa.optString("arabic"), 38, Ui.TEXT, Ui.arabic(this));
        arabic.setIncludeFontPadding(true);
        arabic.setGravity(Gravity.CENTER);
        arabic.setTextDirection(View.TEXT_DIRECTION_RTL);
        arabic.setLineSpacing(Ui.px(10), 1f);
        card.addView(arabic, wide(22));

        TextView latin = Ui.text(this, doa.optString("latin"), 20, 0xFFE8D7B0, Ui.REGULAR);
        latin.setGravity(Gravity.CENTER);
        latin.setLineSpacing(Ui.px(4), 1f);
        card.addView(latin, wide(16));

        TextView tr = Ui.text(this, doa.optString("translation"), 20, Ui.TEXT, Ui.REGULAR);
        tr.setGravity(Gravity.CENTER);
        tr.setLineSpacing(Ui.px(4), 1f);
        card.addView(tr, wide(10));

        TextView src = Ui.text(this, doa.optString("source"), 14, Ui.TEXT3, Ui.REGULAR);
        card.addView(src, wrap(16));

        TextView close = Ui.text(this, "Tutup", 18, Ui.TEXT, Ui.MEDIUM);
        close.setGravity(Gravity.CENTER);
        close.setBackground(Ui.rounded(Ui.ACCENT, 14));
        close.setPadding(Ui.px(40), 0, Ui.px(40), 0);
        Ui.pressable(close);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.px(56));
        clp.topMargin = Ui.px(20);
        card.addView(close, clp);

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Math.round(dm.widthPixels * 0.72f), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        lp.windowAnimations = android.R.style.Animation_Dialog;
        try {
            wm.addView(card, lp);
            overlay = card;
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Overlay doa gagal ditampilkan", e);
        }
    }

    private LinearLayout.LayoutParams wide(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    private LinearLayout.LayoutParams wrap(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(top);
        return lp;
    }

    private void finish() {
        stopSelf();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        for (MediaPlayer mp : players) mp.release();
        players.clear();
        player = null;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focus != null) audio.abandonAudioFocusRequest(focus);
        } else {
            audio.abandonAudioFocus(null);
        }
        if (overlay != null) {
            try {
                ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(overlay);
            } catch (RuntimeException ignored) {
                // sudah dilepas
            }
            overlay = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
