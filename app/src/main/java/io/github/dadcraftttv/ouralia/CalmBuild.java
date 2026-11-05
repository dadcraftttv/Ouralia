// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.Arrays;
import java.util.Random;
import java.util.ArrayDeque;

/**
 * Calm builds, the quiet tune (DadCraft, Nov 3; "Calm builds" switch in Music, off by default, the user's own, not in
 * presets). Replaces the Oct 14 layer lift. DadCraft: "I'd like the chimes, bells, bass, etc to play along with the
 * drums when a room has been quiet for ... 30 seconds to a minute. it should be a slow build up ... can trail off
 * over 15 seconds once a consistent amount of noise has been detected."
 * <p>
 * After a quiet wait (30-60 s, new each time) a two-bar chime phrase, picked fresh for each build, grows a note at a
 * time over {@link #RISE_S}, then develops in eight-bar sections while the room stays quiet. Quiet is judged from the pre-effect mic level
 * against a reference that follows the room's low level while waiting (a steady fan below {@link #GUARD_DB} becomes
 * the reference, so it counts as quiet). A knock or a cough is short and changes nothing (the found drums still catch
 * it). Noise for {@link #NOISE_S} of the last {@link #WINDOW_S} s, or walking, starts a {@link #FADE_S} trail-off that
 * always finishes; then a fresh wait. The patch plays the phrase on the drum grid only while the drums are sounding
 * (calm.py). Nothing stored is changed. Fed once per service tick. Pure logic (test B).
 */
final class CalmBuild {

    static final String ID = "calm_build";
    static final double WAIT_MIN_S = 30, WAIT_MAX_S = 60;   // DadCraft, Nov 2: "kick in after 30 seconds to 1 minute"
    static final double RISE_S = 120;   // a note at a time: six notes over two minutes
    static final double FADE_S = 15;    // DadCraft: "trail off over 15 seconds"
    static final float NOISE_DB = 8f;   // noisy: this far above the quiet reference...
    static final float GUARD_DB = -40f; // ...or louder than this at all (dBFS: a room this loud is never quiet)
    static final double NOISE_S = 3, WINDOW_S = 5;   // consistent noise: noisy for 3 of the last 5 s
    static final double REF_UP_S = 60;  // while waiting the reference rises this slowly toward a new steady sound
    static final double REF_DOWN_S = 3; // and follows a quieter room within a few seconds (1 s until Nov 4)
    /** Below this the level is a dropout, not a room (DadCraft's Moto, Nov 4: a steady -56 dBFS room read about -100
     *  for a moment every 10-20 s). A dropout counts as quiet and never moves the reference: before, each one pulled
     *  the reference down, the room then read as noise and the wait never finished. Even a mic's own hiss is above. */
    static final float DROPOUT_DB = -80f;
    /** The phrase: NOTES chime steps in time order, at 16ths 0, 6, 12 of each of two bars (calm.py). */
    static final int NOTES = 6, LOW = 7, HIGH = 14;

    enum Phase { WAITING, RISING, PLAYING, FADING }

    private static Phase phase = Phase.WAITING;
    private static double phaseS;          // time in the current phase (WAITING: quiet time so far)
    private static float level;            // 0..1: how far the tune has grown
    private static float fadeFrom;
    private static double waitS = WAIT_MIN_S;
    private static float ref;
    private static boolean refSet;
    private static final double[] noisy = new double[(int) WINDOW_S];   // noisy seconds per one-second bucket
    private static int bucket;
    private static double bucketS;
    private static final float[] phrase = new float[NOTES];
    private static boolean phraseNew;
    private static int scaleSize = 7;
    private static int epoch;
    private static boolean resetNew, awaitingApplied, advance;
    private static int pass;
    private static float[] motif;
    private static final ArrayDeque<String> recent = new ArrayDeque<>();
    private static final ArrayDeque<String> contours = new ArrayDeque<>();
    /** 0..1 picks where in WAIT_MIN_S..WAIT_MAX_S a wait falls (tests pin it). */
    static java.util.function.DoubleSupplier wait01 = Math::random;
    /** The phrase's dice (tests seed it). */
    static Random dice = new Random();

    private CalmBuild() { }

