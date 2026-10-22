// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.ArrayList;
import java.util.List;

/**
 * An imported scene's saved state (Oct 22, ROADMAP Compatibility "State saving"). rjlib's saving (`sssad`,
 * `rjloader`, `u_loader`) is a deal with the player: the player sends "save" to RJ_SCENE_SAVE, each saver answers on
 * RJ_SCENE with a list ("rjsave-ex-one a 30"), and the next time the scene opens the player sends those lists back
 * to RJ_SCENE_LOAD (rjlib's rjsave-ex-help). The RjDj app did this; PdParty never did. {@link AudioService} saves at
 * Stop and restores at Start; this class turns the lists into text and back (one per line, atoms tab-separated,
 * "s:" a symbol, "f:" a number), for {@link SceneSettings}.
 */
final class SceneState {
    private SceneState() { }

    /** The player's side of rjlib's saving. */
    static final String SAVE = "RJ_SCENE_SAVE", SAVED = "RJ_SCENE", LOAD = "RJ_SCENE_LOAD";

    static String encode(List<Object[]> messages) {
        StringBuilder b = new StringBuilder();
        for (Object[] m : messages) {
            if (m.length == 0) {
                continue;
            }
            for (int i = 0; i < m.length; i++) {
                b.append(i > 0 ? "\t" : "");
                b.append(m[i] instanceof Number ? "f:" + ((Number) m[i]).floatValue() : "s:" + clean(String.valueOf(m[i])));
            }
            b.append('\n');
        }
        return b.toString();
    }

    static List<Object[]> decode(String text) {
        List<Object[]> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String line : text.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] atoms = line.split("\t");
            Object[] m = new Object[atoms.length];
            boolean ok = true;
            for (int i = 0; i < atoms.length && ok; i++) {
                String a = atoms[i];
                if (a.startsWith("f:")) {
                    try {
                        m[i] = Float.parseFloat(a.substring(2));
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                } else if (a.startsWith("s:")) {
                    m[i] = a.substring(2);
                } else {
                    ok = false;
                }
            }
            if (ok) {
                out.add(m);
            }
        }
        return out;
    }

    /** (a symbol can't hold a tab or line break in the file) */
    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ');
    }
}
