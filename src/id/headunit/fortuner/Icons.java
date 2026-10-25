package id.headunit.fortuner;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Ikon dan ornamen yang digambar langsung (tanpa bitmap) agar tajam di semua resolusi. */
final class Icons {
    private Icons() { }

    /** Kerangka Drawable: subclass cukup mengisi draw(canvas, w, h). */
    abstract static class Base extends Drawable {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            c.save();
            c.translate(b.left, b.top);
            draw(c, b.width(), b.height());
            c.restore();
        }

        abstract void draw(Canvas c, float w, float h);

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }

        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Lencana putar merah gaya YouTube (dipakai bila aplikasi YouTube tidak terpasang). */
    static Drawable youtube() {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                float bw = Math.min(w, h * 1.43f), bh = bw * 0.7f;
                float l = (w - bw) / 2, t = (h - bh) / 2;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFF0033);
                c.drawRoundRect(new RectF(l, t, l + bw, t + bh), bh * 0.28f, bh * 0.28f, p);
                p.setColor(0xFFFFFFFF);
                Path tri = new Path();
                float cx = l + bw / 2, cy = t + bh / 2, s = bh * 0.22f;
                tri.moveTo(cx - s * 0.85f, cy - s);
                tri.lineTo(cx + s * 1.15f, cy);
                tri.lineTo(cx - s * 0.85f, cy + s);
                tri.close();
                c.drawPath(tri, p);
            }
        };
    }

    /** Pin peta empat warna (dipakai bila Google Maps tidak terpasang). */
    static Drawable mapPin() {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                float s = Math.min(w / 0.74f, h);
                float cx = w / 2, top = (h - s) / 2, r = s * 0.37f, cy = top + r;
                Path pin = new Path();
                pin.addCircle(cx, cy, r, Path.Direction.CW);
                Path tail = new Path();
                tail.moveTo(cx - r * 0.86f, cy + r * 0.5f);
                tail.quadTo(cx - r * 0.2f, cy + r * 1.2f, cx, top + s);
                tail.quadTo(cx + r * 0.2f, cy + r * 1.2f, cx + r * 0.86f, cy + r * 0.5f);
                tail.close();
                pin.op(tail, Path.Op.UNION);
                c.save();
                c.clipPath(pin);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF34A853);
                c.drawRect(0, cy, w, h, p);                       // hijau bawah
                p.setColor(0xFF4285F4);
                c.drawRect(0, top, cx, cy + r * 0.2f, p);         // biru kiri atas
                p.setColor(0xFFEA4335);
                c.drawRect(cx, top, w, cy + r * 0.2f, p);         // merah kanan atas
                p.setColor(0xFFFBBC04);
                c.drawRect(0, cy + r * 0.2f, cx - r * 0.1f, cy + r * 0.95f, p); // kuning kiri
                c.restore();
                p.setColor(0xFF0B1A2C);
                c.drawCircle(cx, cy, r * 0.38f, p);
            }
        };
    }

    /** Mushaf terbuka di atas rehal, aksen emas. */
    static Drawable quranBook() {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                float s = Math.min(w, h);
                float l = (w - s) / 2, t = (h - s) / 2;
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, t, 0, t + s, 0xFFF3DDA6, 0xFFB98B3E, Shader.TileMode.CLAMP));
                Path left = new Path(), right = new Path();
                float cx = l + s / 2, top = t + s * 0.18f, bottom = t + s * 0.66f;
                left.moveTo(cx, top + s * 0.06f);
                left.quadTo(cx - s * 0.2f, top - s * 0.04f, l + s * 0.06f, top + s * 0.02f);
                left.lineTo(l + s * 0.06f, bottom);
                left.quadTo(cx - s * 0.2f, bottom - s * 0.06f, cx, bottom + s * 0.04f);
                left.close();
                right.moveTo(cx, top + s * 0.06f);
                right.quadTo(cx + s * 0.2f, top - s * 0.04f, l + s * 0.94f, top + s * 0.02f);
                right.lineTo(l + s * 0.94f, bottom);
                right.quadTo(cx + s * 0.2f, bottom - s * 0.06f, cx, bottom + s * 0.04f);
                right.close();
                c.drawPath(left, p);
                c.drawPath(right, p);
                p.setShader(null);
                // sampul tepi
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.025f);
                p.setColor(0xFF2D5B8F);
                c.drawPath(left, p);
                c.drawPath(right, p);
                // baris teks
                p.setColor(0x995A4320);
                p.setStrokeWidth(s * 0.012f);
                for (int i = 0; i < 4; i++) {
                    float y = top + s * (0.12f + i * 0.09f);
                    c.drawLine(l + s * 0.13f, y, cx - s * 0.06f, y + s * 0.01f, p);
                    c.drawLine(cx + s * 0.06f, y + s * 0.01f, l + s * 0.87f, y, p);
                }
                // rehal (penyangga X)
                p.setColor(0xFF9C7434);
                p.setStrokeWidth(s * 0.05f);
                p.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(l + s * 0.22f, bottom - s * 0.02f, l + s * 0.74f, t + s * 0.94f, p);
                c.drawLine(l + s * 0.78f, bottom - s * 0.02f, l + s * 0.26f, t + s * 0.94f, p);
                p.setStrokeCap(Paint.Cap.BUTT);
            }
        };
    }

    /** Medali bintang delapan (rub el hizb) emas. */
    static Drawable medallion() {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                float r = Math.min(w, h) / 2f, cx = w / 2, cy = h / 2;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x33D4AF6A);
                c.drawPath(star(cx, cy, r * 0.98f), p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, r * 0.07f));
                p.setColor(Ui.GOLD);
                c.drawPath(star(cx, cy, r * 0.96f), p);
                c.drawCircle(cx, cy, r * 0.48f, p);
                p.setStrokeWidth(Math.max(1f, r * 0.04f));
                c.drawPath(star(cx, cy, r * 0.42f), p);
            }
        };
    }

    /** Penanda nomor ayat: bintang delapan dengan angka di tengah. */
    static Drawable ayahBadge(final int number) {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                float r = Math.min(w, h) / 2f, cx = w / 2, cy = h / 2;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, r * 0.08f));
                p.setColor(Ui.GOLD);
                c.drawPath(star(cx, cy, r * 0.94f), p);
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                String s = String.valueOf(number);
                p.setTextSize(r * (s.length() > 2 ? 0.62f : 0.78f));
                p.setTextAlign(Paint.Align.CENTER);
                p.setColor(0xFFF1E3C0);
                c.drawText(s, cx, cy - (p.descent() + p.ascent()) / 2, p);
            }
        };
    }

    private static Path star(float cx, float cy, float r) {
        Path a = new Path(), b = new Path();
        float s = r * 0.72f;
        a.addRect(cx - s, cy - s, cx + s, cy + s, Path.Direction.CW);
        b.moveTo(cx, cy - r);
        b.lineTo(cx + r, cy);
        b.lineTo(cx, cy + r);
        b.lineTo(cx - r, cy);
        b.close();
        a.op(b, Path.Op.UNION);
        return a;
    }

    /** Batang sinyal seluler; level -1 berarti tidak ada layanan. */
    static final class Signal extends Base {
        int level = -1;
        int color = Ui.TEXT;

        @Override void draw(Canvas c, float w, float h) {
            float bw = w / 5.5f, gap = bw * 0.5f;
            for (int i = 0; i < 4; i++) {
                float bh = h * (0.35f + 0.65f * (i + 1) / 4f);
                float x = i * (bw + gap);
                p.setColor(i < level ? color : (color & 0x00FFFFFF) | 0x40000000);
                c.drawRoundRect(new RectF(x, h - bh, x + bw, h), bw * 0.25f, bw * 0.25f, p);
            }
        }
    }

    /** Ikon cuaca menurut kode WMO. */
    static final class Weather extends Base {
        int code = -1;
        boolean day = true;

        @Override void draw(Canvas c, float w, float h) {
            float s = Math.min(w, h);
            float l = (w - s) / 2, t = (h - s) / 2;
            p.setStyle(Paint.Style.FILL);
            boolean clear = code == 0 || code == 1;
            boolean partly = code == 2;
            boolean cloud = code < 0 || code >= 3;
            if (clear || partly) {
                float cx = clear ? l + s / 2 : l + s * 0.62f, cy = clear ? t + s / 2 : t + s * 0.36f;
                float r = clear ? s * 0.24f : s * 0.2f;
                p.setColor(day ? 0xFFFFC53D : 0xFFE8EEF8);
                if (day) {
                    c.drawCircle(cx, cy, r, p);
                    p.setStrokeWidth(s * 0.05f);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        c.drawLine(cx + (float) Math.cos(a) * r * 1.35f, cy + (float) Math.sin(a) * r * 1.35f,
                                cx + (float) Math.cos(a) * r * 1.7f, cy + (float) Math.sin(a) * r * 1.7f, p);
                    }
                } else {
                    Path moon = new Path();
                    moon.addCircle(cx, cy, r * 1.1f, Path.Direction.CW);
                    Path cut = new Path();
                    cut.addCircle(cx + r * 0.6f, cy - r * 0.45f, r * 0.95f, Path.Direction.CW);
                    moon.op(cut, Path.Op.DIFFERENCE);
                    c.drawPath(moon, p);
                }
            }
            if (partly || cloud) {
                p.setColor(code < 0 ? 0x66EAF2FF : 0xFFEAF2FF);
                float by = partly ? t + s * 0.72f : t + s * 0.66f;
                c.drawCircle(l + s * 0.36f, by - s * 0.12f, s * 0.17f, p);
                c.drawCircle(l + s * 0.56f, by - s * 0.2f, s * 0.21f, p);
                c.drawRoundRect(new RectF(l + s * 0.14f, by - s * 0.14f, l + s * 0.86f, by + s * 0.06f),
                        s * 0.1f, s * 0.1f, p);
            }
            if (code >= 51 && code < 95) {
                p.setColor(0xFF5AB0FF);
                p.setStrokeWidth(s * 0.05f);
                p.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 3; i++) {
                    float x = l + s * (0.32f + i * 0.17f);
                    c.drawLine(x, t + s * 0.8f, x - s * 0.05f, t + s * 0.95f, p);
                }
            } else if (code >= 95) {
                p.setColor(0xFFFFC53D);
                Path bolt = new Path();
                bolt.moveTo(l + s * 0.52f, t + s * 0.7f);
                bolt.lineTo(l + s * 0.4f, t + s * 0.86f);
                bolt.lineTo(l + s * 0.5f, t + s * 0.86f);
                bolt.lineTo(l + s * 0.44f, t + s);
                bolt.lineTo(l + s * 0.62f, t + s * 0.8f);
                bolt.lineTo(l + s * 0.52f, t + s * 0.8f);
                bolt.close();
                c.drawPath(bolt, p);
            }
        }
    }

    /** Latar kartu Al-Qur'an: gradasi navy dengan lengkung mihrab dan bintang tipis. */
    static Drawable quranCardBackground(final float radius) { return quranCardBackground(radius, true); }

    static Drawable quranCardBackground(final float radius, final boolean stars) {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                RectF r = new RectF(0, 0, w, h);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, 0, h, 0xFF132845, 0xFF0A1424, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, radius, radius, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, h * 0.008f));
                p.setColor(0x40D4AF6A);
                Path arch = new Path();
                float aw = w * 0.62f, l = (w - aw) / 2, top = h * 0.1f;
                arch.moveTo(l, h);
                arch.lineTo(l, h * 0.45f);
                arch.quadTo(l, top + h * 0.12f, w / 2, top);
                arch.quadTo(l + aw, top + h * 0.12f, l + aw, h * 0.45f);
                arch.lineTo(l + aw, h);
                c.drawPath(arch, p);
                if (stars) {
                    p.setColor(0x26D4AF6A);
                    c.drawPath(star(w * 0.1f, h * 0.2f, h * 0.06f), p);
                    c.drawPath(star(w * 0.9f, h * 0.22f, h * 0.05f), p);
                }
                p.setColor(0x55D4AF6A);
                c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, p);
            }
        };
    }

    /** Latar kartu Navigasi: navy dengan jalan peta samar. */
    static Drawable mapCardBackground(final float radius) {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                RectF r = new RectF(0, 0, w, h);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, w, h, 0xFF10233B, 0xFF0A1626, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, radius, radius, p);
                p.setShader(null);
                c.save();
                Path clip = new Path();
                clip.addRoundRect(r, radius, radius, Path.Direction.CW);
                c.clipPath(clip);
                p.setStyle(Paint.Style.STROKE);
                p.setColor(0x1F7FA8D9);
                p.setStrokeWidth(Math.max(1f, h * 0.012f));
                c.drawLine(-w * 0.1f, h * 0.8f, w * 1.1f, h * 0.25f, p);
                c.drawLine(w * 0.15f, -h * 0.1f, w * 0.55f, h * 1.1f, p);
                p.setStrokeWidth(Math.max(1f, h * 0.006f));
                c.drawLine(w * 0.7f, -h * 0.1f, w * 0.85f, h * 1.1f, p);
                c.drawLine(-w * 0.1f, h * 0.35f, w * 0.5f, h * 0.55f, p);
                p.setColor(0x2234A853);
                p.setStrokeWidth(Math.max(2f, h * 0.03f));
                Path route = new Path();
                route.moveTo(w * 0.08f, h * 0.9f);
                route.quadTo(w * 0.35f, h * 0.55f, w * 0.62f, h * 0.62f);
                route.quadTo(w * 0.85f, h * 0.68f, w * 0.95f, h * 0.2f);
                c.drawPath(route, p);
                c.restore();
                p.setColor(Ui.STROKE);
                p.setStrokeWidth(Math.max(1f, h * 0.006f));
                c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, p);
            }
        };
    }

    /** Latar kartu YouTube: gelap dengan rona merah. */
    static Drawable videoCardBackground(final float radius) {
        return new Base() {
            @Override void draw(Canvas c, float w, float h) {
                RectF r = new RectF(0, 0, w, h);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0, 0, 0, h, 0xFF2A1220, 0xFF100B16, Shader.TileMode.CLAMP));
                c.drawRoundRect(r, radius, radius, p);
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1f, h * 0.006f));
                p.setColor(0x40FF4D6A);
                c.drawRoundRect(new RectF(0.5f, 0.5f, w - 0.5f, h - 0.5f), radius, radius, p);
            }
        };
    }
}
