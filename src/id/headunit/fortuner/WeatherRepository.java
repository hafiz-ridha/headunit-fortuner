package id.headunit.fortuner;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Cuaca aktual dari Open-Meteo (tanpa API key) berdasarkan lokasi GPS/jaringan headunit.
 * Tidak pernah menampilkan angka contoh: tanpa data, UI menulis "menunggu lokasi" atau "offline".
 */
final class WeatherRepository {
    interface Listener {
        void onWeather();
    }

    private static final long REFRESH_MS = 30 * 60 * 1000L;
    private static final long MAX_AGE_MS = 3 * 60 * 60 * 1000L;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean loading;

    Float temperature;
    int code = -1;
    boolean isDay = true;
    String place;
    long fetchedAt;
    String status = "Menunggu lokasi";

    WeatherRepository(Context context) {
        this.context = context.getApplicationContext();
        SharedPreferences sp = Prefs.get(context);
        long at = sp.getLong("wx_at", 0);
        if (System.currentTimeMillis() - at < MAX_AGE_MS) {
            temperature = sp.getFloat("wx_temp", 0f);
            code = sp.getInt("wx_code", -1);
            isDay = sp.getBoolean("wx_day", true);
            place = sp.getString("wx_place", null);
            fetchedAt = at;
        }
    }

    void setListener(Listener l) { listener = l; }

    boolean hasData() {
        return Prefs.flag(context, Prefs.WEATHER, true) && temperature != null
                && System.currentTimeMillis() - fetchedAt < MAX_AGE_MS;
    }

    void refreshIfStale(Location loc) { refresh(loc, false); }

    void refresh(Location loc, boolean force) {
        if (!Prefs.flag(context, Prefs.WEATHER, true)) {
            status = "Cuaca nonaktif";
            return;
        }
        if (loading || (!force && System.currentTimeMillis() - fetchedAt < REFRESH_MS)) return;
        if (loc == null && !QuranDownloadService.hasNetwork(context)) {
            status = "Menunggu lokasi";
            return;
        }
        loading = true;
        if (loc != null) {
            final double lat = loc.getLatitude(), lon = loc.getLongitude();
            new Thread(() -> fetch(lat, lon, null), "weather").start();
        } else {
            // Belum ada GPS/lokasi jaringan: pakai perkiraan lokasi dari alamat IP internet.
            new Thread(this::fetchFromIp, "weather-ip").start();
        }
    }

    /** Perkiraan lokasi dari IP (get.geojs.io, tanpa API key), lalu ambil cuacanya. */
    private void fetchFromIp() {
        try {
            JSONObject geo = new JSONObject(get("https://get.geojs.io/v1/ip/geo.json"));
            double lat = Double.parseDouble(geo.getString("latitude"));
            double lon = Double.parseDouble(geo.getString("longitude"));
            String city = geo.optString("city", null);
            fetch(lat, lon, city == null || city.isEmpty() ? null : city);
        } catch (Exception e) {
            Log.w(AppLauncherRepository.TAG, "Lokasi dari IP gagal", e);
            main.post(() -> {
                loading = false;
                status = "Menunggu lokasi";
                if (listener != null) listener.onWeather();
            });
        }
    }

    private void fetch(double lat, double lon, String fallbackPlace) {
        try {
            String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                    + "&current=temperature_2m,weather_code,is_day&timezone=auto", lat, lon);
            JSONObject cur = new JSONObject(get(url)).getJSONObject("current");
            final float t = (float) cur.getDouble("temperature_2m");
            final int c = cur.getInt("weather_code");
            final boolean day = cur.optInt("is_day", 1) == 1;
            String geocoded = placeName(lat, lon);
            final String p = geocoded != null ? geocoded : fallbackPlace;
            main.post(() -> {
                temperature = t;
                code = c;
                isDay = day;
                if (p != null) place = p;
                fetchedAt = System.currentTimeMillis();
                status = null;
                loading = false;
                Prefs.get(context).edit().putLong("wx_at", fetchedAt).putFloat("wx_temp", t).putInt("wx_code", c)
                        .putBoolean("wx_day", day).putString("wx_place", place).apply();
                if (listener != null) listener.onWeather();
            });
        } catch (Exception e) {
            Log.w(AppLauncherRepository.TAG, "Cuaca gagal diambil", e);
            main.post(() -> {
                loading = false;
                status = "Cuaca offline";
                if (listener != null) listener.onWeather();
            });
        }
    }

    private String placeName(double lat, double lon) {
        if (!Geocoder.isPresent()) return null;
        try {
            List<Address> list = new Geocoder(context, new Locale("id", "ID")).getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return null;
            Address a = list.get(0);
            if (a.getSubAdminArea() != null) return a.getSubAdminArea().replace("Kota ", "").replace("Kabupaten ", "");
            if (a.getLocality() != null) return a.getLocality();
            return a.getAdminArea();
        } catch (Exception e) {
            return null;
        }
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }

    /** Kode cuaca WMO ke teks Bahasa Indonesia. */
    String description() {
        if (code < 0) return "";
        if (code == 0) return "Cerah";
        if (code <= 2) return "Cerah berawan";
        if (code == 3) return "Berawan";
        if (code <= 48) return "Berkabut";
        if (code <= 57) return "Gerimis";
        if (code <= 67) return "Hujan";
        if (code <= 77) return "Salju";
        if (code <= 82) return "Hujan lebat";
        return "Badai petir";
    }
}
