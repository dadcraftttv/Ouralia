// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Does the phone's mic really give two mics in stereo? (DadCraft, Oct 22: "the room in stereo", ROADMAP Open now.)
 * Debug builds only, started with {@code adb shell am start -n io.github.dadcraftttv.ouralia/.MainActivity --ez
 * stereo_probe true}: opens the built-in mic in stereo, for each source (the engine's VOICE_RECOGNITION, UNPROCESSED, MIC, CAMCORDER) for 20 s, and logs
 * per side levels and how alike the sides are (logcat tag StereoProbe). The sound stays in memory and is dropped
 * window by window: nothing is recorded. Taps near the bottom mic, then near the back one, should swing the
 * left/right difference both ways if the two sides are two mics.
 */
final class StereoProbe {
    private static final String TAG = "StereoProbe";
    private static final int RATE = 48000, SECONDS = 20, WINDOW = 2400;   // 50 ms windows

    static void run() {
        new Thread(() -> {
            for (int source : new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.CAMCORDER}) {
                probe(source);
            }
            Log.i(TAG, "done");
        }, "stereo-probe").start();
    }

    @SuppressWarnings("MissingPermission")   // MainActivity only starts it once the mic is allowed
    private static void probe(int source) {
        String name = source == MediaRecorder.AudioSource.MIC ? "MIC"
                : source == MediaRecorder.AudioSource.UNPROCESSED ? "UNPROCESSED"
                : source == MediaRecorder.AudioSource.VOICE_RECOGNITION ? "VOICE_RECOGNITION" : "CAMCORDER";
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord rec;
        try {
            rec = new AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(min, WINDOW * 8));
        } catch (RuntimeException e) {
            Log.i(TAG, name + ": could not open in stereo: " + e);
            return;
        }
        if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.i(TAG, name + ": could not open in stereo");
            rec.release();
            return;
        }
        Log.i(TAG, name + ": channels " + rec.getChannelCount() + ", start tapping (bottom mic, then back mic)");
        short[] buf = new short[WINDOW * 2];
        double sumLL = 0, sumRR = 0, sumLR = 0, sumDD = 0;
        int windows = 0, leftLouder = 0, rightLouder = 0, same = 0;
        double maxDiff = 0, minDiff = 0;
        rec.startRecording();
        for (int w = 0; w < SECONDS * RATE / WINDOW; w++) {
            int got = 0;
            while (got < buf.length) {
                int n = rec.read(buf, got, buf.length - got);
                if (n <= 0) {
                    break;
                }
                got += n;
            }
            double ll = 0, rr = 0, lr = 0, dd = 0;
            for (int i = 0; i + 1 < got; i += 2) {
                double l = buf[i] / 32768.0, r = buf[i + 1] / 32768.0;
                ll += l * l;
                rr += r * r;
                lr += l * r;
                dd += (l - r) * (l - r);
            }
            sumLL += ll;
            sumRR += rr;
            sumLR += lr;
            sumDD += dd;
            if (ll < 1e-9 && rr < 1e-9) {
                continue;
            }
            double diff = 10 * Math.log10((ll + 1e-12) / (rr + 1e-12));   // left over right, dB
            windows++;
            maxDiff = Math.max(maxDiff, diff);
            minDiff = Math.min(minDiff, diff);
            if (diff > 3) {
                leftLouder++;
            } else if (diff < -3) {
                rightLouder++;
            } else {
                same++;
            }
        }
        rec.stop();
        rec.release();
        double corr = sumLR / Math.sqrt(sumLL * sumRR + 1e-24);
        Log.w(TAG, String.format(java.util.Locale.US,
                "%s: left %.1f dB, right %.1f dB, correlation %.3f, sides differ by %.1f dB below the sum; "
                        + "50 ms windows: left louder by >3 dB %d, right louder %d, within 3 dB %d; "
                        + "range of left-right %.1f to %.1f dB",
                name, 10 * Math.log10(sumLL / (SECONDS * RATE) + 1e-12), 10 * Math.log10(sumRR / (SECONDS * RATE) + 1e-12),
                corr, 10 * Math.log10((sumLL + sumRR) / (sumDD + 1e-12)), leftLouder, rightLouder, same, minDiff, maxDiff));
    }
}
