package id.headunit.fortuner;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Launcher utama. UI disusun dalam kode (tanpa XML layout) dan dibangun ulang bila ukuran jendela berubah,
 * supaya proporsinya selalu pas di 1024x600, 1280x720, dan 1920x1080.
 */
public class LauncherActivity extends Activity implements LocationTracker.Listener, WeatherRepository.Listener {
    static final String ACTION_OPEN_QURAN = "id.headunit.fortuner.OPEN_QURAN";
    static final String PAGE_QURAN = "quran";
    private static final int REQ_LOCATION = 11;
    private static final String TAG = AppLauncherRepository.TAG;

    private AppLauncherRepository apps;
    private LocationTracker tracker;
    private WeatherRepository weather;

    private FrameLayout root;
    private NavigationSidebar sidebar;
    private TopStatusBar status;
    private FrameLayout pageHost;
    private HomeDashboard dashboard;
    private AppsPage appsPage;
    private SettingsPage settingsPage;
    private QuranPage quranPage;
    private String page = NavigationSidebar.HOME;
    private int builtW, builtH;
    private boolean resumed;
    private boolean rebuildPending;
    private boolean receiversOn;
    private TelephonyManager telephony;
    private ConnectivityManager connectivity;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        apps = new AppLauncherRepository(this);
        tracker = new LocationTracker(this);
        weather = new WeatherRepository(this);
        weather.setListener(this);
        telephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        root = new FrameLayout(this) {
            @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                if (w <= 0 || h <= 0) return;
                if (builtW == 0) {
                    post(() -> build(w, h));
                } else if (needsRebuild(w, h)) {
                    // Jangan membangun ulang saat tertutup (mis. kamera mundur): tunda sampai launcher tampil lagi.
                    if (resumed) post(() -> build(w, h));
                    else rebuildPending = true;
                }
            }
        };
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        applyWindowMode();
        if (ACTION_OPEN_QURAN.equals(getIntent().getAction())) page = PAGE_QURAN;
        if (!tracker.hasPermission() && state == null) requestLocationPermission();
    }

    /**
     * Ukuran berubah cukup jauh untuk dibangun ulang? Status bar yang disembunyikan/dimunculkan aplikasi lain
     * (mis. kamera mundur: 662 &lt;-&gt; 720 px) hanya mengubah tinggi ±9%; tata letak berbobot cukup menyesuaikan
     * tanpa membangun ulang semuanya.
     */
    private boolean needsRebuild(int w, int h) {
        return Math.abs(w - builtW) > 2 || Math.abs(h - builtH) > builtH * 0.12f;
    }

    /** Membangun seluruh UI untuk ukuran jendela tertentu. */
    private void build(int w, int h) {
        builtW = w;
        builtH = h;
        Ui.init(w, h);
        if (dashboard != null) {
            tracker.removeListener(dashboard.vehicle);
        }
        root.removeAllViews();
        LinearLayout frame = Ui.row(this);
        sidebar = new NavigationSidebar(this, this::showPage);
        frame.addView(sidebar, new LinearLayout.LayoutParams(Ui.px(112), LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout content = Ui.column(this);
        status = new TopStatusBar(this, () -> showPage(NavigationSidebar.SETTINGS));
        content.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(84)));
        pageHost = new FrameLayout(this);
        content.addView(pageHost, Ui.weight(1f, false));
        frame.addView(content, Ui.weight(1f, true));
        root.addView(frame, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        dashboard = new HomeDashboard(this, apps, tracker);
        tracker.addListener(dashboard.vehicle);
        appsPage = null;
        settingsPage = null;
        quranPage = null;
        showPage(page);
        refreshStatus();
        Log.i(TAG, "UI dibangun untuk " + w + "x" + h);
    }

    void showPage(String p) {
        if (pageHost == null) {
            page = p;
            return;
        }
        page = p;
        View v;
        switch (p) {
            case NavigationSidebar.APPS:
                if (appsPage == null) appsPage = new AppsPage(this, apps);
                else appsPage.reload();
                v = appsPage;
                break;
            case NavigationSidebar.SETTINGS:
                if (settingsPage == null) settingsPage = new SettingsPage(this, apps, tracker);
                else settingsPage.rebuild();
                v = settingsPage;
                break;
            case PAGE_QURAN:
                if (quranPage == null) quranPage = new QuranPage(this);
                else quranPage.showSurah(QuranPlayer.get(this).surah(), false);
                v = quranPage;
                break;
            default:
                v = dashboard;
                page = NavigationSidebar.HOME;
                break;
        }
        if (v.getParent() != pageHost) {
            pageHost.removeAllViews();
            if (v.getParent() != null) ((FrameLayout) v.getParent()).removeView(v);
            pageHost.addView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(140).start();
        }
        sidebar.setActive(PAGE_QURAN.equals(page) ? NavigationSidebar.HOME : page);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (ACTION_OPEN_QURAN.equals(intent.getAction())) {
            showPage(PAGE_QURAN);
        } else if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            showPage(NavigationSidebar.HOME);
        }
    }

    @Override
    public void onBackPressed() {
        // Launcher tidak ditutup oleh tombol kembali; cukup kembali ke Beranda.
        if (!NavigationSidebar.HOME.equals(page)) showPage(NavigationSidebar.HOME);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceivers();
        tracker.addListener(this);
        tracker.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (rebuildPending) {
            rebuildPending = false;
            int w = root.getWidth(), h = root.getHeight();
            if (w > 0 && h > 0 && needsRebuild(w, h)) build(w, h);
        }
        applyWindowMode();
        refreshStatus();
        refreshApps();
        if (dashboard != null) dashboard.controls.refreshVolume();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        tracker.removeListener(this);
        tracker.stop();
        unregisterReceivers();
        super.onStop();
    }

    // ---- izin & mode jendela ----

    void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            String[] perms = Build.VERSION.SDK_INT >= 31
                    ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            requestPermissions(perms, REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_LOCATION) {
            tracker.stop();
            tracker.start();
            refreshWeather();
            if (settingsPage != null && settingsPage.getParent() != null) settingsPage.rebuild();
        }
    }

    @SuppressWarnings("deprecation")
    void applyWindowMode() {
        if (Prefs.flag(this, Prefs.FULLSCREEN, false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    void refreshQuran() {
        if (dashboard != null) dashboard.quran.render();
    }

    void refreshApps() {
        if (dashboard != null) dashboard.refreshApps();
    }

    void refreshWeather() {
        weather.refresh(tracker.lastKnown(), true);
        if (status != null) status.updateWeather(weather);
    }

    // ---- status bar ----

    @Override
    public void onLocationState() {
        weather.refreshIfStale(tracker.lastKnown());
    }

    @Override
    public void onWeather() {
        if (status != null) status.updateWeather(weather);
    }

    private void refreshStatus() {
        if (status == null) return;
        status.updateClock();
        updateBluetooth();
        status.updateWifi(isWifiConnected());
        if (telephony == null || telephony.getSimState() != TelephonyManager.SIM_STATE_READY) status.updateCell(-1);
        weather.refreshIfStale(tracker.lastKnown());
        status.updateWeather(weather);
    }

    private void updateBluetooth() {
        boolean enabled = false, connected = false;
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                enabled = bt.isEnabled();
                connected = enabled && (bt.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                        || bt.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Status Bluetooth tidak bisa dibaca", e);
        }
        status.updateBluetooth(enabled, connected);
    }

    private boolean isWifiConnected() {
        if (connectivity == null) return false;
        try {
            for (Network n : connectivity.getAllNetworks()) {
                NetworkCapabilities nc = connectivity.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return true;
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Status jaringan tidak bisa dibaca", e);
        }
        return false;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a == null || status == null) return;
            switch (a) {
                case Intent.ACTION_TIME_TICK:
                case Intent.ACTION_TIME_CHANGED:
                case Intent.ACTION_TIMEZONE_CHANGED:
                    status.updateClock();
                    break;
                case "android.media.VOLUME_CHANGED_ACTION":
                    if (dashboard != null) dashboard.controls.refreshVolume();
                    break;
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGE_CHANGED:
                    refreshApps();
                    if (appsPage != null && appsPage.getParent() != null) appsPage.reload();
                    break;
                default:
                    updateBluetooth();
                    break;
            }
        }
    };

    private final ConnectivityManager.NetworkCallback netCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { postNet(); }

        @Override public void onLost(Network n) { postNet(); }

        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) { postNet(); }
    };

    private void postNet() {
        runOnUiThread(() -> {
            // Tersambung internet: pastikan Lokasi Android menyala (bila diizinkan) dan cuaca langsung diambil.
            if (LocationTracker.ensureLocationEnabled(this)) {
                tracker.stop();
                tracker.start();
            }
            if (status != null) status.updateWifi(isWifiConnected());
            weather.refreshIfStale(tracker.lastKnown());
            QuranDownloadService.resumeIfWanted(this);
        });
    }

    @SuppressWarnings("deprecation")
    private final PhoneStateListener phoneListener = new PhoneStateListener() {
        @Override public void onSignalStrengthsChanged(SignalStrength s) {
            if (status != null && telephony.getSimState() == TelephonyManager.SIM_STATE_READY) {
                status.updateCell(Build.VERSION.SDK_INT >= 23 ? s.getLevel() : 2);
            }
        }

        @Override public void onServiceStateChanged(ServiceState s) {
            if (status != null && s.getState() != ServiceState.STATE_IN_SERVICE) status.updateCell(-1);
        }
    };

    @SuppressWarnings("deprecation")
    private void registerReceivers() {
        if (receiversOn) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(receiver, f);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(receiver, pf);
        try {
            if (connectivity != null) connectivity.registerDefaultNetworkCallback(netCallback);
        } catch (RuntimeException e) {
            Log.w(TAG, "NetworkCallback gagal", e);
        }
        if (telephony != null) {
            try {
                telephony.listen(phoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                        | PhoneStateListener.LISTEN_SERVICE_STATE);
            } catch (RuntimeException e) {
                Log.w(TAG, "Sinyal seluler tidak bisa dibaca", e);
            }
        }
        receiversOn = true;
    }

    @SuppressWarnings("deprecation")
    private void unregisterReceivers() {
        if (!receiversOn) return;
        unregisterReceiver(receiver);
        try {
            if (connectivity != null) connectivity.unregisterNetworkCallback(netCallback);
        } catch (RuntimeException ignored) {
            // belum terdaftar
        }
        if (telephony != null) telephony.listen(phoneListener, PhoneStateListener.LISTEN_NONE);
        receiversOn = false;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        boolean r = super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (dashboard != null && (k == android.view.KeyEvent.KEYCODE_VOLUME_UP || k == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)) {
            dashboard.controls.refreshVolume();
        }
        return r;
    }

}
