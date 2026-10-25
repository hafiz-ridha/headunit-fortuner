package id.headunit.fortuner;

import android.content.Context;

import java.io.File;
import java.util.Locale;

/**
 * Lokasi murattal offline: Android/data/id.headunit.fortuner/files/murattal/SSS/SSSAAA.mp3.
 * Surah yang sudah lengkap diberi penanda ".complete" agar pengecekan cepat.
 */
final class QuranAudioStore {
    static final int TOTAL_AYAT = 6236;

    private QuranAudioStore() { }

    static File root(Context c) {
        File d = c.getExternalFilesDir("murattal");
        return d != null ? d : new File(c.getFilesDir(), "murattal");
    }

    static File surahDir(Context c, int surah) {
        return new File(root(c), String.format(Locale.US, "%03d", surah));
    }

    static File file(Context c, int surah, int ayah) {
        return new File(surahDir(c, surah), String.format(Locale.US, "%03d%03d.mp3", surah, ayah));
    }

    static boolean has(Context c, int surah, int ayah) {
        return file(c, surah, ayah).length() > 0;
    }

    static boolean isComplete(Context c, int surah) {
        return new File(surahDir(c, surah), ".complete").exists();
    }

    static void markComplete(Context c, int surah) {
        try {
            new File(surahDir(c, surah), ".complete").createNewFile();
        } catch (java.io.IOException ignored) {
            // penanda hanya mempercepat pengecekan; file audionya tetap dipakai
        }
    }

    static int completeSurahs(Context c) {
        int n = 0;
        for (int s = 1; s <= 114; s++) if (isComplete(c, s)) n++;
        return n;
    }

    /** Jumlah ayat yang sudah tersimpan (menghitung file .mp3). */
    static int storedAyat(Context c) {
        int n = 0;
        for (int s = 1; s <= 114; s++) {
            String[] names = surahDir(c, s).list();
            if (names == null) continue;
            for (String name : names) if (name.endsWith(".mp3")) n++;
        }
        return n;
    }

    static long bytesUsed(Context c) {
        long total = 0;
        File[] dirs = root(c).listFiles();
        if (dirs == null) return 0;
        for (File d : dirs) {
            File[] files = d.listFiles();
            if (files == null) continue;
            for (File f : files) total += f.length();
        }
        return total;
    }

    static void deleteAll(Context c) {
        File[] dirs = root(c).listFiles();
        if (dirs == null) return;
        for (File d : dirs) {
            File[] files = d.listFiles();
            if (files != null) for (File f : files) f.delete();
            d.delete();
        }
    }
}
