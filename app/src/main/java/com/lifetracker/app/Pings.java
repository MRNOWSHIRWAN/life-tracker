package com.lifetracker.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Stores ping rules and schedules the next alarm for each. Offline, no network. */
final class Pings {
    static final String CHANNEL = "pings";
    private static final String PREFS = "pings", KEY = "rules", SCHEDULED = "scheduled";

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
    }

    static boolean notificationsOn(Context c) {
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        return ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled();
    }

    static void saveAndSchedule(Context c, String json) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY, json).apply();
        scheduleAll(c);
    }

    static void scheduleAll(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> old = new HashSet<>(sp.getStringSet(SCHEDULED, new HashSet<String>()));
        Set<String> now = new HashSet<>();
        try {
            JSONArray a = new JSONArray(sp.getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String key = o.getString("key");
                if (scheduleOne(c, o, true)) now.add(key); else cancel(c, key);
                old.remove(key);
            }
        } catch (Exception ignored) { }
        for (String k : old) cancel(c, k);
        sp.edit().putStringSet(SCHEDULED, now).apply();
    }

    /** Called after an alarm fires: set the next occurrence. */
    static void rescheduleKey(Context c, String key) {
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (key.equals(o.getString("key"))) { scheduleOne(c, o, false); return; }
            }
        } catch (Exception ignored) { }
    }

    static JSONObject rule(Context c, String key) {
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) if (key.equals(a.getJSONObject(i).getString("key"))) return a.getJSONObject(i);
        } catch (Exception ignored) { }
        return null;
    }

    private static PendingIntent pi(Context c, String key, int flags) {
        Intent i = new Intent(c, PingReceiver.class).setData(Uri.parse("lifetracker://ping/" + key)).putExtra("key", key);
        return PendingIntent.getBroadcast(c, key.hashCode(), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancel(Context c, String key) {
        PendingIntent p = pi(c, key, PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(p);
        p.cancel();
    }

    /** honorSkip: skip today's occurrence when the app said the habit is already done. */
    private static boolean scheduleOne(Context c, JSONObject o, boolean honorSkip) throws Exception {
        String time = o.getString("time"), skip = honorSkip ? o.optString("skip", "") : "", until = o.optString("until", "");
        int h = Integer.parseInt(time.substring(0, 2)), m = Integer.parseInt(time.substring(3, 5));
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long nowMs = System.currentTimeMillis();
        for (int n = 0; n < 3; n++) {
            String day = df.format(new Date(cal.getTimeInMillis()));
            boolean past = cal.getTimeInMillis() <= nowMs + 1000;
            if (!until.isEmpty() && day.compareTo(until) >= 0) return false;
            if (!past && !day.equals(skip)) break;
            cal.add(Calendar.DAY_OF_YEAR, 1);
            cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, m);
        }
        String day = df.format(new Date(cal.getTimeInMillis()));
        if (!until.isEmpty() && day.compareTo(until) >= 0) return false;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent p = pi(c, o.getString("key"), PendingIntent.FLAG_UPDATE_CURRENT);
        long at = cal.getTimeInMillis();
        try {
            if (canExact(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
        return true;
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Pings", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminders for your habits and timetable");
            nm.createNotificationChannel(ch);
        }
    }
  }
