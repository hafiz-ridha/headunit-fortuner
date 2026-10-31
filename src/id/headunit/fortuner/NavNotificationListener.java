package id.headunit.fortuner;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Membaca notifikasi panduan arah dari Google Maps/Waze (belokan berikutnya, jarak, nama jalan, waktu tiba)
 * supaya panel mobil di Beranda bisa berubah menjadi panel navigasi selama navigasi berjalan.
 * Butuh "akses notifikasi" (diberikan lewat Pengaturan atau ADB: cmd notification allow_listener ...).
 */
public class NavNotificationListener extends NotificationListenerService {
    private static final String[] NAV_APPS = {"com.google.android.apps.maps", "com.waze"};

    /** Status navigasi terkini, dibaca di thread utama. */
    static final class State {
        boolean active;
        String app;
        String title;
        String text;
        String sub;
        Drawable maneuver;
        PendingIntent open;
    }

    static final State STATE = new State();
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private String currentKey;

    static void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }

    static void removeListener(Runnable r) { listeners.remove(r); }

    private static void changed() {
        MAIN.post(() -> {
            for (Runnable r : new ArrayList<>(listeners)) r.run();
        });
    }

    static boolean hasAccess(Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(new ComponentName(c, NavNotificationListener.class).flattenToString());
    }

    /** Membuka aplikasi navigasi yang sedang memandu (layar penuh). */
    static void openNavApp(Context c) {
        PendingIntent pi = STATE.open;
        try {
            if (pi != null) {
                pi.send();
                return;
            }
        } catch (PendingIntent.CanceledException e) {
            Log.w(AppLauncherRepository.TAG, "Intent navigasi sudah tidak berlaku", e);
        }
        Intent i = STATE.app == null ? null : c.getPackageManager().getLaunchIntentForPackage(STATE.app);
        if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static boolean isNavApp(String pkg) {
        for (String p : NAV_APPS) if (p.equals(pkg)) return true;
        return false;
    }

    /** Notifikasi panduan arah: kategori navigasi, atau notifikasi tetap dari aplikasi navigasi (bukan unduhan). */
    private static boolean isGuidance(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if (Notification.CATEGORY_NAVIGATION.equals(n.category)) return true;
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        boolean progress = n.extras != null && n.extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0;
        String channel = n.getChannelId() == null ? "" : n.getChannelId().toLowerCase();
        return ongoing && !progress && (channel.contains("nav") || "com.waze".equals(sbn.getPackageName()));
    }

    @Override
    public void onListenerConnected() {
        try {
            for (StatusBarNotification sbn : getActiveNotifications()) onNotificationPosted(sbn);
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Notifikasi aktif tidak terbaca", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isNavApp(sbn.getPackageName()) || !isGuidance(sbn)) return;
        Notification n = sbn.getNotification();
        Bundle x = n.extras;
        String title = text(x, Notification.EXTRA_TITLE);
        String body = text(x, Notification.EXTRA_BIG_TEXT);
        if (body == null) body = text(x, Notification.EXTRA_TEXT);
        String sub = text(x, Notification.EXTRA_SUB_TEXT);
        Drawable icon = null;
        Icon large = n.getLargeIcon();
        if (large != null) icon = large.loadDrawable(this);
        final String pkg = sbn.getPackageName();
        final String t = title, b = body, s = sub;
        final Drawable ic = icon;
        final PendingIntent open = n.contentIntent;
        currentKey = sbn.getKey();
        MAIN.post(() -> {
            STATE.active = true;
            STATE.app = pkg;
            STATE.title = t;
            STATE.text = b;
            STATE.sub = s;
            STATE.maneuver = ic;
            STATE.open = open;
        });
        changed();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (!sbn.getKey().equals(currentKey)) return;
        currentKey = null;
        MAIN.post(() -> {
            STATE.active = false;
            STATE.maneuver = null;
            STATE.open = null;
        });
        changed();
    }

    @Override
    public void onListenerDisconnected() {
        currentKey = null;
        MAIN.post(() -> STATE.active = false);
        changed();
    }

    private static String text(Bundle x, String key) {
        if (x == null) return null;
        CharSequence cs = x.getCharSequence(key);
        if (cs == null) return null;
        String s = cs.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
