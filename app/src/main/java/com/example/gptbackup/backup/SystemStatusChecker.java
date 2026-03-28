package com.example.gptbackup.backup;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;

public class SystemStatusChecker {

    private final Context context;

    public SystemStatusChecker(Context context) {
        this.context = context;
    }

    public boolean isWifiConnected() {

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false;

        Network network = cm.getActiveNetwork();
        if (network == null) return false;

        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public boolean isBatteryOkay() {
        BatteryManager bm =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);

        if (bm == null) return false;

        int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        return level > 30; // backup only if >30%
    }

    public boolean isMobileDataConnected() {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }

    public boolean isCharging() {
        BatteryManager bm =
                (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return bm.isCharging();
        }
        return false;
    }
}
