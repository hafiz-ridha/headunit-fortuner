package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Halaman Aplikasi: semua aplikasi terpasang. Tekan lama untuk membuka info aplikasi. */
final class AppsPage extends LinearLayout {
    private final AppLauncherRepository repo;
    private final TextView count;
    private final Adapter adapter = new Adapter();
    private List<AppLauncherRepository.AppEntry> apps = new ArrayList<>();

    @SuppressLint("ViewConstructor")
    AppsPage(Activity a, AppLauncherRepository repo) {
        super(a);
        this.repo = repo;
        setOrientation(VERTICAL);
        setPadding(Ui.px(24), Ui.px(8), Ui.px(24), Ui.px(12));
        LinearLayout header = Ui.row(a);
        header.addView(Ui.text(a, "Aplikasi", 30, Ui.TEXT, Ui.MEDIUM));
        header.addView(Ui.space(a, 16, 1));
        count = Ui.text(a, "", 17, Ui.TEXT2, Ui.REGULAR);
        header.addView(count);
        header.addView(Ui.flex(a));
        header.addView(Ui.text(a, "Tekan lama untuk info aplikasi", 15, Ui.TEXT3, Ui.REGULAR));
        addView(header);

        GridView grid = new GridView(a);
        grid.setColumnWidth(Ui.px(150));
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(Ui.px(14));
        grid.setHorizontalSpacing(Ui.px(14));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, pos, id) -> repo.openApp(a, apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> {
            repo.openAppInfo(a, apps.get(pos).component.getPackageName());
            return true;
        });
        LayoutParams lp = Ui.weight(1f, false);
        lp.topMargin = Ui.px(16);
        addView(grid, lp);
        reload();
    }

    void reload() {
        apps = repo.allApps();
        count.setText(apps.size() + " aplikasi");
        adapter.notifyDataSetChanged();
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }

        @Override public Object getItem(int i) { return apps.get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell = (LinearLayout) convert;
            if (cell == null) {
                cell = Ui.column(parent.getContext());
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(Ui.px(8), Ui.px(14), Ui.px(8), Ui.px(12));
                cell.setBackground(Ui.panel(18));
                ImageView icon = new ImageView(parent.getContext());
                cell.addView(icon, new LayoutParams(Ui.px(64), Ui.px(64)));
                TextView t = Ui.text(parent.getContext(), "", 16, Ui.TEXT, Ui.REGULAR);
                t.setGravity(Gravity.CENTER);
                t.setLines(2);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(t, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
                ((LayoutParams) t.getLayoutParams()).topMargin = Ui.px(10);
            }
            AppLauncherRepository.AppEntry e = apps.get(i);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(repo.iconOf(e.component));
            ((TextView) cell.getChildAt(1)).setText(e.label);
            return cell;
        }
    }
}
