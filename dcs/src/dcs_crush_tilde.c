/* dcs_crush~: the bit crusher. Style 0 (Classic) is the Pd objects it replaces, sample for sample: the input
 * clipped to +-1, held by a sample-and-hold clocked at `rate` (phasor~ into samphold~), rounded to 2^(bits-1) steps
 * per polarity, mixed in by the amount. Nothing to compute but the clock while the amount is 0.
 *
 * Styles 1-6 (Oct 17, DadCraft: "more accurate 8 or 16 bit sounds"): the hardware's own rate, bits and filters, so
 * `rate` and `bits` don't apply. Each is a model by ear and by the hardware's known numbers, not an emulator:
 *   1 8-bit sampler  11025 Hz, unsigned 8-bit truncation, a 4-pole anti-alias filter in and a 2-pole out
 *   2 SP-1200        26040 Hz, 12-bit truncation, no filter in (its grit), a gentle 2-pole out at 12 kHz
 *   3 NES            the DPCM channel: 1-bit steps of 2 on a 7-bit counter at 33144 Hz, then the console's
 *                    output filters (high-pass 90 and 440 Hz, low-pass 14 kHz, first order each)
 *   4 Game Boy       4-bit at 8192 Hz, no filters but a DC block (bright, buzzy)
 *   5 SNES           32000 Hz, BRR (4-bit ADPCM in 16-sample blocks: the best of 4 predictors and 13 shifts per
 *                    block, decoded as the S-DSP does), then its Gaussian interpolation's taps at rest
 *                    (0.18 / 0.64 / 0.18: the soft, muffled SNES sound)
 *   6 Genesis        the YM2612 DAC: 8-bit at ~13 kHz, no interpolation, its "ladder" step either side of zero,
 *                    a first-order low-pass at 6 kHz
 *
 * Messages: rate <Hz>, bits <n>, mix <0..1>, on <0|1>, style <0..6>, smooth <ms>.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_crush_class;

#define NSTYLES 7

/* a biquad (transposed direct form II), coefficients from the audio EQ cookbook */
typedef struct { double b0, b1, b2, a1, a2, z1, z2; } bq;

static void bq_lowpass(bq *f, double hz, double q, double sr)
{
    double w = 2 * M_PI * fmin(hz, 0.49 * sr) / sr, a = sin(w) / (2 * q), c = cos(w), a0 = 1 + a;
    f->b0 = (1 - c) / 2 / a0; f->b1 = (1 - c) / a0; f->b2 = f->b0;
    f->a1 = -2 * c / a0; f->a2 = (1 - a) / a0;
}

static inline double bq_tick(bq *f, double x)
{
    double y = f->b0 * x + f->z1;
    f->z1 = f->b1 * x - f->a1 * y + f->z2;
    f->z2 = f->b2 * x - f->a2 * y;
    return y;
}

/* first-order low-pass / high-pass (one pole) */
typedef struct { double a, y, x1; } op;

static inline double op_lp(op *f, double x) { return f->y += f->a * (x - f->y); }
static inline double op_hp(op *f, double x)
{
    double y = f->a * (f->y + x - f->x1);   /* a = RC / (RC + dt) */
    f->x1 = x; f->y = y;
    return y;
}

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, levels;
    double phase;
    t_sample lastin, held;
    dcs_line rate, lev, mix, on;
    int style;
    /* the styles */
    double ph, step;            /* the hardware's sample clock (cycles of its rate per host sample) */
    double cur;                 /* the value the hardware is putting out */
    bq aa[2], rc[2];            /* anti-alias in, reconstruction out */
    op hp1, hp2, lp1;
    int nes;                    /* NES: the DPCM counter (0..127) */
    /* SNES: BRR in 16-sample blocks */
    int brr_in[16], brr_out[16], brr_n, brr_at, p1, p2, g1, g2;
} t_dcs_crush;

static const double STYLE_RATE[NSTYLES] = {0, 11025, 26040, 33144, 8192, 32000, 13300};

