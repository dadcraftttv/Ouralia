/* dcs_composer: the chords of the Evolving music (DadCraft, Oct 21: with a key and scale he likes, "the band keeps
 * playing much of the same things"). The Classic pad picks a chord near the room's brightness every 7 s, with no
 * memory, so a steady room circles the same two or three chords. This plays progressions instead, in sections:
 *
 *  - Chords: every chord of the scale that isn't diminished. Pentatonic scales borrow the chords of the full scale
 *    they come from (minor pentatonic: natural minor, major pentatonic: major); the melody stays pentatonic, the
 *    patch only plays the pad and bass from here. Whole tone has only its two augmented chords.
 *  - Progressions: each chord leads to the next by weighted moves (a table per mode, with the mode's own colour
 *    chord favoured so Dorian stays Dorian), in phrases of four chords that end on a cadence.
 *  - Sections: Home (from and back to the home chord), Lift (starts on the mode's lifting chord, ends open, the
 *    melody a little higher), Calm (slower chords over the home note in the bass, the melody lower, open voicing)
 *    and Turn (starts away from home). Home comes back between most of the others. A section is remembered: when
 *    it comes back its phrase returns with a chord or two changed, and a section's second phrase is often its first
 *    again.
 *  - New pieces (`renew 1`, DadCraft, Oct 29: the sessions were one piece for 18 minutes): after a few minutes the
 *    piece hands over to a new one the way a DJ blends two songs. The outgoing section's last phrase ends open and
 *    its drums drop to the hats; the new piece's chords come in under the old groove and melody; a phrase later the
 *    groove (drums, walking notes, melody rhythm) is new, and a phrase after that the motif.
 *  - Chords change on the bar (`bar`, from the clock's tick 0), each lasting the bars nearest the section's pace;
 *    without bars (no clock) it keeps its own bar time from the tempo.
 *  - Voicing: each chord is voiced (inversion and octave) closest to the last one, so the pad moves smoothly.
 *
 * Melodies that develop (stage 2, the chimes' notes when `mel_dev` is on): each kind of section has a motif of 3-6
 * notes (mostly steps, a leap now and then). A burst of notes plays it in statements, like a theme and its
 * variations: the motif, then it again or answered (ending on the chord's root), then developed (moved up or down a
 * step or two, turned upside down, played backwards, cut short, or carried on a note further), then the motif
 * back; and round again. A statement starts on the chord tone nearest where the melody sits now, and that place
 * travels a little with each development. A pause (GAP_MS) ends a statement: the next note starts a new one. When
 * a section comes back, so does its motif, a step changed now and then.
 *
 * Patterns made by rules (stage 3, `pat_gen`): the walking notes and the drums get a bar of their own each bar
 * instead of one from the patch's short lists. Drums: hits spread evenly over the bar ("Euclidean": k hits in 16,
 * as even as they fall), as many kicks and hats as the drive (pace or the room) asks, the snare on the backbeat with
 * ghost notes, each section leaning it (Calm sparser, Lift busier). The walking notes: an 8-step figure of the chord's
 * tones (rising, falling, rise and fall, broken, or around the root) wider with the pace, a passing note now and
 * then. Both keep most of the bar each time and change one thing; every 4th bar is the section's own pattern again
 * (the walking figure now and then backwards). A section that comes back brings its patterns back. With Melody
 * rhythm on, its one-bar rhythm too (`cell <drive> <style 1 straight, 2 dotted, 3 mixed>`): as many notes as the
 * drive asks, spread evenly (straight on the 8ths; dotted on the 16ths, which gives 3+3+2 feels, now and then a
 * late start), each bar one note moved, split or merged.
 *
 * Chord colours (`color 1`): now and then a chord gets its seventh (in place of the doubled root), an added ninth, or
 * is sus4 / sus2 (the fourth or second in place of the third) and resolves to the plain chord at the next bar; how
 * often leans on the section (Calm the lushest, Lift the most sus). Every note is from the chords' own scale.
 *
 * Messages: scale <0-7> (Major, Minor, Major pent., Minor pent., Dorian, Mixolydian, Lydian, Whole tone: a new piece,
 * home chord now), tempo <bpm>, bright <0-1>, energy <0-1> (both lean the choice of section), bar, start (the chord
 * now), again (the chord again: the key changed), seed <n>, color <0|1>, renew <0|1>.
 * note <middle> <chord> <n>: the next melody note, as a scale step (middle: where the melody would sit, chord: the
 * chord's scale step, n: notes in the scale).
 * Outlets, left to right: a list per chord: anchor (the scale step that fits it best, for the parts that think in
 * steps), bass (semitones above the key note, 0-11), then four pad notes (semitones from the key note, about
 * -5..+28); a list when a section starts: kind (0 Home, 1 Lift, 2 Calm, 3 Turn), melody register (scale steps); the
 * melody note (a float); a list when a statement starts: its form (0 the motif, 1 again, 2 answered, 3 moved,
 * 4 upside down, 5 backwards, 6 cut short, 7 carried on), its first note; per `drums <drive 0-1> <break 0-1>`
 * three lists, lane (0 kick, 1 snare, 2 hat) then 16 velocities (a hat from 2 up is open: velocity + 2); per
 * `arp <pace 0-1> <n>` "arp" and 8 scale steps above the chord's root (the drum lists come as "drum ..."); per
 * `cell` "cell", 16 note lengths in 16ths at their onsets (0 elsewhere), the number of notes, the last onset.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "m_pd.h"
#include <math.h>
#include <stdlib.h>
#include <time.h>

static t_class *dcs_composer_class;

#define NSCALES 8
#define PLEN 4                     /* chords in a phrase */
#define PIECE_BARS 64              /* a piece lasts at least this many bars (and up to half as many again) with renew */
enum { HOME, LIFT, CALM, TURN, NKINDS };

/* the scales as the patch has them (make_patch.py SCALES), and the mode each one's chords come from */
static const int SCALE_N[NSCALES] = {7, 7, 5, 5, 7, 7, 7, 6};
static const int SCALE_IV[NSCALES][7] = {
    {0, 2, 4, 5, 7, 9, 11}, {0, 2, 3, 5, 7, 8, 10}, {0, 2, 4, 7, 9}, {0, 3, 5, 7, 10},
    {0, 2, 3, 5, 7, 9, 10}, {0, 2, 4, 5, 7, 9, 10}, {0, 2, 4, 6, 7, 9, 11}, {0, 2, 4, 6, 8, 10}};
