package id.headunit.fortuner;

import android.app.Application;

/** Inisialisasi tingkat proses: pemicu doa saat headunit menyala dan unduhan murattal otomatis. */
public class FortunerApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DoaTrigger.install(this);
        KeepAliveService.start(this);
        HomeGuard.afterWake(this); // proses baru dimulai (mis. setelah dimatikan sistem): pastikan launcher ini di depan
        QuranDownloadService.resumeIfWanted(this);
    }
}
