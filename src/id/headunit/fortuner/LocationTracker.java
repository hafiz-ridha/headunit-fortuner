package id.headunit.fortuner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Kecepatan dan jarak tempuh dari GPS headunit. Ini satu-satunya data "kendaraan" yang benar-benar
 * tersedia untuk aplikasi biasa; data CAN-bus (BBM, transmisi) tidak dibaca.
 */
final class LocationTracker implements LocationListener {
    enum State { NO_PERMISSION, GPS_OFF, SEARCHING, FIX }

    interface Listener {
        void onLocationState();
    }

    private static final long STALE_MS = 5000;
    private final Context context;
    private final LocationManager lm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private boolean running;
    private Location last;
    private long lastFixAt;
    private float speedKmh = -1;
    private double tripMeters;
    private long tripSavedAt;

    LocationTracker(Context context) {
        this.context = context.getApplicationContext();
        this.lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        tripMeters = Prefs.get(context).getFloat(Prefs.TRIP_METERS, 0f);
    }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    void removeListener(Listener l) { listeners.remove(l); }

    boolean hasPermission() {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    void start() {
        ensureLocationEnabled(context);
        if (running || lm == null || !hasPermission()) {
            notifyListeners();
            return;
        }
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this, Looper.getMainLooper());
                running = true;
                main.postDelayed(staleCheck, STALE_MS);
            }
            // Lokasi kasar dari jaringan (Wi-Fi/seluler) untuk cuaca saat GPS belum dapat sinyal, mis. di garasi.
            if (lm.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)
                    && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000, 0, networkListener, Looper.getMainLooper());
            }
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "GPS tidak bisa dipakai", e);
        }
        notifyListeners();
    }

    /**
     * Menyalakan setelan Lokasi Android bila sedang mati. Butuh izin WRITE_SECURE_SETTINGS yang diberikan
     * lewat ADB (pm grant); tanpa izin itu tidak melakukan apa-apa. Mengembalikan true bila setelan diubah.
     */
    @SuppressWarnings("deprecation")
    static boolean ensureLocationEnabled(Context c) {
        try {
            int mode = android.provider.Settings.Secure.getInt(c.getContentResolver(),
                    android.provider.Settings.Secure.LOCATION_MODE, android.provider.Settings.Secure.LOCATION_MODE_OFF);
            if (mode != android.provider.Settings.Secure.LOCATION_MODE_OFF) return false;
            if (c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            android.provider.Settings.Secure.putInt(c.getContentResolver(), android.provider.Settings.Secure.LOCATION_MODE,
                    android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
            Log.i(AppLauncherRepository.TAG, "Lokasi Android dinyalakan otomatis");
            return true;
        } catch (RuntimeException e) {
            Log.w(AppLauncherRepository.TAG, "Lokasi tidak bisa dinyalakan otomatis", e);
            return false;
        }
    }

    private Location networkFix;

    private final LocationListener networkListener = new LocationListener() {
        @Override public void onLocationChanged(Location loc) {
            networkFix = loc;
            notifyListeners();
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

        @Override public void onProviderEnabled(String provider) { }

        @Override public void onProviderDisabled(String provider) { }
    };

    void stop() {
        if (lm != null) {
            lm.removeUpdates(this);
            lm.removeUpdates(networkListener);
        }
        running = false;
        main.removeCallbacks(staleCheck);
        saveTrip(true);
    }

    State state() {
        if (!hasPermission()) return State.NO_PERMISSION;
        if (lm == null || !lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return State.GPS_OFF;
        return hasFix() ? State.FIX : State.SEARCHING;
    }

    boolean hasFix() { return lastFixAt != 0 && SystemClock.elapsedRealtime() - lastFixAt < STALE_MS; }

    /** km/h, atau -1 bila belum ada sinyal GPS. */
    float speedKmh() { return hasFix() ? speedKmh : -1; }

    double tripKm() { return tripMeters / 1000.0; }

    void resetTrip() {
        tripMeters = 0;
        saveTrip(true);
        notifyListeners();
    }

    /** Lokasi terakhir yang diketahui (untuk cuaca), dari provider mana pun. */
    @SuppressWarnings("MissingPermission")
    Location lastKnown() {
        if (last != null) return last;
        if (networkFix != null) return networkFix;
        if (lm == null || !hasPermission()) return null;
        Location best = null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (RuntimeException ignored) {
                // provider tidak bisa dibaca
            }
        }
        return best;
    }

    @Override
    public void onLocationChanged(Location loc) {
        long now = SystemClock.elapsedRealtime();
        float speed;
        if (loc.hasSpeed()) {
            speed = loc.getSpeed() * 3.6f;
        } else if (last != null && lastFixAt != 0) {
            float dt = (now - lastFixAt) / 1000f;
            speed = dt > 0 ? last.distanceTo(loc) / dt * 3.6f : 0;
        } else {
            speed = 0;
        }
        if (speed < 2f) speed = 0; // derau GPS saat diam
        // Jarak hanya dihitung bila akurasi baik dan mobil benar-benar bergerak.
        if (last != null && speed >= 3f && (!loc.hasAccuracy() || loc.getAccuracy() <= 25f)) {
            float d = last.distanceTo(loc);
            if (d < 250f) tripMeters += d;
        }
        speedKmh = speed;
        last = loc;
        lastFixAt = now;
        saveTrip(false);
        notifyListeners();
    }

    private void saveTrip(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (force || now - tripSavedAt > 10_000) {
            Prefs.get(context).edit().putFloat(Prefs.TRIP_METERS, (float) tripMeters).apply();
            tripSavedAt = now;
        }
    }

    private final Runnable staleCheck = new Runnable() {
        @Override
        public void run() {
            notifyListeners();
            if (running) main.postDelayed(this, STALE_MS);
        }
    };

    private void notifyListeners() {
        for (Listener l : new ArrayList<>(listeners)) l.onLocationState();
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override public void onProviderEnabled(String provider) { notifyListeners(); }

    @Override public void onProviderDisabled(String provider) { notifyListeners(); }
}
