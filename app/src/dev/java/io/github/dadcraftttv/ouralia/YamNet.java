// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * YAMNet on LiteRT (Oct 31, step 4c, Ouralia Dev only: docs/design/sound-kinds.md, NOTICE in assets/sound_kinds).
 * One thread, the model read from the Dev build's assets. {@link SoundKindsRunner} finds this class by name, so the
 * release build (without it or LiteRT) has no sound kinds.
 */
public final class YamNet implements SoundKinds.Model {
    private final Interpreter interpreter;
    private final float[][] out = new float[1][521];

    public YamNet(Context c) throws IOException {   // (public: SoundKindsRunner finds it by reflection)
        ByteBuffer model;
        try (InputStream in = c.getAssets().open("sound_kinds/yamnet.tflite")) {
            java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();   // (readAllBytes needs API 33)
            byte[] b = new byte[65536];
            for (int n; (n = in.read(b)) > 0; ) {
                all.write(b, 0, n);
            }
            model = ByteBuffer.allocateDirect(all.size()).order(ByteOrder.nativeOrder());
            model.put(all.toByteArray()).rewind();
        }
        interpreter = new Interpreter(model, new Interpreter.Options().setNumThreads(1));
    }

    @Override
    public float[] scores(float[] wave) {
        interpreter.run(wave, out);
        return out[0].clone();
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
