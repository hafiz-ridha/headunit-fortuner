package id.headunit.fortuner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Menampilkan peta rute Google Maps yang asli (real-time) di panel mobil Beranda: jendela picture-in-picture
 * Google Maps dipindah dan diubah ukurannya tepat di atas panel lewat ADB lokal, lalu ditutup lapisan gradien
 * tipis supaya tepi dan sudutnya menyatu dengan tema. Lapisan itu juga menangkap sentuhan, jadi peta tidak bisa
 * tergeser; di atasnya ada tombol minimize (ganti ke petunjuk arah) dan close (kembali ke gambar mobil).
 * Saat Beranda tertutup (aplikasi lain, kamera mundur, layar mati) peta digeser ke luar layar, dan setelah
 * navigasi selesai jendelanya ditutup. Bila ADB lokal tidak tersedia, panel tetap menampilkan petunjuk teks.
 */
final class MapPip {
    static final String PREF = "nav_map_in_panel";
    private static final String MAPS = "com.google.android.apps.maps";
    /** Jeda sebelum menutup peta setelah notifikasi navigasi hilang (notifikasi kadang dipasang ulang). */
    private static final long NAV_END_GRACE_MS = 4000;
    private static final long WATCH_MS = 5000;

    /** Panel diberi tahu bila peta tampil/hilang atau mode panel berubah. */
    interface Listener { void onMapState(); }

    /** Isi panel mobil selama navigasi: peta, petunjuk arah (peta di-minimize), atau gambar mobil (peta ditutup). */
    enum Mode { MAP, GUIDE, CAR }

    private enum Pip { NONE, SHOWN, PARKED }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Runnable EVAL = MapPip::evaluate;
    private static final Runnable WATCH = MapPip::watch;

    private static Context app;
    private static boolean resumed;
    private static Rect target;
    private static int edgeTop, edgeBottom;
    private static Listener listener;
    private static Mode mode = Mode.MAP;
    private static Pip pip = Pip.NONE;
    private static Rect shownAt;
    private static boolean busy, again;
    private static long navEndedAt;
    /** Saat terakhir penempatan gagal; percobaan berikutnya ditahan sebentar agar tidak berulang tiap notifikasi. */
    private static volatile long failedAt;
    private static View overlay;
    static volatile String status = "Belum dicoba. Peta muncul saat Google Maps memandu arah.";

    private MapPip() { }

    static boolean enabled(Context c) { return Prefs.flag(c, PREF, true); }

    static Mode mode() { return mode; }

    /** True bila peta Google Maps sedang menempati panel. */
    static boolean mapShown() { return pip == Pip.SHOWN && shownAt != null; }

    /** Peta bisa dipakai untuk navigasi saat ini (Google Maps dan setelan aktif). */
    static boolean mapAvailable(Context c) {
        return MAPS.equals(NavNotificationListener.STATE.app) && enabled(c);
    }

    /** Tombol minimize/close/tampilkan; berlaku sampai navigasi ini selesai. */
    static void setMode(Mode m) {
        if (mode == m) return;
        mode = m;
        if (m == Mode.MAP) failedAt = 0;
        else removeOverlay();
        notifyState();
        schedule(0);
    }

    /** Dipanggil LauncherActivity saat tampil/tertutup. */
    static void setResumed(Context c, boolean r) {
        app = c.getApplicationContext();
        resumed = r;
        if (!r) removeOverlay();
        schedule(r ? 350 : 250);
    }

    /** Posisi kartu navigasi di layar dan warna panel di atas/bawahnya (untuk menyamarkan sudut). */
    static void setTarget(Context c, Rect r, int top, int bottom, Listener l) {
        app = c.getApplicationContext();
        if (l == listener && r.equals(target) && top == edgeTop && bottom == edgeBottom) return;
        target = new Rect(r);
        edgeTop = top;
        edgeBottom = bottom;
        listener = l;
        schedule(200);
    }

    /** Panel tidak lagi tampil (halaman lain dibuka atau UI dibangun ulang). */
    static void clearTarget(Listener l) {
        if (listener != l) return;
        listener = null;
        target = null;
        removeOverlay();
        schedule(150);
    }

