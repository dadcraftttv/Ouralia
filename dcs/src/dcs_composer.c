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
 *    and Turn (starts away from home). Home comes back between the others. A section is remembered: when it comes
 *    back its phrase returns with a chord or two changed, and a section's second phrase is often its first again.
 *  - Chords change on the bar (`bar`, from the clock's tick 0), each lasting the bars nearest the section's pace;
 *    without bars (no clock) it keeps its own bar time from the tempo.
 *  - Voicing: each chord is voiced (inversion and octave) closest to the last one, so the pad moves smoothly.
 *
 * Messages: scale <0-7> (Major, Minor, Major pent., Minor pent., Dorian, Mixolydian, Lydian, Whole tone: a new piece,
 * home chord now), tempo <bpm>, bright <0-1>, energy <0-1> (both lean the choice of section), bar, start (the chord
 * now), again (the chord again: the key changed), seed <n>.
 * Left outlet: a list per chord: anchor (the scale step that fits it best, for the parts that think in steps), bass
 * (semitones above the key note, 0-11), then four pad notes (semitones from the key note, about -5..+28).
 * Right outlet: a list when a section starts: kind (0 Home, 1 Lift, 2 Calm, 3 Turn), melody register (scale steps).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "m_pd.h"
#include <stdlib.h>
#include <time.h>

static t_class *dcs_composer_class;

#define NSCALES 8
#define PLEN 4                     /* chords in a phrase */
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

/* Home comes back between the others; which other one leans on the room (energy and brightness: Lift, quiet: Calm) */
static int next_kind(t_dcs_composer *x)
{
    if (x->kind != HOME && rnd(x, 10) < 7)
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

static void advance(t_dcs_composer *x)
{
    if (++x->pos >= PLEN) {
        if (--x->phrases_left > 0) {   /* the phrase again: often the same, sometimes changed */
            x->pos = 0;
            if (rnd(x, 2))
                vary_phrase(x, x->kind, x->prog);
        } else {
            int k = next_kind(x);
            if (x->kind != HOME)
                x->last_away = x->kind;
            begin_section(x, k);
        }
    }
    play_pos(x, 0);
}

static void dcs_composer_bar(t_dcs_composer *x)
{
    clock_delay(x->watch, bar_ms(x) * 1.6);
    if (!x->started)
        return;
    if (--x->bars_left <= 0)
        advance(x);
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
        x->have[k] = 0;
    x->voiced = 0;
    x->last_away = CALM;
    begin_section(x, HOME);
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
    x->watch = clock_new(x, (t_method)dcs_composer_watch);
    x->rng = (unsigned int)time(0) * 2654435761u + ++made;   /* a different piece every time */
    x->bpm = 100;
    x->bright = 0.5f;
    x->energy = 0.5f;
    x->kind = HOME;
    x->last_away = CALM;
    for (int k = 0; k < NKINDS; k++)
        x->have[k] = 0;
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
}
