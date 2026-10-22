// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Realms tab (moved out of MainActivity on Oct 22, ROADMAP 22): Realm Hopper and its status line, each
 * Realm's preset tile and picker sheet, and its ingredients (the groups a Realm may change).
 */
final class RealmsUi {
    private final MainActivity a;

    RealmsUi(MainActivity activity) {
        a = activity;
    }

    /** One dropdown per automatic world, and the line saying which one is playing. */
    private final List<WorldRow> worldRows = new ArrayList<>();

    private TextView worldStatus;

    /** The Worlds section: an on/off switch, what is playing now, and which preset each world uses. */
    void fillWorldsSection(LinearLayout body) {
        Switch auto = new Switch(a);
        auto.setText(R.string.worlds_auto);
        a.look.label(auto);
        a.look.switchTint(auto);
        auto.setSwitchPadding(a.dp(16));
        auto.setMinHeight(a.dp(52));
        auto.setChecked(Worlds.isEnabled());
        auto.setOnClickListener(v -> {
            if (!auto.isChecked()) {
                Worlds.setEnabled(false);
                updateWorldStatus();
                return;
            }
            // DadCraft: before Realms take over, say that they change the user's own settings (the mix too)
            auto.setChecked(false);
            a.new LookDialog()
                    .setTitle(R.string.realms_warn_title)
                    .setMessage(R.string.realms_warn_text)
                    .setPositiveButton(R.string.realms_warn_ok, (d, w) -> {
                        auto.setChecked(true);
                        Worlds.setEnabled(true);
                        updateWorldStatus();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
        body.addView(auto);

        worldStatus = new TextView(a);
        a.look.hint(worldStatus);
        worldStatus.setPadding(0, a.dp(2), 0, a.dp(6));
        body.addView(worldStatus);

        // each Realm: its name and when it plays, its preset on a tile (a list sheet), what it changes
        for (Worlds.World w : Worlds.ALL) {
            LinearLayout block = new LinearLayout(a);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, a.dp(14), 0, 0);
            LinearLayout head = new LinearLayout(a);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(a.dp(4), 0, a.dp(4), a.dp(8));
            TextView name = new TextView(a);
            name.setText(w.title);
            a.look.label(name);
            head.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView when = new TextView(a);
            when.setText(w.when);
            a.look.hint(when);
            head.addView(when);
            block.addView(head);

            LinearLayout tile = new LinearLayout(a);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setBackground(a.look.choiceTile(a.getDrawable(R.drawable.ic_chevron_down).mutate()));
            tile.setMinimumHeight(a.dp(52));
            tile.setPadding(a.dp(16), a.dp(8), a.dp(40), a.dp(8));
            TextView value = new TextView(a);
            value.setTypeface(a.look.heading());
            value.setTextSize(15);
            value.setTextColor(a.look.p.text);
            value.setMaxLines(1);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(value);
            tile.setContentDescription(a.getString(R.string.world_row, w.title, w.when));
            WorldRow row = new WorldRow(w, value);
            worldRows.add(row);
            tile.setOnClickListener(v -> showRealmSheet(row));
            block.addView(tile);
            addRealmIngredients(block, w);
            body.addView(block);
        }
        TextView note = new TextView(a);
        note.setText(R.string.worlds_note);
        a.look.hint(note);
        note.setPadding(a.dp(4), a.dp(16), a.dp(4), 0);
        body.addView(note);
        refreshWorldRows();
        updateWorldStatus();
    }

    /** A Realm's preset picker: Off, then every preset (built-in and yours), the chosen one ticked. */
    private void showRealmSheet(WorldRow row) {
        String[] names = new String[row.presets.size() + 1];
        names[0] = a.getString(R.string.world_off);
        for (int i = 0; i < row.presets.size(); i++) {
            names[i + 1] = row.presets.get(i);
        }
        int chosen = row.presets.indexOf(Worlds.presetName(row.world.id)) + 1;
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(row.world.title, row.world.when));
        a.addListRows(content, names, chosen, null, i -> {
            Worlds.setPreset(row.world.id, i == 0 ? Worlds.NONE : row.presets.get(i - 1));
            refreshWorldRows();
            a.closeSheet();
        });
        a.openSheet(content);
    }

    /**
     * A Realm's ingredients: "What it changes: everything" folds open to one switch per group (Rhythm, Music, Echo...).
     * Off = the Realm leaves that group at your own settings.
     */
    private void addRealmIngredients(LinearLayout body, Worlds.World w) {
        TextView summary = new TextView(a);
        a.look.hint(summary);
        summary.setGravity(Gravity.CENTER_VERTICAL);
        summary.setMinHeight(a.dp(40));
        summary.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4));
        summary.setCompoundDrawablePadding(a.dp(6));
        summary.setBackground(a.look.pressable(null, a.look.p.text, Look.RADIUS_TILE));
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(8), 0, 0, a.dp(4));
        box.setVisibility(View.GONE);
        Runnable update = () -> {
            List<String> left = new ArrayList<>();
            for (String[] g : Worlds.GROUPS) {
                if (!Worlds.uses(w.id, g[0])) {
                    left.add(g[1].replaceAll(" \\(.*\\)", ""));
                }
            }
            String what = left.isEmpty() ? a.getString(R.string.realm_changes_all)
                    : left.size() == Worlds.GROUPS.length ? a.getString(R.string.realm_changes_none)
                    : a.getString(R.string.realm_changes_but, String.join(", ", left));
            summary.setText(what);
            boolean open = box.getVisibility() == View.VISIBLE;
            android.graphics.drawable.Drawable chevron = a.getDrawable(open ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down).mutate();
            chevron.setTint(a.look.p.muted);
            chevron.setBounds(0, 0, a.dp(18), a.dp(18));
            summary.setCompoundDrawablesRelative(null, null, chevron, null);
        };
        for (String[] g : Worlds.GROUPS) {
            Switch c = new Switch(a);
            c.setText(g[1]);
            a.look.label(c);
            c.setTextSize(Look.TEXT_HINT + 1);
            a.look.switchTint(c);
            c.setSwitchPadding(a.dp(16));
            c.setMinHeight(a.dp(48));
            c.setChecked(Worlds.uses(w.id, g[0]));
            c.setOnCheckedChangeListener((b, on) -> {
                Worlds.setUses(w.id, g[0], on);
                update.run();
            });
            box.addView(c);
        }
        summary.setOnClickListener(v -> {
            box.setVisibility(box.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            update.run();
        });
        update.run();
        body.addView(summary);
        body.addView(box);
    }

    /** Shows each Realm's preset on its tile (the list changes when you save or import a preset). */
    void refreshWorldRows() {
        List<String> presets = new ArrayList<>(PresetManager.builtInNames());
        presets.addAll(PresetManager.userNames(a));
        for (WorldRow row : worldRows) {
            row.presets = presets;
            String chosen = Worlds.presetName(row.world.id);
            row.value.setText(presets.contains(chosen) ? chosen : a.getString(R.string.world_off));
        }
    }

    void updateWorldStatus() {
        if (worldStatus == null) {
            return;
        }
        String id = Worlds.currentWorld();
        String name = a.getString(R.string.worlds_yours);
        for (Worlds.World w : Worlds.ALL) {
            if (w.id.equals(id)) {
                name = w.title;
            }
        }
        worldStatus.setText(!Worlds.isEnabled() ? a.getString(R.string.worlds_off_status)
                : a.getString(R.string.worlds_now, name));
    }

    private static final class WorldRow {
        final Worlds.World world;
        final TextView value;   // the preset's name on the Realm's tile
        List<String> presets = new ArrayList<>();

        WorldRow(Worlds.World world, TextView value) {
            this.world = world;
            this.value = value;
        }
    }
}
