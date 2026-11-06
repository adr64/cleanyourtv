package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * On-device theme packs under {@code files/themes/<name>/}. Each pack holds a {@code config.json}
 * and optional {@code wallpaper.jpg} / {@code wallpapers/}. Applying one copies those files into
 * the active theme folder (the same place {@code tools/theme.sh} writes) and bumps
 * {@code theme_revision} so Home reloads.
 *
 * <p>A pack may only change the look. Device keys already on the TV (remote {@code app_keys},
 * App library, greeting, Live TV package, hidden apps, …) are copied from the active
 * {@code config.json} into the result, so picking Glass never drops the APPS button mapping.
 */
final class ThemePacks {
    static final String ROOT = "themes";
    static final String PREF_KEY = "theme_pack";
    static final String TITLE_FILE = "title.txt";

    /**
     * Keys that stay with this TV when a look pack is applied. Pack values for these are ignored
     * when the active theme already has the key.
     */
    private static final String[] KEEP_FROM_ACTIVE = {
            "app_keys", "restore_key_services", "settings_keycodes", "settings_key_panel",
            "input_key_keycodes",
            "app_library", "library_excluded_apps", "library_apps",
            "show_greeting", "greeting_name",
            "hidden_apps", "dock_apps", "top_bar_apps",
            "live_tv_package", "live_tv_action", "live_tv_input_id",
            "inputs_connected_only", "launcher_settings_button",
            "panel_service", "panel_action", "panel_extra",
            "input_action", "input_sources", "input_uri_scheme",
            "coocaa_sources", "package_map", "app_labels", "app_banners", "icon_tiles"
    };

    private ThemePacks() { }

    /** Pack folder names that contain a config.json, sorted. */
    static List<String> list(Context ctx) {
        File root = new File(Config.dir(ctx), ROOT);
        File[] kids = root.listFiles();
        if (kids == null) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (File kid : kids) {
            if (kid.isDirectory() && new File(kid, Config.FILE_NAME).isFile()) names.add(kid.getName());
        }
        Collections.sort(names);
        return names;
    }

    /** Label shown in settings: {@code title.txt} first line if present, else {@link #displayName}. */
    static String label(Context ctx, String name) {
        String[] lines = titleLines(ctx, name);
        if (lines.length > 0 && !lines[0].isEmpty()) return lines[0];
        return displayName(name);
    }

    /**
     * Short theme description for the apply confirmation: rest of {@code title.txt}, or a summary
     * built from the pack's {@code config.json}.
     */
    static String blurb(Context ctx, String name) {
        String[] lines = titleLines(ctx, name);
        if (lines.length > 1) {
            StringBuilder body = new StringBuilder();
            for (int i = 1; i < lines.length; i++) {
                if (lines[i].isEmpty()) continue;
                if (body.length() > 0) body.append('\n');
                body.append(lines[i]);
            }
            if (body.length() > 0) return body.toString();
        }
        File packConfig = new File(new File(Config.dir(ctx), ROOT), name + "/" + Config.FILE_NAME);
        String fromJson = blurbFromConfig(readJson(packConfig));
        return fromJson != null ? fromJson : "";
    }

    private static String[] titleLines(Context ctx, String name) {
        File title = new File(new File(Config.dir(ctx), ROOT), name + "/" + TITLE_FILE);
        if (!title.isFile()) return new String[0];
        try (FileInputStream in = new FileInputStream(title)) {
            byte[] buf = new byte[2048];
            int n = in.read(buf);
            if (n <= 0) return new String[0];
            String text = new String(buf, 0, n, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return new String[0];
            return text.split("\\R", -1);
        } catch (IOException ignored) {
            return new String[0];
        }
    }

    /** One-line feature summary from a pack config (effects, dock, wallpaper). */
    static String blurbFromConfig(JSONObject json) {
        if (json == null) return null;
        List<String> bits = new ArrayList<>();
        if (json.optBoolean("dock_glass", false)) bits.add("cristal");
        if (json.optBoolean("cinematic_home", false)) bits.add("cinematic");
        if (json.optBoolean("advanced_effects", false)) bits.add("efectos extra");
        if (json.optBoolean("app_glow", false)) bits.add("glow");
        if (json.optBoolean("ambient_glow", false)) bits.add("ambiente");
        String glow = json.optString("glow_style", "").trim();
        if (!glow.isEmpty() && !"default".equals(glow)) bits.add("glow " + glow);
        int dim = json.optInt("wallpaper_dim", -1);
        if (dim == 0) bits.add("fondos al 100%");
        else if (dim > 0) bits.add("fondos −" + dim + "%");
        int rot = json.optInt("wallpaper_rotation_minutes", 0);
        if (rot > 0) bits.add("rotación " + rot + " min");
        int cols = json.optInt("columns", 0);
        if (cols > 0) bits.add(cols + " columnas");
        int idle = json.optInt("home_idle_clock_minutes", 0);
        if (idle > 0) bits.add("protector " + idle + " min");
        if (bits.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bits.size(); i++) {
            if (i > 0) out.append(" · ");
            out.append(bits.get(i));
        }
        return out.toString();
    }

    static String current(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY, "");
    }