    static synchronized void reset() {
        phase = Phase.WAITING;
        phaseS = 0;
        level = 0;
        refSet = false;
        Arrays.fill(noisy, 0);
        bucketS = 0;
        clearPhrase();
        newWait();
    }

    private static void newWait() {
        waitS = WAIT_MIN_S + (WAIT_MAX_S - WAIT_MIN_S) * Math.max(0, Math.min(1, wait01.getAsDouble()));
    }

    static synchronized double waitS() {
        return waitS;
    }

    /**
     * One tick. {@code on}: the switch and Music on. {@code walking}: counts as noise. {@code micDb}: the
     * pre-effect mic level in dBFS. Returns true when the tune has just started or just ended (for the event log).
     */
    static synchronized boolean tick(double dt, boolean on, boolean walking, float micDb) {
        Phase before = phase;
        if (!on) {
            boolean was = phase != Phase.WAITING;
            phase = Phase.WAITING;
            phaseS = 0;
            level = 0;
            Arrays.fill(noisy, 0);
            if (was) {
                clearPhrase();
                newWait();
            }
            return was;
        }
        boolean dropout = micDb < DROPOUT_DB;
        if (!refSet && !dropout) {
            ref = micDb;
            refSet = true;
        }
        boolean loud = walking || (!dropout && (micDb > GUARD_DB || micDb > ref + NOISE_DB));
        if (dropout || !refSet) {
            // (the reference stays where the room put it)
        } else if (micDb < ref) {
            ref += (micDb - ref) * (float) Math.min(1, dt / REF_DOWN_S);
        } else if (phase == Phase.WAITING) {   // (held while the tune plays and fades: noise can't become the floor)
            ref += (micDb - ref) * (float) Math.min(1, dt / REF_UP_S);
        }
        bucketS += dt;
        if (loud) {
            noisy[bucket] += dt;
        }
        while (bucketS >= 1) {
            bucketS -= 1;
            bucket = (bucket + 1) % noisy.length;
            noisy[bucket] = 0;
        }
        double sum = 0;
        for (double s : noisy) {
            sum += s;
        }
        boolean noise = sum >= NOISE_S;
        phaseS += dt;
        switch (phase) {
            case WAITING:
                if (noise) {
                    phaseS = 0;
                } else if (phaseS >= waitS) {
                    clearPhrase();
                    pickPhrase();
                    motif = phrase.clone();
                    contours.add(contour(motif));
                    next(Phase.RISING);
                }
                break;
            case RISING:
            case PLAYING:
                if (noise) {
                    fadeFrom = level;
                    next(Phase.FADING);
                } else if (phase == Phase.RISING) {
                    level = (float) Math.min(1, phaseS / RISE_S);
                    if (phaseS >= RISE_S) {
                        next(Phase.PLAYING);
                    }
                }
                break;
            case FADING:
                level = (float) Math.max(0, fadeFrom * (1 - phaseS / FADE_S));
                if (phaseS >= FADE_S) {
                    level = 0;
                    clearPhrase();
                    newWait();
                    next(Phase.WAITING);
                }
                break;
        }
        return (before == Phase.WAITING && phase == Phase.RISING) || (before == Phase.FADING && phase == Phase.WAITING);
    }

    private static void next(Phase p) {
        phase = p;
        phaseS = 0;
    }

    /** A gentle phrase: starts on a chord tone, moves a step or two, the second bar answers and comes home. */
    private static void pickPhrase() {
        int[] chord = {LOW, LOW + 2, LOW + 4};
        int start = chord[dice.nextInt(chord.length)];
        int at = start;
        for (int i = 0; i < NOTES; i++) {
            if (i == 0) {
                at = start;
            } else if (i == 3) {
                at = chord[dice.nextInt(chord.length)];
                if (dice.nextBoolean()) {
                    at = octave(at, scaleSize);
                }
            } else if (i == NOTES - 1) {
                at = start;   // home
            } else {
                int move = dice.nextInt(4) + 1;   // 1..4 -> -2, -1, +1, +2
                at = Math.max(LOW, Math.min(HIGH, at + (move <= 2 ? move - 3 : move - 2)));
            }
            phrase[i] = at;
        }
        phraseNew = true;
    }

    /** Try the octave in either direction; retain the chord tone if neither fits. Never clamp to HIGH. */
    static int octave(int step, int size) {
        return step + size <= HIGH ? step + size : step - size >= LOW ? step - size : step;
    }

