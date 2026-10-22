package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Malaysian prayer times from JAKIM's e-Solat (https://www.e-solat.gov.my), for the zone in config.xml
 * (prayer_zone, e.g. MLK01 for Melaka). A whole month is fetched at once and kept in preferences, so the
 * row on Home works offline for the rest of the month and the site is asked about once a month.
 */
final class PrayerTimes {
    private static final String TAG = "TvHome.Prayer";
    private static final String PREFS = "prayer_times";
    /** Wait this long after a failed fetch before trying again. */
    private static final long RETRY_MS = 30 * 60_000L;

    /** e-Solat's keys, in the order shown on Home. */
    static final String[] KEYS = {"imsak", "fajr", "syuruk", "dhuha", "dhuhr", "asr", "maghrib", "isha"};

    private PrayerTimes() {}

    /** Today's eight times as minutes after midnight, or null when this month is not cached yet. */
    static int[] today(Context ctx, String zone) {
        Calendar now = Calendar.getInstance();
        String json = prefs(ctx).getString(monthKey(zone, now), null);
        if (json == null) return null;
        try {
            JSONArray days = new JSONObject(json).getJSONArray("prayerTime");
            String day = String.format(Locale.US, "%02d-", now.get(Calendar.DAY_OF_MONTH));
            for (int i = 0; i < days.length(); i++) {
                JSONObject d = days.getJSONObject(i);
                if (!d.optString("date").startsWith(day)) continue;
                int[] out = new int[KEYS.length];
                for (int k = 0; k < KEYS.length; k++) {
                    String[] hm = d.getString(KEYS[k]).split(":");
                    out[k] = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
                }
                return out;
            }
        } catch (Exception e) {
            Log.w(TAG, "bad cached times", e);
        }
        return null;
    }

    /** Whether this month still has to be fetched (and the last failure is old enough to retry). */
    static boolean due(Context ctx, String zone) {
        SharedPreferences p = prefs(ctx);
        if (p.contains(monthKey(zone, Calendar.getInstance()))) return false;
        return System.currentTimeMillis() - p.getLong("failed_at", 0) > RETRY_MS;
    }

    /** Fetches this month for the zone. Blocking: call off the main thread. True when new times were stored. */
    static boolean refresh(Context ctx, String zone) {
        SharedPreferences p = prefs(ctx);
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(
                    "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=month&zone="
                            + zone).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) buf.write(b, 0, n);
                String json = buf.toString(StandardCharsets.UTF_8.name());
                JSONObject o = new JSONObject(json);
                if (o.getJSONArray("prayerTime").length() == 0) throw new IllegalStateException("no days");
                // The site answers for the month it is now; old months are dropped.
                p.edit().clear().putString(monthKey(zone, Calendar.getInstance()), json).apply();
                return true;
            } finally {
                c.disconnect();
            }
        } catch (Exception e) {
            Log.w(TAG, "fetch failed for " + zone, e);
            p.edit().putLong("failed_at", System.currentTimeMillis()).apply();
            return false;
        }
    }

    private static String monthKey(String zone, Calendar c) {
        return String.format(Locale.US, "%s-%d-%02d", zone, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
