package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Panel navigasi yang menggantikan gambar mobil selama Google Maps/Waze memandu arah: ikon belokan berikutnya,
 * jarak, instruksi/nama jalan, dan waktu tiba, dengan latar navy bergaris jalan dan gradien sesuai tema.
 * Ketuk untuk kembali ke aplikasi navigasi. Tombol di pojok: tampilkan peta (bila tersedia) dan tutup
 * (kembali ke gambar mobil).
 */
final class NavigationCard extends FrameLayout {
    private final ImageView maneuver;
    private final TextView title;
    private final TextView text;
    private final TextView sub;
    private final TextView app;
    private final LinearLayout content;
    private final LinearLayout buttons;
    private final View showMap;

    @SuppressLint("ViewConstructor")
    NavigationCard(Context c) {
        super(c);
        float r = Ui.px(18);
        setBackground(Icons.mapCardBackground(r));
        // Gradien tema: rona biru di atas, gelap di bawah supaya teks tetap terbaca.
        View glow = new View(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x401685FF, 0x000B1A2E, 0xCC07111F});
        g.setCornerRadius(r);
        glow.setBackground(g);
        addView(glow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout col = Ui.column(c);
        content = col;
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setPadding(Ui.px(24), Ui.px(16), Ui.px(24), Ui.px(16));

        LinearLayout row = Ui.row(c);
        maneuver = new ImageView(c);
        maneuver.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Ui.ACCENT);
        maneuver.setBackground(circle);
        int pad = Ui.px(18);
        maneuver.setPadding(pad, pad, pad, pad);
        row.addView(maneuver, new LinearLayout.LayoutParams(Ui.px(104), Ui.px(104)));
        LinearLayout texts = Ui.column(c);
        title = Ui.text(c, "", 40, Ui.TEXT, Ui.MEDIUM);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        text = Ui.text(c, "", 22, Ui.TEXT, Ui.REGULAR);
        text.setMaxLines(3);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setLineSpacing(Ui.px(4), 1f);
        texts.addView(title);
        texts.addView(text);
        ((LinearLayout.LayoutParams) text.getLayoutParams()).topMargin = Ui.px(8);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.px(22);
        row.addView(texts, tlp);
        col.addView(row);

        sub = Ui.text(c, "", 18, Ui.TEXT2, Ui.REGULAR);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(sub);
        ((LinearLayout.LayoutParams) sub.getLayoutParams()).topMargin = Ui.px(18);

        app = Ui.text(c, "", 13, Ui.ACCENT, Ui.MEDIUM);
        app.setLetterSpacing(0.1f);
        col.addView(app);
        ((LinearLayout.LayoutParams) app.getLayoutParams()).topMargin = Ui.px(10);

        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        buttons = Ui.row(c);
        showMap = MapPip.roundButton(c, R.drawable.ic_map, "Tampilkan peta Google Maps",
                () -> MapPip.setMode(MapPip.Mode.MAP));
        buttons.addView(showMap);
        View close = MapPip.roundButton(c, R.drawable.ic_close, "Tutup panel navigasi, tampilkan gambar mobil",
                () -> MapPip.setMode(MapPip.Mode.CAR));
        buttons.addView(close);
        ((LinearLayout.LayoutParams) close.getLayoutParams()).leftMargin = Ui.px(10);
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        blp.setMargins(0, Ui.px(12), Ui.px(12), 0);
        addView(buttons, blp);

        setContentDescription("Navigasi berjalan. Ketuk untuk membuka aplikasi navigasi.");
        Ui.pressable(this);
        setOnClickListener(v -> NavNotificationListener.openNavApp(getContext()));
    }

    void bind(NavNotificationListener.State s) {
        if (s.maneuver != null) {
            maneuver.setImageDrawable(s.maneuver);
        } else {
            maneuver.setImageDrawable(Ui.icon(getContext(), R.drawable.ic_route, Ui.TEXT));
        }
        title.setText(s.title != null ? s.title : "Navigasi");
        text.setText(s.text != null ? s.text : "");
        text.setVisibility(s.text != null ? VISIBLE : GONE);
        sub.setText(s.sub != null ? s.sub : "");
        sub.setVisibility(s.sub != null ? VISIBLE : GONE);
        app.setText("com.waze".equals(s.app) ? "WAZE · KETUK UNTUK MEMBUKA" : "GOOGLE MAPS · KETUK UNTUK MEMBUKA");
        showMap.setVisibility(MapPip.mapAvailable(getContext()) ? VISIBLE : GONE);
    }

    /** Saat peta Google Maps menempati kartu ini, petunjuk teks dan tombolnya disembunyikan. */
    void setMapMode(boolean map) {
        for (View v : new View[]{content, buttons}) {
            v.animate().cancel();
            v.animate().alpha(map ? 0f : 1f).setDuration(200).start();
        }
    }
}
