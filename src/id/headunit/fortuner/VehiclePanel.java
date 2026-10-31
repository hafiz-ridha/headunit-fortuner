package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Panel mobil: Fortuner putih 360° yang bisa diputar ({@link Car360View}), kecepatan dari GPS, status GPS,
 * dan trip GPS. Data CAN-bus seperti BBM/transmisi tidak ditampilkan
 * karena launcher tidak terhubung ke sumbernya.
 */
final class VehiclePanel extends FrameLayout implements LocationTracker.Listener, MapPip.Listener {
    private final LocationTracker tracker;
    private final TextView speed;
    private final TextView unit;
    private final TextView gpsText;
    private final ImageView gpsIcon;
    private final TextView trip;
    private final Car360View car;
    private final NavigationCard nav;
    private TextView hint;
    private final TextView navChip;
    private final Runnable onNav = () -> {
        updateNavigation();
        MapPip.navChanged();
    };
    private final int[] loc = new int[2];

    @SuppressLint("ViewConstructor")
    VehiclePanel(Context c, LocationTracker tracker, Runnable onPermission) {
        super(c);
        this.tracker = tracker;
        final float radius = Ui.px(22);
        setBackground(Ui.gradient(0xFF13294A, 0xFF07111F, 22));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);

        car = new Car360View(c);
        LayoutParams carLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        carLp.setMargins(Ui.px(8), Ui.px(76), Ui.px(8), Ui.px(104));
        addView(car, carLp);
        // Selama Google Maps/Waze memandu arah, gambar mobil diganti panel navigasi.
        nav = new NavigationCard(c);
        nav.setVisibility(GONE);
        LayoutParams navLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        navLp.setMargins(Ui.px(14), Ui.px(88), Ui.px(14), Ui.px(84));
        addView(nav, navLp);
        View bottomFade = new View(c);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC07111F, 0x0007111F}));
        addView(bottomFade, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(70), Gravity.BOTTOM));

        // Kecepatan dan satuan dalam satu baris (sejajar baseline) agar mobil mendapat ruang lebih besar.
        LinearLayout speedBox = Ui.row(c);
        speedBox.setGravity(Gravity.CENTER_HORIZONTAL);
        speedBox.setBaselineAligned(true);
        speed = Ui.text(c, "--", 64, Ui.TEXT, Ui.MEDIUM);
        unit = Ui.text(c, "km/h", 20, Ui.TEXT2, Ui.REGULAR);
        speedBox.addView(speed);
        speedBox.addView(unit);
        ((LinearLayout.LayoutParams) unit.getLayoutParams()).leftMargin = Ui.px(10);
        LayoutParams sLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        sLp.topMargin = Ui.px(12);
        addView(speedBox, sLp);

        LinearLayout chips = Ui.row(c);
        LinearLayout gps = chip(c);
        gpsIcon = Ui.iconView(c, R.drawable.ic_gps, Ui.TEXT2, 22);
        gpsText = Ui.text(c, "GPS", 16, Ui.TEXT, Ui.MEDIUM);
        gps.addView(gpsIcon);
        gps.addView(Ui.space(c, 10, 1));
        gps.addView(gpsText);
        gps.setOnClickListener(v -> {
            if (tracker.state() == LocationTracker.State.NO_PERMISSION) onPermission.run();
        });
        chips.addView(gps, new LinearLayout.LayoutParams(0, Ui.px(56), 1f));
        chips.addView(Ui.space(c, 10, 1));

        LinearLayout tripChip = chip(c);
        LinearLayout tripCol = Ui.column(c);
        tripCol.addView(Ui.text(c, "TRIP GPS", 13, Ui.TEXT2, Ui.MEDIUM));
        trip = Ui.text(c, "0.0 km", 20, Ui.TEXT, Ui.MEDIUM);
        tripCol.addView(trip);
        ((LinearLayout.LayoutParams) trip.getLayoutParams()).topMargin = Ui.px(3);
        tripChip.addView(Ui.iconView(c, R.drawable.ic_route, Ui.TEXT2, 22));
        tripChip.addView(Ui.space(c, 10, 1));
        tripChip.addView(tripCol);
        tripChip.setOnLongClickListener(v -> {
            new AlertDialog.Builder(getContext()).setTitle("Reset Trip GPS?")
                    .setMessage(String.format(Locale.US, "Trip saat ini %.1f km akan dikembalikan ke 0.", tracker.tripKm()))
                    .setPositiveButton("Reset", (d, w) -> tracker.resetTrip())
                    .setNegativeButton("Batal", null).show();
            return true;
        });
        chips.addView(tripChip, new LinearLayout.LayoutParams(0, Ui.px(56), 1.15f));
        LayoutParams cLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        cLp.setMargins(Ui.px(14), 0, Ui.px(14), Ui.px(14));
        addView(chips, cLp);

        // Petunjuk singkat; hilang setelah beberapa detik atau saat mobil pertama kali diputar.
        hint = Ui.text(c, "⟲  Geser untuk memutar", 14, Ui.TEXT2, Ui.REGULAR);
        hint.setPadding(Ui.px(14), Ui.px(6), Ui.px(14), Ui.px(6));
        hint.setBackground(Ui.rounded(0x990A1628, 14));
        LayoutParams hLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        hLp.bottomMargin = Ui.px(78);
        addView(hint, hLp);
        final TextView h = hint;
        Runnable hideHint = () -> h.animate().alpha(0f).setDuration(400).withEndAction(() -> h.setVisibility(GONE)).start();
        car.setOnFirstTouch(hideHint);
        hint.postDelayed(hideHint, 5000);

        // Panel navigasi ditutup (tombol X) selagi navigasi berjalan: tombol kecil untuk menampilkannya lagi.
        navChip = Ui.text(c, "Navigasi berjalan  ·  Tampilkan", 15, Ui.TEXT, Ui.MEDIUM);
        navChip.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(c, R.drawable.ic_route, Ui.ACCENT), null, null, null);
        navChip.setCompoundDrawablePadding(Ui.px(10));
        navChip.setPadding(Ui.px(16), Ui.px(9), Ui.px(18), Ui.px(9));
        GradientDrawable chipBg = Ui.rounded(0xE60A1628, 20);
        chipBg.setStroke(Math.max(1, Ui.px(1.2f)), 0x881685FF);
        navChip.setBackground(chipBg);
        navChip.setVisibility(GONE);
        Ui.pressable(navChip);
        navChip.setOnClickListener(v -> MapPip.setMode(MapPip.Mode.MAP));
        LayoutParams ncLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        ncLp.topMargin = Ui.px(92);
        addView(navChip, ncLp);
        onLocationState();
    }

    /** Ganti tampilan mobil &lt;-&gt; panel navigasi dengan transisi halus. */
    private void updateNavigation() {
        NavNotificationListener.State s = NavNotificationListener.STATE;
        boolean closed = MapPip.mode() == MapPip.Mode.CAR;
        navChip.setVisibility(s.active && closed ? VISIBLE : GONE);
        nav.setMapMode(MapPip.mapShown());
        if (s.active && !closed) {
            nav.bind(s);
            if (nav.getVisibility() != VISIBLE) {
                nav.setAlpha(0f);
                nav.setVisibility(VISIBLE);
                nav.animate().alpha(1f).setDuration(250).start();
                car.animate().alpha(0f).setDuration(250).withEndAction(() -> car.setVisibility(INVISIBLE)).start();
                hint.setVisibility(GONE);
            }
        } else if (nav.getVisibility() == VISIBLE) {
            car.setVisibility(VISIBLE);
            car.animate().alpha(1f).setDuration(250).start();
            nav.animate().alpha(0f).setDuration(250).withEndAction(() -> nav.setVisibility(GONE)).start();
        }
    }

    /** Laporkan posisi kartu navigasi di layar supaya peta Google Maps bisa ditempatkan tepat di atasnya. */
    private void reportMapTarget() {
        if (!isAttachedToWindow() || getWidth() == 0) return;
        LayoutParams lp = (LayoutParams) nav.getLayoutParams();
        getLocationOnScreen(loc);
        int l = loc[0] + lp.leftMargin, t = loc[1] + lp.topMargin;
        int r = loc[0] + getWidth() - lp.rightMargin, b = loc[1] + getHeight() - lp.bottomMargin;
        // Warna latar panel (gradien atas-bawah) tepat di belakang sudut kartu.
        float ft = lp.topMargin / (float) getHeight(), fb = 1f - lp.bottomMargin / (float) getHeight();
        MapPip.setTarget(getContext(), new android.graphics.Rect(l, t, r, b),
                Ui.blend(0xFF13294A, 0xFF07111F, ft), Ui.blend(0xFF13294A, 0xFF07111F, fb), this);
    }

    @Override
    public void onMapState() {
        updateNavigation();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        post(this::reportMapTarget);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NavNotificationListener.addListener(onNav);
        onNav.run();
        post(this::reportMapTarget);
    }

    @Override
    protected void onDetachedFromWindow() {
        NavNotificationListener.removeListener(onNav);
        MapPip.clearTarget(this);
        super.onDetachedFromWindow();
    }

    private LinearLayout chip(Context c) {
        LinearLayout l = Ui.row(c);
        l.setPadding(Ui.px(16), 0, Ui.px(14), 0);
        GradientDrawable g = Ui.rounded(0xCC0A1628, 14);
        g.setStroke(Math.max(1, Ui.px(1)), Ui.STROKE);
        l.setBackground(g);
        Ui.pressable(l);
        return l;
    }

    @Override
    public void onLocationState() {
        float s = tracker.speedKmh();
        speed.setText(s < 0 ? "--" : String.valueOf(Math.round(s)));
        LocationTracker.State st = tracker.state();
        switch (st) {
            case FIX:
                gpsText.setText("GPS terkunci");
                gpsIcon.setImageDrawable(Ui.icon(getContext(), R.drawable.ic_gps, 0xFF3DDC84));
                break;
            case SEARCHING:
                gpsText.setText("Mencari GPS…");
                gpsIcon.setImageDrawable(Ui.icon(getContext(), R.drawable.ic_gps, Ui.WARN));
                break;
            case GPS_OFF:
                gpsText.setText("GPS mati");
                gpsIcon.setImageDrawable(Ui.icon(getContext(), R.drawable.ic_gps, Ui.TEXT3));
                break;
            default:
                gpsText.setText("Izinkan lokasi");
                gpsIcon.setImageDrawable(Ui.icon(getContext(), R.drawable.ic_gps, Ui.WARN));
                break;
        }
        trip.setText(String.format(Locale.US, "%.1f km", tracker.tripKm()));
    }
}
