package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in; a
 * ClockRefresh keeps its TextClocks current where Android would not.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** Soft crossfade when the screensaver rotates to the next picture. */
    private static final long CROSSFADE_MS = 1400L;
    /** Drift the clock to a new spot without blinking it out (used over a wallpaper). */
    private static final long SLIDE_MS = 1200L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final ClockRefresh clockRefresh;
    private final ImageView ambient;
    private final ImageView ambientNext;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** One number per decode request: a result whose number is no longer the current one is dropped. Main thread only. */
    private int wallpaperGeneration;
    /** clock_wallpaper with a collection: the pictures, how long each lasts, and how the next one is picked. */
    private List<File> rotation;
    private long rotationMs;
    private long shownMs;
    private String order;
    private Map<String, Set<String>> moods;
    private String latitude, longitude;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            advanceWallpaper();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        clockRefresh = new ClockRefresh(content);
        ambient = root.findViewById(R.id.dreamAmbient);
        ambientNext = root.findViewById(R.id.dreamAmbientNext);
        SoftText.applyChildren(root.findViewById(R.id.brandMark));
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            lockGlassToWorstCase();
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        clockRefresh.start();
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper(false);
    }

    /**
     * Sizes the frosted plate once to the widest time (+ AM/PM in 12h) and date this locale/font
     * can draw, then keeps that size so a minute tick never resizes the glass.
     */
    private void lockGlassToWorstCase() {
        TextClock time = content.findViewById(R.id.dreamClock);
        TextClock ampm = content.findViewById(R.id.dreamAmPm);
        TextClock date = content.findViewById(R.id.dreamDate);
        if (time == null || date == null) return;

        DisplayMetrics dm = time.getResources().getDisplayMetrics();
        float padH = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 48f, dm);
        float padTop = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, dm);
        float padBottom = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 26f, dm);
        float dateGap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, dm);
        float widthSlack = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, dm);

        // Worst hour width (12h single-digit and 24h double-digit) so minutes never grow the plate.
        float timeW = measureWidest(time.getPaint(), "88:88", "00:00", "12:59", "23:59", "9:59");
        float ampmW = 0f;
        float ampmGap = 0f;
        boolean twelveHour = ampm != null && !DateFormat.is24HourFormat(ampm.getContext());
        if (twelveHour) {
            // Reserve the widest AM/PM label once; swapping AM↔PM or 9:59→10:00 must not resize.
            ampmW = measureWidest(ampm.getPaint(), "A. M.", "P. M.", "AM", "PM", "a. m.", "p. m.");
            ampmGap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, dm);
        }
        float timeRow = timeW + ampmGap + ampmW;
        float dateW = widestDate(date);
        float innerW = Math.max(timeRow, dateW);

        // Layout height uses full line boxes (avoids clipping); optical nudge centres the glyphs.
        float timeH = lineHeight(time.getPaint());
        float ampmH = ampm == null ? 0f : lineHeight(ampm.getPaint());
        float timeRowH = Math.max(timeH, ampmH);
        float dateH = lineHeight(date.getPaint());

        int width = (int) Math.ceil(innerW + widthSlack + padH * 2f);
        int height = (int) Math.ceil(timeRowH + dateGap + dateH + padTop + padBottom);
        content.setPadding((int) padH, (int) padTop, (int) padH, (int) padBottom);
        ViewGroup.LayoutParams lp = content.getLayoutParams();
        if (lp == null) lp = new ViewGroup.LayoutParams(width, height);
        else {
            lp.width = width;
            lp.height = height;
        }
        content.setLayoutParams(lp);
        content.setMinimumWidth(width);
        content.setMinimumHeight(height);
        if (content instanceof ViewGroup) {
            ViewGroup plate = (ViewGroup) content;
            plate.setClipChildren(false);
            plate.setClipToPadding(false);
            if (plate instanceof android.widget.LinearLayout) {
                ((android.widget.LinearLayout) plate).setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            }
        }
        // Large display fonts leave empty ascent above the digits; shift the time row so the
        // visible glyph box sits midway in the plate with the date (fixes "más arriba / poco abajo").
        View timeRowView = (View) time.getParent();
        float optical = opticalNudgeY(time.getPaint(), "88:88");
        timeRowView.setTranslationY(optical);
        date.setTranslationY(optical * 0.2f);
        if (timeRowView.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams rowLp =
                    (android.widget.LinearLayout.LayoutParams) timeRowView.getLayoutParams();
            rowLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            rowLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            timeRowView.setLayoutParams(rowLp);
        }
        if (date.getLayoutParams() instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams dateLp =
                    (android.widget.LinearLayout.LayoutParams) date.getLayoutParams();
            dateLp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            dateLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            dateLp.topMargin = (int) dateGap;
            date.setLayoutParams(dateLp);
        }
    }

    /** Negative Y shifts glyphs up when the font keeps unused ascent above the digits. */
    private static float opticalNudgeY(Paint paint, String sample) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        android.graphics.Rect glyph = new android.graphics.Rect();
        paint.getTextBounds(sample, 0, sample.length(), glyph);
        float unusedAbove = glyph.top - fm.ascent;   // ascent is more negative than glyph.top
        float unusedBelow = fm.descent - glyph.bottom;
        return -0.55f * (unusedAbove - unusedBelow);
    }

    private static float lineHeight(Paint paint) {
        Paint.FontMetrics fm = paint.getFontMetrics();
        return Math.max(1f, fm.descent - fm.ascent + fm.leading);
    }

    private static float measureWidest(Paint paint, String... samples) {
        float widest = 0f;
        for (String sample : samples) {
            if (sample == null) continue;
            widest = Math.max(widest, paint.measureText(sample.toUpperCase(Locale.getDefault())));
            widest = Math.max(widest, paint.measureText(sample));
        }
        return widest;
    }

    /** Longest rendering of {@code date_format} across weekdays and months in the current locale. */
    private static float widestDate(TextClock date) {
        Locale locale = Locale.getDefault();
        String pattern = date.getContext().getString(R.string.date_format);
        SimpleDateFormat fmt;
        try {
            fmt = new SimpleDateFormat(pattern, locale);
        } catch (IllegalArgumentException e) {
            return measureWidest(date.getPaint(), "WEDNESDAY 31 DECEMBER");
        }
        Paint paint = date.getPaint();
        float widest = 0f;
        Calendar cal = Calendar.getInstance(locale);
        // 12 months × a full week covers every weekday/month name the pattern can emit.
        for (int month = Calendar.JANUARY; month <= Calendar.DECEMBER; month++) {
            cal.set(2024, month, 1);
            int days = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int day = 1; day <= Math.min(days, 7); day++) {
                cal.set(2024, month, day);
                String text = fmt.format(cal.getTime()).toUpperCase(locale);
                widest = Math.max(widest, paint.measureText(text));
            }
            cal.set(2024, month, days);
            widest = Math.max(widest, paint.measureText(fmt.format(cal.getTime()).toUpperCase(locale)));
        }
        return widest;
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        clockRefresh.stop();
        content.animate().cancel();
        ambient.animate().cancel();
        if (ambientNext != null) ambientNext.animate().cancel();
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
        // Drop full-screen wallpaper bitmaps when the clock leaves: otherwise they linger in the
        // process until GC while Home is already showing its own picture (~20 MB on a 1080p set).
        releaseWallpaperBitmaps(ambient);
        if (ambientNext != null) releaseWallpaperBitmaps(ambientNext);
        wallpaperShown = false;
    }

    private static void releaseWallpaperBitmaps(ImageView view) {
        Drawable d = view.getDrawable();
        view.setImageDrawable(null);
        view.setAlpha(1f);
        recycleBitmapDrawable(d);
    }

    private static void recycleBitmapDrawable(Drawable d) {
        if (d instanceof LayerDrawable) {
            LayerDrawable layer = (LayerDrawable) d;
            for (int i = 0; i < layer.getNumberOfLayers(); i++) {
                recycleBitmapDrawable(layer.getDrawable(i));
            }
            return;
        }
        if (d instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) d).getBitmap();
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
    }

    /**
     * clock_wallpaper: put the picture Home is showing behind the clock, darkened by clock_wallpaper_dim, in
     * place of the glow, and carry a collection's rotation on while the screensaver is up. Decoded off the main
     * thread at screen size in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read,
     * the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!cfg.clockWallpaper) {
            // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            }
            return;
        }
        // On-TV screensaver choice (home / pick / rotate) is independent of Home's wallpaper_choice.
        ClockWallpaper.Result clock = ClockWallpaper.resolve(ctx, cfg);
        if (clock.files.isEmpty()) {
            wallpaperFile = null;
            rotation = null;
            rotationMs = 0;
        } else {
            rotation = new ArrayList<>(clock.files);
            String shown = ctx.getSharedPreferences("wallpaper_rotation", Context.MODE_PRIVATE)
                    .getString("image", "");
            File current = WallpaperRotation.find(rotation, shown);
            wallpaperFile = current != null ? current : rotation.get(0);
            rotationMs = clock.intervalMs;
            order = cfg.wallpaperOrder;
            moods = cfg.wallpaperMoods;
            latitude = moods.isEmpty() ? "" : ctx.getString(R.string.latitude);
            longitude = moods.isEmpty() ? "" : ctx.getString(R.string.longitude);
        }
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
    }

    /**
     * @param crossfade true to fade the new picture over the current one; false for the first show.
     */
    private void loadWallpaper(boolean crossfade) {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = ++wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration) {
                    bmp.recycle();
                    return;
                }
                Drawable layer = new LayerDrawable(new Drawable[]{
                        new BitmapDrawable(res, bmp), new ColorDrawable(shade)});
                ambient.setBackground(null);
                if (!crossfade || ambientNext == null) {
                    // First picture: fade in with the Home→screensaver activity fade, not a hard cut.
                    ambient.animate().cancel();
                    ambient.setImageDrawable(layer);
                    ambient.setAlpha(0f);
                    if (ambientNext != null) {
                        ambientNext.animate().cancel();
                        ambientNext.setImageDrawable(null);
                        ambientNext.setAlpha(0f);
                    }
                    ambient.animate().alpha(1f).setDuration(CROSSFADE_MS).start();
                    wallpaperShown = true;
                    return;
                }
                // Soft crossfade: new picture rises over the old; the clock stays put above both.
                ambientNext.animate().cancel();
                ambientNext.setImageDrawable(layer);
                ambientNext.setAlpha(0f);
                ambientNext.animate().alpha(1f).setDuration(CROSSFADE_MS).withEndAction(() -> {
                    if (generation != wallpaperGeneration) return;
                    Drawable previous = ambient.getDrawable();
                    ambient.setImageDrawable(ambientNext.getDrawable());
                    ambient.setAlpha(1f);
                    ambientNext.setImageDrawable(null);
                    ambientNext.setAlpha(0f);
                    // Previous frame is no longer on screen; free it so rotate does not stack bitmaps.
                    if (previous != ambient.getDrawable()) recycleBitmapDrawable(previous);
                    wallpaperShown = true;
                }).start();
            });
        }, "clock-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Step to the next picture once its turn is up; the tick already runs every minute. */
    private void advanceWallpaper() {
        if (rotation == null || rotationMs <= 0 || rotation.size() < 2) return;
        shownMs += MOVE_MS;
        if (shownMs < rotationMs) return;
        shownMs = 0;
        // The same pick as Home's, so a shuffled collection stays shuffled and wallpaper_moods still
        // decides what may show at this hour.
        File next = WallpaperRotation.pick(playable(), wallpaperFile.getName(), order, random);
        if (next.equals(wallpaperFile)) return;   // a mood group of one
        wallpaperFile = next;
        loadWallpaper(true);
    }

    /** WallpaperPlayer's filter: the pictures wallpaper_moods allows at this hour, or all of them. */
    private List<File> playable() {
        if (moods.isEmpty()) return rotation;
        LocalDateTime now = LocalDateTime.now();
        int[] sun = WallpaperMood.sun(now.toLocalDate(), latitude, longitude, ZoneId.systemDefault());
        return WallpaperMood.select(rotation, moods,
                WallpaperMood.timeOfDay(now.getHour() * 60 + now.getMinute(), sun[0], sun[1]));
    }

    private void moveClock() {
        // Over a wallpaper, keep the clock visible: slide to a new spot instead of fading out/in
        // (that blink lined up with picture changes and looked like the clock vanished with the image).
        if (wallpaperFile != null && wallpaperShown) {
            float[] spot = randomSpot();
            if (spot == null) return;
            content.animate().cancel();
            content.setAlpha(1f);
            content.animate().x(spot[0]).y(spot[1]).setDuration(SLIDE_MS).withLayer().start();
            setGlowCenter(spot[0], spot[1]);
            return;
        }
        content.animate().alpha(0f).setDuration(900).withLayer().withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).withLayer().start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        float[] spot = randomSpot();
        if (spot == null) return;
        content.setX(spot[0]);
        content.setY(spot[1]);
        setGlowCenter(spot[0], spot[1]);
    }

    /** {@code [x, y]} for the clock, or null before layout. */
    private float[] randomSpot() {
        View parent = (View) content.getParent();
        if (parent.getWidth() <= 0 || parent.getHeight() <= 0) return null;
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        return new float[]{x, y};
    }

    private void setGlowCenter(float clockX, float clockY) {
        View parent = (View) content.getParent();
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((clockX + content.getWidth() / 2f) / parent.getWidth(),
                    (clockY + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        if (wallpaperShown) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
