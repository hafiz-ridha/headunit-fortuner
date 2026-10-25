package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Kartu besar di kanan atas (YouTube, Navigasi, Al-Qur'an). */
final class AppShortcutCard extends LinearLayout {
    private final ImageView icon;
    private final TextView note;

    @SuppressLint("ViewConstructor")
    AppShortcutCard(Context c, String label, Drawable art, Drawable background, float iconWidthFactor) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackground(background);
        setPadding(Ui.px(10), Ui.px(10), Ui.px(10), Ui.px(10));
        icon = new ImageView(c);
        icon.setImageDrawable(art);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        addView(icon, new LayoutParams(Math.round(Ui.px(64) * iconWidthFactor), Ui.px(64)));
        TextView t = Ui.text(c, label, 24, Ui.TEXT, Ui.MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        addView(t);
        ((LayoutParams) t.getLayoutParams()).topMargin = Ui.px(14);
        note = Ui.text(c, "", 13, Ui.TEXT2, Ui.REGULAR);
        note.setGravity(Gravity.CENTER);
        note.setSingleLine(true);
        addView(note);
        ((LayoutParams) note.getLayoutParams()).topMargin = Ui.px(4);
        note.setVisibility(GONE);
        setContentDescription(label);
        Ui.pressable(this);
    }

    void setArt(Drawable d) { icon.setImageDrawable(d); }

    /** Catatan kecil di bawah judul, mis. "Belum terpasang"; null untuk menyembunyikan. */
    void setNote(String s) {
        note.setText(s == null ? "" : s);
        note.setVisibility(s == null ? GONE : VISIBLE);
    }
}
