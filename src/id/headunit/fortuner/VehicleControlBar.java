package id.headunit.fortuner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Panel kontrol bawah: pintasan Pengaturan Kendaraan (aplikasi CarSetting headunit) dan volume. */
final class VehicleControlBar extends LinearLayout {
    private final AudioManager audio;
    private final TextView volumeText;
    private final ImageView volumeIcon;

    @SuppressLint("ViewConstructor")
    VehicleControlBar(Activity a, AppLauncherRepository repo) {
        super(a);
        audio = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = Ui.gradient(0xFF0E1D31, 0xFF0A1626, 18);
        bg.setStroke(Math.max(1, Ui.px(1)), Ui.STROKE);
        setBackground(bg);
        setPadding(Ui.px(10), 0, Ui.px(10), 0);

        LinearLayout vehicle = Ui.row(a);
        vehicle.setPadding(0, 0, Ui.px(18), 0);
        ImageView car = Ui.iconView(a, R.drawable.ic_car, Ui.TEXT, 30);
        int pad = Ui.px(10);
        car.setPadding(pad, pad, pad, pad);
        car.setLayoutParams(new LayoutParams(Ui.px(54), Ui.px(54)));
        vehicle.addView(car);
        vehicle.addView(Ui.text(a, "Pengaturan Kendaraan", 18, Ui.TEXT, Ui.MEDIUM));
        vehicle.setContentDescription("Pengaturan kendaraan");
        Ui.pressable(vehicle);
        vehicle.setOnClickListener(v -> repo.open(a, AppLauncherRepository.Shortcut.VEHICLE));
        addView(vehicle, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(Ui.flex(a));
        addView(divider(a));

        LinearLayout vol = Ui.row(a);
        ImageView down = button(a, R.drawable.ic_chevron_left, Ui.TEXT2, "Volume turun");
        down.setOnClickListener(v -> adjust(AudioManager.ADJUST_LOWER));
        volumeIcon = button(a, R.drawable.ic_volume, Ui.TEXT, "Tampilkan volume");
        volumeIcon.setOnClickListener(v -> adjust(AudioManager.ADJUST_SAME));
        volumeText = Ui.text(a, "", 18, Ui.TEXT, Ui.MEDIUM);
        volumeText.setMinWidth(Ui.px(30));
        volumeText.setGravity(Gravity.CENTER);
        ImageView up = button(a, R.drawable.ic_chevron_right, Ui.TEXT2, "Volume naik");
        up.setOnClickListener(v -> adjust(AudioManager.ADJUST_RAISE));
        vol.addView(down);
        vol.addView(volumeIcon);
        vol.addView(volumeText);
        vol.addView(up);
        addView(vol);
        refreshVolume();
    }

    private ImageView button(Context c, int res, int color, String desc) {
        ImageView v = Ui.iconView(c, res, color, 30);
        int pad = Ui.px(10);
        v.setPadding(pad, pad, pad, pad);
        v.setLayoutParams(new LayoutParams(Ui.px(54), Ui.px(54)));
        v.setContentDescription(desc);
        Ui.pressable(v);
        return v;
    }

    private View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Ui.STROKE);
        LayoutParams lp = new LayoutParams(Math.max(1, Ui.px(1)), Ui.px(30));
        lp.setMargins(Ui.px(10), 0, Ui.px(10), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void adjust(int direction) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
        refreshVolume();
    }

    void refreshVolume() {
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        volumeText.setText(String.valueOf(v));
        volumeIcon.setImageDrawable(Ui.icon(getContext(), v == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume, Ui.TEXT));
    }
}
