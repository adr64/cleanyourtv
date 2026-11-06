package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * On-TV choice for the clock screensaver's picture: match Home, one fixed file, or rotate the
 * theme collection on its own interval (independent of Home's {@code wallpaper_choice}).
 */
final class ClockWallpaper {
    static final String MODE_HOME = "home";
    static final String MODE_PICK = "pick";
    static final String MODE_ROTATE = "rotate";
    /** Allowed screensaver rotation intervals, in minutes. */
    static final int[] ROTATION_MINUTES = {1, 3, 5, 10};
    private static final int DEFAULT_ROTATION_MINUTES = 1;

    /** What {@link #resolve} hands the screensaver: the pictures to show and how long each lasts. */
    static final class Result {
        final List<File> files;
        /** Milliseconds each picture stays; 0 means hold the first (or only) one. */
        final long intervalMs;

        Result(List<File> files, long intervalMs) {
            this.files = files;
            this.intervalMs = intervalMs;
        }
    }

    private ClockWallpaper() {}

    static String mode(SharedPreferences prefs) {
        return normalizeMode(prefs.getString(Config.CLOCK_WALLPAPER_MODE_KEY, MODE_HOME));
    }

    static String normalizeMode(String mode) {
        if (MODE_PICK.equals(mode) || MODE_ROTATE.equals(mode)) return mode;
        return MODE_HOME;
    }

    /** Clamped to {@link #ROTATION_MINUTES}; default 1. */
    static int rotationMinutes(SharedPreferences prefs) {
        return clampRotationMinutes(prefs.getInt(Config.CLOCK_WALLPAPER_ROTATION_MINUTES_KEY,
                DEFAULT_ROTATION_MINUTES));
    }

    static int clampRotationMinutes(int minutes) {
        for (int allowed : ROTATION_MINUTES) {
            if (allowed == minutes) return minutes;
        }
        return DEFAULT_ROTATION_MINUTES;
    }

    /**
     * Pictures and interval for the clock screensaver. Reads Launcher settings and, for
     * {@link #MODE_HOME}, the filename Home's {@link WallpaperPlayer} last showed.
     */
    static Result resolve(Context ctx, Config cfg) {
        SharedPreferences choices = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        File dir = Config.dir(ctx);
        File wallpapersDir = dir == null ? null : new File(dir, "wallpapers");
        List<File> collection = WallpaperRotation.excluding(WallpaperRotation.images(wallpapersDir),
                choices.getStringSet(Config.CLOCK_WALLPAPER_DISABLED_KEY, Collections.emptySet()));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && !(single.isFile() && single.length() > 0)) single = null;
        String shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                .getString("image", "");
        return resolveFrom(mode(choices), choices.getString(Config.CLOCK_WALLPAPER_PICK_KEY, null),
                rotationMinutes(choices), collection, cfg.wallpapers, shown, single);
    }

    /**
     * Pure resolution used by {@link #resolve} and tests: no Android I/O beyond the lists already
     * gathered.
     */
    static Result resolveFrom(String mode, String pick, int rotationMinutes,
                              List<File> collection, List<File> homeWallpapers,
                              String homeShown, File singleWallpaper) {
        mode = normalizeMode(mode);
        if (MODE_ROTATE.equals(mode)) {
            if (collection.isEmpty()) return new Result(Collections.emptyList(), 0);
            return new Result(new ArrayList<>(collection),
                    clampRotationMinutes(rotationMinutes) * 60_000L);
        }
        if (MODE_PICK.equals(mode)) {
            File picked = WallpaperRotation.find(collection, pick);
            if (picked == null && singleWallpaper != null && pick != null
                    && pick.equals(singleWallpaper.getName())) {
                picked = singleWallpaper;
            }
            if (picked != null) {
                return new Result(Collections.singletonList(picked), 0);
            }
            // Missing pick falls back to Home's current picture.
        }
        return homeResult(homeWallpapers, collection, homeShown);
    }

    /** One picture: whatever Home is showing, with no rotation while the clock is up. */
    private static Result homeResult(List<File> homeList, List<File> collection, String shown) {
        File current = WallpaperRotation.find(homeList, shown);
        if (current == null) current = WallpaperRotation.find(collection, shown);
        if (current == null && !homeList.isEmpty()) current = homeList.get(0);
        if (current == null && !collection.isEmpty()) current = collection.get(0);
        if (current == null) return new Result(Collections.emptyList(), 0);
        return new Result(Collections.singletonList(current), 0);
    }
}
