package id.headunit.fortuner;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Teks Al-Qur'an dari assets/quran/ (dibuat oleh tools/build_quran.mjs dari API equran.id, sumber
 * Kemenag RI). Teks tidak pernah dibuat atau diubah oleh aplikasi; hanya dibaca apa adanya.
 */
final class QuranRepository {
    static final class SurahInfo {
        final int number;
        final String latin;
        final String arabic;
        final String meaning;
        final int ayahCount;
        final String place;

        SurahInfo(int number, String latin, String arabic, String meaning, int ayahCount, String place) {
            this.number = number;
            this.latin = latin;
            this.arabic = arabic;
            this.meaning = meaning;
            this.ayahCount = ayahCount;
            this.place = place;
        }
    }

    static final class Ayah {
        final int number;
        final String arabic;
        final String latin;
        final String translation;
        final String audioUrl;

        Ayah(int number, String arabic, String latin, String translation, String audioUrl) {
            this.number = number;
            this.arabic = arabic;
            this.latin = latin;
            this.translation = translation;
            this.audioUrl = audioUrl;
        }
    }

    static final class Surah {
        final SurahInfo info;
        final List<Ayah> ayat;

        Surah(SurahInfo info, List<Ayah> ayat) {
            this.info = info;
            this.ayat = ayat;
        }
    }

    private static QuranRepository instance;
    private final Context context;
    private List<SurahInfo> index;
    private final LruCache<Integer, Surah> cache = new LruCache<>(4);

    static synchronized QuranRepository get(Context c) {
        if (instance == null) instance = new QuranRepository(c.getApplicationContext());
        return instance;
    }

    private QuranRepository(Context context) {
        this.context = context;
    }

    synchronized List<SurahInfo> index() {
        if (index != null) return index;
        List<SurahInfo> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(read("quran/index.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONArray s = arr.getJSONArray(i);
                list.add(new SurahInfo(s.getInt(0), s.getString(1), s.getString(2), s.getString(3),
                        s.getInt(4), s.getString(5)));
            }
        } catch (IOException | JSONException e) {
            Log.e(AppLauncherRepository.TAG, "Indeks Al-Qur'an tidak terbaca", e);
        }
        index = Collections.unmodifiableList(list);
        return index;
    }

    SurahInfo info(int number) {
        List<SurahInfo> idx = index();
        return number >= 1 && number <= idx.size() ? idx.get(number - 1) : null;
    }

    synchronized Surah surah(int number) {
        Surah s = cache.get(number);
        if (s != null) return s;
        SurahInfo info = info(number);
        if (info == null) return null;
        try {
            JSONObject o = new JSONObject(read(String.format(Locale.US, "quran/%03d.json", number)));
            JSONArray arr = o.getJSONArray("ayat");
            List<Ayah> ayat = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                JSONArray a = arr.getJSONArray(i);
                ayat.add(new Ayah(i + 1, a.getString(0), a.getString(1), a.getString(2), a.optString(3, "")));
            }
            s = new Surah(info, Collections.unmodifiableList(ayat));
            cache.put(number, s);
            return s;
        } catch (IOException | JSONException e) {
            Log.e(AppLauncherRepository.TAG, "Surah " + number + " tidak terbaca", e);
            return null;
        }
    }

    Ayah ayah(int surah, int ayah) {
        Surah s = surah(surah);
        if (s == null || ayah < 1 || ayah > s.ayat.size()) return null;
        return s.ayat.get(ayah - 1);
    }

    private String read(String asset) throws IOException {
        try (InputStream in = context.getAssets().open(asset)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