    static synchronized void scaleSize(int size) {
        if (size < 1 || size == scaleSize) return;
        scaleSize = size;
        if (phase == Phase.RISING || phase == Phase.PLAYING) {
            clearPhrase();
            pickPhrase();
            motif = phrase.clone();
            contours.add(contour(motif));
        }
    }

    private static void clearPhrase() {
        epoch = epoch % 1_000_000 + 1;   // exactly represented by Pd's float, including after long sessions
        resetNew = true;
        phraseNew = awaitingApplied = advance = false;
        pass = 0;
        motif = null;
        recent.clear();
        contours.clear();
    }

    /** Send before a pending phrase or level: invalidates active/pending Pd notes on reset, off and new build. */
    static synchronized Integer takeReset() {
        if (!resetNew) return null;
        resetNew = false;
        return epoch;
    }

    /** Pd acknowledges only a pending phrase actually committed at a two-bar boundary. No Pd calls here. */
    static synchronized void phraseApplied(float generation) {
        if (generation != epoch || !awaitingApplied) return;
        awaitingApplied = false;
        recent.add(sounding(phrase));
        while (recent.size() > 16) recent.removeFirst();
        advance = phase == Phase.RISING || phase == Phase.PLAYING;
    }

    private static String sounding(float[] notes) {
        StringBuilder s = new StringBuilder();
        for (float n : notes) if (n >= 0) s.append((int) n).append(',');
        return s.toString();
    }

    private static String contour(float[] notes) {
        StringBuilder s = new StringBuilder();
        for (int i = 1; i < notes.length; i++) s.append((int) (notes[i] - notes[i - 1])).append(',');
        return s.toString();
    }

    private static int near(int step, int move) {
        int n = step + move;
        return n < LOW || n > HIGH ? step - move : n;
    }

    /** Develop the previous motif, rejecting recent pitch sequences and section contours (including A/B/A/B). */
    private static void develop() {
        float[] next = null;
        for (int attempt = 0; next == null; attempt++) {
            float[] c = motif.clone();
            if (attempt < 128) {
                c[0] = near((int) motif[NOTES - 1], dice.nextBoolean() ? 1 : -1);
                int a = 1 + dice.nextInt(4), b = 1 + dice.nextInt(4);
                c[a] = near((int) c[a], dice.nextBoolean() ? 1 : -1);
                c[b] = near((int) c[b], dice.nextBoolean() ? 2 : -2);
                c[NOTES - 1] = c[0];
            } else {
                // Exhaustive fallback: 8^3 candidates, far more than the short forbidden history.
                int k = attempt - 128, width = HIGH - LOW + 1;
                c[1] = LOW + k % width;
                c[3] = LOW + k / width % width;
                c[4] = LOW + k / (width * width) % width;
            }
            if (!recent.contains(sounding(c)) && !contours.contains(contour(c))) next = c;
        }
        motif = next;
        contours.add(contour(motif));
        while (contours.size() > 4) contours.removeFirst();
    }

    private static void nextPass() {
        if (phase == Phase.PLAYING) {
            pass = (pass + 1) % 4;
            if (pass == 0) develop();
        }
        System.arraycopy(motif, 0, phrase, 0, NOTES);
        if (pass >= 2) {   // third pass answers differently; fourth closes or breathes
            phrase[3] = near((int) motif[3], dice.nextBoolean() ? 1 : -1);
            if (dice.nextBoolean()) phrase[4] = near((int) motif[4], dice.nextBoolean() ? 1 : -1);
            if (pass == 3 && dice.nextBoolean()) {
                phrase[2] = phrase[4] = -1;
            }
        }
        phraseNew = true;
    }

    static synchronized float level() {
        return level;
    }

    static synchronized Phase phase() {
        return phase;
    }

    static synchronized float reference() {
        return ref;
    }

    /** Prepare one pass ahead after Pd acknowledges the last one; the service sends it outside this lock. */
    static synchronized float[] takePhrase() {
        if (advance) {
            advance = false;
            nextPass();
        }
        if (!phraseNew) {
            return null;
        }
        phraseNew = false;
        awaitingApplied = true;
        return phrase.clone();
    }
}
