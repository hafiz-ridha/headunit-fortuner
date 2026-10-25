package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Sidebar kiri: Beranda, Aplikasi, Pengaturan. */
final class NavigationSidebar extends LinearLayout {
    interface OnSelect {
        void onSelect(String page);
    }

    static final String HOME = "home", APPS = "apps", SETTINGS = "settings";
    private final String[] ids = {HOME, APPS, SETTINGS};
    private final String[] labels = {"Beranda", "Aplikasi", "Pengaturan"};
    private final int[] icons = {R.drawable.ic_home, R.drawable.ic_apps, R.drawable.ic_settings};
    private final LinearLayout[] items = new LinearLayout[3];

    @SuppressLint("ViewConstructor")
    NavigationSidebar(Context c, OnSelect onSelect) {
        super(c);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setBackground(Ui.gradient(0xFF091526, 0xFF07111F, 0));
        setPadding(0, Ui.px(84), 0, 0);

        for (int i = 0; i < 3; i++) {
            final String id = ids[i];
            LinearLayout item = Ui.column(c);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LayoutParams(Ui.px(92), Ui.px(100)));
            ((LayoutParams) item.getLayoutParams()).topMargin = Ui.px(i == 0 ? 6 : 14);
            item.addView(Ui.iconView(c, icons[i], Ui.TEXT2, 36));
            TextView t = Ui.text(c, labels[i], 16, Ui.TEXT2, Ui.REGULAR);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            item.addView(t);
            ((LayoutParams) t.getLayoutParams()).topMargin = Ui.px(10);
            Ui.pressable(item);
            item.setOnClickListener(v -> onSelect.onSelect(id));
            items[i] = item;
            addView(item);
        }
        View sep = new View(c);
        sep.setLayoutParams(new LayoutParams(1, 1, 1f));
        addView(sep);
    }

    void setActive(String page) {
        for (int i = 0; i < 3; i++) {
            boolean on = ids[i].equals(page);
            LinearLayout item = items[i];
            if (on) {
                android.graphics.drawable.GradientDrawable g = Ui.gradient(0xFF173A66, 0xFF11294A, 16);
                g.setStroke(Math.max(1, Ui.px(1.2f)), 0x661685FF);
                item.setBackground(g);
            } else {
                item.setBackground(null);
            }
            ((ImageView) item.getChildAt(0)).setImageDrawable(
                    Ui.icon(getContext(), icons[i], on ? Ui.ACCENT : Ui.TEXT2));
            ((TextView) item.getChildAt(1)).setTextColor(on ? Ui.TEXT : Ui.TEXT2);
        }
    }
}