enum { IONIAN, AEOLIAN, DORIAN, MIXO, LYDIAN, WHOLE };
static const int PARENT[NSCALES] = {IONIAN, AEOLIAN, IONIAN, AEOLIAN, DORIAN, MIXO, LYDIAN, WHOLE};

typedef struct {
    int n;                  /* degrees */
    int iv[7];              /* semitones */
    int ok[7];              /* chords used (no diminished) */
    int w[7][7];            /* how likely each chord moves to each other one */
    int lift[3], calm[3], open[3];   /* a Lift's first chords, a Calm's, the open endings (a Lift's last chord) */
} t_mode;

/* Degrees 0-6 (I ii iii IV V vi vii in major). The weights follow common practice and pop: in major I goes to IV or
 * V most, ii to V, vi to IV or ii; each mode leans on its colour chord: Dorian's IV, Mixolydian's bVII, Lydian's II.
 * -1 ends a short list. */
static const t_mode MODES[6] = {
    {7, {0, 2, 4, 5, 7, 9, 11}, {1, 1, 1, 1, 1, 1, 0},
     {{0, 2, 1, 4, 4, 3, 0}, {1, 0, 0, 1, 5, 1, 0}, {0, 1, 0, 3, 0, 4, 0}, {3, 2, 0, 0, 4, 2, 0},
      {5, 0, 0, 2, 0, 3, 0}, {1, 3, 1, 4, 2, 0, 0}, {1, 0, 0, 0, 0, 0, 0}},
     {3, 1, -1}, {5, -1, -1}, {4, -1, -1}},
    {7, {0, 2, 3, 5, 7, 8, 10}, {1, 0, 1, 1, 1, 1, 1},
     {{0, 0, 2, 3, 2, 4, 3}, {1, 0, 0, 0, 0, 0, 0}, {1, 0, 0, 3, 0, 3, 2}, {3, 0, 0, 0, 2, 1, 3},
      {4, 0, 0, 1, 0, 3, 0}, {2, 0, 2, 2, 0, 0, 4}, {4, 0, 3, 0, 0, 2, 0}},
     {5, 3, -1}, {2, -1, -1}, {4, 6, -1}},
    {7, {0, 2, 3, 5, 7, 9, 10}, {1, 1, 1, 1, 1, 0, 1},
     {{0, 2, 2, 5, 2, 0, 3}, {2, 0, 2, 2, 2, 0, 0}, {0, 2, 0, 3, 0, 0, 2}, {5, 0, 0, 0, 2, 0, 2},
      {3, 0, 0, 3, 0, 0, 2}, {1, 0, 0, 0, 0, 0, 0}, {3, 0, 2, 3, 0, 0, 0}},
     {3, -1, -1}, {2, 6, -1}, {3, 4, -1}},
    {7, {0, 2, 4, 5, 7, 9, 10}, {1, 1, 0, 1, 1, 1, 1},
     {{0, 1, 0, 4, 2, 2, 5}, {2, 0, 0, 2, 2, 0, 2}, {1, 0, 0, 0, 0, 0, 0}, {4, 0, 0, 0, 2, 0, 3},
      {3, 0, 0, 3, 0, 0, 2}, {0, 2, 0, 3, 0, 0, 3}, {5, 0, 0, 3, 1, 0, 0}},
     {3, 6, -1}, {5, 4, -1}, {6, -1, -1}},
    {7, {0, 2, 4, 6, 7, 9, 11}, {1, 1, 1, 0, 1, 1, 1},
     {{0, 5, 1, 0, 2, 2, 2}, {5, 0, 2, 0, 0, 0, 2}, {2, 2, 0, 0, 0, 3, 0}, {1, 0, 0, 0, 0, 0, 0},
      {3, 3, 0, 0, 0, 2, 0}, {2, 3, 0, 0, 2, 0, 1}, {3, 2, 0, 0, 0, 2, 0}},
     {1, -1, -1}, {5, 2, -1}, {1, -1, -1}},
    {6, {0, 2, 4, 6, 8, 10}, {1, 1, 1, 1, 1, 1, 0},
     {{0, 2, 2, 2, 2, 2, 0}, {2, 0, 2, 2, 2, 2, 0}, {2, 2, 0, 2, 2, 2, 0}, {2, 2, 2, 0, 2, 2, 0},
      {2, 2, 2, 2, 0, 2, 0}, {2, 2, 2, 2, 2, 0, 0}, {0}},
     {1, 2, -1}, {3, -1, -1}, {3, -1, -1}}};

/* per kind of section: seconds a chord lasts (rounded to whole bars), melody register (scale steps), bass on the
 * chord's root (percent; else the home note), open voicing */
static const float KIND_SECS[NKINDS] = {6.5f, 4.5f, 9.0f, 5.5f};
static const int KIND_REG[NKINDS] = {0, 2, -2, 1};
static const int KIND_BASS[NKINDS] = {65, 100, 0, 100};
static const int KIND_OPEN[NKINDS] = {0, 0, 1, 0};
/* chord colours per section, percent: seventh, added ninth, sus (the rest plain) */
static const int KIND_COLOR[NKINDS][3] = {{25, 10, 10}, {20, 10, 30}, {30, 40, 0}, {35, 10, 15}};

#define MOTIF_MAX 8
#define GAP_MS 2200        /* a pause this long ends a statement */
#define ROAM 4             /* how far (scale steps) the statements travel from the melody's middle */
enum { F_MOTIF, F_AGAIN, F_ANSWER, F_MOVED, F_INVERT, F_BACK, F_SHORT, F_LONGER };

typedef struct {
    int n;
    int iv[MOTIF_MAX];     /* each note in scale steps from the first */
} t_motif;

/* drums: per section the drive moves this much (Calm sparser, Lift busier) */
static const float KIND_DRIVE[NKINDS] = {0.0f, 0.15f, -0.25f, 0.05f};
#define ARP_LEN 8

