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

    /** Label shown in settings: {@code title.txt} if present, otherwise the folder name. */
    static String label(Context ctx, String name) {
        File title = new File(new File(Config.dir(ctx), ROOT), name + "/" + TITLE_FILE);
        if (title.isFile()) {
            try (FileInputStream in = new FileInputStream(title)) {
                byte[] buf = new byte[120];
                int n = in.read(buf);
                if (n > 0) {
                    String line = new String(buf, 0, n, StandardCharsets.UTF_8).trim();
                    int cut = line.indexOf('\n');
                    if (cut >= 0) line = line.substring(0, cut).trim();
                    if (!line.isEmpty()) return line;
                }
            } catch (IOException ignored) {
            }
        }
        return name;
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
            case "custom": return "Custom (mis fotos)";
            default: return folder;
        }
    }
}
