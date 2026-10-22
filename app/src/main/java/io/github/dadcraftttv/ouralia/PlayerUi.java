// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The docked player and its sheet (moved out of MainActivity on Oct 22, ROADMAP 22): Start inside the small
 * Intensity dial, the status line and Save sound; opened, the big dial with Start/Stop, the full status, the Realm
 * line with why it plays, Hold realm and Save sound. The sheet itself stays in MainActivity (every picker uses it).
 */
final class PlayerUi {
    private final MainActivity a;

    PlayerUi(MainActivity activity) {
        a = activity;
    }

    private DialView miniDial;

    private DialView playerDial;

    private TextView dialHint;

    ImageButton miniStart;

    ImageButton miniSave;

    View playerMiddle;   // (the tour points at it)

    private TextView miniStatus;

    private TextView miniSub;

    private LinearLayout playerContent;

    private Button startStop;

    TextView status;

    /** The bar pinned to the bottom: what is playing and why, Start/Stop, hold the world, save this sound. */
    private TextView nowLine;

    private Button holdButton;

    private Button snapButton;

    private Button sleepButton;

    /** The docked player: the small dial with Start inside, the status line, and Save sound. */
    View buildPlayer() {
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(a.look.round(a.look.p.dock, Look.RADIUS_CARD));
        card.setElevation(a.dp(2));
        card.setPadding(a.dp(8), a.dp(8), a.dp(6), a.dp(8));

        android.widget.FrameLayout ringBox = new android.widget.FrameLayout(a);
        miniDial = new DialView(a, a.look, false, "");
        ringBox.addView(miniDial, new android.widget.FrameLayout.LayoutParams(a.dp(56), a.dp(56)));
        miniStart = new ImageButton(a);
        miniStart.setBackground(a.look.pressable(a.look.round(a.look.p.primary, 20), a.look.p.onPrimary, 20));
        miniStart.setImageTintList(android.content.res.ColorStateList.valueOf(a.look.p.onPrimary));
        miniStart.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        miniStart.setPadding(a.dp(11), a.dp(11), a.dp(11), a.dp(11));
        miniStart.setOnClickListener(v -> a.onStartStop());
        ringBox.addView(miniStart, new android.widget.FrameLayout.LayoutParams(a.dp(40), a.dp(40), Gravity.CENTER));
        card.addView(ringBox, new LinearLayout.LayoutParams(a.dp(56), a.dp(56)));

        LinearLayout middle = new LinearLayout(a);
        middle.setOrientation(LinearLayout.HORIZONTAL);
        middle.setGravity(Gravity.CENTER_VERTICAL);
        middle.setPadding(a.dp(10), 0, a.dp(6), 0);
        middle.setBackground(a.look.pressable(null, a.look.p.text, Look.RADIUS_TILE));
        middle.setContentDescription(a.getString(R.string.player_open));
        middle.setOnClickListener(v -> openPlayer());
        playerMiddle = middle;
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        miniStatus = new TextView(a);
        miniStatus.setTypeface(a.look.heading());
        miniStatus.setTextSize(Look.TEXT_LABEL);
        miniStatus.setTextColor(a.look.p.text);
        miniStatus.setSingleLine(true);
        miniStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(miniStatus);
        miniSub = new TextView(a);
        a.look.hint(miniSub);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(miniSub);
        middle.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ImageView up = new ImageView(a);
        up.setImageResource(R.drawable.ic_chevron_up);
        up.setImageTintList(android.content.res.ColorStateList.valueOf(a.look.p.muted));
        middle.addView(up, new LinearLayout.LayoutParams(a.dp(20), a.dp(20)));
        card.addView(middle, new LinearLayout.LayoutParams(0, a.dp(56), 1f));

        miniSave = new ImageButton(a);
        miniSave.setImageResource(R.drawable.ic_bookmark);
        miniSave.setImageTintList(android.content.res.ColorStateList.valueOf(a.look.p.onContainer));
        miniSave.setBackground(a.look.pressable(a.look.round(a.look.p.container, 22), a.look.p.onContainer, 22));
        miniSave.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        miniSave.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12));
        miniSave.setContentDescription(a.getString(R.string.snapshot));
        miniSave.setOnClickListener(v -> a.presetUi.saveSnapshot());
        card.addView(miniSave, new LinearLayout.LayoutParams(a.dp(44), a.dp(44)));
        return card;
    }

    /**
     * The player's sheet content: the big Intensity dial (drag around the ring) with Start/Stop in its middle, the
     * full status, the realm line, Hold realm and Save sound.
     */
    void buildPlayerContent() {
        playerContent = new LinearLayout(a);
        playerContent.setOrientation(LinearLayout.VERTICAL);
        playerContent.setGravity(Gravity.CENTER_HORIZONTAL);
        playerContent.setPadding(a.dp(4), 0, a.dp(4), 0);

        android.widget.FrameLayout dialBox = new android.widget.FrameLayout(a);
        playerDial = new DialView(a, a.look, true, a.getString(R.string.intensity_caption));
        playerDial.setContentDescription(a.getString(R.string.intensity_card));
        playerDial.setListener((v, done) -> {
            ParamStore.set(Intensity.ID, v);
            updateIntensityRow();
            if (done) {
                a.settingRows.refreshRows();   // the slider in Input & output follows
            }
        });
        dialBox.addView(playerDial, new android.widget.FrameLayout.LayoutParams(a.dp(248), a.dp(248)));
        startStop = new Button(a);
        a.look.button(startStop, true);
        startStop.setTypeface(a.look.value());   // DadCraft: the words in the slider numbers' font
        startStop.setBackground(a.look.pressable(a.look.round(a.look.p.primary, 52), a.look.p.onPrimary, 52));
        startStop.setPadding(0, a.dp(20), 0, a.dp(16));
        startStop.setCompoundDrawablePadding(a.dp(2));
        startStop.setOnClickListener(v -> a.onStartStop());
        dialBox.addView(startStop, new android.widget.FrameLayout.LayoutParams(a.dp(104), a.dp(104), Gravity.CENTER));
        playerContent.addView(dialBox, new LinearLayout.LayoutParams(a.dp(248), a.dp(248)));

        status = new TextView(a);
        a.look.hint(status);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, a.dp(6), 0, 0);
        playerContent.addView(status);
        dialHint = new TextView(a);   // (hidden while a scene plays: a scene has no Intensity)
        dialHint.setText(R.string.dial_hint);
        a.look.hint(dialHint);
        dialHint.setTextSize(12);
        dialHint.setGravity(Gravity.CENTER);
        dialHint.setPadding(0, a.dp(2), 0, 0);
        playerContent.addView(dialHint);
        nowLine = new TextView(a);
        a.look.label(nowLine);
        nowLine.setTextSize(Look.TEXT_HINT + 1);
        nowLine.setGravity(Gravity.CENTER);
        nowLine.setPadding(0, a.dp(4), 0, 0);
        playerContent.addView(nowLine);
        LinearLayout row = a.newButtonRow(playerContent);
        row.setPadding(0, a.dp(12), 0, 0);
        holdButton = a.addButton(row, R.string.hold_world, () -> {
            Worlds.setHeld(!Worlds.isHeld());
            updateNowLine();
        });
        snapButton = a.addButton(row, R.string.snapshot, a.presetUi::saveSnapshot);
        sleepButton = a.addButton(a.newButtonRow(playerContent), R.string.sleep_timer, this::showSleepSheet);
    }

    /** The sleep timer's choices: Off and SleepTimer.MINUTES, the one running ticked. */
    private void showSleepSheet() {
        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(a.sheetHeading(a.getString(R.string.sleep_timer), a.getString(R.string.sleep_hint)));
        String[] names = new String[SleepTimer.MINUTES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = SleepTimer.MINUTES[i] == 0 ? a.getString(R.string.sleep_off)
                    : a.getString(R.string.sleep_minutes, SleepTimer.MINUTES[i]);
        }
        int chosen = AudioService.sleepLeftMs() > 0 ? -1 : 0;
        a.addListRows(content, names, chosen, null, i -> {
            AudioService.setSleep(SleepTimer.MINUTES[i]);
            updateNowLine();
            openPlayer();   // (back to the player: the sheet swaps its content)
        });
        a.openSheet(content);
    }

    private void openPlayer() {
        a.openSheet(playerContent);
    }

    /** Start's icon and words follow whether the sound is on. */
    void updateStartButtons(boolean on) {
        miniStart.setImageResource(on ? R.drawable.ic_stop : R.drawable.ic_play);
        miniStart.setContentDescription(a.getString(on ? R.string.stop : R.string.start));
        startStop.setText(on ? R.string.stop : R.string.start);
        android.graphics.drawable.Drawable icon = a.getDrawable(on ? R.drawable.ic_stop : R.drawable.ic_play).mutate();
        icon.setTint(a.look.p.onPrimary);
        startStop.setCompoundDrawablesRelativeWithIntrinsicBounds(null, icon, null, null);
    }

    /** The dials follow Intensity (empty while a scene plays: a scene has no Intensity), and so does the player's line. */
    void updateIntensityRow() {
        if (playerDial == null) {
            return;
        }
        SceneLibrary.Scene scene = SceneSettings.current(a);
        float v = ParamStore.get(Intensity.ID);
        playerDial.setActive(scene == null);
        miniDial.setActive(scene == null);
        dialHint.setVisibility(scene == null ? View.VISIBLE : View.GONE);
        playerDial.setValue(v);
        miniDial.setValue(v);
        String sub = scene != null ? scene.name : a.getString(R.string.scene_built_in);
        String realm = playingRealm();
        if (scene == null && realm != null) {   // DadCraft: the bar showed Built-in effects while a Realm played
            sub = a.getString(R.string.mini_realm, realm);
            if (Worlds.isHeld()) {
                sub = a.getString(R.string.now_line_held, sub);
            }
        }
        if (scene == null) {
            sub += " · " + a.getString(R.string.intensity_card_value, Math.round(v * 100))
                    + (Modulation.isMoving(Intensity.ID) ? SettingRows.MOVER_DOT : "");
        }
        miniSub.setText(sub);
    }

    /** The title of the Realm playing now (Realm Hopper on, built-in effects running), or null. */
    private String playingRealm() {
        if (!AudioService.isRunning() || !Worlds.isEnabled()) {
            return null;
        }
        String id = Worlds.currentWorld();
        for (Worlds.World w : Worlds.ALL) {
            if (w.id.equals(id)) {
                return w.title;
            }
        }
        return null;
    }

    /** One line: the world playing now and, in a few words, why. Refreshed once a second while visible. */
    void updateNowLine() {
        boolean builtIn = AudioService.isRunning() && SceneSettings.current(a) == null;
        boolean worlds = builtIn && Worlds.isEnabled();
        holdButton.setVisibility(worlds ? View.VISIBLE : View.GONE);
        holdButton.setText(Worlds.isHeld() ? R.string.holding_world : R.string.hold_world);
        snapButton.setVisibility(SceneSettings.current(a) == null ? View.VISIBLE : View.GONE);
        long sleepLeft = AudioService.sleepLeftMs();
        sleepButton.setVisibility(AudioService.isRunning() ? View.VISIBLE : View.GONE);
        if (sleepLeft > 0) {
            sleepButton.setText(a.getString(R.string.sleep_in, SleepTimer.minutesLeft(sleepLeft)));
        } else {
            sleepButton.setText(R.string.sleep_timer);
        }
        miniSave.setVisibility(snapButton.getVisibility());
        if (!worlds) {
            nowLine.setVisibility(View.GONE);
            return;
        }
        nowLine.setVisibility(View.VISIBLE);
        String id = Worlds.currentWorld();
        String name = a.getString(R.string.worlds_yours);
        for (Worlds.World w : Worlds.ALL) {
            if (w.id.equals(id)) {
                name = w.title;
            }
        }
        List<String> why = new ArrayList<>();
        int minute = Worlds.minuteOfDayNow();
        if (minute >= WorldSelector.NIGHT_START_MINUTE && minute < WorldSelector.NIGHT_END_MINUTE) {
            why.add(a.getString(R.string.why_night));
        }
        double nowS = android.os.SystemClock.elapsedRealtimeNanos() / 1e9;
        if (WorldSelector.UNSTABLE.equals(id)) {
            why.add(a.getString(R.string.why_shaken));
        }
        if (StepDetector.walking(nowS)) {
            boolean clock = ParamStore.get("beat_follow") >= 0.5f && AudioService.tempo() > 0;
            why.add(clock ? a.getString(R.string.why_walking_bpm, Math.round(AudioService.tempo()))
                    : a.getString(R.string.why_walking));
        }
        if (PhoneContext.inPocket()) {
            why.add(a.getString(R.string.why_pocket));
        }
        if (CalmBuild.level() > 0.02f) {
            why.add(a.getString(R.string.why_building));
        }
        if (HandlingDetector.level() > 0.3f) {
            why.add(a.getString(R.string.why_handling));
        }
        int room = Worlds.roomQuiet() ? R.string.why_room_quiet
                : Worlds.roomLoud() ? (Worlds.roomLively() ? R.string.why_room_lively : R.string.why_room_steady)
                : R.string.why_room_middle;
        why.add(a.getString(room, Math.round(Worlds.roomLevelDb())));
        int heard = AudioService.roomKind();   // the room listener (only listens for now)
        if (heard >= 0) {
            why.add(a.getString(new int[]{R.string.hears_talking, R.string.hears_music, R.string.hears_traffic,
                    R.string.hears_wind}[heard]));
        }
        if (ParamStore.get("beat_level") > 0 && ParamStore.get("beat_follow") >= 0.5f && ParamStore.get("rhythm_src") >= 0.5f) {
            int[] kit = AudioService.kit();
            why.add(a.getString(R.string.why_kit, kit[0], kit[1], kit[2], kit[3]));
        }
        String line = a.getString(R.string.now_line, name, String.join(" \u00b7 ", why));
        if (Worlds.isHeld()) {
            line = a.getString(R.string.now_line_held, line);
        }
        nowLine.setText(line);
    }

    /** The status line: stopped, running, paused for a call, an error, or the mic overloading (checked once a second). */
    void updateStatus() {
        boolean on = AudioService.isRunning();
        String error = AudioService.getLastError();
        boolean missing = on && AudioService.objectsMissing();
        boolean overload = on && !AudioService.isPausedForCall() && AudioService.clips().warning(ClipWatch.IN);
        status.setTypeface(overload || missing ? a.look.textStrong() : a.look.text());
        if (error != null && !on) {
            status.setText(error);
        } else if (missing) {
            status.setText(R.string.status_objects_missing);
        } else if (overload) {
            status.setText(R.string.status_mic_overload);
        } else {
            int hz = AudioService.actualSampleRateHz();
            status.setText(!on ? a.getString(R.string.status_stopped)
                    : AudioService.isPausedForCall() ? a.getString(R.string.status_call)
                    : hz > 0 ? a.getString(R.string.status_running_rate, hz) : a.getString(R.string.status_running));
        }
        // the docked player shows the same, on one line: warnings in the error colour so they're seen at a glance
        miniStatus.setText(status.getText());
        miniStatus.setTextColor(overload || missing || (error != null && !on) ? a.look.p.err : a.look.p.text);
    }
}
