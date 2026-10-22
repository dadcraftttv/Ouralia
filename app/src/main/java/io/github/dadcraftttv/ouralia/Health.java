// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.util.Locale;

/**
 * The event log's health line (Oct 22, ROADMAP "Open now", from DadCraft's long session: adb's logger dies when the
 * cable comes out, so the app writes battery, temperature and its own CPU itself, once a minute while playing).
 * Plain logic: {@link AudioService} reads the phone and writes the line.
 */
final class Health {
    private Health() { }

    /** How often the line is written while the sound plays. */
    static final long EVERY_MS = 60_000;

    /** The app's CPU over a stretch, in percent of one core (100 = one core busy all the time; -1: unknown). */
    static int cpuPercent(long cpuMs, long wallMs) {
        return wallMs <= 0 || cpuMs < 0 ? -1 : (int) Math.round(100.0 * cpuMs / wallMs);
    }

    /** Android's thermal status (PowerManager.THERMAL_STATUS_*) in a word. */
    static String thermal(int status) {
        String[] names = {"none", "light", "moderate", "severe", "critical", "emergency", "shutdown"};
        return status >= 0 && status < names.length ? names[status] : "unknown";
    }

    /**
     * One line: "Health: battery 73% unplugged, 31.2 C, thermal none, app CPU 48% of a core, dropouts 0". A value
     * not known is left out (battery or temperature below 0, cpu -1, thermal null, dropouts below 0).
     */
    static String line(int batteryPct, boolean plugged, float tempC, String thermal, int cpuPct, int dropouts) {
        StringBuilder b = new StringBuilder("Health:");
        if (batteryPct >= 0) {
            b.append(" battery ").append(batteryPct).append(plugged ? "% plugged in," : "% unplugged,");
        }
        if (tempC > 0) {
            b.append(String.format(Locale.US, " %.1f C,", tempC));
        }
        if (thermal != null) {
            b.append(" thermal ").append(thermal).append(',');
        }
        if (cpuPct >= 0) {
            b.append(" app CPU ").append(cpuPct).append("% of a core,");
        }
        if (dropouts >= 0) {
            b.append(" dropouts ").append(dropouts).append(',');
        }
        return b.charAt(b.length() - 1) == ',' ? b.substring(0, b.length() - 1) : b.toString();
    }
}
