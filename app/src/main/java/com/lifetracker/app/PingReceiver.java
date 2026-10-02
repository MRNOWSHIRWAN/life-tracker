package com.lifetracker.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

public class PingReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent in) {
        String key = in.getStringExtra("key");
        if (key == null) return;
        try {
            JSONObject o = Pings.rule(c, key);
            if (o != null && Pings.notificationsOn(c)) {
                Pings.ensureChannel(c);
                Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent tap = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                Notification.Builder b = new Notification.Builder(c, Pings.CHANNEL)
                    .setSmallIcon(R.drawable.ic_stat).setContentTitle(o.optString("title", "Life Tracker"))
                    .setContentText(o.optString("body", "Time for it")).setContentIntent(tap).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_REMINDER);
                ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(key.hashCode(), b.build());
            }
        } catch (Exception ignored) { }
        finally { Pings.rescheduleKey(c, key); }
    }
}
