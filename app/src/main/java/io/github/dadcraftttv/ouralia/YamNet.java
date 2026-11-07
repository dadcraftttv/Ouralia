// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Context;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * YAMNet on LiteRT (Oct 31, step 4c: docs/design/sound-kinds.md, NOTICE in assets/sound_kinds). One thread, the model
 * read from the assets. In every build since Nov 5 (DadCraft: "bring it in"), ARM phones only. {@link SoundKindsRunner}
 * finds this class by name (kept by app/proguard-rules.pro), so the desktop and test builds without it have none.
 * Since accuracy step 4d (Nov 6) the model also outputs its 1024-value embedding (tools/sound_kinds/embed_model.py: the
 * scores are identical, the embedding was computed anyway), read by {@link Layer}.
 */
public final class YamNet implements SoundKinds.Model {
    private final Interpreter interpreter;
    private final float[][] out = new float[1][521];
    private final ByteBuffer emb;   // (int8, null if the model has no embedding output)
    private final float scale;
    private final int zero;
    private final Map<Integer, Object> outputs = new HashMap<>();
    private final Object[] inputs = new Object[1];

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
        outputs.put(0, out);
        Tensor e = interpreter.getOutputTensorCount() > 1 ? interpreter.getOutputTensor(1) : null;
        if (e != null && e.numElements() == 1024) {
            emb = ByteBuffer.allocateDirect(e.numBytes()).order(ByteOrder.nativeOrder());
            scale = e.quantizationParams().getScale();
            zero = e.quantizationParams().getZeroPoint();
            outputs.put(1, emb);
        } else {
            emb = null;
            scale = 0;
            zero = 0;
        }
    }

    @Override
    public float[] scores(float[] wave) {
        inputs[0] = wave;
        if (emb != null) {
            emb.rewind();
        }
        interpreter.runForMultipleInputsOutputs(inputs, outputs);
        return out[0].clone();
    }

    @Override
    public float[] embedding() {
        if (emb == null) {
            return null;
        }
        float[] e = new float[1024];
        for (int i = 0; i < e.length; i++) {
            e[i] = (emb.get(i) - zero) * scale;   // (int8: YAMNet's own scale and zero point)
        }
        return e;
    }

    @Override
    public void close() {
        interpreter.close();
    }
}
