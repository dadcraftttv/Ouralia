// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

/**
 * Sleep fade (Oct 22, ROADMAP 22's app ideas): the sound plays on for the chosen time, fading out slowly over its last
 * minutes, then stops. Plain logic; {@link AudioService} keeps the time and fades through the same dip as another
 * app's audio ("duck" in the patch, a scene's volume), so the dry room fades with the music.
 */
final class SleepTimer {
    private SleepTimer() { }

    /** The choices on the player's sheet, in minutes (0 = no timer). */
    static final int[] MINUTES = {0, 15, 30, 45, 60, 90};
    /** The fade takes the last this long... */
    static final long FADE_MS = 5 * 60_000L;
    /** ...going down this far, evenly in dB (so it sounds even), before the sound stops. */
    static final float FADE_DB = 48f;

    /** The output's gain with this long left: 1 until the fade, then down to -FADE_DB, 0 when the time is up. */
    static float gain(long leftMs) {
        if (leftMs >= FADE_MS) {
            return 1f;
        }
        if (leftMs <= 0) {
            return 0f;
        }
        double gone = 1 - leftMs / (double) FADE_MS;
        return (float) Math.pow(10, -FADE_DB * gone / 20);
    }

    /** Whole minutes left, rounded up (the button says "Sleep in 1 min" to the end). */
    static int minutesLeft(long leftMs) {
        return (int) Math.max(0, (leftMs + 59_999) / 60_000);
    }
}