typedef struct {
    t_object x_obj;
    t_outlet *out_sect;
    t_clock *watch;
    unsigned int rng;
    int scale;
    float bpm, bright, energy;
    /* the form */
    int have[NKINDS], kept[NKINDS][PLEN];   /* each kind's phrase, kept for when it comes back */
    int kind, last_away, phrases_left;
    int prog[PLEN], pos, bars_left;
    int started;
    /* the sound now */
    int deg, voice[4], voiced, bass;
    /* the melody */
    t_outlet *out_mel, *out_form;
    t_motif motif[NKINDS], stmt;
    int have_motif[NKINDS], mel_kind, stmt_k, stmt_pos, stmt_base, stmt_on, drift;
    double last_note;
    /* patterns made by rules */
    t_outlet *out_pat;
    float drum_base[NKINDS][3][16], drum_cur[3][16], drum_d[NKINDS];
    int drum_have[NKINDS], drum_kind, drum_bars;
    int arp_base[NKINDS][ARP_LEN], arp_cur[ARP_LEN], arp_have[NKINDS], arp_kind, arp_bars, arp_n;
    float arp_pace[NKINDS];
    int cell_base[NKINDS][16], cell_cur[16], cell_have[NKINDS], cell_style[NKINDS], cell_kind, cell_bars;
    float cell_d[NKINDS];
    /* chord colours */
    int color, sus_voice, sus_third;   /* a sus chord's voice and the third it resolves to (-1: none pending) */
    /* new pieces: bars until the next, the open ending under way, and the handover's phrases (0: none) */
    int renew, piece_bars, leadin, handover;
} t_dcs_composer;

static int rnd(t_dcs_composer *x, int n)   /* 0..n-1 */
{
    x->rng = x->rng * 1664525u + 1013904223u;
    return n > 0 ? (int)((x->rng >> 8) % (unsigned)n) : 0;
}

static const t_mode *mode(t_dcs_composer *x) { return &MODES[PARENT[x->scale]]; }

/* a chord from a short list (-1 ends it), skipping one */
static int pick_of(t_dcs_composer *x, const int *list, int avoid)
{
    int c[3], n = 0;
    for (int i = 0; i < 3 && list[i] >= 0; i++)
        if (list[i] != avoid)
            c[n++] = list[i];
    return n ? c[rnd(x, n)] : list[0];
}

/* the next chord after `from`, weighted by the mode's moves (and, if to >= 0, by how well it leads into `to`) */
static int step(t_dcs_composer *x, int from, int to, int avoid)
{
    const t_mode *m = mode(x);
    int w[7], sum = 0;
    for (int d = 0; d < m->n; d++) {
        w[d] = (m->ok[d] && d != from && d != avoid) ? m->w[from][d] * (to >= 0 ? m->w[d][to] + 1 : 1) : 0;
        if (to >= 0 && d == to)
            w[d] = 0;
        sum += w[d];
    }
    if (sum == 0) {   /* nothing leads there: any other chord */
        for (int d = 0; d < m->n; d++)
            sum += (w[d] = (m->ok[d] && d != from && d != to) ? 1 : 0);
        if (sum == 0)
            return from;
    }
    int r = rnd(x, sum);
    for (int d = 0; d < m->n; d++)
        if ((r -= w[d]) < 0)
            return d;
    return from;
}

static int other_chord(t_dcs_composer *x, int avoid)
{
    const t_mode *m = mode(x);
    int c[7], n = 0;
    for (int d = 0; d < m->n; d++)
        if (m->ok[d] && d != avoid)
            c[n++] = d;
    return n ? c[rnd(x, n)] : 0;
}

/* a new phrase for a kind of section: its first chord, two that move, and its ending */
static void make_phrase(t_dcs_composer *x, int kind, int *p)
{
    const t_mode *m = mode(x);
    int first, last;
    switch (kind) {
    case LIFT: first = pick_of(x, m->lift, -1); last = pick_of(x, m->open, first); break;
    case CALM: first = pick_of(x, m->calm, -1); last = rnd(x, 10) < 6 ? 0 : first; break;
    case TURN: first = other_chord(x, 0); last = rnd(x, 2) ? 0 : pick_of(x, m->calm, first); break;
    default: first = 0; last = 0;
    }
    p[0] = first;
    p[1] = step(x, first, -1, -1);
    p[2] = step(x, p[1], last, -1);   /* (never p[1] again, never the ending early) */
    p[3] = last;
}

/* the same phrase with a chord in the middle changed (and sometimes Lift's ending) */
static void vary_phrase(t_dcs_composer *x, int kind, int *p)
{
    int i = 1 + rnd(x, PLEN - 2);
    p[i] = step(x, p[i - 1], p[i + 1], -1);
    if (kind == LIFT && rnd(x, 4) == 0)
        p[PLEN - 1] = pick_of(x, mode(x)->open, p[PLEN - 2]);
}

static void begin_section(t_dcs_composer *x, int kind)
{
    x->kind = kind;
    x->phrases_left = 2 + (kind != HOME && rnd(x, 3) == 0);
    if (x->have[kind]) {            /* it comes back, a little changed */
        for (int i = 0; i < PLEN; i++)
            x->prog[i] = x->kept[kind][i];
        vary_phrase(x, kind, x->prog);
    } else {
        make_phrase(x, kind, x->prog);
        x->have[kind] = 1;
    }
    for (int i = 0; i < PLEN; i++)
        x->kept[kind][i] = x->prog[i];
    x->pos = 0;
    t_atom a[2];
    SETFLOAT(a, kind);
    SETFLOAT(a + 1, KIND_REG[kind]);
    outlet_list(x->out_sect, &s_list, 2, a);
}

/* Home comes back between most of the others (with renew half the time: one journey can lead into another); which
 * other one leans on the room (energy and brightness: Lift, quiet: Calm) */
static int next_kind(t_dcs_composer *x)
{
    if (x->kind != HOME && rnd(x, 10) < (x->renew ? 5 : 7))
        return HOME;
    float w[NKINDS] = {0, 1.0f + x->energy + 0.5f * x->bright, 1.0f + (1 - x->energy), 0.7f};
    w[x->kind] = 0;
    if (x->last_away != x->kind)
        w[x->last_away] *= 0.4f;     /* rather not the same journey twice in a row */
    float sum = w[1] + w[2] + w[3], r = sum * rnd(x, 1000) / 1000.0f;
    for (int k = 1; k < NKINDS; k++)
        if ((r -= w[k]) < 0)
            return k;
    return TURN;
}

