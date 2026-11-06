package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }

    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private static final String[] CLOCK_FORMATS = {"system", "12", "24"};
    private final TextView[] clockChoices = new TextView[CLOCK_FORMATS.length];
    private TextView themeChoice;
    private TextView wallpaperChoice;
    private TextView homeWallpaperRotationChoice;
    private TextView homeWallpaperImagesChoice;
    private TextView clockWallpaperChoice;
    private TextView clockWallpaperRotationChoice;
    private TextView clockWallpaperImagesChoice;
    private TextView homeIdleChoice;
    private TextView greetingNameChoice;
    private int homeIdleMinutes;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private String clockFormat;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        clockFormat = config.clockFormat;
        homeIdleMinutes = config.homeIdleClockMinutes;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] clockIds = {R.id.clockSystem, R.id.clock12, R.id.clock24};
        for (int i = 0; i < clockIds.length; i++) {
            final String value = CLOCK_FORMATS[i];
            clockChoices[i] = findViewById(clockIds[i]);
            clockChoices[i].setOnClickListener(v -> {
                if (value.equals(clockFormat)) return;
                clockFormat = value;
                choices.edit().putString(Config.CLOCK_FORMAT_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Mirror text alignment without globally opting the launcher into RTL. Numeric rows
        // still need explicit ordering in Android's no-supportsRtl compatibility mode.
        if (getResources().getBoolean(R.bool.rtl_menu_bar))
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
            reverseRow(clockChoices);
        }
        themeChoice = findViewById(R.id.themeChoice);
        themeChoice.setOnClickListener(v -> showThemePicker());
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        homeWallpaperRotationChoice = findViewById(R.id.homeWallpaperRotationChoice);
        homeWallpaperRotationChoice.setOnClickListener(v -> showHomeWallpaperRotationPicker());
        homeWallpaperImagesChoice = findViewById(R.id.homeWallpaperImagesChoice);
        homeWallpaperImagesChoice.setOnClickListener(v -> showWallpaperImagesPicker(
                Config.HOME_WALLPAPER_DISABLED_KEY, R.string.wallpaper_images_label));
        clockWallpaperChoice = findViewById(R.id.clockWallpaperChoice);
        clockWallpaperChoice.setOnClickListener(v -> showClockWallpaperPicker());
        clockWallpaperRotationChoice = findViewById(R.id.clockWallpaperRotationChoice);
        clockWallpaperRotationChoice.setOnClickListener(v -> showClockWallpaperRotationPicker());
        clockWallpaperImagesChoice = findViewById(R.id.clockWallpaperImagesChoice);
        clockWallpaperImagesChoice.setOnClickListener(v -> showWallpaperImagesPicker(
                Config.CLOCK_WALLPAPER_DISABLED_KEY, R.string.clock_wallpaper_images_label));
        homeIdleChoice = findViewById(R.id.homeIdleChoice);
        homeIdleChoice.setOnClickListener(v -> showHomeIdlePicker());
        greetingNameChoice = findViewById(R.id.greetingNameChoice);
        greetingNameChoice.setOnClickListener(v -> showGreetingNameEditor());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY)
                    .remove(Config.HOME_WALLPAPER_DISABLED_KEY)
                    .remove(Config.CLOCK_WALLPAPER_MODE_KEY).remove(Config.CLOCK_WALLPAPER_PICK_KEY)
                    .remove(Config.CLOCK_WALLPAPER_ROTATION_MINUTES_KEY)
                    .remove(Config.CLOCK_WALLPAPER_DISABLED_KEY)
                    .remove(Config.HOME_IDLE_MINUTES_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(Config.CLOCK_FORMAT_KEY).remove(OpenOnStart.KEY)
                    .remove(Config.GREETING_NAME_KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            clockFormat = current.clockFormat;
            homeIdleMinutes = current.homeIdleClockMinutes;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v ->
                open(new Intent(this, HomeSettingsActivity.class)));
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(SystemSettings.intent(this, Settings.ACTION_SETTINGS)));
        refresh();
        // Appearance first: theme when packs exist, otherwise wallpaper / columns.
        if (!ThemePacks.list(this).isEmpty()) themeChoice.requestFocus();
        else if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showThemePicker() {
        List<String> packs = ThemePacks.list(this);
        if (packs.isEmpty()) {
            Toast.makeText(this, R.string.theme_none, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[packs.size()];
        String current = ThemePacks.current(this);
        int checked = 0;
        for (int i = 0; i < packs.size(); i++) {
            String name = packs.get(i);
            labels[i] = ThemePacks.label(this, name);
            if (name.equals(current)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.theme_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    confirmTheme(packs.get(which), labels[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Confirm with a short feature blurb before applying the pack. */
    private void confirmTheme(String pack, String title) {
        String blurb = ThemePacks.blurb(this, pack);
        if (blurb.isEmpty()) blurb = getString(R.string.theme_blurb_fallback);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(blurb)
                .setPositiveButton(R.string.theme_apply, (dialog, which) -> applyTheme(pack))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyTheme(String pack) {
        if (ThemePacks.apply(this, pack)) {
            Toast.makeText(this, R.string.theme_applied, Toast.LENGTH_SHORT).show();
            Config applied = Config.load(this);
            columns = applied.columns;
            rowsBelowDock = applied.rowsBelowDock;
            dockOpacity = applied.dockOpacity;
            clockFormat = applied.clockFormat;
            homeIdleMinutes = applied.homeIdleClockMinutes;
            animations = applied.animations;
            advancedEffects = applied.animations && applied.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        } else {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }

    /** Shown after the greeting: the name saved on the TV, else the theme's; "" is the greeting alone. */
    private String currentGreetingName() {
        return Config.chosenGreetingName(choices.getString(Config.GREETING_NAME_KEY, null),
                Config.load(this).greetingName);
    }

    /** A one-line text box (the TV's on-screen keyboard): OK saves, "No name" clears, Cancel keeps things. */
    private void showGreetingNameEditor() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Config.GREETING_NAME_MAX)});
        input.setHint(R.string.greeting_name_label);
        input.setText(currentGreetingName());
        input.setSelection(input.getText().length());
        // Side margins so the box does not touch the dialog's edge on the TV.
        FrameLayout box = new FrameLayout(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.greeting_name_label)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, which) -> saveGreetingName(input.getText().toString()))
                .setNeutralButton(R.string.greeting_name_clear, (d, which) -> saveGreetingName(""))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        input.requestFocus();
    }

    private void saveGreetingName(String typed) {
        choices.edit().putString(Config.GREETING_NAME_KEY, Config.cleanGreetingName(typed)).apply();
        changed();
        Toast.makeText(this, R.string.greeting_name_applied, Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        File wallpapersDir = dir == null ? null : new File(dir, "wallpapers");
        List<File> images = new ArrayList<>(WallpaperRotation.images(wallpapersDir));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        Map<String, String> captions = WallpaperCaptions.read(wallpapersDir);
        // [0] Rotate, then each picture, then None.
        String[] labels = new String[images.size() + 2];
        labels[0] = getString(R.string.wallpaper_rotate);
        for (int i = 0; i < images.size(); i++) {
            String file = images.get(i).getName();
            String caption = captions.get(file);
            labels[i + 1] = caption != null ? caption : file;
        }
        labels[images.size() + 1] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = 0;
        if ("none".equals(current)) checked = images.size() + 1;
        else if (Config.WALLPAPER_CHOICE_ROTATE.equals(current)) checked = 0;
        else if (current != null) {
            int idx = indexOf(images, current);
            checked = idx >= 0 ? idx + 1 : 0;
        } else {
            checked = -1; // theme default: no radio preselected; Neutral still clears
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                Config.WALLPAPER_CHOICE_ROTATE).apply();
                    } else if (which == images.size() + 1) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which - 1).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHomeWallpaperRotationPicker() {
        Config live = Config.load(this);
        int current = choices.contains(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY)
                ? choices.getInt(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY, 1)
                : Math.max(1, live.wallpaperRotationMinutes);
        String[] labels = new String[Config.HOME_WALLPAPER_ROTATION_MINUTES.length];
        int checked = 0;
        for (int i = 0; i < Config.HOME_WALLPAPER_ROTATION_MINUTES.length; i++) {
            int minutes = Config.HOME_WALLPAPER_ROTATION_MINUTES[i];
            labels[i] = getString(R.string.wallpaper_rotation_minutes, minutes);
            if (minutes == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpaper_rotation_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY,
                            Config.HOME_WALLPAPER_ROTATION_MINUTES[which]).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Multi-select which pictures stay in a rotation (Home or screensaver). */
    private void showWallpaperImagesPicker(String disabledKey, int titleRes) {
        File dir = Config.dir(this);
        File wallpapersDir = dir == null ? null : new File(dir, "wallpapers");
        List<File> images = WallpaperRotation.images(wallpapersDir);
        if (images.isEmpty()) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            return;
        }
        Map<String, String> captions = WallpaperCaptions.read(wallpapersDir);
        CharSequence[] labels = new CharSequence[images.size()];
        boolean[] checked = new boolean[images.size()];
        Set<String> disabled = new HashSet<>(choices.getStringSet(disabledKey, Collections.emptySet()));
        for (int i = 0; i < images.size(); i++) {
            String file = images.get(i).getName();
            String caption = captions.get(file);
            labels[i] = caption != null ? caption : file;
            checked[i] = !disabled.contains(file);
        }
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.wallpaper_images_apply, (dialog, which) -> {
                    Set<String> next = new HashSet<>();
                    for (int i = 0; i < images.size(); i++) {
                        if (!checked[i]) next.add(images.get(i).getName());
                    }
                    // Leaving every box unchecked would blank the rotation; treat as all on.
                    if (next.size() == images.size()) next.clear();
                    SharedPreferences.Editor edit = choices.edit();
                    if (next.isEmpty()) edit.remove(disabledKey);
                    else edit.putStringSet(disabledKey, next);
                    edit.apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Same as Home / rotate all / one picture from the theme collection. */
    private void showClockWallpaperPicker() {
        File dir = Config.dir(this);
        File wallpapersDir = dir == null ? null : new File(dir, "wallpapers");
        List<File> images = new ArrayList<>(WallpaperRotation.images(wallpapersDir));
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        java.util.Map<String, String> captions = WallpaperCaptions.read(wallpapersDir);
        // [0] Same as Home, [1] Rotate all, then each picture.
        String[] labels = new String[images.size() + 2];
        labels[0] = getString(R.string.clock_wallpaper_home);
        labels[1] = getString(R.string.clock_wallpaper_rotate);
        for (int i = 0; i < images.size(); i++) {
            String file = images.get(i).getName();
            String caption = captions.get(file);
            labels[i + 2] = caption != null ? caption : file;
        }
        String mode = ClockWallpaper.mode(choices);
        String pick = choices.getString(Config.CLOCK_WALLPAPER_PICK_KEY, null);
        int checked = 0;
        if (ClockWallpaper.MODE_ROTATE.equals(mode)) checked = 1;
        else if (ClockWallpaper.MODE_PICK.equals(mode)) {
            int idx = indexOf(images, pick);
            checked = idx >= 0 ? idx + 2 : 0;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.clock_wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    SharedPreferences.Editor edit = choices.edit();
                    if (which == 0) {
                        edit.putString(Config.CLOCK_WALLPAPER_MODE_KEY, ClockWallpaper.MODE_HOME)
                                .remove(Config.CLOCK_WALLPAPER_PICK_KEY);
                    } else if (which == 1) {
                        edit.putString(Config.CLOCK_WALLPAPER_MODE_KEY, ClockWallpaper.MODE_ROTATE)
                                .remove(Config.CLOCK_WALLPAPER_PICK_KEY);
                    } else {
                        edit.putString(Config.CLOCK_WALLPAPER_MODE_KEY, ClockWallpaper.MODE_PICK)
                                .putString(Config.CLOCK_WALLPAPER_PICK_KEY,
                                        images.get(which - 2).getName());
                    }
                    edit.apply();
                    changed();
                    Toast.makeText(this, R.string.clock_wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showClockWallpaperRotationPicker() {
        int current = ClockWallpaper.rotationMinutes(choices);
        String[] labels = new String[ClockWallpaper.ROTATION_MINUTES.length];
        int checked = 0;
        for (int i = 0; i < ClockWallpaper.ROTATION_MINUTES.length; i++) {
            int minutes = ClockWallpaper.ROTATION_MINUTES[i];
            labels[i] = getString(R.string.clock_wallpaper_rotation_minutes, minutes);
            if (minutes == current) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.clock_wallpaper_rotation_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    choices.edit().putInt(Config.CLOCK_WALLPAPER_ROTATION_MINUTES_KEY,
                            ClockWallpaper.ROTATION_MINUTES[which]).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showHomeIdlePicker() {
        String[] labels = new String[Config.HOME_IDLE_MINUTES.length];
        int checked = 0;
        for (int i = 0; i < Config.HOME_IDLE_MINUTES.length; i++) {
            int minutes = Config.HOME_IDLE_MINUTES[i];
            labels[i] = getString(R.string.home_idle_minutes, minutes);
            if (minutes == homeIdleMinutes) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.home_idle_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    homeIdleMinutes = Config.HOME_IDLE_MINUTES[which];
                    choices.edit().putInt(Config.HOME_IDLE_MINUTES_KEY, homeIdleMinutes).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        for (int i = 0; i < clockChoices.length; i++) {
            clockChoices[i].setSelected(CLOCK_FORMATS[i].equals(clockFormat));
        }
        List<String> packs = ThemePacks.list(this);
        String pack = ThemePacks.current(this);
        String packLabel = pack.isEmpty() ? getString(R.string.theme_none)
                : (packs.contains(pack) ? ThemePacks.displayName(pack) : pack);
        if (!pack.isEmpty() && packs.contains(pack)) {
            String titled = ThemePacks.label(this, pack);
            packLabel = titled.equals(pack) ? ThemePacks.displayName(pack) : titled;
        } else if (packs.isEmpty()) {
            packLabel = getString(R.string.theme_none);
        } else if (pack.isEmpty()) {
            packLabel = getString(R.string.wallpaper_theme_default);
        }
        themeChoice.setText(getString(R.string.theme_state, packLabel));
        themeChoice.setEnabled(!packs.isEmpty());
        themeChoice.setAlpha(packs.isEmpty() ? 0.5f : 1f);
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        String wallpaperLabel = getString(R.string.wallpaper_theme_default);
        if ("none".equals(wallpaperCurrent)) {
            wallpaperLabel = getString(R.string.wallpaper_none);
        } else if (Config.WALLPAPER_CHOICE_ROTATE.equals(wallpaperCurrent)) {
            wallpaperLabel = getString(R.string.wallpaper_rotate);
        } else if (wallpaperCurrent != null) {
            File dir = Config.dir(this);
            Map<String, String> captions = WallpaperCaptions.read(
                    dir == null ? null : new File(dir, "wallpapers"));
            String caption = captions.get(wallpaperCurrent);
            wallpaperLabel = caption != null ? caption : wallpaperCurrent;
        }
        wallpaperChoice.setText(getString(R.string.wallpaper_state, wallpaperLabel));
        Config live = Config.load(this);
        boolean homeRotating = Config.WALLPAPER_CHOICE_ROTATE.equals(wallpaperCurrent)
                || (wallpaperCurrent == null && live.wallpaperRotationMinutes > 0);
        homeWallpaperRotationChoice.setVisibility(homeRotating ? View.VISIBLE : View.GONE);
        homeWallpaperImagesChoice.setVisibility(homeRotating ? View.VISIBLE : View.GONE);
        if (homeRotating) {
            int homeMins = choices.contains(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY)
                    ? choices.getInt(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY, 1)
                    : Math.max(1, live.wallpaperRotationMinutes);
            homeWallpaperRotationChoice.setText(getString(R.string.wallpaper_rotation_state,
                    getString(R.string.wallpaper_rotation_minutes, homeMins)));
            homeWallpaperImagesChoice.setText(getString(R.string.wallpaper_images_state,
                    imagesSummary(Config.HOME_WALLPAPER_DISABLED_KEY)));
        }
        clockWallpaperChoice.setText(getString(R.string.clock_wallpaper_state, clockWallpaperLabel()));
        boolean rotate = ClockWallpaper.MODE_ROTATE.equals(ClockWallpaper.mode(choices));
        clockWallpaperRotationChoice.setVisibility(rotate ? View.VISIBLE : View.GONE);
        clockWallpaperImagesChoice.setVisibility(rotate ? View.VISIBLE : View.GONE);
        if (rotate) {
            clockWallpaperRotationChoice.setText(getString(R.string.clock_wallpaper_rotation_state,
                    getString(R.string.clock_wallpaper_rotation_minutes,
                            ClockWallpaper.rotationMinutes(choices))));
            clockWallpaperImagesChoice.setText(getString(R.string.clock_wallpaper_images_state,
                    imagesSummary(Config.CLOCK_WALLPAPER_DISABLED_KEY)));
        }
        homeIdleChoice.setText(getString(R.string.home_idle_state,
                getString(R.string.home_idle_minutes, homeIdleMinutes)));
        String name = currentGreetingName();
        greetingNameChoice.setText(getString(R.string.greeting_name_state,
                name.isEmpty() ? getString(R.string.greeting_name_none) : name));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private String imagesSummary(String disabledKey) {
        File dir = Config.dir(this);
        List<File> images = WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers"));
        if (images.isEmpty()) return getString(R.string.wallpaper_images_all);
        Set<String> disabled = choices.getStringSet(disabledKey, Collections.emptySet());
        int enabled = 0;
        for (File image : images) {
            if (!disabled.contains(image.getName())) enabled++;
        }
        if (enabled >= images.size() || disabled.isEmpty()) {
            return getString(R.string.wallpaper_images_all);
        }
        return getString(R.string.wallpaper_images_count, enabled, images.size());
    }

    private String clockWallpaperLabel() {
        String mode = ClockWallpaper.mode(choices);
        if (ClockWallpaper.MODE_ROTATE.equals(mode)) {
            return getString(R.string.clock_wallpaper_rotate);
        }
        if (ClockWallpaper.MODE_PICK.equals(mode)) {
            String pick = choices.getString(Config.CLOCK_WALLPAPER_PICK_KEY, null);
            if (pick != null) {
                File dir = Config.dir(this);
                java.util.Map<String, String> captions = WallpaperCaptions.read(
                        dir == null ? null : new File(dir, "wallpapers"));
                String caption = captions.get(pick);
                return caption != null ? caption : pick;
            }
        }
        return getString(R.string.clock_wallpaper_home);
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY)
                || choices.contains(Config.HOME_WALLPAPER_ROTATION_MINUTES_KEY)
                || choices.contains(Config.HOME_WALLPAPER_DISABLED_KEY)
                || choices.contains(Config.CLOCK_WALLPAPER_MODE_KEY)
                || choices.contains(Config.CLOCK_WALLPAPER_PICK_KEY)
                || choices.contains(Config.CLOCK_WALLPAPER_ROTATION_MINUTES_KEY)
                || choices.contains(Config.CLOCK_WALLPAPER_DISABLED_KEY)
                || choices.contains(Config.HOME_IDLE_MINUTES_KEY)
                || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(Config.CLOCK_FORMAT_KEY) || choices.contains(OpenOnStart.KEY)
                || choices.contains(Config.GREETING_NAME_KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