    static void navChanged() {
        if (!NavNotificationListener.STATE.active) navEndedAt = SystemClock.elapsedRealtime();
        schedule(NavNotificationListener.STATE.active ? 200 : NAV_END_GRACE_MS + 100);
    }

    /** Setelan berubah: evaluasi ulang. */
    static void refresh() {
        failedAt = 0;
        schedule(0);
    }

    private static void schedule(long delay) {
        MAIN.removeCallbacks(EVAL);
        MAIN.postDelayed(EVAL, delay);
    }

    private static void evaluate() {
        if (app == null) return;
        if (busy) {
            again = true;
            return;
        }
        NavNotificationListener.State s = NavNotificationListener.STATE;
        boolean navOver = !s.active && SystemClock.elapsedRealtime() - navEndedAt >= NAV_END_GRACE_MS;
        if (navOver && mode != Mode.MAP) {
            // Navigasi berikutnya kembali dimulai dengan peta.
            mode = Mode.MAP;
            notifyState();
        }
        boolean navOn = s.active && MAPS.equals(s.app);
        boolean want = navOn && resumed && target != null && enabled(app) && mode == Mode.MAP;
        if (want) {
            if (pip == Pip.SHOWN && target.equals(shownAt)) return;
            if (SystemClock.elapsedRealtime() - failedAt < 20_000) return;
            final Rect r = new Rect(target);
            run(() -> place(r), ok -> {
                if (ok) {
                    pip = Pip.SHOWN;
                    shownAt = r;
                    if (resumed && r.equals(target) && mode == Mode.MAP) showOverlay(r);
                    MAIN.removeCallbacks(WATCH);
                    MAIN.postDelayed(WATCH, WATCH_MS);
                } else {
                    pip = Pip.NONE;
                    shownAt = null;
                    removeOverlay();
                }
                notifyState();
            });
            return;
        }
        if (pip == Pip.SHOWN || (pip == Pip.PARKED && (navOver || !enabled(app)))) {
            removeOverlay();
            shownAt = null;
            notifyState();
            final boolean close = navOver || !enabled(app);
            run(() -> park(close), parked -> {
                if (parked) pip = close ? Pip.NONE : Pip.PARKED;
            });
        }
    }

    /**
     * Penjaga posisi: selama peta tampil, pastikan jendelanya tetap tepat di panel (sistem bisa menggesernya).
     * Bila jendela peta hilang (mis. Google Maps ditutup), panel kembali ke petunjuk teks.
     */
    private static void watch() {
        if (pip != Pip.SHOWN || shownAt == null || !resumed) return;
        if (busy) {
            MAIN.postDelayed(WATCH, 1000);
            return;
        }
        final Rect r = new Rect(shownAt);
        run(() -> verify(r), ok -> {
            if (ok) {
                if (pip == Pip.SHOWN) MAIN.postDelayed(WATCH, WATCH_MS);
                return;
            }
            pip = Pip.NONE;
            shownAt = null;
            removeOverlay();
            notifyState();
            schedule(500);
        });
    }

    private interface Job { boolean call(); }

    private interface Done { void done(boolean ok); }

    private static void run(Job job, Done done) {
        busy = true;
        IO.execute(() -> {
            boolean ok = job.call();
            MAIN.post(() -> {
                busy = false;
                done.done(ok);
                if (again) {
                    again = false;
                    evaluate();
                }
            });
        });
    }

    private static void notifyState() {
        if (listener != null) listener.onMapState();
    }

    // ---- perintah shell (thread IO) ----

    /** Mencetak "P id" bila Maps ada di stack pinned, "S id" bila Maps teratas di stack biasa, atau kosong. */
    private static final String FIND = "am stack list | awk '"
            + "/^Stack id=/{split($2,a,\"=\");sid=a[2];pin=0} "
            + "/mWindowingMode=pinned/{pin=1} "
            + "index($0,\"taskId=\")&&index($0,\": " + MAPS + "/\"){"
            + "if(pin){print \"P \" sid;exit} "
            + "if(index($0,\"topActivity=ComponentInfo{" + MAPS + "/\")){print \"S \" sid;exit}}'";

