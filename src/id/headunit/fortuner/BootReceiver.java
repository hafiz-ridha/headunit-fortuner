package id.headunit.fortuner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Headunit selesai booting: putar doa naik kendaraan. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) DoaTrigger.onBoot(c);
    }
}
