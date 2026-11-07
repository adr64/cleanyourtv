package com.gathin.tvhome;

import android.media.session.PlaybackState;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NowPlayingTest {

    @Test
    public void isActive_playingFamilyOnly() {
        assertTrue(NowPlaying.isActive(PlaybackState.STATE_PLAYING));
        assertTrue(NowPlaying.isActive(PlaybackState.STATE_BUFFERING));
        assertTrue(NowPlaying.isActive(PlaybackState.STATE_CONNECTING));
        assertFalse(NowPlaying.isActive(PlaybackState.STATE_PAUSED));
        assertFalse(NowPlaying.isActive(PlaybackState.STATE_STOPPED));
        assertFalse(NowPlaying.isActive(PlaybackState.STATE_NONE));
    }

    @Test
    public void pickPackage_prefersPlayingOverBuffering() {
        String picked = NowPlaying.pickPackage(Arrays.asList(
                new NowPlaying.Session("com.radio", PlaybackState.STATE_BUFFERING),
                new NowPlaying.Session("com.spotify.tv.android", PlaybackState.STATE_PLAYING)
        ), "com.gathin.tvhome");
        assertEquals("com.spotify.tv.android", picked);
    }

    @Test
    public void pickPackage_skipsPausedAndSelf() {
        assertNull(NowPlaying.pickPackage(Arrays.asList(
                new NowPlaying.Session("com.spotify.tv.android", PlaybackState.STATE_PAUSED),
                new NowPlaying.Session("com.gathin.tvhome", PlaybackState.STATE_PLAYING)
        ), "com.gathin.tvhome"));
    }

    @Test
    public void pickPackage_emptyIsNull() {
        assertNull(NowPlaying.pickPackage(Collections.emptyList(), "com.gathin.tvhome"));
    }

    @Test
    public void pickPackage_fallsBackToBuffering() {
        assertEquals("com.radio", NowPlaying.pickPackage(Collections.singletonList(
                new NowPlaying.Session("com.radio", PlaybackState.STATE_BUFFERING)
        ), "com.gathin.tvhome"));
    }
}
