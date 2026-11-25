package com.udarmolota.xdroid360;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A game's achievements: the list the game itself defines (read from its default.xex,
 * {@link XdbfAchievements}) and which of them the player has unlocked (userdata/achievements.json,
 * {id: time in ms}). The game reports an unlock through GameActivity.onAchievementUnlocked.
 */
final class Achievements {
    private static final Map<String, List<XdbfAchievements.Entry>> CACHE = new HashMap<>();

    private Achievements() {}

    /** Whether this game has achievements the launcher can show (Dragon Age: Origins). */
    static boolean supported(Instance instance) {
        return instance != null && instance.hasAchievements() && new File(instance.game(), "default.xex").isFile();
    }

    /** Every achievement of the game, in the game's order. Throws if the XEX can't be read. */
    static synchronized List<XdbfAchievements.Entry> list(Instance instance) throws IOException {
        String key = instance.root.getPath();
        List<XdbfAchievements.Entry> cached = CACHE.get(key);
        if (cached == null) {
            cached = Collections.unmodifiableList(XdbfAchievements.readFromXex(new File(instance.game(), "default.xex")));
            CACHE.put(key, cached);
        }
        return cached;
    }

    private static File file(Instance instance) {
        return new File(instance.userdata(), "achievements.json");
    }

    /** The unlocked achievements: id to the time of the unlock (ms since the epoch). */
    static synchronized Map<Integer, Long> unlocked(Instance instance) {
        Map<Integer, Long> out = new HashMap<>();
        try {
            File f = file(instance);
            if (!f.isFile()) return out;
            JSONObject json = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
                String k = it.next();
                out.put(Integer.parseInt(k), json.optLong(k, 0));
            }
        } catch (IOException | JSONException | NumberFormatException e) {
            android.util.Log.w("XDroid360", "achievements.json: " + e);
        }
        return out;
    }

    /** Records an unlock; false if the achievement was unlocked before (no second notice). */
    static synchronized boolean markUnlocked(Instance instance, int id) {
        Map<Integer, Long> have = unlocked(instance);
        if (have.containsKey(id)) return false;
        have.put(id, System.currentTimeMillis());
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<Integer, Long> e : have.entrySet()) json.put(String.valueOf(e.getKey()), e.getValue());
            //noinspection ResultOfMethodCallIgnored
            instance.userdata().mkdirs();
            File tmp = new File(file(instance).getPath() + ".tmp");
            Files.write(tmp.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), file(instance).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | JSONException e) {
            android.util.Log.w("XDroid360", "achievements.json not written: " + e);
        }
        return true;
    }

    static XdbfAchievements.Entry find(List<XdbfAchievements.Entry> all, int id) {
        for (XdbfAchievements.Entry e : all) if (e.id == id) return e;
        return null;
    }

    /** The XDBF language code for the game's language setting (the phone's language while unset). */
    static String languageCode(Instance instance) {
        String l = instance.language == null ? "" : instance.language;
        switch (l) {
            case "english": return "en";
            case "german": return "de";
            case "french": return "fr";
            case "italian": return "it";
            case "spanish": return "es";
            case "polish": return "pl";
            case "russian": return "ru";
            default: return Locale.getDefault().getLanguage();
        }
    }

    /** A text in the language, else English, else any. */
    static String text(Map<String, String> texts, String language) {
        String t = texts.get(language);
        if (t == null) t = texts.get("en");
        if (t == null && !texts.isEmpty()) t = texts.values().iterator().next();
        return t == null ? "" : t;
    }

    static int totalPoints(List<XdbfAchievements.Entry> all) {
        int n = 0;
        for (XdbfAchievements.Entry e : all) n += e.points;
        return n;
    }

    static int earnedPoints(List<XdbfAchievements.Entry> all, Map<Integer, Long> unlocked) {
        int n = 0;
        for (XdbfAchievements.Entry e : all) if (unlocked.containsKey(e.id)) n += e.points;
        return n;
    }

    static List<XdbfAchievements.Entry> copy(List<XdbfAchievements.Entry> all) {
        return new ArrayList<>(all);
    }
}
