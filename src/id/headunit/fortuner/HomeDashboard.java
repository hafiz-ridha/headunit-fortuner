package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.widget.LinearLayout;

import id.headunit.fortuner.AppLauncherRepository.Shortcut;

/** Halaman Beranda: panel mobil, tiga kartu utama, widget Al-Qur'an, dock aplikasi, dan panel kontrol. */
final class HomeDashboard extends LinearLayout {
    private final LauncherActivity activity;
    private final AppLauncherRepository repo;
    final VehiclePanel vehicle;
    final QuranWidget quran;
    final BottomAppDock dock;
    final VehicleControlBar controls;
    private final AppShortcutCard youtube;
    private final AppShortcutCard navigation;

    @SuppressLint("ViewConstructor")
    HomeDashboard(LauncherActivity a, AppLauncherRepository repo, LocationTracker tracker) {
        super(a);
        this.activity = a;
        this.repo = repo;
        setOrientation(VERTICAL);
        setPadding(Ui.px(16), Ui.px(6), Ui.px(22), Ui.px(12));

        LinearLayout middle = Ui.row(a);
        vehicle = new VehiclePanel(a, tracker, a::requestLocationPermission);
        middle.addView(vehicle, Ui.weight(0.42f, true));
        middle.addView(Ui.space(a, 16, 1));

        LinearLayout right = Ui.column(a);
        LinearLayout cards = Ui.row(a);
        float r = Ui.px(22);
        youtube = new AppShortcutCard(a, "YouTube", Icons.youtube(), Icons.videoCardBackground(r), 1.43f);
        youtube.setOnClickListener(v -> repo.open(a, Shortcut.YOUTUBE));
        navigation = new AppShortcutCard(a, "Navigasi", Icons.mapPin(), Icons.mapCardBackground(r), 0.8f);
        navigation.setOnClickListener(v -> repo.open(a, Shortcut.NAVIGATION));
        AppShortcutCard quranCard = new AppShortcutCard(a, "Al-Qur'an", Icons.quranBook(), Icons.quranCardBackground(r), 1.3f);
        quranCard.setOnClickListener(v -> a.showPage(LauncherActivity.PAGE_QURAN));
        cards.addView(youtube, Ui.weight(1f, true));
        cards.addView(Ui.space(a, 14, 1));
        cards.addView(navigation, Ui.weight(1f, true));
        cards.addView(Ui.space(a, 14, 1));
        cards.addView(quranCard, Ui.weight(1f, true));
        right.addView(cards, Ui.weight(0.4f, false));
        right.addView(Ui.space(a, 1, 14));
        quran = new QuranWidget(a, () -> a.showPage(LauncherActivity.PAGE_QURAN));
        right.addView(quran, Ui.weight(0.6f, false));
        middle.addView(right, Ui.weight(0.58f, true));
        addView(middle, Ui.weight(1f, false));

        dock = new BottomAppDock(a, repo);
        LayoutParams dockLp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(104));
        dockLp.topMargin = Ui.px(10);
        addView(dock, dockLp);

        controls = new VehicleControlBar(a, repo);
        LayoutParams cLp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(62));
        cLp.topMargin = Ui.px(6);
        addView(controls, cLp);
        refreshApps();
    }

    /** Perbarui ikon/keterangan kartu dan dock sesuai aplikasi yang terpasang. */
    void refreshApps() {
        Drawable yt = repo.installedIcon(Shortcut.YOUTUBE);
        youtube.setArt(yt != null ? yt : Icons.youtube());
        youtube.setNote(repo.resolve(Shortcut.YOUTUBE) != null ? null : "Belum terpasang");
        Drawable nav = repo.installedIcon(Shortcut.NAVIGATION);
        navigation.setArt(nav != null ? nav : Icons.mapPin());
        navigation.setNote(repo.resolve(Shortcut.NAVIGATION) != null ? null : "Belum terpasang");
        dock.refresh();
    }
}
