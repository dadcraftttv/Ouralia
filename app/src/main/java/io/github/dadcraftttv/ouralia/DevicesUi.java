// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Setup's Audio Devices and Diagnostics cards (moved out of MainActivity on Oct 22, ROADMAP 22): the
 * headphone and mic pickers (Automatic first), the hint under them when a Bluetooth headset is the mic, and the
 * diagnostics lines (mics and headphones in use, rate, levels, overloads), refreshed while visible.
 */
final class DevicesUi {
    private final MainActivity a;

    DevicesUi(MainActivity activity) {
        a = activity;
    }

    private DevicePicker inputPicker;

    private DevicePicker outputPicker;

    private TextView micHint;

    private TextView diagText;
    private android.widget.ProgressBar diagLevel;

    /** A dropdown of connected audio devices, plus "Automatic" as the first entry. */
    private static final class DevicePicker {
        final boolean input;
        final View tile;
        final TextView value;   // the device shown on the tile
        List<AudioDevices.Device> devices = new ArrayList<>();
        String[] names = new String[0];   // what the sheet lists: Automatic, then the devices
        int selected;
        String shownKey = AudioSettings.AUTOMATIC;   // what the tile currently displays

        DevicePicker(boolean input, View tile, TextView value) {
            this.input = input;
            this.tile = tile;
            this.value = value;
        }
    }

    /** Advice that appears when a Bluetooth headset is the chosen microphone. */
    private void updateMicHint() {
        if (micHint == null) {
            return;
        }
        AudioDevices.Device in = AudioDevices.find(a, true);
        if (AudioDevices.isLeAudio(in)) {
            micHint.setText(R.string.mic_hint_le);
        } else if (AudioDevices.isBluetoothMic(in)) {
            micHint.setText(R.string.mic_hint_call);
        } else if (AudioDevices.wiredWithoutMic(a)) {
            micHint.setText(R.string.mic_hint_no_wired_mic);
        } else {
            micHint.setText("");
        }
        micHint.setVisibility(micHint.length() == 0 ? View.GONE : View.VISIBLE);
    }

    void addAudioSection(LinearLayout parent) {
        LinearLayout body = a.settingRows.addSection(parent, "audio", a.getString(R.string.section_audio), false);
        inputPicker = addDevicePicker(body, R.string.audio_input, true);
        outputPicker = addDevicePicker(body, R.string.audio_output, false);

        if (!AudioService.canSelectDevices()) {
            for (DevicePicker picker : new DevicePicker[] {inputPicker, outputPicker}) {
                picker.tile.setEnabled(false);
                picker.tile.setAlpha(0.5f);
            }
        }
        micHint = new TextView(a);
        a.look.hint(micHint);
        micHint.setPadding(a.dp(4), a.dp(8), 0, 0);
        body.addView(micHint);
        updateMicHint();
        addStereoRow(body);
        TextView note = new TextView(a);
        note.setText(AudioService.canSelectDevices()
                ? R.string.audio_note : R.string.audio_unsupported);
        a.look.hint(note);
        note.setPadding(a.dp(4), a.dp(12), 0, 0);
        body.addView(note);
    }

    // ---- the room in stereo (Oct 22): the phone's two mics as left and right, measured per phone ----
    private android.widget.Switch stereoSwitch;
    private TextView stereoStatus;
    private android.widget.Button stereoCheck;
    private LinearLayout stereoLevelRow;
    private TextView stereoLevelLabel;
    private android.widget.SeekBar stereoLevel;
    private boolean stereoMeasuring;