static int bar_ms(t_dcs_composer *x) { return (int)(240000.0f / (x->bpm > 30 ? x->bpm : 100)); }

static int chord_bars(t_dcs_composer *x)
{
    int b = (int)(KIND_SECS[x->kind] * 1000 / bar_ms(x) + 0.5f);
    b = b < 1 ? 1 : b > 4 ? 4 : b;
    if (x->pos == PLEN - 1 && rnd(x, 100) < 35)
        b++;                        /* a cadence held a little longer */
    return b;
}

static int pc(const t_mode *m, int d)   /* semitones of a degree, over octaves */
{
    return m->iv[d % m->n] + 12 * (d / m->n);
}

/* the pad: the chord's three notes voiced closest to the last chord (root position at first), the root doubled an
 * octave up; Calm spreads it (the middle note an octave up) */
static void voice_chord(t_dcs_composer *x)
{
    const t_mode *m = mode(x);
    int t[3] = {pc(m, x->deg) % 12, pc(m, x->deg + 2) % 12, pc(m, x->deg + 4) % 12};
    int best[3] = {0, 0, 0}, bestcost = 1 << 30;
    for (int inv = 0; inv < 3; inv++)
        for (int base = -5; base <= 8; base++) {
            if ((base + 120) % 12 != t[inv])
                continue;
            int v[3];
            v[0] = base;
            v[1] = base + (t[(inv + 1) % 3] - t[inv] + 12) % 12;
            v[2] = v[1] + (t[(inv + 2) % 3] - t[(inv + 1) % 3] + 12) % 12;
            if (KIND_OPEN[x->kind]) {   /* drop the middle note an octave higher */
                int mid = v[1];
                v[1] = v[2];
                v[2] = mid + 12;
            }
            int cost = (base < -2 ? -2 - base : base > 5 ? base - 5 : 0) * 3 + (v[2] > 19 ? v[2] - 19 : 0) * 3;
            if (x->voiced)
                for (int i = 0; i < 3; i++)
                    cost += abs(v[i] - x->voice[i]);
            else
                cost += inv * 4;
            if (cost < bestcost) {
                bestcost = cost;
                for (int i = 0; i < 3; i++)
                    best[i] = v[i];
            }
        }
    for (int i = 0; i < 3; i++)
        x->voice[i] = best[i];
    int root = best[0];
    for (int i = 0; i < 3; i++)
        if ((best[i] + 120) % 12 == t[0]) {
            root = best[i];
            break;
        }
    x->voice[3] = root + 12;
    x->voiced = 1;
    x->bass = rnd(x, 100) < KIND_BASS[x->kind] ? t[0] : 0;
    x->sus_voice = -1;
    if (x->color && PARENT[x->scale] != WHOLE) {
        const int *c = KIND_COLOR[x->kind];
        int r = rnd(x, 100);
        if (r < c[0])                                          /* its seventh, a little below the doubled root */
            x->voice[3] = root + (pc(m, x->deg + 6) - pc(m, x->deg) + 24) % 12;
        else if (r < c[0] + c[1])                              /* an added ninth */
            x->voice[3] = root + 12 + (pc(m, x->deg + 1) - pc(m, x->deg) + 24) % 12;
        else if (r < c[0] + c[1] + c[2]) {                     /* sus4 or sus2, resolving at the next bar */
            int four = (pc(m, x->deg + 3) - pc(m, x->deg) + 24) % 12, two = (pc(m, x->deg + 1) - pc(m, x->deg) + 24) % 12;
            int up = four == 5 && (two != 2 || rnd(x, 3)) ? 1 : two == 2 ? -1 : 0;
            for (int i = 0; i < 3 && up; i++)
                if ((x->voice[i] + 120) % 12 == t[1]) {
                    x->sus_voice = i;
                    x->sus_third = x->voice[i];
                    int third = (t[1] - t[0] + 12) % 12;       /* 3 or 4 semitones above the root */
                    x->voice[i] += up > 0 ? four - third : two - third;
                    break;
                }
        }
    }
}

/* the step of the chosen scale that fits the chord best: its root, else the most notes shared */
static int anchor(t_dcs_composer *x)
{
    const t_mode *m = mode(x);
    int n = SCALE_N[x->scale];
    if (n == m->n) {   /* the chords' own scale: the degree itself */
        int same = 1;
        for (int i = 0; i < n; i++)
            same &= SCALE_IV[x->scale][i] == m->iv[i];
        if (same)
            return x->deg;
    }
    int t[3] = {pc(m, x->deg) % 12, pc(m, x->deg + 2) % 12, pc(m, x->deg + 4) % 12};
    int best = 0, bestscore = -1;
    for (int s = 0; s < n; s++) {
        int score = 0;
        for (int k = 0; k < 3; k++) {
            int p = SCALE_IV[x->scale][(s + 2 * k) % n];
            for (int j = 0; j < 3; j++)
                score += p == t[j];
        }
        score += (SCALE_IV[x->scale][s] == t[0]) * 3;
        if (score > bestscore) {
            bestscore = score;
            best = s;
        }
    }
    return best;
}

static void emit(t_dcs_composer *x)
{
    t_atom a[6];
    SETFLOAT(a, anchor(x));
    SETFLOAT(a + 1, x->bass);
    for (int i = 0; i < 4; i++)
        SETFLOAT(a + 2 + i, x->voice[i]);
    outlet_list(x->x_obj.ob_outlet, &s_list, 6, a);
}

static void play_pos(t_dcs_composer *x, int fresh)
{
    int same = x->voiced && x->prog[x->pos] == x->deg;
    x->deg = x->prog[x->pos];
    x->bars_left = chord_bars(x);
    if (same && !fresh)
        return;   /* the same chord again (a phrase ends home, the next starts there): it is simply held */
    voice_chord(x);
    emit(x);
}

static void handover(t_dcs_composer *x);

/* the last phrase of a piece: its ending open (the mode's open chord), so the new piece answers it */
static void lead_in(t_dcs_composer *x)
{
    x->leadin = 1;
    x->prog[PLEN - 1] = pick_of(x, mode(x)->open, x->prog[PLEN - 2]);
}