static void style_reset(t_dcs_crush *x)
{
    double sr = x->sr;
    memset(x->aa, 0, sizeof(x->aa)); memset(x->rc, 0, sizeof(x->rc));
    memset(&x->hp1, 0, sizeof(op)); memset(&x->hp2, 0, sizeof(op)); memset(&x->lp1, 0, sizeof(op));
    x->ph = 0; x->cur = 0; x->nes = 64;
    x->brr_n = x->brr_at = x->p1 = x->p2 = x->g1 = x->g2 = 0;
    memset(x->brr_out, 0, sizeof(x->brr_out));
    if (x->style <= 0 || x->style >= NSTYLES) return;
    double rate = STYLE_RATE[x->style];
    x->step = rate / sr;
    for (int k = 0; k < 2; k++) {       /* defaults: no filters (an all-pass biquad) */
        x->aa[k].b0 = x->rc[k].b0 = 1;
    }
    switch (x->style) {
    case 1:     /* 8-bit sampler: 4-pole Butterworth in at 0.45 fs, a 2-pole out at 0.4 fs */
        bq_lowpass(&x->aa[0], 0.45 * rate, 0.5412, sr); bq_lowpass(&x->aa[1], 0.45 * rate, 1.3066, sr);
        bq_lowpass(&x->rc[0], 0.4 * rate, 0.7071, sr);
        break;
    case 2:     /* SP-1200 */
        bq_lowpass(&x->rc[0], 12000, 0.6, sr);
        break;
    case 3:     /* NES: HP 90, HP 440, LP 14000 */
        x->hp1.a = 1 / (1 + 2 * M_PI * 90 / sr); x->hp2.a = 1 / (1 + 2 * M_PI * 440 / sr);
        x->lp1.a = 1 - exp(-2 * M_PI * 14000 / sr);
        bq_lowpass(&x->aa[0], 0.45 * rate, 0.7071, sr);
        break;
    case 4:     /* Game Boy: a DC block */
        x->hp1.a = 1 / (1 + 2 * M_PI * 20 / sr);
        break;
    case 5:     /* SNES: the samples were made with an anti-alias filter */
        bq_lowpass(&x->aa[0], 0.45 * rate, 0.5412, sr); bq_lowpass(&x->aa[1], 0.45 * rate, 1.3066, sr);
        break;
    case 6:     /* Genesis */
        x->lp1.a = 1 - exp(-2 * M_PI * 6000 / sr);
        break;
    }
}

/* SNES BRR: decode one sample from a nibble (as the S-DSP: (n << shift) >> 1, plus the predictor, clamped and
 * wrapped to 15 bits). p1, p2: the last two decoded samples. */
static inline int brr_decode(int nib, int shift, int filter, int p1, int p2)
{
    int s = shift <= 12 ? (nib << shift) >> 1 : (nib < 0 ? -2048 : 0);
    switch (filter) {
    case 1: s += p1 + ((-p1) >> 4); break;
    case 2: s += (p1 << 1) + ((-((p1 << 1) + p1)) >> 5) - p2 + (p2 >> 4); break;
    case 3: s += (p1 << 1) + ((-(p1 + (p1 << 2) + (p1 << 3))) >> 6) - p2 + (((p2 << 1) + p2) >> 4); break;
    }
    if (s > 32767) s = 32767;
    if (s < -32768) s = -32768;
    return (int16_t)(s << 1) >> 1;
}

/* Encode the 16 samples in brr_in as the S-DSP would play them back: try every predictor and shift, keep the
 * closest; the decoded block goes to brr_out. */
static void brr_block(t_dcs_crush *x)
{
    long best = -1;
    int out[16], bp1 = x->g1, bp2 = x->g2;
    for (int filter = 0; filter < 4; filter++) {
        for (int shift = 0; shift <= 12; shift++) {
            int p1 = x->g1, p2 = x->g2, dec[16];
            long err = 0;
            for (int i = 0; i < 16; i++) {
                int pred = brr_decode(0, shift, filter, p1, p2);
                double want = (x->brr_in[i] - pred) * 2.0 / (double)(1 << shift);
                int nib = (int)lrint(want);
                if (nib > 7) nib = 7;
                if (nib < -8) nib = -8;
                int s = brr_decode(nib, shift, filter, p1, p2);
                long e = s - x->brr_in[i];
                err += e * e;
                dec[i] = s; p2 = p1; p1 = s;
                if (best >= 0 && err >= best) break;
            }
            if (best < 0 || err < best) {
                best = err;
                memcpy(out, dec, sizeof(out));
                bp1 = p1; bp2 = p2;
            }
        }
    }
    memcpy(x->brr_out, out, sizeof(out));
    x->g1 = bp1; x->g2 = bp2;
}

/* One new hardware sample from the (filtered) input. */
static double style_sample(t_dcs_crush *x, double v)
{
    if (v > 1) v = 1;
    if (v < -1) v = -1;
    switch (x->style) {
    case 1: {   /* unsigned 8-bit, truncated */
        double q = floor((v + 1) * 128);
        if (q > 255) q = 255;
        return q / 128 - 1;
    }
    case 2: {   /* 12-bit, truncated */
        double q = floor(v * 2048);
        if (q > 2047) q = 2047;
        return q / 2048;
    }
    case 3: {   /* NES DPCM: one step of 2 toward the input */
        double want = (v + 1) * 63.5;
        if (want > x->nes) { if (x->nes <= 125) x->nes += 2; }
        else if (x->nes >= 2) x->nes -= 2;
        return x->nes / 63.5 - 1;
    }
    case 4: {   /* 4-bit */
        double q = floor((v + 1) * 8);
        if (q > 15) q = 15;
        return q / 7.5 - 1;
    }
    case 5: {   /* SNES: BRR with a block of delay, then the Gaussian taps */
        x->brr_in[x->brr_n++] = (int)lrint(v * 16383);
        int s = x->brr_out[x->brr_at++];
        if (x->brr_n == 16) {
            brr_block(x);
            x->brr_n = x->brr_at = 0;
        }
        double g = (0.1826 * x->p2 + 0.6372 * x->p1 + 0.1807 * s) / 16383;   /* (the middle tap: the last sample) */
        x->p2 = x->p1; x->p1 = s;
        return g;
    }
    case 6: {   /* YM2612 DAC: 8-bit signed, the ladder step either side of zero */
        double q = floor(v * 128);
        if (q > 127) q = 127;
        return (q + (q >= 0 ? 0.5 : -0.5) * 3) / 128;
    }
    }
    return v;
}

