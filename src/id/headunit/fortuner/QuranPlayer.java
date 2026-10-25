package id.headunit.fortuner;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Posisi baca (surah:ayat) dan pemutar murattal per ayat. Audio diambil dari unduhan offline
 * ({@link QuranAudioStore}) bila ada, selain itu di-stream dari cdn.equran.id; setelah satu ayat selesai,
 * ayat berikutnya diputar sampai akhir surah. Tombol media (termasuk tombol setir bila
 * headunit meneruskannya) dikendalikan lewat MediaSession.
 */
final class QuranPlayer {
    interface Listener {
        void onQuranChanged();
    }

    private static QuranPlayer instance;
    private final Context context;
    private final QuranRepository repo;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private MediaPlayer player;
    private MediaSession session;
    private AudioFocusRequest focusRequest;

    private int surah;
    private int ayah;
    private boolean playing;
    private boolean preparing;
    private String error;

    static synchronized QuranPlayer get(Context c) {
        if (instance == null) instance = new QuranPlayer(c.getApplicationContext());
        return instance;
    }

    private QuranPlayer(Context context) {
        this.context = context;
        this.repo = QuranRepository.get(context);
        this.audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        surah = Prefs.get(context).getInt(Prefs.QURAN_SURAH, 1);
        ayah = Prefs.get(context).getInt(Prefs.QURAN_AYAH, 1);
        if (repo.ayah(surah, ayah) == null) {
            surah = 1;
            ayah = 1;
        }
    }

    int surah() { return surah; }

    int ayah() { return ayah; }

    boolean isPlaying() { return playing || preparing; }

    boolean isPreparing() { return preparing; }

    String error() { return error; }

    void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    void removeListener(Listener l) { listeners.remove(l); }

    private void changed() {
        main.post(() -> {
            for (Listener l : new ArrayList<>(listeners)) l.onQuranChanged();
        });
    }

    /** Pindah posisi; bila sedang memutar, langsung memutar ayat baru. */
    void setPosition(int s, int a) {
        if (repo.ayah(s, a) == null) return;
        boolean wasPlaying = isPlaying();
        surah = s;
        ayah = a;
        Prefs.get(context).edit().putInt(Prefs.QURAN_SURAH, s).putInt(Prefs.QURAN_AYAH, a).apply();
        error = null;
        if (wasPlaying) playCurrent();
        else changed();
    }

    void next() {
        QuranRepository.SurahInfo info = repo.info(surah);
        if (info == null) return;
        if (ayah < info.ayahCount) setPosition(surah, ayah + 1);
        else if (surah < 114) setPosition(surah + 1, 1);
    }

    void previous() {
        if (ayah > 1) setPosition(surah, ayah - 1);
        else if (surah > 1) {
            QuranRepository.SurahInfo prev = repo.info(surah - 1);
            if (prev != null) setPosition(surah - 1, prev.ayahCount);
        }
    }

    void toggle() {
        if (isPlaying()) pause();
        else playCurrent();
    }

    void playCurrent() {
        QuranRepository.Ayah a = repo.ayah(surah, ayah);
        java.io.File local = QuranAudioStore.file(context, surah, ayah);
        boolean offline = local.length() > 0;
        if (a == null || (!offline && a.audioUrl.isEmpty())) {
            error = "Audio ayat ini tidak tersedia";
            changed();
            return;
        }
        if (!requestFocus()) {
            error = "Audio sedang dipakai aplikasi lain";
            changed();
            return;
        }
        error = null;
        ensurePlayer();
        try {
            player.reset();
            // File unduhan dipakai lebih dulu, jadi murattal tetap jalan tanpa internet.
            player.setDataSource(offline ? local.getPath() : a.audioUrl);
            preparing = true;
            playing = false;
            player.prepareAsync();
        } catch (Exception e) {
            Log.w(AppLauncherRepository.TAG, "Murattal gagal disiapkan", e);
            preparing = false;
            error = "Audio gagal dimuat";
        }
        updateSession();
        try {
            context.startService(new Intent(context, QuranAudioService.class));
        } catch (IllegalStateException e) {
            // Android 8+ menolak startService dari latar; audio tetap jalan selama proses hidup.
            Log.w(AppLauncherRepository.TAG, "Layanan murattal tidak bisa dimulai dari latar", e);
        }
        changed();
    }

    void pause() {
        if (player != null && playing) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
                // pemutar sudah berhenti
            }
        }
        if (preparing && player != null) player.reset();
        playing = false;
        preparing = false;
        updateSession();
        changed();
    }

    void stop() {
        pause();
        abandonFocus();
        if (player != null) {
            player.release();
            player = null;
        }
        context.stopService(new Intent(context, QuranAudioService.class));
        changed();
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(mp -> {
            preparing = false;
            playing = true;
            mp.start();
            updateSession();
            changed();
        });
        player.setOnCompletionListener(mp -> {
            playing = false;
            QuranRepository.SurahInfo info = repo.info(surah);
            if (info != null && ayah < info.ayahCount) {
                surah = info.number;
                ayah = ayah + 1;
                Prefs.get(context).edit().putInt(Prefs.QURAN_AYAH, ayah).apply();
                playCurrent();
            } else {
                stop();
            }
        });
        player.setOnErrorListener((mp, what, extra) -> {
            Log.w(AppLauncherRepository.TAG, "MediaPlayer error " + what + "/" + extra);
            playing = false;
            preparing = false;
            error = QuranAudioStore.has(context, surah, ayah) ? "Audio gagal diputar"
                    : "Audio surah ini belum diunduh dan tidak ada internet";
            updateSession();
            changed();
            return true;
        });
    }

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && player != null && playing) {
            player.setVolume(0.3f, 0.3f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN && player != null) {
            player.setVolume(1f, 1f);
        }
    };

    @SuppressWarnings("deprecation")
    private boolean requestFocus() {
        int r;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setOnAudioFocusChangeListener(focusListener, main).build();
            }
            r = audio.requestAudioFocus(focusRequest);
        } else {
            r = audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest != null) audio.abandonAudioFocusRequest(focusRequest);
        } else {
            audio.abandonAudioFocus(focusListener);
        }
    }

    /**
     * MediaSession hanya hidup selama murattal diputar. Saat dijeda/berhenti sesi dilepas, supaya tombol
     * media dan tombol setir kembali ke pemutar musik/radio bawaan headunit.
     */
    private void updateSession() {
        if (!playing && !preparing) {
            if (session != null) {
                session.setActive(false);
                session.release();
                session = null;
            }
            return;
        }
        if (session == null) {
            session = new MediaSession(context, "FortunerQuran");
            session.setCallback(new MediaSession.Callback() {
                @Override public void onPlay() { playCurrent(); }

                @Override public void onPause() { pause(); }

                @Override public void onStop() { stop(); }

                @Override public void onSkipToNext() { next(); }

                @Override public void onSkipToPrevious() { previous(); }
            });
        }
        int state = preparing ? PlaybackState.STATE_BUFFERING : PlaybackState.STATE_PLAYING;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                        | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f).build());
        session.setActive(true);
    }

    /** Judul untuk notifikasi, mis. "Al-Fatihah · Ayat 1". */
    String title() {
        QuranRepository.SurahInfo info = repo.info(surah);
        return (info == null ? "Al-Qur'an" : info.latin) + " · Ayat " + ayah;
    }
}