static void advance(t_dcs_composer *x)
{
    if (++x->pos >= PLEN) {
        if (x->handover && ++x->handover == 2) {   /* a phrase into the new piece: a new groove */
            for (int k = 0; k < NKINDS; k++)
                x->drum_have[k] = x->arp_have[k] = x->cell_have[k] = 0;
            x->drum_kind = x->arp_kind = x->cell_kind = -1;
        } else if (x->handover == 3) {             /* and a phrase later, new motifs */
            for (int k = 0; k < NKINDS; k++)
                x->have_motif[k] = 0;
            x->mel_kind = -1;
            x->handover = 0;
        }
        if (x->leadin)
            handover(x);
        else if (--x->phrases_left > 0) {   /* the phrase again: often the same, sometimes changed */
            x->pos = 0;
            if (rnd(x, 2))
                vary_phrase(x, x->kind, x->prog);
        } else {
            int k = next_kind(x);
            if (x->kind != HOME)
                x->last_away = x->kind;
            begin_section(x, k);
        }
        if (x->renew && !x->leadin && !x->handover && x->piece_bars <= 0 && x->phrases_left == 1)
            lead_in(x);
    }
    play_pos(x, 0);
}

/* ---- the melody ---- */

static void make_motif(t_dcs_composer *x, t_motif *m)
{
    m->n = 3 + rnd(x, 4);
    m->iv[0] = 0;
    int dir = rnd(x, 2) ? 1 : -1, run = 0;
    for (int i = 1; i < m->n; i++) {
        int r = rnd(x, 11), size = r < 6 ? 1 : r < 9 ? 2 : r < 10 ? 0 : 3;   /* steps, some skips, a repeat, a leap */
        if (rnd(x, 100) < 35 || run >= 2) {
            dir = -dir;
            run = 0;
        }
        int v = m->iv[i - 1] + dir * size;
        if (v > 4 || v < -4) {                   /* keep it within a narrow range */
            dir = -dir;
            v = m->iv[i - 1] + dir * size;
        }
        run += size != 0;
        m->iv[i] = v;
    }
}

/* a returning motif: one of its notes moved a step (not the first) */
static void nudge_motif(t_dcs_composer *x, t_motif *m)
{
    if (rnd(x, 10) < 6)
        m->iv[1 + rnd(x, m->n - 1)] += rnd(x, 2) ? 1 : -1;
}

/* the chord tone (chord, +2, +4 steps, any octave) nearest a step */
static int chord_tone(int target, int chord, int n, int root_only)
{
    int best = target, bd = 1 << 20;
    for (int o = -4; o <= 6; o++)
        for (int k = 0; k < (root_only ? 1 : 3); k++) {
            int d = chord + 2 * k + o * n, dist = abs(d - target);
            if (dist < bd) {
                bd = dist;
                best = d;
            }
        }
    return best;
}

static void new_statement(t_dcs_composer *x, int middle, int chord, int n)
{
    int carry = x->handover && x->mel_kind >= 0 && x->have_motif[x->mel_kind];   /* a handover: the old motif */
    if (!carry && (x->mel_kind != x->kind || !x->have_motif[x->kind])) {        /* the section's own motif */
        x->mel_kind = x->kind;
        if (x->have_motif[x->kind])
            nudge_motif(x, &x->motif[x->kind]);
        else
            make_motif(x, &x->motif[x->kind]);
        x->have_motif[x->kind] = 1;
        x->stmt_k = 0;
        x->drift = 0;
    }
    const t_motif *m = &x->motif[x->mel_kind];
    t_motif *st = &x->stmt;
    /* the first round states it: the motif, again or answered, developed, the motif; later rounds work on it:
     * the motif, developed, developed, answered */
    int form, k = x->stmt_k % 4, first = x->stmt_k < 4;
    int develop = first ? k == 2 : k == 1 || k == 2;
    if (k == 0 || (first && k == 3))
        form = F_MOTIF;
    else if (!develop)
        form = first && rnd(x, 2) ? F_AGAIN : F_ANSWER;
    else {
        int r = rnd(x, 10);
        form = r < 3 ? F_MOVED : r < 5 ? F_INVERT : r < 6 ? F_BACK : r < 8 ? F_SHORT : F_LONGER;
    }
    *st = *m;
    switch (form) {
    case F_INVERT:
        for (int i = 0; i < st->n; i++)
            st->iv[i] = -m->iv[i];
        break;
    case F_BACK:
        for (int i = 0; i < st->n; i++)
            st->iv[i] = m->iv[m->n - 1 - i] - m->iv[m->n - 1];
        break;
    case F_SHORT:
        st->n = m->n > 3 ? m->n - 1 - rnd(x, m->n - 3) : m->n;
        break;
    case F_LONGER:
        if (st->n < MOTIF_MAX) {
            int last = m->iv[m->n - 1], prev = m->n > 1 ? m->iv[m->n - 2] : 0;
            st->iv[st->n++] = last + (last >= prev ? 1 : -1);   /* one more step the way it was going */
        }
        break;
    }
    int base;
    if (form == F_MOVED || (form == F_INVERT && rnd(x, 2))) {
        int s = (rnd(x, 2) ? 1 : -1) * (1 + rnd(x, 2));       /* the melody travels */
        x->drift = x->drift + s > ROAM || x->drift + s < -ROAM ? x->drift - s : x->drift + s;
        base = x->stmt_on ? x->stmt_base + s : chord_tone(middle + x->drift, chord, n, 0);
    } else {
        if (k == 0 && first)
            x->drift = 0;
        else if (k == 0)
            x->drift /= 2;                                   /* the theme comes back, partway toward the middle */
        base = chord_tone(middle + x->drift, chord, n, 0);
    }
    if (base > middle + ROAM + 1 || base < middle - ROAM - 1)
        base = chord_tone(middle + x->drift, chord, n, 0);
    if (form == F_ANSWER) {   /* the motif again, landing on the chord's root */
        int end = base + st->iv[st->n - 1];
        st->iv[st->n - 1] = chord_tone(end, chord, n, 1) - base;
    }
    x->stmt_base = base;
    x->stmt_pos = 0;
    x->stmt_on = 1;
    x->stmt_k++;
    t_atom a[2];
    SETFLOAT(a, form);
    SETFLOAT(a + 1, base);
    outlet_list(x->out_form, &s_list, 2, a);
}

