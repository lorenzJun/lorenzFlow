package com.lorenzflow.app.util;

import android.view.View;

import androidx.annotation.NonNull;

/**
 * Small shared view animations for the soft-and-airy polish pass.
 */
public final class AnimUtil {

    private static final long FADE_DURATION_MS = 200L;
    private static final long STAGGER_MS = 28L;

    private AnimUtil() {
    }

    /**
     * Subtle fade-in for list items: plays at most once per ViewHolder so
     * recycled rows and data reloads don't flicker. Pass the ViewHolder's
     * current flag in and store the returned value back on it.
     */
    public static boolean fadeInOnce(@NonNull View view, int position, boolean alreadyAnimated) {
        if (alreadyAnimated) {
            // Rebinds of an already-seen row stay fully opaque
            view.setAlpha(1f);
        } else {
            view.setAlpha(0f);
            view.animate()
                    .alpha(1f)
                    .setDuration(FADE_DURATION_MS)
                    // Stagger the first screenful slightly; scrolled-in rows start at 0
                    .setStartDelay(Math.min(position, 6) * STAGGER_MS)
                    .start();
        }
        return true;
    }
}