static inline double style_tick(t_dcs_crush *x, double in)
{
    double v = bq_tick(&x->aa[1], bq_tick(&x->aa[0], in));
    x->ph += x->step;
    if (x->ph >= 1) {           /* the hardware takes its next sample */
        x->ph -= 1;
        x->cur = style_sample(x, v);
    }
    double y = bq_tick(&x->rc[1], bq_tick(&x->rc[0], x->cur));
    switch (x->style) {
    case 3: y = op_lp(&x->lp1, op_hp(&x->hp2, op_hp(&x->hp1, y))); break;
    case 4: y = op_hp(&x->hp1, y); break;
    case 6: y = op_lp(&x->lp1, y); break;
    }
    return y;
}

static t_int *dcs_crush_perform(t_int *w)
{
    t_dcs_crush *x = (t_dcs_crush *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    DCS_SCRATCH(rate); DCS_SCRATCH(ph);
    dcs_line_block(&x->rate, rate, n, x->sr);
    int off = dcs_line_still(&x->mix) && dcs_line_still(&x->on) && x->mix.target * x->on.target == 0;
    /* the clock and the hold run whatever happens (as they did in Pd), so turning on again matches */
    dcs_phasor_block(&x->phase, rate, 0, ph, n, x->sr);
    if (off) {
        t_sample lastin = x->lastin, held = x->held;
        for (int i = 0; i < n; i++) {
            if (ph[i] < lastin) {
                t_sample f = in[i];
                held = f < -1 ? -1 : f > 1 ? 1 : f;
            }
            lastin = ph[i];
        }
        x->lastin = lastin; x->held = held;
        dcs_line_skip(&x->lev, n, x->sr);
        dcs_line_skip(&x->mix, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        if (in != out) memmove(out, in, n * sizeof(t_sample));
        return w + 5;
    }
    DCS_SCRATCH(lev); DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_line_block(&x->lev, lev, n, x->sr);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    t_sample lastin = x->lastin, held = x->held;
    for (int i = 0; i < n; i++) {
        t_sample f = in[i], c = f < -1 ? -1 : f > 1 ? 1 : f;
        if (ph[i] < lastin) held = c;
        lastin = ph[i];
        t_sample crushed;
        if (x->style > 0)
            crushed = style_tick(x, f);
        else {
            t_sample half = held * lev[i] + 0.5f;
            t_sample rounded = half - dcs_wrap(half);
            crushed = lev[i] ? rounded / lev[i] : 0;
        }
        out[i] = f + (crushed - f) * (mix[i] * on[i]);
    }
    x->lastin = lastin; x->held = held;
    return w + 5;
}

static void dcs_crush_dsp(t_dcs_crush *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_crush~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    if (x->sr != sp[0]->s_sr) {
        x->sr = sp[0]->s_sr;
        style_reset(x);
    }
    dsp_add(dcs_crush_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_crush_rate(t_dcs_crush *x, t_floatarg f) { dcs_line_set(&x->rate, f, x->smooth); }
/* levels = 2^(bits-1), computed as the Pd objects did ([- 1] [* 0.693147] [exp], in floats) */
static void dcs_crush_bits(t_dcs_crush *x, t_floatarg f)
{
    t_float a = f - 1, b = a * (t_float)0.693147;
    if (b > 87.3365f) b = 87.3365f;
    dcs_line_set(&x->lev, expf(b), x->smooth);
}
static void dcs_crush_mix(t_dcs_crush *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_crush_on(t_dcs_crush *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_crush_smooth(t_dcs_crush *x, t_floatarg f) { x->smooth = f; }
static void dcs_crush_style(t_dcs_crush *x, t_floatarg f)
{
    int s = (int)f;
    if (s < 0 || s >= NSTYLES) s = 0;
    if (s != x->style) {
        x->style = s;
        style_reset(x);
    }
}

static void *dcs_crush_new(void)
{
    t_dcs_crush *x = (t_dcs_crush *)pd_new(dcs_crush_class);
    x->smooth = 30;
    x->sr = 44100;
    style_reset(x);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_crush_tilde_setup(void)
{
    dcs_crush_class = class_new(gensym("dcs_crush~"), (t_newmethod)dcs_crush_new, 0, sizeof(t_dcs_crush), 0, 0);
    CLASS_MAINSIGNALIN(dcs_crush_class, t_dcs_crush, x_f);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_rate, gensym("rate"), A_FLOAT, 0);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_bits, gensym("bits"), A_FLOAT, 0);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_style, gensym("style"), A_FLOAT, 0);
    class_addmethod(dcs_crush_class, (t_method)dcs_crush_smooth, gensym("smooth"), A_FLOAT, 0);
}
