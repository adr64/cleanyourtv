package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ClockWallpaperTest {

    @Test
    public void clampRotationMinutesKeepsAllowedValues() {
        assertEquals(1, ClockWallpaper.clampRotationMinutes(1));
        assertEquals(3, ClockWallpaper.clampRotationMinutes(3));
        assertEquals(5, ClockWallpaper.clampRotationMinutes(5));
        assertEquals(10, ClockWallpaper.clampRotationMinutes(10));
        assertEquals(1, ClockWallpaper.clampRotationMinutes(2));
        assertEquals(1, ClockWallpaper.clampRotationMinutes(0));
        assertEquals(1, ClockWallpaper.clampRotationMinutes(7));
    }

    @Test
    public void normalizeModeFallsBackToHome() {
        assertEquals(ClockWallpaper.MODE_HOME, ClockWallpaper.normalizeMode(null));
        assertEquals(ClockWallpaper.MODE_HOME, ClockWallpaper.normalizeMode(""));
        assertEquals(ClockWallpaper.MODE_HOME, ClockWallpaper.normalizeMode("other"));
        assertEquals(ClockWallpaper.MODE_PICK, ClockWallpaper.normalizeMode(ClockWallpaper.MODE_PICK));
        assertEquals(ClockWallpaper.MODE_ROTATE, ClockWallpaper.normalizeMode(ClockWallpaper.MODE_ROTATE));
    }

    @Test
    public void homeUsesShownPictureWithNoInterval() {
        List<File> home = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        ClockWallpaper.Result r = ClockWallpaper.resolveFrom(ClockWallpaper.MODE_HOME, null, 1,
                collection, home, "b.jpg", null);
        assertEquals(Collections.singletonList("b.jpg"), names(r.files));
        assertEquals(0, r.intervalMs);
    }

    @Test
    public void homeFallsBackToFirstHomePicture() {
        List<File> home = Collections.singletonList(new File("pinned.jpg"));
        ClockWallpaper.Result r = ClockWallpaper.resolveFrom(ClockWallpaper.MODE_HOME, null, 1,
                Collections.emptyList(), home, "gone.jpg", null);
        assertEquals(Collections.singletonList("pinned.jpg"), names(r.files));
        assertEquals(0, r.intervalMs);
    }

    @Test
    public void pickUsesChosenFile() {
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        ClockWallpaper.Result r = ClockWallpaper.resolveFrom(ClockWallpaper.MODE_PICK, "b.jpg", 5,
                collection, Collections.singletonList(new File("a.jpg")), "a.jpg", null);
        assertEquals(Collections.singletonList("b.jpg"), names(r.files));
        assertEquals(0, r.intervalMs);
    }

    @Test
    public void missingPickFallsBackToHome() {
        List<File> home = Collections.singletonList(new File("a.jpg"));
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        ClockWallpaper.Result r = ClockWallpaper.resolveFrom(ClockWallpaper.MODE_PICK, "gone.jpg", 1,
                collection, home, "a.jpg", null);
        assertEquals(Collections.singletonList("a.jpg"), names(r.files));
        assertEquals(0, r.intervalMs);
    }

    @Test
    public void rotateUsesFullCollectionAndInterval() {
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"), new File("c.jpg"));
        List<File> home = Collections.singletonList(new File("a.jpg")); // Home pinned one
        ClockWallpaper.Result r = ClockWallpaper.resolveFrom(ClockWallpaper.MODE_ROTATE, null, 3,
                collection, home, "a.jpg", null);
        assertEquals(Arrays.asList("a.jpg", "b.jpg", "c.jpg"), names(r.files));
        assertEquals(3 * 60_000L, r.intervalMs);
    }

    @Test
    public void rotateClampsBadIntervalAndEmptyCollection() {
        List<File> collection = Arrays.asList(new File("a.jpg"), new File("b.jpg"));
        ClockWallpaper.Result r = ClockWallpaper.resolveFrom(ClockWallpaper.MODE_ROTATE, null, 7,
                collection, collection, "a.jpg", null);
        assertEquals(1 * 60_000L, r.intervalMs);
        assertTrue(ClockWallpaper.resolveFrom(ClockWallpaper.MODE_ROTATE, null, 1,
                Collections.emptyList(), collection, "a.jpg", null).files.isEmpty());
    }

    private static List<String> names(List<File> files) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (File f : files) out.add(f.getName());
        return out;
    }
}