static void dcs_composer_note(t_dcs_composer *x, t_floatarg fmid, t_floatarg fchord, t_floatarg fn)
{
    int middle = (int)fmid, chord = (int)fchord, n = fn >= 1 ? (int)fn : 7;
    if (!x->stmt_on || clock_gettimesince(x->last_note) > GAP_MS || x->stmt_pos >= x->stmt.n)
        new_statement(x, middle, chord, n);
    int deg = x->stmt_base + x->stmt.iv[x->stmt_pos++];
    while (deg > middle + ROAM + 4)   /* (whole octaves back toward the middle) */
        deg -= n;
    while (deg < middle - ROAM - 4)
        deg += n;
    x->last_note = clock_getlogicaltime();
    outlet_float(x->out_mel, deg);
}

/* ---- patterns made by rules ---- */

static int euclid(int i, int k, int rot, int steps)   /* is step i one of k hits spread evenly over the steps */
{
    return k > 0 && (((i + rot) % steps) * k) % steps < k;
}

static void make_drums(t_dcs_composer *x, float d, float brk, float (*p)[16])
{
    for (int l = 0; l < 3; l++)
        for (int i = 0; i < 16; i++)
            p[l][i] = 0;
    int kicks = 2 + (int)(3 * d + 0.5f);
    for (int i = 0; i < 16; i++)
        if (euclid(i, kicks, 0, 16))
            p[0][i] = i == 0 ? 1.0f : i == 8 ? 0.85f : 0.65f;
    if (brk > 0.3f && p[0][8] > 0 && rnd(x, 2)) {   /* a breakbeat leans the middle kick late */
        p[0][8] = 0;
        p[0][10] = 0.8f;
    }
    if (d < 0.2f)
        p[1][12] = 0.6f;
    else
        p[1][4] = p[1][12] = 0.9f;
    static const int ghost[5] = {7, 9, 10, 14, 15};
    for (int g = (int)(3 * d * rnd(x, 100) / 100.0f + 0.5f); g > 0; g--)
        p[1][ghost[rnd(x, 5)]] = 0.3f;
    int hats = 4 + (int)(8 * d + 0.5f), rot = hats == 4 && rnd(x, 3) == 0 ? 2 : 0;   /* (4 hats: sometimes off the beat) */
    for (int i = 0; i < 16; i++)
        if (euclid(i, hats, rot, 16))
            p[2][i] = i % 4 == 0 ? 0.6f : i % 2 == 0 ? 0.45f : 0.28f;
    if (d > 0.5f && rnd(x, 2))
        p[2][14] = 2.45f;                            /* an open hat before the bar comes round */
}

/* one thing changes: a kick moves, a ghost note comes or goes, a hat comes or goes or opens */
static void vary_drums(t_dcs_composer *x, float (*p)[16])
{
    int what = rnd(x, 3);
    if (what == 0) {
        int hits[16], nh = 0;
        for (int i = 1; i < 16; i++)
            if (p[0][i] > 0 && p[0][i] < 0.8f)       /* (the downbeat and the strong middle kick stay put) */
                hits[nh++] = i;
        if (nh) {
            int from = hits[rnd(x, nh)], to = from + (rnd(x, 2) ? 1 : -1) * (1 + rnd(x, 2));
            if (to > 0 && to < 16 && p[0][to] == 0) {
                p[0][to] = 0.6f;
                p[0][from] = 0;
            }
        } else
            p[0][6 + 4 * rnd(x, 2)] = 0.6f;
    } else if (what == 1) {
        static const int ghost[5] = {7, 9, 10, 14, 15};
        int i = ghost[rnd(x, 5)];
        if (p[1][i] < 0.5f)
            p[1][i] = p[1][i] > 0 ? 0 : 0.3f;
    } else {
        int i = 1 + 2 * rnd(x, 8);                   /* an off-beat 16th */
        if (i == 15 || i == 13)
            p[2][14] = p[2][14] >= 2 ? 0.45f : p[2][14] > 0 ? 2.45f : 0.45f;
        else
            p[2][i] = p[2][i] > 0 ? 0 : 0.28f;
    }
}

/* the section whose patterns play: the old piece's for the first phrase of a handover */
static int pat_kind(t_dcs_composer *x, int cur)
{
    return x->handover == 1 && cur >= 0 ? cur : x->kind;
}

static void dcs_composer_drums(t_dcs_composer *x, t_floatarg fdrive, t_floatarg fbrk)
{
    int k = pat_kind(x, x->drum_kind);
    float d = fdrive + KIND_DRIVE[k];
    d = d < 0 ? 0 : d > 1 ? 1 : d;
    if (!x->drum_have[k] || fabsf(d - x->drum_d[k]) > 0.15f) {   /* this section's own bar (again, if the pace moved) */
        make_drums(x, d, fbrk, x->drum_base[k]);
        x->drum_have[k] = 1;
        x->drum_d[k] = d;
        x->drum_kind = -1;
    }
    if (x->drum_kind != k || ++x->drum_bars % 4 == 0) {
        if (x->drum_kind != k)
            x->drum_bars = 0;
        x->drum_kind = k;
        for (int l = 0; l < 3; l++)
            for (int i = 0; i < 16; i++)
                x->drum_cur[l][i] = x->drum_base[k][l][i];
    } else if (rnd(x, 10) < 7)
        vary_drums(x, x->drum_cur);
    for (int l = 0; l < 3; l++) {
        t_atom a[17];
        SETFLOAT(a, l);
        for (int i = 0; i < 16; i++)
            SETFLOAT(a + 1 + i, l < 2 && x->leadin && x->pos == PLEN - 1 ? 0 : x->drum_cur[l][i]);   /* the drop */
        outlet_anything(x->out_pat, gensym("drum"), 17, a);
    }
}

static int tone_step(int t, int n)   /* the t-th chord tone up (0 root, 1 third, 2 fifth, 3 the root an octave up..) */
{
    return (t % 3) * 2 + (t / 3) * n;
}

