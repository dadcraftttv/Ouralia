// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Finds out what this phone's mics give in stereo (the room in stereo, Oct 22; DadCraft: "the experience should
 * be universal", so nothing is fixed to one phone). Each way of opening the mic listens for a few seconds while the
 * user talks or claps: the mono VoiceRecognition the app uses today (the reference level), then the stereo
 * candidates ({@link StereoRoom#CANDIDATES}). Levels compare the loudest moments (LOUD_SHARE), not the background:
 * a phone's own processing (the Moto's Camcorder mode suppresses a quiet room's steady background) moves the
 * background, so measuring it gave -10.6 dB in one room and +6 in a quieter one (Oct 22). Only levels and how alike
 * the two sides are stay; the sound itself is dropped window by window, never stored. The sound must be off (the mic
 * is in use otherwise): the caller stops it.
 */
final class StereoCalibration {
    private static final String TAG = "StereoCalibration";
    private static final int RATE = 48000, WINDOW = 2400, SECONDS_EACH = 3;   // 50 ms windows
    /** The level of a mode: its windows at this share from the quietest (0.9: the loud tenth, the talk or claps). */
    private static final double LOUD_SHARE = 0.9;

    private StereoCalibration() { }

    /** Measures on a background thread (about 13 s), then hands the choice to {@code done} on the main thread. */
    static void run(Consumer<StereoRoom.Choice> done) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            StereoRoom.Choice choice;
            try {
                StereoRoom.Measure reference = measure(StereoRoom.VOICE_RECOGNITION, false);
                List<StereoRoom.Measure> measured = new ArrayList<>();
                for (int preset : StereoRoom.CANDIDATES) {
                    StereoRoom.Measure m = measure(preset, true);
                    measured.add(m);
                    Log.i(TAG, String.format(java.util.Locale.US, "preset %d: opened %b, identical %b, correlation %.3f, level %.1f dB",
                            m.preset, m.opened, m.identical, m.correlation, m.levelDb));
                }
                choice = reference.opened ? StereoRoom.choose(reference.levelDb, measured) : new StereoRoom.Choice(-1, 0f);
                Log.i(TAG, String.format(java.util.Locale.US, "reference %.1f dB; chose preset %d, trim %.1f dB",
                        reference.levelDb, choice.preset, choice.trimDb));
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not measure the mics", e);
                choice = new StereoRoom.Choice(-1, 0f);
            }
            StereoRoom.Choice result = choice;
            main.post(() -> done.accept(result));
        }, "stereo-calibration").start();
    }

    @SuppressWarnings("MissingPermission")   // only offered once the mic is allowed
    private static StereoRoom.Measure measure(int preset, boolean stereo) {
        int mask = stereo ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int channels = stereo ? 2 : 1;
        int min = AudioRecord.getMinBufferSize(RATE, mask, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec;
        try {
            rec = new AudioRecord(preset, RATE, mask, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, WINDOW * channels * 8));
        } catch (RuntimeException e) {
            return new StereoRoom.Measure(preset, false, false, 0, -120);
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED || rec.getChannelCount() != channels) {
            rec.release();
            return new StereoRoom.Measure(preset, false, false, 0, -120);
        }
        short[] buf = new short[WINDOW * channels];
        int windows = SECONDS_EACH * RATE / WINDOW;
        double[] levels = new double[windows];
        double sumLL = 0, sumRR = 0, sumLR = 0;
        boolean identical = stereo;
        rec.startRecording();
        for (int w = 0; w < windows + 4; w++) {   // (the first 4 windows let the mic settle: not counted)
            int got = 0;
            while (got < buf.length) {
                int n = rec.read(buf, got, buf.length - got);
                if (n <= 0) {
                    break;
                }
                got += n;
            }
            if (w < 4) {
                continue;
            }
            double mm = 0;
            for (int i = 0; i + channels - 1 < got; i += channels) {
                double l = buf[i] / 32768.0, r = stereo ? buf[i + 1] / 32768.0 : l;
                if (stereo) {
                    identical &= buf[i] == buf[i + 1];
                    sumLL += l * l;
                    sumRR += r * r;
                    sumLR += l * r;
                }
                double mid = (l + r) / 2;
                mm += mid * mid;
            }
            levels[w - 4] = 10 * Math.log10(mm / Math.max(1, got / channels) + 1e-12);
        }
        rec.stop();
        rec.release();
        Arrays.sort(levels);
        double corr = stereo ? sumLR / Math.sqrt(sumLL * sumRR + 1e-24) : 1;
        return new StereoRoom.Measure(preset, true, identical, corr, levels[(int) (levels.length * LOUD_SHARE)]);
    }
}
