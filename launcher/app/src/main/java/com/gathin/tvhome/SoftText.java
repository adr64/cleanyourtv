package com.gathin.tvhome;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Soft dark halo on Home / screensaver labels so they stay readable on light wallpapers
 * without a glass plate.
 */
final class SoftText {
    private SoftText() {}

    static void apply(View... views) {
        for (View view : views) {
            if (!(view instanceof TextView)) continue;
            TextView text = (TextView) view;
            float density = text.getResources().getDisplayMetrics().density;
            text.setShadowLayer(2.6f * density, 0f, 0.7f * density, 0xA6000000);
        }
    }

    static void applyChildren(View group) {
        if (!(group instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) group;
        for (int i = 0; i < parent.getChildCount(); i++) apply(parent.getChildAt(i));
    }
}