static void make_arp(t_dcs_composer *x, float pace, int n, int *p)
{
    int top = 3 + (int)(2 * pace + 0.5f), shape = rnd(x, 5), t[ARP_LEN];
    for (int i = 0; i < ARP_LEN; i++) {
        int up = i % (top + 1), tri = i <= top ? i : 2 * top - i;
        switch (shape) {
        case 0: t[i] = tri < 0 ? 0 : tri; break;                          /* rise and fall */
        case 1: t[i] = up; break;                                         /* rising */
        case 2: t[i] = top - up; break;                                   /* falling */
        case 3: t[i] = (i / 2) + (i % 2) * 2; break;                      /* broken: 0 2 1 3 2 4 .. */
        default: t[i] = i % 2 ? 1 + (i / 2) % top : 0;                    /* around the root */
        }
        t[i] = t[i] > top + 1 ? top + 1 : t[i];
    }
    for (int i = 0; i < ARP_LEN; i++) {
        p[i] = tone_step(t[i], n);
        if (i % 2 && rnd(x, 4) == 0)
            p[i] += 1;                               /* a passing note on a weak step */
    }
}

static void dcs_composer_arp(t_dcs_composer *x, t_floatarg fpace, t_floatarg fn)
{
    int k = pat_kind(x, x->arp_kind), n = fn >= 1 ? (int)fn : 7;
    float pace = fpace < 0 ? 0 : fpace > 1 ? 1 : fpace;
    if (!x->arp_have[k] || n != x->arp_n || fabsf(pace - x->arp_pace[k]) > 0.3f) {
        make_arp(x, pace, n, x->arp_base[k]);
        x->arp_have[k] = 1;
        x->arp_pace[k] = pace;
        x->arp_n = n;
        x->arp_kind = -1;
    }
    if (x->arp_kind != k || ++x->arp_bars % 4 == 0) {
        if (x->arp_kind != k)
            x->arp_bars = 0;
        x->arp_kind = k;
        for (int i = 0; i < ARP_LEN; i++)
            x->arp_cur[i] = x->arp_base[k][i];
    } else if (x->arp_bars % 8 == 6) {               /* the figure backwards */
        for (int i = 0; i < ARP_LEN; i++)
            x->arp_cur[i] = x->arp_base[k][ARP_LEN - 1 - i];
    } else if (rnd(x, 10) < 6) {                     /* a note moves to a neighbouring chord tone */
        int i = 1 + rnd(x, ARP_LEN - 1), t = 0;      /* (the bar's first note stays) */
        while (tone_step(t + 1, n) <= x->arp_cur[i] && t < 7)
            t++;
        t += (t == 0 || rnd(x, 2)) ? 1 : -1;
        x->arp_cur[i] = tone_step(t, n);
    }
    t_atom a[ARP_LEN];
    for (int i = 0; i < ARP_LEN; i++)
        SETFLOAT(a + i, x->arp_cur[i]);
    outlet_anything(x->out_pat, gensym("arp"), ARP_LEN, a);
}

/* a one-bar rhythm as onsets (1) on 16 steps; lengths are the gaps between them */
static void make_cell(t_dcs_composer *x, float d, int dotted, int *on)
{
    for (int i = 0; i < 16; i++)
        on[i] = 0;
    if (!dotted) {
        static const int K[5] = {1, 2, 4, 6, 8};          /* even counts on the 8ths stay straight (3 would be 3+3+2) */
        int k = K[(int)(d * 4 + 0.5f)];
        for (int i = 0; i < 8; i++)
            on[2 * i] = euclid(i, k, 0, 8);
    } else {
        static const int K[3] = {3, 5, 7};                 /* odd counts on the 16ths: 3+3+2-like, dotted */
        int k = K[(int)(d * 2 + 0.5f)], late = rnd(x, 4) == 0 ? 2 : 0;
        for (int i = 0; i < 16; i++)
            if (euclid(i, k, 0, 16) && i + late < 16)
                on[i + late] = 1;
    }
    if (!on[0] && !on[2])
        on[0] = 1;
}

static void vary_cell(t_dcs_composer *x, int dotted, int *on)
{
    int at[16], n = 0, grid = dotted ? 1 : 2;
    for (int i = 0; i < 16; i++)
        if (on[i])
            at[n++] = i;
    int what = rnd(x, 3);
    if (what == 0 && n > 1) {                            /* a note (not the first) moves */
        int i = at[1 + rnd(x, n - 1)], to = i + (rnd(x, 2) ? grid : -grid);
        if (to > 0 && to < 16 && !on[to]) {
            on[to] = 1;
            on[i] = 0;
        }
    } else if (what == 1) {                              /* a long note splits */
        for (int j = 0; j < n; j++) {
            int end = j + 1 < n ? at[j + 1] : 16;
            if (end - at[j] >= 4) {
                on[at[j] + (end - at[j]) / 2 / grid * grid] = 1;
                break;
            }
        }
    } else if (n > 2)                                    /* two notes merge */
        on[at[1 + rnd(x, n - 2)]] = 0;
}

static void dcs_composer_cell(t_dcs_composer *x, t_floatarg fdrive, t_floatarg fstyle)
{
    int k = pat_kind(x, x->cell_kind), style = (int)fstyle;
    float d = fdrive + KIND_DRIVE[k];
    d = d < 0 ? 0 : d > 1 ? 1 : d;
    int dotted = style == 2 ? 1 : style == 3 ? (x->cell_have[k] ? x->cell_style[k] == 2 : rnd(x, 2)) : 0;
    if (!x->cell_have[k] || fabsf(d - x->cell_d[k]) > 0.2f || (style != 3 && (x->cell_style[k] == 2) != dotted)) {
        make_cell(x, d, dotted, x->cell_base[k]);
        x->cell_have[k] = 1;
        x->cell_d[k] = d;
        x->cell_style[k] = dotted ? 2 : 1;
        x->cell_kind = -1;
    }
    dotted = x->cell_style[k] == 2;
    if (x->cell_kind != k || ++x->cell_bars % 4 == 0) {
        if (x->cell_kind != k)
            x->cell_bars = 0;
        x->cell_kind = k;
        for (int i = 0; i < 16; i++)
            x->cell_cur[i] = x->cell_base[k][i];
    } else if (rnd(x, 10) < 6)
        vary_cell(x, dotted, x->cell_cur);
    t_atom a[18];
    int count = 0, last = 0;
    for (int i = 0; i < 16; i++) {
        int len = 0;
        if (x->cell_cur[i]) {
            int j = i + 1;
            while (j < 16 && !x->cell_cur[j])
                j++;
            len = j - i;
            count++;
            last = i;
        }
        SETFLOAT(a + i, len);
    }
    SETFLOAT(a + 16, count);
    SETFLOAT(a + 17, last);
    outlet_anything(x->out_pat, gensym("cell"), 18, a);
}

