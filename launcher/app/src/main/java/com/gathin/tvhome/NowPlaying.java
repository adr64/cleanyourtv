package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * now_playing_pill: which app holds an active media session, for the top-bar pill. No album art and no new
 * permission. Needs {@link GuardRevival} allowed as a notification listener so
 * {@link MediaSessionManager#getActiveSessions} can run; without it the pill stays hidden.
 */
final class NowPlaying {

    private NowPlaying() {
    }

    static final class Session {
        final String packageName;
        final int state;

        Session(String packageName, int state) {
            this.packageName = packageName;
            this.state = state;
        }
    }

    static boolean isActive(int state) {
        return state == PlaybackState.STATE_PLAYING
                || state == PlaybackState.STATE_BUFFERING
                || state == PlaybackState.STATE_CONNECTING
                || state == PlaybackState.STATE_FAST_FORWARDING
                || state == PlaybackState.STATE_REWINDING
                || state == PlaybackState.STATE_SKIPPING_TO_NEXT
                || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
                || state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM;
    }

    /**
     * Package to show on the pill, or null. Prefers an actively playing session over a merely buffering one.
     */
    static String pickPackage(List<Session> sessions, String selfPackage) {
        String buffering = null;
        for (Session s : sessions) {
            if (s.packageName == null || s.packageName.isEmpty()) continue;
            if (selfPackage != null && selfPackage.equals(s.packageName)) continue;
            if (!isActive(s.state)) continue;
            if (s.state == PlaybackState.STATE_PLAYING) return s.packageName;
            if (buffering == null) buffering = s.packageName;
        }
        return buffering;
    }

    static List<Session> fromManager(Context ctx) {
        MediaSessionManager mgr = ctx.getSystemService(MediaSessionManager.class);
        if (mgr == null) return Collections.emptyList();
        List<MediaController> controllers;
        try {
            controllers = mgr.getActiveSessions(new ComponentName(ctx, GuardRevival.class));
        } catch (SecurityException e) {
            return Collections.emptyList();
        }
        List<Session> out = new ArrayList<>(controllers.size());
        for (MediaController c : controllers) {
            PlaybackState state = c.getPlaybackState();
            out.add(new Session(c.getPackageName(), state != null ? state.getState() : PlaybackState.STATE_NONE));
        }
        return out;
    }

    static String label(PackageManager pm, String packageName) {
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null && label.length() > 0) return label.toString();
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return packageName;
    }

    /** Pause whatever holds the active media session, without needing session tokens. */
    static void pause(AudioManager am) {
        if (am == null) return;
        long t = SystemClock.uptimeMillis();
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
        am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0));
    }
}
