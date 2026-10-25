package id.headunit.fortuner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mencari dan membuka aplikasi. Tiap pintasan punya beberapa kandidat (paket atau komponen) yang dicoba
 * berurutan, lalu fallback berupa intent umum. Bila tidak ada yang terpasang, pengguna diberi tahu dengan
 * jelas; tidak ada tombol yang pura-pura berhasil.
 */
final class AppLauncherRepository {
    static final String TAG = "FortunerLauncher";

    /** Pintasan beserta kandidat aplikasinya. Komponen ditulis "paket/.Activity". */
    enum Shortcut {
        YOUTUBE("YouTube", "com.google.android.youtube", "app.revanced.android.youtube",
                "com.vanced.android.youtube", "com.liskovsoft.smarttubetv.beta", "com.teamsmart.videomanager.tv"),
        NAVIGATION("Navigasi", "com.google.android.apps.maps", "com.waze", "com.here.app.maps",
                "net.osmand", "net.osmand.plus", "com.sygic.aura", "ru.yandex.yandexnavi"),
        MUSIC("Musik", "com.tw.music"),
        BLUETOOTH("Bluetooth", "com.tw.bt"),
        RADIO("Radio", "com.tw.radio"),
        VIDEO("Video", "com.tw.video"),
        FILES("File Manager", "com.tw.twfileexplore", "com.android.documentsui"),
        EQUALIZER("Equalizer", "com.tw.eq/.EQActivity", "com.tw.eq"),
        CAR_LINK("Car Link", "com.zjinnova.zlink", "net.easyconn", "com.suding.speedplay",
                "com.carbit.mapp", "com.ucar.ucarlink", "cn.manstep.phonemirror", "com.autokit.autokit"),
        DSP("DSP", "com.tw.eq/.DSPActivity"),
        VEHICLE("Pengaturan Kendaraan", "com.dofun.carsetting/.ui.MainActivity", "com.dofun.carsetting");

        final String label;
        final String[] candidates;

        Shortcut(String label, String... candidates) {
            this.label = label;
            this.candidates = candidates;
        }
    }

    static final class AppEntry {
        final String label;
        final ComponentName component;

        AppEntry(String label, ComponentName component) {
            this.label = label;
            this.component = component;
        }
    }

    private final Context context;
    private final PackageManager pm;

    AppLauncherRepository(Context context) {
        this.context = context.getApplicationContext();
        this.pm = context.getPackageManager();
    }

    /** Intent untuk kandidat pertama yang benar-benar terpasang, atau null. */
    Intent resolve(Shortcut s) {
        for (String cand : s.candidates) {
            Intent i = intentFor(cand);
            if (i != null) return i;
        }
        if (s == Shortcut.MUSIC) return categoryIntent(Intent.CATEGORY_APP_MUSIC);
        if (s == Shortcut.NAVIGATION) {
            Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
            if (geo.resolveActivity(pm) != null) return geo;
        }
        return null;
    }

    boolean isAvailable(Shortcut s) {
        return resolve(s) != null;
    }

    private Intent intentFor(String candidate) {
        int slash = candidate.indexOf('/');
        if (slash < 0) {
            Intent i = pm.getLaunchIntentForPackage(candidate);
            if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return i;
        }
        String pkg = candidate.substring(0, slash);
        String cls = candidate.substring(slash + 1);
        if (cls.startsWith(".")) cls = pkg + cls;
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(new ComponentName(pkg, cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        // resolveActivity memperhitungkan status aktif/nonaktif saat runtime, bukan hanya manifest.
        ResolveInfo ri = pm.resolveActivity(i, 0);
        ActivityInfo ai = ri == null ? null : ri.activityInfo;
        if (ai == null || !ai.exported) return null;
        return i;
    }

    private Intent categoryIntent(String category) {
        Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
        if (i.resolveActivity(pm) == null) return null;
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Membuka aplikasi pintasan; bila tidak terpasang, tampilkan penjelasan (dan tawaran Play Store bila relevan). */
    void open(Activity a, Shortcut s) {
        Intent i = resolve(s);
        if (i != null && start(a, i)) return;
        explainMissing(a, s);
    }

    boolean start(Activity a, Intent i) {
        try {
            a.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Gagal membuka " + i, e);
            Toast.makeText(a, "Aplikasi tidak bisa dibuka: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void explainMissing(Activity a, Shortcut s) {
        String store = null;
        String msg;
        switch (s) {
            case YOUTUBE:
                msg = "Aplikasi YouTube belum terpasang di headunit ini.";
                store = "com.google.android.youtube";
                break;
            case NAVIGATION:
                msg = "Belum ada aplikasi navigasi di headunit ini (Google Maps, Waze, dll.).";
                store = "com.google.android.apps.maps";
                break;
            case CAR_LINK:
                msg = "Aplikasi Car Link (Zlink, EasyConnection, Carbit, AutoKit) tidak ditemukan di headunit ini.";
                break;
            default:
                msg = "Aplikasi " + s.label + " tidak ditemukan di headunit ini.";
                break;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(s.label).setMessage(msg)
                .setNegativeButton("Tutup", null);
        if (store != null) {
            final String pkg = store;
            b.setPositiveButton("Buka Play Store", (d, w) -> openStore(a, pkg));
        }
        b.show();
    }

    void openStore(Activity a, String pkg) {
        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (market.resolveActivity(pm) == null) {
            market = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        start(a, market);
    }

    /** Semua aplikasi yang bisa dibuka dari launcher, diurutkan menurut nama. */
    List<AppEntry> allApps() {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
        List<AppEntry> out = new ArrayList<>();
        for (ResolveInfo ri : list) {
            if (ri.activityInfo == null || context.getPackageName().equals(ri.activityInfo.packageName)) continue;
            CharSequence label = ri.loadLabel(pm);
            out.add(new AppEntry(label == null ? ri.activityInfo.packageName : label.toString(),
                    new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)));
        }
        final Collator col = Collator.getInstance();
        Collections.sort(out, (x, y) -> col.compare(x.label, y.label));
        return out;
    }

    void openApp(Activity a, AppEntry e) {
        start(a, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    void openAppInfo(Activity a, String pkg) {
        start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Launcher HOME lain (mis. Dofun) supaya pengguna bisa berpindah kapan saja. */
    ResolveInfo otherHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo ri : pm.queryIntentActivities(home, 0)) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(context.getPackageName()) && !"com.android.settings".equals(p)
                    && !p.startsWith("com.android.provision") && !"android".equals(p)) {
                return ri;
            }
        }
        return null;
    }

    /** True bila launcher ini yang sekarang dipakai sebagai HOME default. */
    boolean isDefaultHome() {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && context.getPackageName().equals(ri.activityInfo.packageName);
    }

    android.graphics.drawable.Drawable iconOf(ComponentName cn) {
        try {
            return pm.getActivityIcon(cn);
        } catch (PackageManager.NameNotFoundException e) {
            return pm.getDefaultActivityIcon();
        }
    }

    /** Ikon asli aplikasi kandidat pertama yang terpasang (untuk kartu YouTube/Navigasi), atau null. */
    android.graphics.drawable.Drawable installedIcon(Shortcut s) {
        for (String cand : s.candidates) {
            String pkg = cand.contains("/") ? cand.substring(0, cand.indexOf('/')) : cand;
            try {
                return pm.getApplicationIcon(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
                // coba kandidat berikutnya
            }
        }
        return null;
    }
}
