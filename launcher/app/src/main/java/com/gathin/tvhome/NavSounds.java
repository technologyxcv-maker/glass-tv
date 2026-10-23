package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interface sounds for Home: a click as the selection moves, another as something opens, and so on.
 * Sound packs are folders the owner pushes to the TV (tools/sounds.ps1), so no recording ships in the APK:
 * {@code <external files>/sounds/<pack>/<event>.wav} (or .ogg). The pack and the volume are picked on the
 * TV from the Sound pill and kept in preferences; nothing plays until a pack is chosen.
 */
final class NavSounds {
    private static final String TAG = "TvHome.Sounds";
    static final String DIR = "sounds";
    private static final String PREFS = "nav_sounds";

    static final String NAVIGATION = "navigation", BUMP = "bump", CONFIRM = "confirm", LAUNCH = "launch",
            BACK = "back", OPEN = "open", CLOSE = "close", ON = "on";
    private static final String[] EVENTS = {NAVIGATION, BUMP, CONFIRM, LAUNCH, BACK, OPEN, CLOSE, ON};

    /** Volume steps offered on the TV, as SoundPool gains. */
    static final float[] VOLUMES = {0.25f, 0.5f, 0.85f};

    /** Display names for the packs whose folder names are known; any other folder shows as named. */
    private static final Map<String, String> NAMES = new HashMap<>();
    static {
        NAMES.put("ps2", "PS2 System");
        NAMES.put("ps5", "PS5");
        NAMES.put("switch", "Switch");
        NAMES.put("xbox", "Xbox Series");
        NAMES.put("persona", "Persona 5");
        NAMES.put("pipboy", "Pip-Boy");
        NAMES.put("portal", "Portal 2");
    }
    private static final List<String> ORDER = Arrays.asList("ps2", "ps5", "switch", "xbox", "persona", "pipboy", "portal");

    private final Context ctx;
    private final SharedPreferences prefs;
    private final SoundPool pool;
    /** Sound ids of the pack now loaded, by event; an id is playable once its load has completed. */
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<Integer, Boolean> ready = new HashMap<>();
    private String loaded;
    /** Played as soon as its sound finishes loading (a preview of a pack that was not loaded yet). */
    private int playWhenReady;
    private long lastNavAt;

    NavSounds(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        pool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        pool.setOnLoadCompleteListener((p, id, status) -> {
            if (status != 0) return;
            ready.put(id, true);
            if (id == playWhenReady) {
                playWhenReady = 0;
                p.play(id, volume(), volume(), 1, 0, 1f);
            }
        });
        String pack = pack();
        if (pack != null) load(pack);
    }

    /** The chosen pack's folder name, or null for off. */
    String pack() {
        String p = prefs.getString("pack", null);
        return p != null && packDir(p).isDirectory() ? p : null;
    }

    int volumeStep() {
        return Math.max(0, Math.min(VOLUMES.length - 1, prefs.getInt("volume", 1)));
    }

    private float volume() {
        return VOLUMES[volumeStep()];
    }

    /** Pack folders on the TV, known packs first in a fixed order. */
    List<String> packs() {
        List<String> out = new ArrayList<>();
        File[] dirs = root().listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) out.add(d.getName());
        out.sort((a, b) -> {
            int ia = ORDER.indexOf(a), ib = ORDER.indexOf(b);
            if (ia < 0) ia = ORDER.size();
            if (ib < 0) ib = ORDER.size();
            return ia != ib ? ia - ib : a.compareToIgnoreCase(b);
        });
        return out;
    }

    static String name(String pack) {
        String n = NAMES.get(pack);
        return n != null ? n : pack;
    }

    void choose(String pack) {
        prefs.edit().putString("pack", pack).apply();
        if (pack == null) unload();
        else load(pack);
    }

    void setVolumeStep(int step) {
        prefs.edit().putInt("volume", step).apply();
    }

    /** Plays a pack's navigation sound, loading the pack first if needed; the chosen pack stays as it is. */
    void preview(String pack) {
        if (pack == null) return;
        load(pack);
        Integer id = ids.get(NAVIGATION);
        if (id == null) return;
        if (Boolean.TRUE.equals(ready.get(id))) pool.play(id, volume(), volume(), 1, 0, 1f);
        else playWhenReady = id;
    }

    /** Puts the chosen pack back after previews of others. */
    void restore() {
        String pack = pack();
        if (pack == null) unload();
        else load(pack);
    }

    void play(String event) {
        if (loaded == null || !loaded.equals(pack())) return;
        // Holding a direction repeats the key many times a second: one click per step, but not a buzz.
        if (NAVIGATION.equals(event) || BUMP.equals(event)) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastNavAt < 70) return;
            lastNavAt = now;
        }
        Integer id = ids.get(event);
        if (id != null && Boolean.TRUE.equals(ready.get(id))) pool.play(id, volume(), volume(), 1, 0, 1f);
    }

    private void load(String pack) {
        if (pack.equals(loaded)) return;
        unload();
        File dir = packDir(pack);
        for (String event : EVENTS) {
            File f = file(dir, event);
            if (f == null) continue;
            try {
                ids.put(event, pool.load(f.getAbsolutePath(), 1));
            } catch (Exception e) {
                Log.w(TAG, "cannot load " + f, e);
            }
        }
        loaded = pack;
    }

    private void unload() {
        for (int id : ids.values()) pool.unload(id);
        ids.clear();
        ready.clear();
        playWhenReady = 0;
        loaded = null;
    }

    private static File file(File dir, String event) {
        for (String ext : new String[]{".ogg", ".wav", ".mp3"}) {
            File f = new File(dir, event + ext);
            if (f.isFile() && f.length() > 0) return f;
        }
        return null;
    }

    private File root() {
        File ext = ctx.getExternalFilesDir(null);
        return new File(ext != null ? ext : ctx.getFilesDir(), DIR);
    }

    private File packDir(String pack) {
        return new File(root(), pack);
    }

    void release() {
        pool.release();
    }
}