    /** The switch, a line saying what this phone's mics gave, and Check Again (once measured). */
    private void addStereoRow(LinearLayout body) {
        stereoSwitch = new android.widget.Switch(a);
        stereoSwitch.setText(R.string.stereo_room);
        a.look.label(stereoSwitch);
        a.look.switchTint(stereoSwitch);
        stereoSwitch.setSwitchPadding(a.dp(16));
        stereoSwitch.setMinHeight(a.dp(52));
        stereoSwitch.setPadding(a.dp(4), a.dp(8), 0, 0);
        stereoSwitch.setOnClickListener(v -> onStereoSwitch(stereoSwitch.isChecked()));
        body.addView(stereoSwitch);
        stereoStatus = new TextView(a);
        a.look.hint(stereoStatus);
        stereoStatus.setPadding(a.dp(4), 0, 0, 0);
        body.addView(stereoStatus);
        stereoLevelRow = new LinearLayout(a);
        stereoLevelRow.setOrientation(LinearLayout.VERTICAL);
        stereoLevelLabel = new TextView(a);
        a.look.label(stereoLevelLabel);
        stereoLevelLabel.setPadding(a.dp(4), a.dp(12), 0, 0);
        stereoLevelRow.addView(stereoLevelLabel);
        stereoLevel = new android.widget.SeekBar(a);
        stereoLevel.setMax(2 * AudioSettings.STEREO_ADJUST_RANGE);
        a.look.slider(stereoLevel);
        stereoLevel.setMinimumHeight(a.dp(40));
        stereoLevel.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar b, int progress, boolean fromUser) {
                int db = progress - AudioSettings.STEREO_ADJUST_RANGE;
                stereoLevelLabel.setText(a.getString(R.string.stereo_level, db));
                stereoLevel.setContentDescription(stereoLevelLabel.getText());
                if (fromUser) {
                    AudioSettings.setStereoAdjust(a, db);
                    AudioService.sendStereoLevel(a);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar b) { }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar b) { }
        });
        stereoLevelRow.addView(stereoLevel);
        TextView levelHint = new TextView(a);
        levelHint.setText(R.string.stereo_level_hint);
        a.look.hint(levelHint);
        levelHint.setPadding(a.dp(4), 0, 0, 0);
        stereoLevelRow.addView(levelHint);
        body.addView(stereoLevelRow);
        LinearLayout row = a.newButtonRow(body);
        stereoCheck = a.addButton(row, R.string.stereo_check_again, this::measureStereo);
        updateStereo();
    }

    private void onStereoSwitch(boolean on) {
        if (on && a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            a.toast(R.string.stereo_needs_mic);
            updateStereo();
            return;
        }
        AudioSettings.setStereoRoom(a, on);
        if (on && AudioSettings.stereoChoice(a) == null) {
            measureStereo();   // the first time: find out what this phone's mics give
            return;
        }
        restartIfRunning();
        updateStereo();
    }

    /**
     * Listens to the room in each way of opening the mic (about 8 s, StereoCalibration). The mic can't be in two
     * places, so a playing sound stops for it and starts again after.
     */
    private void measureStereo() {
        if (stereoMeasuring) {
            return;
        }
        stereoMeasuring = true;
        updateStereo();
        boolean wasRunning = AudioService.isRunning();
        if (wasRunning) {
            a.startService(new Intent(a, AudioService.class).setAction(AudioService.ACTION_STOP));
        }
        stereoStatus.postDelayed(() -> StereoCalibration.run(choice -> {
            AudioSettings.setStereoChoice(a, choice);
            if (!choice.stereo()) {
                AudioSettings.setStereoRoom(a, false);
            }
            stereoMeasuring = false;
            if (wasRunning) {
                a.startAudio();
            }
            updateStereo();
        }), wasRunning ? 600 : 0);   // (the stopped engine lets go of the mic first)
    }

    private void restartIfRunning() {
        if (AudioService.isRunning()) {   // the mic is opened when the engine starts
            a.startForegroundService(new Intent(a, AudioService.class).setAction(AudioService.ACTION_RESTART));
        }
    }

    void updateStereo() {
        if (stereoSwitch == null) {
            return;
        }
        StereoRoom.Choice choice = AudioSettings.stereoChoice(a);
        stereoSwitch.setChecked(AudioSettings.stereoRoom(a));
        stereoSwitch.setEnabled(!stereoMeasuring);
        stereoCheck.setEnabled(!stereoMeasuring);
        ((View) stereoCheck.getParent()).setVisibility(choice != null && !stereoMeasuring ? View.VISIBLE : View.GONE);
        stereoLevelRow.setVisibility(choice != null && choice.stereo() && !stereoMeasuring ? View.VISIBLE : View.GONE);
        stereoLevel.setProgress(AudioSettings.stereoAdjust(a) + AudioSettings.STEREO_ADJUST_RANGE);
        stereoLevelLabel.setText(a.getString(R.string.stereo_level, AudioSettings.stereoAdjust(a)));
        String text;
        if (stereoMeasuring) {
            text = a.getString(R.string.stereo_measuring);
        } else if (choice == null) {
            text = a.getString(R.string.stereo_unmeasured);
        } else if (!choice.stereo()) {
            text = a.getString(R.string.stereo_mono_only);
        } else {
            int mode = choice.preset == StereoRoom.UNPROCESSED ? R.string.stereo_mode_unprocessed
                    : choice.preset == StereoRoom.CAMCORDER ? R.string.stereo_mode_camcorder : R.string.stereo_mode_mic;
            text = a.getString(R.string.stereo_found, a.getString(mode), Math.round(choice.trimDb));
            if (AudioSettings.stereoRoom(a) && !AudioDevices.isPhoneMic(a, AudioDevices.find(a, true))) {
                text += "\n" + a.getString(R.string.stereo_phone_mic_only);
            }
        }
        stereoStatus.setText(text);
    }

    /** An audio device choice: a tile (its name small, the device large) that opens a list sheet of devices. */
    private DevicePicker addDevicePicker(LinearLayout parent, int labelRes, boolean input) {
        LinearLayout tile = new LinearLayout(a);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setBackground(a.look.choiceTile(a.getDrawable(R.drawable.ic_chevron_down).mutate()));
        tile.setMinimumHeight(a.dp(64));
        tile.setPadding(a.dp(16), a.dp(8), a.dp(40), a.dp(8));
        TextView name = new TextView(a);
        name.setText(labelRes);
        a.look.hint(name);
        name.setTextSize(12);
        tile.addView(name);
        TextView value = new TextView(a);
        value.setTypeface(a.look.heading());
        value.setTextSize(15);
        value.setTextColor(a.look.p.text);
        tile.addView(value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, a.dp(12), 0, 0);
        parent.addView(tile, lp);
        DevicePicker picker = new DevicePicker(input, tile, value);
        tile.setOnClickListener(v -> {
            LinearLayout content = new LinearLayout(a);
            content.setOrientation(LinearLayout.VERTICAL);
            // the input list warns before a Bluetooth mic is picked (the hint under the tile only says so after)
            String[] rows = picker.names.clone();
            boolean callMic = false;
            for (int i = 0; picker.input && i < picker.devices.size(); i++) {
                AudioDevices.Device d = picker.devices.get(i);
                if (AudioDevices.isLeAudio(d)) {
                    rows[i + 1] = a.getString(R.string.mic_row_le, rows[i + 1]);
                } else if (AudioDevices.isBluetoothMic(d)) {
                    rows[i + 1] = a.getString(R.string.mic_row_call, rows[i + 1]);
                    callMic = true;
                }
            }
            content.addView(a.sheetHeading(a.getString(labelRes), callMic ? a.getString(R.string.mic_sheet_call) : null));
            a.addListRows(content, rows, picker.selected, null, i -> {
                a.closeSheet();
                String key = i == 0 || i > picker.devices.size()
                        ? AudioSettings.AUTOMATIC : picker.devices.get(i - 1).key;
                if (key.equals(picker.shownKey)) {
                    return;
                }
                picker.shownKey = key;
                picker.selected = i;
                value.setText(picker.names[i]);
                AudioSettings.setDeviceKey(a, picker.input, key);
                updateMicHint();
                updateStereo();   // (the room in stereo only takes the phone's own mic)
                if (AudioService.isRunning()) {
                    a.startForegroundService(new Intent(a, AudioService.class)
                            .setAction(AudioService.ACTION_RESTART));
                }
            });
            a.openSheet(content);
        });
        return picker;
    }

    /** Rebuilds both dropdowns from the devices connected right now. */
    void refreshDevicePickers() {
        fillPicker(inputPicker);
        fillPicker(outputPicker);
        updateMicHint();
        updateStereo();   // (the room in stereo only takes the phone's own mic)
    }

    private void fillPicker(DevicePicker picker) {
        picker.devices = AudioDevices.list(a, picker.input);
        List<String> names = new ArrayList<>();
        names.add(a.getString(picker.input ? R.string.audio_automatic
                : picker.devices.isEmpty() ? R.string.audio_no_headphones
                : R.string.audio_output_automatic));
        for (AudioDevices.Device d : picker.devices) {
            names.add(d.label);
        }
        // A remembered device that isn't plugged in shows as Automatic, but stays remembered.
        String saved = AudioSettings.deviceKey(a, picker.input);
        int selected = 0;
        for (int i = 0; i < picker.devices.size(); i++) {
            if (picker.devices.get(i).key.equals(saved)) {
                selected = i + 1;
            }
        }
        picker.shownKey = selected == 0 ? AudioSettings.AUTOMATIC : saved;
        picker.names = names.toArray(new String[0]);
        picker.selected = selected;
        picker.value.setText(picker.names[selected]);
    }

    /** Setup's Diagnostics: the mic and headphones really in use, the mic level and overloads in the last minute. */
    void addDiagnosticsSection(LinearLayout parent) {
        a.settingRows.addSection(parent, "diagnostics", a.getString(R.string.section_diag), false, body -> {
            diagText = new TextView(a);
            diagText.setTextSize(14);
            diagText.setPadding(0, a.dp(4), 0, a.dp(4));
            body.addView(diagText);
            diagLevel = new android.widget.ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            diagLevel.setMax(80);   // -80 .. 0 dB
            body.addView(diagLevel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, a.dp(10)));
            TextView note = new TextView(a);
            note.setText(R.string.diag_note);
            note.setTextSize(13);
            note.setPadding(0, a.dp(8), 0, 0);
            body.addView(note);
            updateDiagnostics();
        });
    }

    void updateDiagnostics() {
        if (diagText == null || !diagText.isShown()) {
            return;
        }
        boolean on = AudioService.isRunning();
        diagLevel.setVisibility(on ? View.VISIBLE : View.GONE);
        if (!on) {
            diagText.setText(R.string.diag_not_running);
            return;
        }
        List<String> lines = new ArrayList<>();
        String mic = AudioDevices.inputInUse(a);
        String asked = AudioService.inputAsked();
        lines.add(mic != null ? a.getString(R.string.diag_mic, mic)
                : a.getString(R.string.diag_mic_unknown, asked != null ? asked : a.getString(R.string.diag_automatic)));
        if (AudioService.callModeMic()) {
            lines.add("  " + a.getString(R.string.diag_call_mode));
        }
        if (AudioService.dropouts() > 0) {
            lines.add(a.getString(R.string.diag_dropouts, AudioService.dropouts()));
        }
        if (AudioService.isStereo()) {
            lines.add("  " + a.getString(R.string.diag_stereo));
        }
        int hz = AudioService.actualSampleRateHz();
        String out = AudioService.outputLabel() != null ? AudioService.outputLabel() : "?";
        lines.add(hz > 0 ? a.getString(R.string.diag_out_rate, out, hz) : a.getString(R.string.diag_out, out));
        int level = Math.round(Math.max(-80f, Math.min(0f, Modulation.micLevelDb())));
        int gate = Math.round(ParamStore.get("gate_thresh"));
        lines.add(gate > -80 ? a.getString(R.string.diag_level_gate, level, gate) : a.getString(R.string.diag_level, level));
        diagLevel.setProgress(level + 80);
        ClipWatch c = AudioService.clips();
        if (!c.hasReports()) {
            lines.add(a.getString(R.string.diag_scene));
        } else {
            int in = c.episodes(ClipWatch.IN), outs = c.episodes(ClipWatch.OUT);
            lines.add(a.getString(R.string.diag_clip_in, in));
            if (in > 0) {
                lines.add("  " + a.getString(R.string.diag_clip_in_help));
            }
            lines.add(a.getString(R.string.diag_clip_gain, c.episodes(ClipWatch.GAIN)));
            lines.add(a.getString(R.string.diag_handling, AudioService.handlingSkipsLastMinute()));
            lines.add(a.getString(R.string.diag_clip_out, outs));
            if (outs > 0) {
                lines.add("  " + a.getString(R.string.diag_clip_out_help));
            }
        }
        diagText.setText(String.join("\n", lines));
    }
}
