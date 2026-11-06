// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Accuracy step 4d (Nov 6; docs/design/sound-kinds.md "Accuracy step 4"): a small layer on YAMNet's 1024-value
 * embedding that tells a quiet room from a sound across the room or in the next room, and names the sound. Trained on
 * the desktop by tools/sound_kinds/step4_train.py --export (public room tones and clips, CC0 / public domain / CC BY
 * only; attributions in assets/sound_kinds/NOTICE.txt). A softmax over quiet and the six kinds, about 7k
 * multiply-adds a judgement. Plain Java, so the tests and the replay tool read the same file.
 */
final class Layer {
    final int inputs, outputs;
    /** A sound is heard when 1 - P(quiet) is at least this (set in training: quiet rooms over it ~2% of windows). */
    final float cutoff;
    private final float[] w, b;   // (inputs x outputs, row by row; outputs: quiet, then SoundKinds.NAMES' six kinds)

    private Layer(int inputs, int outputs, float cutoff, float[] w, float[] b) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.cutoff = cutoff;
        this.w = w;
        this.b = b;
    }

    /** sound_kinds/layer.bin: little-endian int32 inputs, int32 outputs, float32 cutoff, the weights, the bias. */
    static Layer read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        byte[] head = new byte[12];
        d.readFully(head);
        ByteBuffer h = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int inputs = h.getInt(), outputs = h.getInt();
        float cutoff = h.getFloat();
        if (inputs != 1024 || outputs != SoundKinds.NAMES.length) {
            throw new IOException("layer.bin: " + inputs + " x " + outputs);
        }
        byte[] rest = new byte[(inputs * outputs + outputs) * 4];
        d.readFully(rest);
        if (d.read() != -1) {
            throw new IOException("layer.bin: longer than its header says");
        }
        ByteBuffer r = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
        float[] w = new float[inputs * outputs], b = new float[outputs];
        r.asFloatBuffer().get(w);
        r.position(w.length * 4);
        r.asFloatBuffer().get(b);
        return new Layer(inputs, outputs, cutoff, w, b);
    }

    /** The probabilities in SoundKinds.NAMES' order (the six kinds, then quiet at SoundKinds.QUIET). */
    float[] probs(float[] embedding) {
        double[] l = new double[outputs];
        for (int o = 0; o < outputs; o++) {
            l[o] = b[o];
        }
        for (int i = 0, j = 0; i < inputs; i++) {
            float e = embedding[i];
            for (int o = 0; o < outputs; o++) {
                l[o] += e * w[j++];
            }
        }
        double max = l[0];
        for (double v : l) {
            max = Math.max(max, v);
        }
        double sum = 0;
        for (int o = 0; o < outputs; o++) {
            l[o] = Math.exp(l[o] - max);
            sum += l[o];
        }
        float[] p = new float[outputs];
        for (int k = 0; k < SoundKinds.QUIET; k++) {
            p[k] = (float) (l[k + 1] / sum);   // (the file's order: quiet first)
        }
        p[SoundKinds.QUIET] = (float) (l[0] / sum);
        return p;
    }
}
