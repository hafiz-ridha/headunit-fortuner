package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Jam & tanggal sistem, status Bluetooth/Wi-Fi/seluler, cuaca aktual, dan tombol pengaturan. */
final class TopStatusBar extends LinearLayout {
    private final TextView time;
    private final TextView date;
    private final ImageView bt;
    private final ImageView wifi;
    private final Icons.Signal signal = new Icons.Signal();
    private final ImageView signalView;
    private final Icons.Weather weatherIcon = new Icons.Weather();
    private final ImageView weatherView;
    private final TextView temp;
    private final TextView place;

    @SuppressLint("ViewConstructor")
    TopStatusBar(Context c, Runnable onSettings) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.px(28), Ui.px(8), Ui.px(24), Ui.px(4));

        LinearLayout clock = Ui.column(c);
        time = Ui.text(c, "--:--", 40, Ui.TEXT, Ui.MEDIUM);
        date = Ui.text(c, "", 17, Ui.TEXT2, Ui.REGULAR);
        clock.addView(time);
        clock.addView(date);
        ((LayoutParams) date.getLayoutParams()).topMargin = Ui.px(5);
        addView(clock);
        addView(Ui.flex(c));

        bt = Ui.iconView(c, R.drawable.ic_bluetooth, Ui.TEXT, 26);
        wifi = Ui.iconView(c, R.drawable.ic_wifi, Ui.TEXT, 26);
        signalView = Ui.iconView(c, signal, 22);
        addView(bt);
        addView(Ui.space(c, 22, 1));
        addView(wifi);
        addView(Ui.space(c, 22, 1));
        addView(signalView);
        addView(Ui.space(c, 40, 1));

        weatherView = Ui.iconView(c, weatherIcon, 46);
        addView(weatherView);
        addView(Ui.space(c, 12, 1));
        LinearLayout wx = Ui.column(c);
        temp = Ui.text(c, "--°C", 24, Ui.TEXT, Ui.MEDIUM);
        place = Ui.text(c, "Menunggu lokasi", 15, Ui.TEXT2, Ui.REGULAR);
        wx.addView(temp);
        wx.addView(place);
        ((LayoutParams) place.getLayoutParams()).topMargin = Ui.px(4);
        addView(wx);
        addView(Ui.space(c, 26, 1));

        ImageView gear = Ui.iconView(c, R.drawable.ic_settings, Ui.TEXT2, 30);
        int pad = Ui.px(8);
        gear.setPadding(pad, pad, pad, pad);
        gear.setLayoutParams(new LayoutParams(Ui.px(46), Ui.px(46)));
        gear.setContentDescription("Pengaturan");
        Ui.pressable(gear);
        gear.setOnClickListener(v -> onSettings.run());
        addView(gear);
        updateClock();
    }

    void updateClock() {
        Date now = new Date();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
        time.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(now));
        date.setText(new SimpleDateFormat("EEE, d MMM yyyy", new Locale("id", "ID")).format(now));
    }

    void updateBluetooth(boolean enabled, boolean connected) {
        bt.setImageDrawable(Ui.icon(getContext(), R.drawable.ic_bluetooth, connected ? Ui.ACCENT : Ui.TEXT));
        bt.setAlpha(enabled ? 1f : 0.3f);
        bt.setContentDescription(!enabled ? "Bluetooth mati" : connected ? "Bluetooth tersambung" : "Bluetooth aktif");
    }

    void updateWifi(boolean connected) {
        wifi.setAlpha(connected ? 1f : 0.3f);
        wifi.setContentDescription(connected ? "Wi-Fi tersambung" : "Wi-Fi tidak tersambung");
    }

    void updateCell(int level) {
        signal.level = level;
        signal.invalidateSelf();
        signalView.setAlpha(level < 0 ? 0.35f : 1f);
    }

    void updateWeather(WeatherRepository w) {
        if (w.hasData()) {
            weatherIcon.code = w.code;
            weatherIcon.day = w.isDay;
            temp.setText(Math.round(w.temperature) + "°C");
            place.setText(w.place != null ? w.place : w.description());
        } else {
            weatherIcon.code = -1;
            temp.setText("--°C");
            place.setText(w.status != null ? w.status : "Cuaca");
        }
        weatherIcon.invalidateSelf();
    }
}
