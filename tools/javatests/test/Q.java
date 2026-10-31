package io.github.dadcraftttv.ouralia;
import android.content.Context;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;
/** Settings made before kit removal must still restore the rest of DadCraft's sound. */
public class Q {
    static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        System.out.println("ok:   " + label);
    }
    public static void main(String[] args) throws Exception {
        Context ctx = new Context(Files.createTempDirectory("old-settings").toFile());
        JSONObject values = new JSONObject().put("wet", 0.23).put("pad_room", 1).put("note_big", 20.5);
        List<String> removed = new ArrayList<>();
        removed.add("kit");
        for (String part : List.of("mel", "bass", "pad", "drums"))
            for (String prefix : List.of("kitp_", "kitl_", "kitb_")) removed.add(prefix + part);
        for (String id : removed) values.put(id, 99);
        JSONObject state = new JSONObject().put("values", values)
                .put("locked", new org.json.JSONArray().put("kit").put("wet"));
        ctx.getSharedPreferences("ouralia_state", 0).edit().putString("state", state.toString()).apply();
        ParamStore.loadOnce(ctx);
        check(Math.abs(ParamStore.get("wet") - 0.23f) < 1e-6 && ParamStore.get("pad_room") == 1f
                && ParamStore.get("note_big") == 20.5f && ParamStore.isLocked("wet")
                && !ParamStore.isLocked("kit"), "old saved state keeps settings and known locks");
        Map<String, Float> preset = PresetManager.parse(new JSONObject().put("name", "Old preset")
                .put("values", values).put("kits", new JSONObject().put("kit", "mel: engine pluck"))
                .toString()).values;
        ParamStore.applyPreset(preset);
        check(Math.abs(ParamStore.get("wet") - 0.23f) < 1e-6 && ParamStore.get("pad_room") == 1f
                && ParamStore.get("note_big") == 20.5f, "old preset loads its surviving settings");
        check(removed.stream().allMatch(id -> Params.byId(id) == null && !preset.containsKey(id)
                && !ParamStore.snapshot().containsKey(id)), "all 13 old kit keys ignored");
        String exported = PresetManager.toJson("New preset", ParamStore.snapshot());
        check(!new JSONObject(exported).has("kits") && !exported.contains("kit"), "new presets contain no kit data");
        ParamStore.save(ctx);
        check(!ctx.getSharedPreferences("ouralia_state", 0).getString("state", "").contains("kit"),
                "next state save drops old kit keys");
        System.out.println("ALL PASSED");
    }
}