    private static boolean place(Rect r) {
        String b = r.left + " " + r.top + " " + r.right + " " + r.bottom;
        String cmd = "set -- $(" + FIND + "); "
                + "if [ \"$1\" = S ]; then am stack move-top-activity-to-pinned-stack $2 " + b + " >/dev/null 2>&1; "
                + "sleep 0.8; set -- $(" + FIND + "); fi; "
                + "if [ \"$1\" = P ]; then am stack resize $2 " + b + " && echo PLACED; else echo NOTASK; fi";
        String out = exec(cmd);
        if (out == null) return false;
        if (out.contains("PLACED")) {
            status = "Aktif. Peta Google Maps tampil di panel selama navigasi.";
            return true;
        }
        failedAt = SystemClock.elapsedRealtime();
        status = "Google Maps tidak ditemukan di latar belakang. Buka navigasi di Google Maps lalu kembali ke Beranda.";
        Log.i(AppLauncherRepository.TAG, "PiP Maps tidak ditempatkan: " + out.trim());
        return false;
    }

    /** True bila jendela peta masih ada (dikembalikan ke posisinya bila tergeser), false bila sudah hilang. */
    private static boolean verify(Rect r) {
        String b = r.left + " " + r.top + " " + r.right + " " + r.bottom;
        String bounds = "[" + r.left + "," + r.top + "][" + r.right + "," + r.bottom + "]";
        String cmd = "set -- $(" + FIND + "); [ \"$1\" = P ] || { echo GONE; exit; }; "
                + "am stack list | grep -qF \"Stack id=$2 bounds=" + bounds + "\" || am stack resize $2 " + b + "; "
                + "echo OK";
        String out = exec(cmd);
        // ADB sesaat gagal: anggap peta masih ada, coba lagi di putaran berikutnya.
        return out == null || out.contains("OK");
    }

    private static boolean park(boolean close) {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        int x = dm.widthPixels + 10, y = dm.heightPixels + 10;
        String cmd = "set -- $(" + FIND + "); [ \"$1\" = P ] || { echo GONE; exit; }; "
                + (close ? "am stack remove $2" : "am stack resize $2 " + x + " " + y + " " + (x + 296) + " " + (y + 166))
                + " && echo OK";
        String out = exec(cmd);
        return out != null && (out.contains("OK") || out.contains("GONE"));
    }

    private static String exec(String cmd) {
        try {
            return AdbLocal.shell(app, cmd, 8000, false);
        } catch (IOException e) {
            failedAt = SystemClock.elapsedRealtime();
            status = AdbLocal.authorized ? "ADB lokal terputus (port 5555 tertutup?). Panel memakai petunjuk teks."
                    : "Butuh ADB lokal: buka port 5555 lalu ketuk Hubungkan dan pilih Izinkan di headunit.";
            Log.i(AppLauncherRepository.TAG, "ADB lokal tidak tersedia: " + e.getMessage());
            return null;
        }
    }

    /** Dari Pengaturan: sambungkan ADB lokal, memunculkan dialog izin di headunit bila perlu. */
    static void authorize(Context c, Runnable done) {
        app = c.getApplicationContext();
        status = "Menghubungkan… pilih \"Izinkan\" (centang selalu) bila dialog muncul.";
        IO.execute(() -> {
            try {
                String out = AdbLocal.shell(app, "echo ok", 8000, true);
                failedAt = 0;
                status = out.contains("ok") ? "Terhubung. Peta Google Maps akan tampil di panel selama navigasi."
                        : "ADB lokal membalas tak terduga.";
            } catch (IOException e) {
                status = AdbLocal.portOpen() ? "Izin ditolak atau tidak dijawab. Coba lagi dan pilih Izinkan."
                        : "Port ADB 5555 tertutup. Buka lewat Termux, lalu coba lagi.";
            }
            MAIN.post(() -> {
                done.run();
                refresh();
            });
        });
    }

    // ---- lapisan di atas peta ----

