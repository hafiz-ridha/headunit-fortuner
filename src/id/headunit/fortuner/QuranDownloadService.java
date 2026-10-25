package id.headunit.fortuner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Mengunduh murattal per ayat (Misyari Rasyid Al-Afasi, cdn.equran.id) ke penyimpanan headunit agar Al-Qur'an
 * bisa diputar tanpa internet. Bisa per surah atau semua surah; bisa dilanjutkan bila terputus.
 * Unduh otomatis hanya berjalan di Wi-Fi yang tidak berbayar (bukan hotspot HP); "Unduh semua" dari pengguna
 * berjalan di jaringan apa pun.
 */
public class QuranDownloadService extends Service {
    static final String ALL = "murattal_download_all";
    static final String AUTO = "murattal_auto_wifi";
    private static final String ACTION_ALL = "id.headunit.fortuner.DOWNLOAD_ALL";
    private static final String ACTION_SURAH = "id.headunit.fortuner.DOWNLOAD_SURAH";
    private static final String ACTION_STOP = "id.headunit.fortuner.DOWNLOAD_STOP";
    private static final String EXTRA_SURAH = "surah";
    private static final String EXTRA_AUTO = "auto";
    private static final String CHANNEL = "murattal";
    private static final int ID = 8;

    // ---- status bersama untuk UI (hanya diubah di thread utama) ----
    static boolean running;
    static int currentSurah;
    static int storedAyat = -1;
    static String lastError;
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }

    static void removeListener(Runnable r) { listeners.remove(r); }

    private static void changed() {
        MAIN.post(() -> {
            for (Runnable r : new ArrayList<>(listeners)) r.run();
        });
    }

    static void downloadAll(Context c) {
        Prefs.set(c, ALL, true);
        start(c, new Intent(c, QuranDownloadService.class).setAction(ACTION_ALL));
    }

    static void downloadSurah(Context c, int surah) {
        start(c, new Intent(c, QuranDownloadService.class).setAction(ACTION_SURAH).putExtra(EXTRA_SURAH, surah));
    }

    static void stop(Context c) {
        Prefs.set(c, ALL, false);
        start(c, new Intent(c, QuranDownloadService.class).setAction(ACTION_STOP));
    }

    /** Dipanggil saat proses mulai atau jaringan berubah: lanjutkan unduhan bila memang diinginkan. */
    static void resumeIfWanted(Context c) {
        if (running) return;
        boolean all = Prefs.flag(c, ALL, false);
        boolean auto = Prefs.flag(c, AUTO, true) && onUnmeteredNetwork(c);
        if (!all && !auto) return;
        if (!hasNetwork(c)) return;
        new Thread(() -> {
            if (QuranAudioStore.completeSurahs(c) < 114) {
                MAIN.post(() -> start(c, new Intent(c, QuranDownloadService.class).setAction(ACTION_ALL)
                        .putExtra(EXTRA_AUTO, !all)));
            }
        }, "murattal-check").start();
    }

    private static void start(Context c, Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Unduhan murattal tidak bisa dimulai", e);
        }
    }

    @SuppressWarnings("deprecation")
    static boolean hasNetwork(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    static boolean onUnmeteredNetwork(Context c) {
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm != null && hasNetwork(c) && !cm.isActiveNetworkMetered();
    }

    // ---- layanan ----
    private final Set<Integer> queue = new LinkedHashSet<>();
    private boolean wantAll;
    private boolean autoOnly;
    private volatile boolean cancelled;
    private Thread worker;
    private NotificationManager nm;

    @Override
    public void onCreate() {
        super.onCreate();
        nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Unduhan murattal", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, notification(0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ID, notification(0, 0));
        }
        String action = intent == null ? ACTION_ALL : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            cancelled = true;
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (queue) {
            if (ACTION_SURAH.equals(action)) {
                queue.add(intent.getIntExtra(EXTRA_SURAH, 1));
            } else {
                wantAll = true;
                autoOnly = intent != null && intent.getBooleanExtra(EXTRA_AUTO, false) && !Prefs.flag(this, ALL, false);
            }
        }
        if (worker == null) {
            worker = new Thread(this::work, "murattal-download");
            worker.start();
        }
        return START_NOT_STICKY;
    }

    private int nextSurah() {
        synchronized (queue) {
            if (!queue.isEmpty()) {
                int s = queue.iterator().next();
                queue.remove(s);
                return s;
            }
        }
        if (wantAll) {
            for (int s = 1; s <= 114; s++) if (!QuranAudioStore.isComplete(this, s)) return s;
        }
        return 0;
    }

    private void work() {
        MAIN.post(() -> {
            running = true;
            lastError = null;
        });
        int stored = QuranAudioStore.storedAyat(this);
        MAIN.post(() -> storedAyat = stored);
        changed();
        QuranRepository repo = QuranRepository.get(this);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        String error = null;
        try {
            int s;
            while (!cancelled && (s = nextSurah()) != 0) {
                if (autoOnly && !onUnmeteredNetwork(this)) {
                    error = "Unduh otomatis menunggu Wi-Fi";
                    break;
                }
                final int surah = s;
                MAIN.post(() -> currentSurah = surah);
                QuranRepository.Surah data = repo.surah(surah);
                if (data == null) continue;
                QuranAudioStore.surahDir(this, surah).mkdirs();
                List<Future<Boolean>> jobs = new ArrayList<>();
                for (QuranRepository.Ayah a : data.ayat) {
                    if (QuranAudioStore.has(this, surah, a.number)) continue;
                    jobs.add(pool.submit(() -> fetch(a.audioUrl, QuranAudioStore.file(this, surah, a.number))));
                }
                boolean ok = true;
                for (Future<Boolean> j : jobs) {
                    try {
                        ok &= j.get();
                    } catch (Exception e) {
                        ok = false;
                    }
                }
                if (cancelled) break;
                if (!ok) {
                    error = hasNetwork(this) ? "Sebagian audio gagal diunduh, coba lagi nanti" : "Unduhan terhenti: tidak ada internet";
                    break;
                }
                QuranAudioStore.markComplete(this, surah);
                changed();
            }
        } finally {
            pool.shutdownNow();
        }
        final String err = error;
        MAIN.post(() -> {
            running = false;
            currentSurah = 0;
            lastError = err;
            worker = null;
            changed();
            stopSelf();
        });
    }

    /** Unduh satu file ke .part lalu ganti nama; dicoba ulang 3 kali. */
    private boolean fetch(String url, File dest) {
        if (url == null || url.isEmpty()) return false;
        File part = new File(dest.getPath() + ".part");
        for (int attempt = 1; attempt <= 3 && !cancelled; attempt++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(20000);
                if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part)) {
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (cancelled) return false;
                        out.write(buf, 0, n);
                    }
                }
                if (part.length() == 0 || !part.renameTo(dest)) throw new IOException("file kosong");
                MAIN.post(() -> {
                    storedAyat++;
                    throttledUpdate();
                });
                return true;
            } catch (IOException e) {
                Log.w(AppLauncherRepository.TAG, "Gagal mengunduh " + url + " (percobaan " + attempt + ")", e);
                part.delete();
                try {
                    Thread.sleep(1500L * attempt);
                } catch (InterruptedException ie) {
                    return false;
                }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return false;
    }

    private long lastUpdate;

    private void throttledUpdate() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastUpdate < 700) return;
        lastUpdate = now;
        nm.notify(ID, notification(storedAyat, currentSurah));
        changed();
    }

    @SuppressWarnings("deprecation")
    private Notification notification(int done, int surah) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        QuranRepository.SurahInfo info = surah > 0 ? QuranRepository.get(this).info(surah) : null;
        return b.setSmallIcon(R.drawable.ic_book)
                .setContentTitle("Mengunduh murattal offline")
                .setContentText((info != null ? info.latin + " · " : "") + done + " / " + QuranAudioStore.TOTAL_AYAT + " ayat")
                .setProgress(QuranAudioStore.TOTAL_AYAT, Math.max(0, done), done <= 0)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    @Override
    public void onDestroy() {
        cancelled = true;
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
