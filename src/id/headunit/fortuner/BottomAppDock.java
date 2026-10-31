package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import id.headunit.fortuner.AppLauncherRepository.Shortcut;

/** Deretan pintasan aplikasi headunit. Aplikasi yang tidak terpasang tampil redup dan menjelaskan dirinya. */
final class BottomAppDock extends LinearLayout {
    private static final Shortcut[] ITEMS = {Shortcut.MUSIC, Shortcut.BLUETOOTH, Shortcut.RADIO,
            Shortcut.FILES, Shortcut.EQUALIZER, Shortcut.CAR_LINK};
    private static final int[] ICONS = {R.drawable.ic_music, R.drawable.ic_bluetooth, R.drawable.ic_radio,
            R.drawable.ic_folder, R.drawable.ic_equalizer, R.drawable.ic_phonelink};
    private static final int[][] COLORS = {
            {0xFFFF5F7E, 0xFFE0304F}, {0xFF3B9BFF, 0xFF1565E0}, {0xFF9B6BFF, 0xFF6A3FE0},
            {0xFFFFC24A, 0xFFF0961C}, {0xFF55657E, 0xFF35435A}, {0xFF3DDC84, 0xFF16A35A}};

    private final AppLauncherRepository repo;
    private final ImageView[] tiles = new ImageView[ITEMS.length];

    @SuppressLint("ViewConstructor")
    BottomAppDock(Activity a, AppLauncherRepository repo) {
        super(a);
        this.repo = repo;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < ITEMS.length; i++) {
            final Shortcut s = ITEMS[i];
            LinearLayout item = Ui.column(a);
            item.setGravity(Gravity.CENTER);
            ImageView tile = Ui.iconView(a, ICONS[i], 0xFFFFFFFF, 64);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, COLORS[i]);
            g.setCornerRadius(Ui.px(18));
            tile.setBackground(g);
            int pad = Ui.px(15);
            tile.setPadding(pad, pad, pad, pad);
            tile.setElevation(Ui.px(4));
            item.addView(tile);
            TextView label = Ui.text(a, s.label, 16, Ui.TEXT, Ui.REGULAR);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            item.addView(label);
            ((LayoutParams) label.getLayoutParams()).topMargin = Ui.px(9);
            item.setContentDescription(s.label);
            Ui.pressable(item);
            item.setOnClickListener(v -> repo.open(a, s));
            tiles[i] = tile;
            addView(item, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        }
        refresh();
    }

    /** Redupkan pintasan yang aplikasinya tidak terpasang (dipanggil ulang saat paket berubah). */
    void refresh() {
        for (int i = 0; i < ITEMS.length; i++) {
            boolean ok = repo.isAvailable(ITEMS[i]);
            tiles[i].setAlpha(ok ? 1f : 0.35f);
            ((TextView) ((LinearLayout) getChildAt(i)).getChildAt(1)).setTextColor(ok ? Ui.TEXT : Ui.TEXT3);
        }
    }
}
