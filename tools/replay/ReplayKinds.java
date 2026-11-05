// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.dadcraftttv.ouralia;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replays a session log's sound-kinds judgements through the app's own SoundKinds (Nov 5, the 2026-11-03 review,
 * DadCraft: "go ahead with the replay tool and the time-based windows"), so the cutoffs can be tuned on real places.
 * Run it through tools/replay_kinds.py, which finds the sessions and compiles this; its --help has the options.
 * Reads only the seven kind scores each judgement logged (never audio: the mic never touches disk).
 */
public final class ReplayKinds {
    static final String[] STATES = {"talking", "room music", "water", "birds", "traffic scene", "traffic pass", "unsure"};
    static final Pattern T = Pattern.compile("\"t\":([0-9.]+)"), EV = Pattern.compile("\"ev\":\"(\\w+)\""),
            V = Pattern.compile("\"v\":\\[([^\\]]*)\\]"), MIXED = Pattern.compile("\"sk_mixed\":([0-9.]+)"),
            SET_MIXED = Pattern.compile("\"id\":\"sk_mixed\",\"v\":([0-9.]+)"), MIC = Pattern.compile("\"mic_db\":(-?[0-9.]+)"),
            TEXT = Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** One judgement as logged, or a mark / mixed change / level reading between them. */
    static final class Row {
        long ms;
        float[] v;          // the seven scores (null for the other rows)
        Boolean silent;     // "s":1 (null: an older log that didn't keep it)
        String mark;
        Boolean mixed;
        Float micDb;
    }

    /** What one run gives: time in each state and its changes. */
    static final class Run {
        final long[] onMs = new long[STATES.length];
        final int[] changes = new int[STATES.length];
        final long[] kindMs = new long[SoundKinds.NAMES.length];
        final long[] presentMs = new long[SoundKinds.NAMES.length];
        final List<String> timeline = new ArrayList<>();
    }