    private static void showOverlay(Rect r) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(app)) return;
        removeOverlay();
        WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        // Bisa disentuh: sentuhan tidak sampai ke jendela PiP, jadi sistem tidak bisa menggeser/mengecilkan peta.
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(r.width(), r.height(), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = r.left;
        lp.y = r.top;
        lp.setTitle("FortunerMapBlend");
        View v = buildOverlay(app);
        try {
            wm.addView(v, lp);
            overlay = v;
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Lapisan peta gagal dipasang", e);
        }
    }

    private static View buildOverlay(Context c) {
        FrameLayout root = new FrameLayout(c);
        root.addView(new Blend(c, edgeTop, edgeBottom), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.setContentDescription("Peta navigasi. Ketuk untuk membuka Google Maps.");
        // Hanya ketukan singkat yang membuka Google Maps; geseran diabaikan (posisi peta tetap terkunci).
        final int slop = ViewConfiguration.get(c).getScaledTouchSlop() * 2;
        final float[] down = new float[2];
        root.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = e.getRawX();
                down[1] = e.getRawY();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                    && Math.hypot(e.getRawX() - down[0], e.getRawY() - down[1]) < slop
                    && e.getEventTime() - e.getDownTime() < 500) {
                NavNotificationListener.openNavApp(c);
            }
            return true;
        });

        LinearLayout buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(roundButton(c, R.drawable.ic_close_fullscreen, "Kecilkan peta, tampilkan petunjuk arah",
                () -> setMode(Mode.GUIDE)));
        View close = roundButton(c, R.drawable.ic_close, "Tutup peta, tampilkan gambar mobil",
                () -> setMode(Mode.CAR));
        buttons.addView(close);
        ((LinearLayout.LayoutParams) close.getLayoutParams()).leftMargin = Ui.px(10);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        blp.setMargins(0, Ui.px(12), Ui.px(12), 0);
        root.addView(buttons, blp);
        return root;
    }

    /** Tombol bulat gelap dengan ikon, dipakai di atas peta dan di kartu petunjuk arah. */
    static ImageView roundButton(Context c, int icon, String desc, Runnable onClick) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(Ui.icon(c, icon, Ui.TEXT));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.px(11);
        b.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xD90A1628);
        bg.setStroke(Math.max(1, Ui.px(1.2f)), 0x557FA8D9);
        b.setBackground(bg);
        b.setContentDescription(desc);
        Ui.pressable(b);
        b.setOnClickListener(v -> onClick.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));
        return b;
    }

    private static void removeOverlay() {
        if (overlay == null) return;
        try {
            ((WindowManager) app.getSystemService(Context.WINDOW_SERVICE)).removeView(overlay);
        } catch (RuntimeException ignored) {
            // sudah dilepas
        }
        overlay = null;
    }

    /** Sudut membulat berwarna panel, tepi memudar ke navy, rona tipis, dan garis tepi seperti kartu lain. */
    private static final class Blend extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int top, bottom;
        private final float radius = Ui.px(18);

        Blend(Context c, int top, int bottom) {
            super(c);
            this.top = top;
            this.bottom = bottom;
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            RectF all = new RectF(0, 0, w, h);
            Path round = new Path();
            round.addRoundRect(all, radius, radius, Path.Direction.CW);

            c.save();
            c.clipPath(round);
            c.drawColor(0x1A0B2A55);
            fill(c, 0, 0, w, h * 0.18f, 0, 0, 0, h * 0.18f, 0x7007111F, 0x0007111F);
            // Bawah dibuat tipis: Google Maps menaruh petunjuk belokan di sana.
            fill(c, 0, h * 0.82f, w, h, 0, h * 0.82f, 0, h, 0x0007111F, 0x4007111F);
            fill(c, 0, 0, w * 0.07f, h, 0, 0, w * 0.07f, 0, 0x6007111F, 0x0007111F);
            fill(c, w * 0.93f, 0, w, h, w * 0.93f, 0, w, 0, 0x0007111F, 0x6007111F);
            c.restore();

            Path corners = new Path();
            corners.setFillType(Path.FillType.EVEN_ODD);
            corners.addRect(all, Path.Direction.CW);
            corners.addRoundRect(all, radius, radius, Path.Direction.CW);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
            c.drawPath(corners, paint);
            paint.setShader(null);

            float sw = Math.max(1, Ui.px(1.2f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(sw);
            paint.setColor(Ui.STROKE);
            c.drawRoundRect(new RectF(sw / 2, sw / 2, w - sw / 2, h - sw / 2), radius, radius, paint);
        }

        private void fill(Canvas c, float l, float t, float r, float b, float x0, float y0, float x1, float y1,
                          int from, int to) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(x0, y0, x1, y1, from, to, Shader.TileMode.CLAMP));
            c.drawRect(l, t, r, b, paint);
            paint.setShader(null);
        }
    }
}