    /**
     * Copies the pack over the active theme files. Returns false if the pack is missing or a copy fails.
     */
    static boolean apply(Context ctx, String name) {
        File dest = Config.dir(ctx);
        if (dest == null) return false;
        File pack = new File(dest, ROOT + "/" + name);
        File packConfig = new File(pack, Config.FILE_NAME);
        if (!packConfig.isFile()) return false;

        // Only replace pictures when the pack ships them; style-only packs keep the current collection.
        File packWall = new File(pack, Config.WALLPAPER_NAME);
        File packWallpapers = new File(pack, "wallpapers");
        File[] packImages = packWallpapers.isDirectory() ? packWallpapers.listFiles() : null;
        boolean hasWallpapers = packImages != null && packImages.length > 0;
        boolean hasSingle = packWall.isFile();

        try {
            // Look from the pack; remote / apps / greeting stay as this TV already had them.
            writeUtf8(new File(dest, Config.FILE_NAME),
                    mergeKeep(readJson(new File(dest, Config.FILE_NAME)), readJson(packConfig)).toString(2) + "\n");
            if (hasWallpapers || hasSingle) {
                File wallpapers = new File(dest, "wallpapers");
                if (wallpapers.isDirectory()) {
                    File[] old = wallpapers.listFiles();
                    if (old != null) for (File f : old) f.delete();
                }
                new File(dest, Config.WALLPAPER_NAME).delete();
                if (hasSingle) copyFile(packWall, new File(dest, Config.WALLPAPER_NAME));
                if (hasWallpapers) {
                    if (!wallpapers.exists() && !wallpapers.mkdirs()) return false;
                    for (File image : packImages) {
                        if (image.isFile()) copyFile(image, new File(wallpapers, image.getName()));
                    }
                }
            }
            String revision = UUID.randomUUID().toString().replace("-", "");
            writeUtf8(new File(dest, "theme_revision"), revision);
        } catch (IOException | JSONException e) {
            return false;
        }

        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        // Keep wallpaper_choice: a re-push of the same collection should not drop the pick; a
        // missing picture falls back to the theme in WallpaperRotation.chosen.
        choices.edit()
                .putString(PREF_KEY, name)
                .remove("advanced_effects")
                .remove("animations")
                .putLong(Config.SETTINGS_REVISION_KEY,
                        choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1)
                .commit();
        return true;
    }

    private static void copyFile(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("mkdir " + parent);
        }
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static void writeUtf8(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Pack look keys, then re-apply every KEEP key still present on the active theme. */
    static JSONObject mergeKeep(JSONObject active, JSONObject pack) throws JSONException {
        JSONObject out = new JSONObject();
        if (pack != null) {
            for (Iterator<String> it = pack.keys(); it.hasNext(); ) {
                String key = it.next();
                out.put(key, pack.get(key));
            }
        }
        if (active != null) {
            for (String key : KEEP_FROM_ACTIVE) {
                if (active.has(key)) out.put(key, active.get(key));
            }
        }
        return out;
    }

    private static JSONObject readJson(File file) {
        if (file == null || !file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) Math.min(file.length(), 512 * 1024L)];
            int n = in.read(buf);
            if (n <= 0) return null;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);
            return new JSONObject(text);
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    /** Friendly names for known packs when title.txt is absent. */
    static String displayName(String folder) {
        switch (folder) {
            case "cinematic": return "Cinematic";
            case "glass": return "Glass";
            case "minimal": return "Minimal";
            case "nature": return "Nature";
            case "custom": return "Bravia X805G";
            case "nature-calm": return "Nature Calm";
            case "dock-glass": return "Dock Glass";
            case "red-rain": return "Red Rain";
            case "six-columns": return "Six Columns";
            default: return folder;
        }
    }
}
