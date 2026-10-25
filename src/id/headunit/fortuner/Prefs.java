package id.headunit.fortuner;

import android.content.Context;
import android.content.SharedPreferences;

/** Preferensi sederhana launcher. */
final class Prefs {
    static final String SHOW_LATIN = "quran_show_latin";
    static final String SHOW_TRANSLATION = "quran_show_translation";
    static final String WEATHER = "weather_enabled";
    static final String FULLSCREEN = "fullscreen";
    static final String QURAN_SURAH = "quran_surah";
    static final String QURAN_AYAH = "quran_ayah";
    static final String TRIP_METERS = "trip_meters";

    private static SharedPreferences sp;

    private Prefs() { }

    static SharedPreferences get(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("launcher", Context.MODE_PRIVATE);
        return sp;
    }

    static boolean flag(Context c, String key, boolean def) { return get(c).getBoolean(key, def); }

    static void set(Context c, String key, boolean value) { get(c).edit().putBoolean(key, value).apply(); }
}