    public static void main(String[] args) throws Exception {
        String kindsTxt = null, mixedArg = null;
        double from = 0, to = Double.MAX_VALUE;
        boolean scores = false, heard = false;
        List<String> sets = new ArrayList<>(), files = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--kinds": kindsTxt = args[++i]; break;
                case "--mixed": mixedArg = args[++i]; break;
                case "--from": from = Double.parseDouble(args[++i]); break;
                case "--to": to = Double.parseDouble(args[++i]); break;
                case "--scores": scores = true; break;
                case "--heard": heard = true; break;
                case "--set": sets.add(args[++i]); break;
                default: files.add(args[i]);
            }
        }
        String kinds = new String(Files.readAllBytes(Paths.get(kindsTxt)), StandardCharsets.UTF_8);
        for (String f : files) {
            replay(f, kinds, mixedArg, sets, (long) (from * 1000), to == Double.MAX_VALUE ? Long.MAX_VALUE : (long) (to * 1000), scores,
                    heard);
        }
    }

    static List<Row> read(String file) throws Exception {
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            Matcher ev = EV.matcher(line), t = T.matcher(line);
            if (!ev.find() || !t.find()) {
                continue;
            }
            Row r = new Row();
            r.ms = Math.round(Double.parseDouble(t.group(1)) * 1000);
            switch (ev.group(1)) {
                case "kinds": {
                    Matcher v = V.matcher(line);
                    if (!v.find()) continue;
                    String[] p = v.group(1).split(",");
                    if (p.length != SoundKinds.NAMES.length) continue;
                    r.v = new float[p.length];
                    for (int i = 0; i < p.length; i++) r.v[i] = Float.parseFloat(p[i]);
                    r.silent = line.contains("\"s\":1") ? Boolean.TRUE : null;
                    break;
                }
                case "start": {
                    Matcher m = MIXED.matcher(line);
                    if (!m.find()) continue;
                    r.mixed = Float.parseFloat(m.group(1)) >= 0.5f;
                    break;
                }
                case "set": {
                    Matcher m = SET_MIXED.matcher(line);
                    if (!m.find()) continue;
                    r.mixed = Float.parseFloat(m.group(1)) >= 0.5f;
                    break;
                }
                case "hear": {
                    Matcher m = MIC.matcher(line);
                    if (!m.find()) continue;
                    r.micDb = Float.parseFloat(m.group(1));
                    break;
                }
                case "mark": {
                    Matcher m = TEXT.matcher(line);
                    r.mark = m.find() ? m.group(1) : "?";
                    break;
                }
                default: continue;
            }
            rows.add(r);
        }
        return rows;
    }

    /** Applies "name=value" (see tools/replay_kinds.py --help); false if the name is unknown. */
    static boolean tune(SoundKinds k, String set) {
        String[] p = set.split("=", 2);
        if (p.length != 2) return false;
        String name = p[0].trim().toLowerCase(Locale.US);
        float v = Float.parseFloat(p[1].trim());
        switch (name) {
            case "present_min": Arrays.fill(k.presentMin, v); return true;
            case "present_rel": k.presentRel = v; return true;
            case "quiet_floor": k.quietFloor = v; return true;
            case "strong": k.strong = v; return true;
            case "lead_ratio": k.leadRatio = v; return true;
            default:
        }
        if (name.startsWith("present_min.")) {
            int i = Arrays.asList(SoundKinds.NAMES).indexOf(name.substring(12));
            if (i < 0 || i == SoundKinds.QUIET) return false;
            k.presentMin[i] = v;
            return true;
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        SoundKinds.Window w;
        switch (name.substring(0, dot)) {
            case "talk": w = k.talk; break;
            case "room": w = k.room; break;
            case "water": w = k.waterW; break;
            case "birds": w = k.birdsW; break;
            case "traffic_scene": w = k.trafficW; break;
            default: return false;
        }
        switch (name.substring(dot + 1)) {
            case "on": w.on = v; break;
            case "off": w.off = v; break;
            case "s": w.ms = Math.round(v * 1000); break;
            default: return false;
        }
        w.clear();
        return true;
    }

    static boolean[] states(SoundKinds k) {
        return new boolean[]{k.talking(), k.roomMusic(), k.waterLeads(), k.birdsLead(), k.trafficScene(), k.trafficAllowed(),
                k.unsure()};
    }

    static Run run(List<Row> rows, String kinds, Boolean mixedArg, List<String> sets, long from, long to, boolean guessSilent,
            boolean heard) {
        SoundKinds k = new SoundKinds(kinds);
        for (String s : sets) {
            if (!tune(k, s)) throw new IllegalArgumentException("unknown --set " + s + " (see --help)");
        }
        Run run = new Run();
        boolean[] was = new boolean[STATES.length];
        int wasKind = -1;
        long prev = -1;
        float[] mics = new float[5];   // (the last 5 s of mic levels: the loudest is shown, as the Moto's readings drop out)
        Arrays.fill(mics, Float.NaN);
        int micI = 0;
        SoundKinds.mixed = false;
        for (Row r : rows) {
            if (r.mixed != null) {
                SoundKinds.mixed = mixedArg != null ? mixedArg : r.mixed;
                continue;
            }
            if (mixedArg != null) SoundKinds.mixed = mixedArg;
            if (r.micDb != null) {
                mics[micI++ % mics.length] = r.micDb;
                continue;
            }
            boolean inRange = r.ms >= from && r.ms <= to;
            if (r.mark != null) {
                if (inRange) run.timeline.add(clock(r.ms) + "  mark: " + r.mark);
                continue;
            }
            boolean silent = r.silent != null ? r.silent : guessSilent && quietLeads(r.v);
            k.judgeKinds(r.v, silent, r.ms);
            long step = prev < 0 ? SoundKinds.STEP_MS : Math.max(0, Math.min(SoundKinds.MAX_STEP_MS, r.ms - prev));
            prev = r.ms;
            boolean[] now = states(k);
            float mic = Float.NaN;
            for (float m : mics) if (!Float.isNaN(m) && (Float.isNaN(mic) || m > mic)) mic = m;
            int kind = k.kind();
            if (inRange) {
                if (kind >= 0) run.kindMs[kind] += step;
                for (int i = 0; i < SoundKinds.QUIET; i++) if (k.present(i)) run.presentMs[i] += step;
                for (int i = 0; i < STATES.length; i++) if (now[i]) run.onMs[i] += step;
            }
            for (int i = 0; i < STATES.length; i++) {
                if (now[i] != was[i] && inRange) {
                    run.changes[i]++;
                    if (!heard && i == STATES.length - 1) continue;   // (unsure flips with single judgements: --heard)
                    run.timeline.add(String.format(Locale.US, "%s  %s %s%s", clock(r.ms), STATES[i], now[i] ? "on" : "off",
                            detail(k, i, mic)));
                }
            }
            if (heard && kind != wasKind && inRange && kind >= 0) {
                run.timeline.add(String.format(Locale.US, "%s  heard: %s %.2f", clock(r.ms), SoundKinds.NAMES[kind], r.v[kind]));
            }
            was = now;
            wasKind = kind;
        }
        SoundKinds.mixed = false;
        return run;
    }

    static String detail(SoundKinds k, int state, float mic) {
        SoundKinds.Window w = state == 0 ? k.talk : state == 1 ? k.room : state == 2 ? k.waterW : state == 3 ? k.birdsW
                : state == 4 ? k.trafficW : null;
        String s = w == null ? "" : String.format(Locale.US, " (%.0f%% of %d s)", 100 * w.share(), w.ms / 1000);
        return Float.isNaN(mic) ? s : s + String.format(Locale.US, ", mic up to %.0f dB lately", mic);
    }

    /** For logs from before Nov 5, which didn't keep YAMNet's top class: the quiet group (Silence) leading every kind. */
    static boolean quietLeads(float[] v) {
        for (int i = 0; i < SoundKinds.QUIET; i++) if (v[i] >= v[SoundKinds.QUIET]) return false;
        return true;
    }

    static void replay(String file, String kinds, String mixedArg, List<String> sets, long from, long to, boolean showScores,
            boolean heard) throws Exception {
        List<Row> rows = read(file);
        List<Row> judgements = new ArrayList<>();
        Boolean logMixed = null;
        boolean silentLogged = false;
        for (Row r : rows) {
            if (r.v != null) {
                judgements.add(r);
                silentLogged |= r.silent != null;
            }
            if (r.mixed != null && logMixed == null) logMixed = r.mixed;
        }
        String name = Paths.get(file).getFileName().toString();
        if (judgements.isEmpty()) {
            System.out.println(name + ": no sound-kinds scores (they are logged since Nov 4, with Session Log on)");
            return;
        }
        Boolean mixed = mixedArg == null ? null : mixedArg.equals("on");
        long first = judgements.get(0).ms, end = judgements.get(judgements.size() - 1).ms;
        long[] steps = new long[judgements.size() - 1];
        int over = 0;
        long longest = 0, longestAt = 0;
        for (int i = 1; i < judgements.size(); i++) {
            steps[i - 1] = judgements.get(i).ms - judgements.get(i - 1).ms;
            if (steps[i - 1] > 500) over++;
            if (steps[i - 1] > longest) {
                longest = steps[i - 1];
                longestAt = judgements.get(i).ms;
            }
        }
        long[] sorted = steps.clone();
        Arrays.sort(sorted);
        System.out.printf(Locale.US, "%s: %s of judgements from %s, %d judgements, Mixed Surroundings %s%n", name,
                clock(end - first), clock(first), judgements.size(),
                mixed != null ? (mixed ? "on (--mixed)" : "off (--mixed)") : logMixed == null ? "off (not in the log)"
                        : (logMixed ? "on" : "off") + " (from the log)");
        if (steps.length > 0) {
            System.out.printf(Locale.US, "  steps: median %d ms; %d over 0.5 s, longest %.1f s at %s%n", sorted[steps.length / 2],
                    over, longest / 1000.0, clock(longestAt));
        }
        if (!silentLogged) {
            System.out.println("  (an older log without YAMNet's silence flag: guessed from the quiet group leading)");
        }
        // the scores, per kind
        long span = 0;
        List<float[]> inRange = new ArrayList<>();
        for (Row r : judgements) if (r.ms >= from && r.ms <= to) inRange.add(r.v);
        Run base = run(rows, kinds, mixed, new ArrayList<>(), from, to, !silentLogged, heard);
        Run tuned = sets.isEmpty() ? null : run(rows, kinds, mixed, sets, from, to, !silentLogged, heard);
        for (long m : base.kindMs) span += m;
        System.out.println("  kind     median   p90   max   clearly there" + (tuned != null ? " (tuned)" : "") + "   heard as the kind");
        for (int k = 0; k < SoundKinds.NAMES.length; k++) {
            float[] col = new float[inRange.size()];
            for (int i = 0; i < col.length; i++) col[i] = inRange.get(i)[k];
            Arrays.sort(col);
            String present = k == SoundKinds.QUIET ? "-" : pct(base.presentMs[k], span)
                    + (tuned != null ? " -> " + pct(tuned.presentMs[k], span) : "");
            System.out.printf(Locale.US, "  %-8s %5.2f  %5.2f  %5.2f   %-18s %s%n", SoundKinds.NAMES[k], q(col, 0.5), q(col, 0.9),
                    q(col, 1), present, pct(base.kindMs[k], span) + (tuned != null ? " -> " + pct(tuned.kindMs[k], span) : ""));
        }
        System.out.println("  state           time on   changes" + (tuned != null ? "     tuned: time on   changes" : ""));
        for (int i = 0; i < STATES.length; i++) {
            System.out.printf(Locale.US, "  %-15s %7s   %4d%s%n", STATES[i], clock(base.onMs[i]), base.changes[i],
                    tuned == null ? "" : String.format(Locale.US, "            %7s   %4d", clock(tuned.onMs[i]), tuned.changes[i]));
        }
        if (tuned != null) System.out.println("  tuned: " + String.join(" ", sets));
        Run shown = tuned != null ? tuned : base;
        System.out.println("  timeline" + (tuned != null ? " (tuned)" : "") + (heard ? "" : " (scene windows and marks; --heard adds"
                + " each change of the heard kind and unsure)") + ":");
        int cap = 200;
        for (int i = 0; i < Math.min(cap, shown.timeline.size()); i++) System.out.println("    " + shown.timeline.get(i));
        if (shown.timeline.size() > cap) {
            System.out.printf("    ... %d more (narrow it with --from/--to)%n", shown.timeline.size() - cap);
        }
        if (showScores) scoresPerSecond(judgements, from, to);
    }

    /** --scores: each second's mean score per kind, for a short stretch (at most 120 lines). */
    static void scoresPerSecond(List<Row> judgements, long from, long to) {
        System.out.println("  second  " + String.join(" ", Arrays.stream(SoundKinds.NAMES).map(n -> String.format("%6.6s", n))
                .toArray(String[]::new)));
        long sec = -1;
        float[] sum = new float[SoundKinds.NAMES.length];
        int n = 0, lines = 0;
        for (Row r : judgements) {
            if (r.ms < from || r.ms > to) continue;
            long s = r.ms / 1000;
            if (s != sec && n > 0) {
                if (lines++ >= 120) {
                    System.out.println("  ... (narrow it with --from/--to)");
                    return;
                }
                printSecond(sec, sum, n);
                Arrays.fill(sum, 0);
                n = 0;
            }
            sec = s;
            for (int k = 0; k < sum.length; k++) sum[k] += r.v[k];
            n++;
        }
        if (n > 0) printSecond(sec, sum, n);
    }

    static void printSecond(long sec, float[] sum, int n) {
        StringBuilder b = new StringBuilder(String.format(Locale.US, "  %6s  ", clock(sec * 1000)));
        for (float s : sum) b.append(String.format(Locale.US, "%6.2f ", s / n));
        System.out.println(b.toString().replaceAll("\\s+$", ""));
    }

    static float q(float[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.min(sorted.length - 1, Math.floor(p * sorted.length))];
    }

    static String pct(long ms, long span) {
        return span == 0 ? "0%" : Math.round(100.0 * ms / span) + "%";
    }

    static String clock(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
