package com.example.minesweeper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

public class NetworkChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // הודעת מערכת על שינוי רשת
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            boolean connected = ni != null && ni.isConnected();
            Log.i("NetReceiver", "Network connected=" + connected);
            Toast.makeText(context, connected ? "Online" : "Offline", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.w("NetReceiver", "Failed to detect network: " + e);
        }
    }
}
