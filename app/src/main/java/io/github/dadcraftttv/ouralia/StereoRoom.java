// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * The room in stereo (DadCraft, Oct 22): the phone's two mics as left and right, for a wider dry room. How the
 * patch takes them is in make_patch.py (add_stereo_in, add_stereo_out); what each phone's mics give is measured
 * by {@link StereoCalibration}. This class is the plain logic: which way of opening the mic to use, the values the
 * patch gets, and how much of the room is still plain (effects that reshape the room narrow it back to mono).
 */
final class StereoRoom {
    private StereoRoom() { }

    /** Android's input presets (MediaRecorder.AudioSource values; Oboe's InputPreset uses the same numbers). */
    static final int VOICE_RECOGNITION = 6, UNPROCESSED = 9, CAMCORDER = 5, MIC = 1;
    /** Tried in this order: the most neutral first. VOICE_RECOGNITION, the mono default, is the reference level. */
    static final int[] CANDIDATES = {UNPROCESSED, CAMCORDER, MIC};
    /** The trim is kept within these, whatever a phone's processing does to its level. */
    static final float MIN_TRIM_DB = -40f, MAX_TRIM_DB = 6f;

    /** One way of opening the mic, as measured: in stereo, the room for a couple of seconds. */
    static final class Measure {
        final int preset;
        final boolean opened;      // the phone opened it in stereo at all
        final boolean identical;   // left and right came out the same, sample for sample: one mic on both sides
        final double correlation;  // of left and right (1 = alike, 0 = unrelated, below 0 = partly opposite)
        final double levelDb;      // the middle level of the mid (left plus right, halved), dB full scale

        Measure(int preset, boolean opened, boolean identical, double correlation, double levelDb) {
            this.preset = preset;
            this.opened = opened;
            this.identical = identical;
            this.correlation = correlation;
            this.levelDb = levelDb;
        }
    }

    /** What a phone gives: the preset to open (-1: mono only) and the trim matching today's level. */
    static final class Choice {
        final int preset;
        final float trimDb;

        Choice(int preset, float trimDb) {
            this.preset = preset;
            this.trimDb = trimDb;
        }

        boolean stereo() {
            return preset >= 0;
        }
    }

    /**
     * The first candidate that gives two different mics in step with each other: opened in stereo, not identical,
     * not partly opposite (the Moto G Stylus's MIC preset: correlation -0.16, a hollow room that loses its bass in
     * the mid). Its trim brings its level to the reference's (the mono VoiceRecognition the app opens today).
     */
    static Choice choose(double referenceDb, List<Measure> measured) {
        for (int preset : CANDIDATES) {
            for (Measure m : measured) {
                if (m.preset == preset && m.opened && !m.identical && m.correlation >= 0) {
                    float trim = (float) (referenceDb - m.levelDb);
                    return new Choice(preset, Math.max(MIN_TRIM_DB, Math.min(MAX_TRIM_DB, trim)));
                }
            }
        }
        return new Choice(-1, 0f);
    }

    /**
     * The patch's st_r, st_mid and st_side: the right channel's share, the mid's gain and the side's. Off, the mid
     * is the left channel as it is and the side is silence (the sound exactly as before).
     */
    static float[] patchValues(boolean on, float trimDb) {
        if (!on) {
            return new float[]{0f, 1f, 0f};
        }
        float half = (float) Math.pow(10, trimDb / 20.0) / 2f;
        return new float[]{1f, half, half};
    }

    /** The effects that reshape the room before it reaches the ears: on switch, amount (0 = the room as it was). */
    static final String[][] RESHAPERS = {
            {null, "tune_amount"}, {"voc_on", "voc_mix"}, {"ring_on", "ring_mix"}, {"pitch_on", "pitch_mix"},
            {"crush_on", "crush_mix"}, {"dist_on", "dist_mix"}, {"filter_on", "filter_mix"}};

    /**
     * How much of the room is still plain, 0..1: the product of what each reshaping effect leaves. The side (the
     * stereo width) is scaled by it, so a pitch-shifted room doesn't keep an unshifted stereo edge.
     * {@code value} gives a setting as Pd gets it (movers and Intensity included).
     */
    static float plain(ToDoubleFunction<String> value) {
        double keep = 1;
        for (String[] r : RESHAPERS) {
            double on = r[0] == null ? 1 : (value.applyAsDouble(r[0]) >= 0.5 ? 1 : 0);
            keep *= 1 - on * Math.max(0, Math.min(1, value.applyAsDouble(r[1])));
        }
        return (float) keep;
    }

    /**
     * The side's delay beyond the spatial stage's, in samples: Smooth distortion turned up delays the whole room by
     * 16 (its oversampling filters), so the side waits too.
     */
    static int extraDelay(ToDoubleFunction<String> value) {
        boolean smooth = value.applyAsDouble("dist_quality") >= 0.5;
        boolean on = value.applyAsDouble("dist_on") >= 0.5 && value.applyAsDouble("dist_mix") > 0;
        return smooth && on ? 16 : 0;
    }
}