static void dcs_composer_bar(t_dcs_composer *x)
{
    clock_delay(x->watch, bar_ms(x) * 1.6);
    if (!x->started)
        return;
    x->piece_bars--;
    if (--x->bars_left <= 0)
        advance(x);
    else if (x->sus_voice >= 0) {   /* a sus chord resolves */
        x->voice[x->sus_voice] = x->sus_third;
        x->sus_voice = -1;
        emit(x);
    }
}

/* no bar came: keep time from the tempo */
static void dcs_composer_watch(t_dcs_composer *x)
{
    dcs_composer_bar(x);
    clock_delay(x->watch, bar_ms(x));
}

static void piece(t_dcs_composer *x)   /* a new piece: forget the sections, Home first */
{
    for (int k = 0; k < NKINDS; k++)
        x->have[k] = x->have_motif[k] = x->drum_have[k] = x->arp_have[k] = x->cell_have[k] = 0;
    x->drum_kind = x->arp_kind = x->cell_kind = -1;
    x->voiced = 0;
    x->mel_kind = -1;
    x->stmt_on = 0;
    x->last_away = CALM;
    x->leadin = x->handover = 0;
    x->piece_bars = PIECE_BARS;   /* (the first piece draws no random number: a seed gives the same piece) */
    begin_section(x, HOME);
}

/* the new piece takes over: its own chords and sections now, under the old groove and motif (they follow in advance) */
static void handover(t_dcs_composer *x)
{
    for (int k = 0; k < NKINDS; k++)
        x->have[k] = 0;
    x->last_away = CALM;
    x->leadin = 0;
    x->handover = 1;
    x->piece_bars = PIECE_BARS + rnd(x, PIECE_BARS / 2 + 1);
    begin_section(x, HOME);
}

static void dcs_composer_renew(t_dcs_composer *x, t_floatarg f)
{
    x->renew = f >= 0.5f;
    if (!x->renew)
        x->leadin = 0;   /* (a handover under way finishes) */
}

static void dcs_composer_scale(t_dcs_composer *x, t_floatarg f)
{
    int s = (int)f;
    x->scale = s < 0 ? 0 : s >= NSCALES ? NSCALES - 1 : s;
    piece(x);
    if (x->started)
        play_pos(x, 1);
}

static void dcs_composer_start(t_dcs_composer *x)
{
    x->started = 1;
    play_pos(x, 1);
    clock_delay(x->watch, bar_ms(x) * 1.6);
}

static void dcs_composer_again(t_dcs_composer *x)
{
    if (x->started)
        emit(x);
}

static void dcs_composer_tempo(t_dcs_composer *x, t_floatarg f) { x->bpm = f; }
static void dcs_composer_color(t_dcs_composer *x, t_floatarg f) { x->color = f >= 0.5f; }
static void dcs_composer_bright(t_dcs_composer *x, t_floatarg f) { x->bright = f < 0 ? 0 : f > 1 ? 1 : f; }
static void dcs_composer_energy(t_dcs_composer *x, t_floatarg f) { x->energy = f < 0 ? 0 : f > 1 ? 1 : f; }

static void dcs_composer_seed(t_dcs_composer *x, t_floatarg f)
{
    x->rng = (unsigned int)f * 2654435761u + 1;
    piece(x);
}

static void *dcs_composer_new(void)
{
    static unsigned int made;
    t_dcs_composer *x = (t_dcs_composer *)pd_new(dcs_composer_class);
    outlet_new(&x->x_obj, &s_list);
    x->out_sect = outlet_new(&x->x_obj, &s_list);
    x->out_mel = outlet_new(&x->x_obj, &s_float);
    x->out_form = outlet_new(&x->x_obj, &s_list);
    x->out_pat = outlet_new(&x->x_obj, &s_list);
    x->drum_kind = x->arp_kind = x->cell_kind = -1;
    x->arp_n = 0;
    x->color = 0;
    x->sus_voice = -1;
    x->mel_kind = -1;
    x->stmt_on = 0;
    x->renew = x->leadin = x->handover = 0;
    for (int k = 0; k < NKINDS; k++)
        x->have_motif[k] = 0;
    x->watch = clock_new(x, (t_method)dcs_composer_watch);
    x->rng = (unsigned int)time(0) * 2654435761u + ++made;   /* a different piece every time */
    x->piece_bars = PIECE_BARS;
    x->bpm = 100;
    x->bright = 0.5f;
    x->energy = 0.5f;
    x->kind = HOME;
    x->last_away = CALM;
    for (int k = 0; k < NKINDS; k++)
        x->have[k] = x->drum_have[k] = x->arp_have[k] = 0;
    make_phrase(x, HOME, x->prog);   /* (quietly: nothing is sent while the patch loads) */
    x->have[HOME] = 1;
    for (int i = 0; i < PLEN; i++)
        x->kept[HOME][i] = x->prog[i];
    x->phrases_left = 2;
    x->deg = 0;
    x->bars_left = 1;
    return x;
}

static void dcs_composer_free(t_dcs_composer *x)
{
    clock_free(x->watch);
}

void dcs_composer_setup(void)
{
    dcs_composer_class = class_new(gensym("dcs_composer"), (t_newmethod)dcs_composer_new,
        (t_method)dcs_composer_free, sizeof(t_dcs_composer), CLASS_DEFAULT, 0);
    class_addbang(dcs_composer_class, dcs_composer_again);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_bar, gensym("bar"), 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_start, gensym("start"), 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_again, gensym("again"), 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_scale, gensym("scale"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_tempo, gensym("tempo"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_bright, gensym("bright"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_energy, gensym("energy"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_seed, gensym("seed"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_color, gensym("color"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_renew, gensym("renew"), A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_drums, gensym("drums"), A_FLOAT, A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_arp, gensym("arp"), A_FLOAT, A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_cell, gensym("cell"), A_FLOAT, A_FLOAT, 0);
    class_addmethod(dcs_composer_class, (t_method)dcs_composer_note, gensym("note"), A_FLOAT, A_FLOAT, A_FLOAT, 0);
}
