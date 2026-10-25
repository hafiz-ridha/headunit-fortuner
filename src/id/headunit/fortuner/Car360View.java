package id.headunit.fortuner;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mobil 360°: 72 gambar render (assets/img/car360/car_NN.webp, tiap 5°) yang berputar MENGIKUTI JARI:
 * permukaan mobil di bawah jari bergerak searah dan sejauh jari, berhenti tepat saat jari dilepas, lalu
 * setelah {@link #RETURN_AFTER_MS} tanpa sentuhan kembali pelan ke sudut belakang-samping.
 * Semua frame disiapkan di latar dalam ukuran tampilan, jadi tidak ada decode saat jari bergerak.
 * Menggambar ulang hanya saat disentuh/beranimasi.
 */
final class Car360View extends View {
    private static final String DIR = "img/car360";
    private static final float DEFAULT_ANGLE = 40f;
    private static final long RETURN_AFTER_MS = 8000;
    private static final float MAX_BYTES = 36f * 1024 * 1024;

    private final int frames;
    private final float step;
    private final Bitmap[] scaled;
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF dst = new android.graphics.RectF();
    private int loadedW, loadedH;
    private int generation;
    private float angle = DEFAULT_ANGLE;
    private float downX, downAngle;
    private ValueAnimator anim;
    private Runnable onFirstTouch;

    Car360View(Context c) {
        super(c);
        int n = 0;
        try {
            String[] names = c.getAssets().list(DIR);
            if (names != null) for (String s : names) if (s.endsWith(".webp")) n++;
        } catch (IOException e) {
            Log.e(AppLauncherRepository.TAG, "Gambar 360 tidak ditemukan", e);
        }
        frames = n;
        step = n > 0 ? 360f / n : 360f;
        scaled = new Bitmap[Math.max(1, n)];
        setContentDescription("Toyota Fortuner putih. Geser ke kiri atau kanan untuk memutar.");
    }

    void setOnFirstTouch(Runnable r) { onFirstTouch = r; }

    private int index(float deg) {
        if (frames == 0) return 0;
        int i = Math.round(deg / step) % frames;
        return i < 0 ? i + frames : i;
    }

    private Bitmap decode(int i) {
        try (InputStream in = getContext().getAssets().open(String.format(Locale.US, "%s/car_%02d.webp", DIR, i))) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException | OutOfMemoryError e) {
            Log.w(AppLauncherRepository.TAG, "Frame 360 #" + i + " gagal dibaca", e);
            return null;
        }
    }

    /** Decode semua frame lalu perkecil ke ukuran gambar di layar, mulai dari sudut yang sedang tampil. */
    private void preload(int w, int h) {
        final int gen = ++generation;
        final int startIndex = index(angle);
        loader.execute(() -> {
            for (int k = 0; k < frames; k++) {
                if (gen != generation) return;
                int i = (startIndex + ((k % 2 == 0) ? k / 2 : frames - (k + 1) / 2)) % frames;
                Bitmap full = decode(i);
                if (full == null) continue;
                float s = Math.min(w / (float) full.getWidth(), h / (float) full.getHeight());
                // Batasi total memori semua frame (layar besar): sedikit lebih kecil lebih baik daripada berat.
                float bytes = full.getWidth() * s * full.getHeight() * s * 4f * frames;
                if (bytes > MAX_BYTES) s *= (float) Math.sqrt(MAX_BYTES / bytes);
                int dw = Math.max(1, Math.round(full.getWidth() * s)), dh = Math.max(1, Math.round(full.getHeight() * s));
                Bitmap fit = (dw == full.getWidth() && dh == full.getHeight()) ? full
                        : Bitmap.createScaledBitmap(full, dw, dh, true);
                if (fit != full) full.recycle();
                final int idx = i;
                main.post(() -> {
                    if (gen != generation) return;
                    scaled[idx] = fit;
                    invalidate();
                });
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        glow.setShader(new RadialGradient(w / 2f, h * 0.84f, w * 0.48f,
                new int[]{0x401685FF, 0x141685FF, 0x001685FF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        if (frames > 0 && w > 0 && h > 0 && (w != loadedW || h != loadedH)) {
            loadedW = w;
            loadedH = h;
            java.util.Arrays.fill(scaled, null);
            preload(w, h);
        }
    }

    /** Frame untuk sudut sekarang, atau frame terdekat yang sudah siap. */
    private Bitmap current() {
        int i = index(angle);
        for (int d = 0; d < frames; d++) {
            Bitmap b = scaled[(i + d) % frames];
            if (b != null) return b;
            b = scaled[((i - d) % frames + frames) % frames];
            if (b != null) return b;
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.save();
        c.scale(1f, 0.28f, w / 2f, h * 0.84f);
        c.drawCircle(w / 2f, h * 0.84f, w * 0.48f, glow);
        c.restore();
        Bitmap b = current();
        if (b == null) return;
        // Biasanya 1:1; diperbesar hanya bila frame dibatasi memori di layar besar.
        float s = Math.min(w / (float) b.getWidth(), h / (float) b.getHeight());
        float dw = b.getWidth() * s, dh = b.getHeight() * s;
        dst.set((w - dw) / 2f, h - dh, (w + dw) / 2f, h);
        c.drawBitmap(b, null, dst, bitmapPaint);
    }

    private void setAngle(float a) {
        int before = index(angle);
        angle = ((a % 360f) + 360f) % 360f;
        if (index(angle) != before) invalidate();
    }

    /**
     * Derajat putar per piksel geseran, agar sisi mobil di bawah jari ikut bergerak sejauh jari:
     * pada mobil berjari-jari r piksel, geseran dx di tepinya setara dx / r radian.
     */
    private float degreesPerPixel() {
        Bitmap b = current();
        float carWidth = b != null
                ? b.getWidth() * Math.min(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight())
                : getWidth() * 0.9f;
        return (float) Math.toDegrees(1.0 / Math.max(1f, carWidth * 0.5f));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopAnim();
                removeCallbacks(returnHome);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                downX = e.getX();
                downAngle = angle;
                if (onFirstTouch != null) {
                    onFirstTouch.run();
                    onFirstTouch = null;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                // Geser ke kanan = sisi yang menghadap layar ikut bergerak ke kanan.
                setAngle(downAngle + (e.getX() - downX) * degreesPerPixel());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                postDelayed(returnHome, RETURN_AFTER_MS);
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }

    private final Runnable returnHome = () -> {
        float diff = ((DEFAULT_ANGLE - angle) % 360f + 540f) % 360f - 180f; // jalur terpendek
        final float start = angle;
        stopAnim();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1400);
        anim.setInterpolator(new DecelerateInterpolator(1.6f));
        anim.addUpdateListener(a -> setAngle(start + diff * (float) a.getAnimatedValue()));
        anim.start();
    };

    private void stopAnim() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnim();
        removeCallbacks(returnHome);
        generation++;
        super.onDetachedFromWindow();
    }
}
