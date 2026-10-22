// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Setup's About card (moved out of MainActivity on Oct 22, ROADMAP 22): the licence line and the source link,
 * the phone's sensors, Send feedback (an email to DadCraft, the end of the event log if the tester agrees) and the
 * licence texts the GPL asks the app to carry.
 */
final class AboutUi {
    private final MainActivity a;

    AboutUi(MainActivity activity) {
        a = activity;
    }

    void addAboutSection(LinearLayout parent) {
        LinearLayout body = a.settingRows.addSection(parent, "about", a.getString(R.string.section_about), false);
        TextView text = new TextView(a);
        text.setTextSize(14);
        String source = a.getString(R.string.source_url);
        text.setText(source.isEmpty() ? a.getString(R.string.about_text)
                : a.getString(R.string.about_text_with_source, source));
        text.setPadding(0, 0, 0, a.dp(8));
        body.addView(text);
        TextView sensorLine = new TextView(a);
        sensorLine.setTextSize(12);
        sensorLine.setText(a.getString(R.string.sensors_line, SensorInput.describe(a)));
        sensorLine.setPadding(0, 0, 0, a.dp(8));
        body.addView(sensorLine);
        TextView feedback = new TextView(a);
        feedback.setTextSize(14);
        feedback.setAutoLinkMask(android.text.util.Linkify.EMAIL_ADDRESSES);
        feedback.setText(R.string.about_feedback);
        feedback.setPadding(0, 0, 0, a.dp(8));
        body.addView(feedback);
        LinearLayout row = a.newButtonRow(body);
        a.addButton(row, R.string.show_licenses, this::showLicenses);
        a.addButton(row, R.string.walk_show_again, a::showWalkthrough);
        LinearLayout row2 = a.newButtonRow(body);
        a.addButton(row2, R.string.feedback_send, this::sendFeedback);
    }

    /**
     * Opens an email to DadCraft with the phone, Android and app version filled in and, if the tester agrees, the
     * end of the event log (no audio: times, room levels, what the app did), so a report carries what's needed.
     */
    private void sendFeedback() {
        CheckBox withLog = new CheckBox(a);
        withLog.setText(R.string.feedback_with_log);
        withLog.setChecked(true);
        TextView note = new TextView(a);
        note.setText(R.string.feedback_log_note);
        note.setTextSize(13);
        note.setPadding(0, a.dp(8), 0, 0);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(a.dp(20), a.dp(8), a.dp(20), 0);
        box.addView(withLog);
        box.addView(note);
        a.new LookDialog().setTitle(R.string.feedback_send).setView(box)
                .setPositiveButton(R.string.feedback_write, (d, w) -> {
                    String version = "?";
                    try {
                        version = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // keep "?"
                    }
                    StringBuilder body = new StringBuilder(a.getString(R.string.feedback_body_start)).append("\n\n\n");
                    body.append("---\n").append(a.getString(R.string.feedback_device, Build.MANUFACTURER + " " + Build.MODEL,
                            Build.VERSION.RELEASE, version)).append("\n");
                    SceneLibrary.Scene scene = SceneSettings.current(a);
                    body.append(a.getString(R.string.feedback_playing, scene != null ? scene.name
                            : a.getString(R.string.scene_built_in))).append("\n");
                    if (withLog.isChecked()) {
                        body.append("\n").append(a.getString(R.string.feedback_log_heading)).append("\n").append(eventLogTail(200));
                    }
                    Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                            .putExtra(Intent.EXTRA_EMAIL, new String[]{a.getString(R.string.feedback_address)})
                            .putExtra(Intent.EXTRA_SUBJECT, a.getString(R.string.feedback_subject, version))
                            .putExtra(Intent.EXTRA_TEXT, body.toString());
                    try {
                        a.startActivity(mail);
                    } catch (android.content.ActivityNotFoundException e) {
                        a.toast(R.string.feedback_no_mail, a.getString(R.string.feedback_address));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** The last {@code lines} lines of the event log (the older file first, if it rolled over). */
    private String eventLogTail(int lines) {
        java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
        for (String name : new String[]{"events.log.1", "events.log"}) {
            File f = new File(a.getFilesDir(), name);
            if (!f.isFile()) {
                continue;
            }
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String l;
                while ((l = r.readLine()) != null) {
                    tail.addLast(l);
                    if (tail.size() > lines) {
                        tail.removeFirst();
                    }
                }
            } catch (IOException ignored) {
                // a missing or unreadable log just leaves less to send
            }
        }
        return tail.isEmpty() ? a.getString(R.string.feedback_log_empty) : String.join("\n", tail);
    }

    /** Licence texts shipped inside the app (assets/licenses), as the GPL requires. */
    private void showLicenses() {
        String[][] docs = {
                {"Notice", "NOTICE.txt"},
                {"GNU GPL v3", "GPL-3.0.txt"},
                {"RjDj library (rjlib)", "rjlib-LICENSE.txt"},
                {"Pure Data (BSD)", "pure-data-LICENSE.txt"},
                {"PdParty helper objects (BSD)", "pdparty-LICENSE.txt"},
                {"Oboe and libpd (Apache 2.0)", "Apache-2.0.txt"},
                {"Fonts: Sora, Inter, JetBrains Mono (OFL)", "fonts-OFL.txt"},
        };
        String[] names = new String[docs.length];
        for (int i = 0; i < docs.length; i++) {
            names[i] = docs[i][0];
        }
        a.new LookDialog()
                .setTitle(R.string.show_licenses)
                .setItems(names, (d, which) -> showAssetText(docs[which][0], "licenses/" + docs[which][1]))
                .setNegativeButton(R.string.close, null)
                .show();
    }

    private void showAssetText(String title, String assetPath) {
        String text;
        try (InputStream in = a.getAssets().open(assetPath)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bytes.write(buf, 0, n);
            }
            text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            text = a.getString(R.string.err_open_file);
        }
        TextView view = new TextView(a);
        view.setText(text);
        view.setTextSize(12);
        view.setTypeface(Typeface.MONOSPACE);
        view.setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8));
        view.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(view);
        a.new LookDialog()
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }
}
