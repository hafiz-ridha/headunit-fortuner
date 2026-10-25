package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

/**
 * Tema dan ukuran. Semua ukuran ditulis untuk layar acuan 1280x680 piksel lalu diskalakan ke ukuran
 * jendela sebenarnya, sehingga komposisi sama persis di 1024x600, 1280x720, maupun 1920x1080.
 */
final class Ui {
    static final int BG = 0xFF07111F;
    static final int PANEL = 0xFF0C1A2D;
    static final int PANEL_HI = 0xFF11243C;
    static final int STROKE = 0x2E7FA8D9;
    static final int ACCENT = 0xFF1685FF;
    static final int ACCENT_SOFT = 0x331685FF;
    static final int GOLD = 0xFFD4AF6A;
    static final int GOLD_SOFT = 0x55D4AF6A;
    static final int TEXT = 0xFFEAF2FF;
    static final int TEXT2 = 0xFF9FB2CC;
    static final int TEXT3 = 0xFF5F7391;
    static final int WARN = 0xFFFFB020;

    static final float REF_W = 1280f, REF_H = 680f;
    private static float scale = 1f;
    private static Typeface arabic;

    private Ui() { }

    private static boolean initialized;

    static void init(int widthPx, int heightPx) {
        scale = Math.max(0.4f, Math.min(widthPx / REF_W, heightPx / REF_H));
        initialized = true;
    }

    /** Untuk layanan tanpa Activity (overlay doa): hitung skala dari layar bila belum pernah dihitung. */
    static void initOnce(int widthPx, int heightPx) {
        if (!initialized) init(widthPx, heightPx);
    }

    static int px(float v) { return Math.round(v * scale); }

    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface CONDENSED = Typeface.create("sans-serif-condensed", Typeface.BOLD);

    /**
     * Noto Naskh Arabic terbaru dari assets (OFL), karena font bawaan Android 10 belum punya sebagian tanda
     * waqaf Al-Qur'an (mis. U+08D6). Bila aset gagal dimuat, pakai Noto Naskh Arabic bawaan sistem
     * (bukan varian UI, agar harakat tidak terpotong).
     */
    static Typeface arabic(Context c) {
        if (arabic == null) {
            try {
                arabic = Typeface.createFromAsset(c.getAssets(), "fonts/NotoNaskhArabic.ttf");
            } catch (RuntimeException e) {
                File f = new File("/system/fonts/NotoNaskhArabic-Regular.ttf");
                arabic = f.exists() ? Typeface.createFromFile(f) : Typeface.SERIF;
            }
        }
        return arabic;
    }

    static TextView text(Context c, CharSequence s, float size, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setIncludeFontPadding(false);
        return t;
    }

    static void size(TextView t, float size) { t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); }

    static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(radius));
        return g;
    }

    static GradientDrawable panel(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xF0102238, 0xF00A1628});
        g.setCornerRadius(px(radius));
        g.setStroke(Math.max(1, px(1.2f)), STROKE);
        return g;
    }

    static GradientDrawable gradient(int top, int bottom, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(px(radius));
        return g;
    }

    static Drawable icon(Context c, int res, int color) {
        Drawable d = c.getDrawable(res).mutate();
        d.setTint(color);
        return d;
    }

    static ImageView iconView(Context c, int res, int color, float size) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(icon(c, res, color));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(size), px(size)));
        return v;
    }

    static ImageView iconView(Context c, Drawable d, float size) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(d);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(size), px(size)));
        return v;
    }

    /** Tombol bundar berisi ikon, mis. play/pause di widget Al-Qur'an. */
    static ImageView roundButton(Context c, int res, float size, int bg, int fg) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(icon(c, res, fg));
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bg);
        g.setStroke(Math.max(1, px(1)), STROKE);
        v.setBackground(g);
        int pad = Math.round(px(size) * 0.26f);
        v.setPadding(pad, pad, pad, pad);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(size), px(size)));
        pressable(v);
        return v;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams weight(float w, boolean horizontal) {
        return horizontal ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, w)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, w);
    }

    static View space(Context c, float w, float h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(px(w), px(h)));
        return v;
    }

    static View flex(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return v;
    }

    /** Umpan balik sentuh ringan: sedikit mengecil dan meredup saat ditekan. */
    @SuppressLint("ClickableViewAccessibility")
    static void pressable(View v) {
        v.setClickable(true);
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.96f).scaleY(0.96f).alpha(0.82f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).alpha(view.isEnabled() ? 1f : 0.45f).setDuration(110).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
