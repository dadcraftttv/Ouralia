/* dcs_fx.h: the building blocks of Pd's own signal objects, the same arithmetic, for dcs effects that replace a chain
 * of Pd objects sample for sample (so a ported effect sounds exactly as before, at a fraction of the cost).
 *
 *   dcs_line   line~ (a value that glides to each new target over whole blocks, linear within a block)
 *   dcs_cos    cos~ (Pd 0.55's 2048-point table, linear interpolation)
 *   dcs_osc    osc~ / phasor~ (a double-precision phase, Hoeldrich's trick)
 *   dcs_wrap   wrap~
 *   dcs_delay  delwrite~ + delread4~ / delread~ (a ring buffer, 4-point interpolation)
 *
 * The cos table, phase and interpolation code follow Pd's d_osc.c, d_math.c, d_ctl.c and d_delay.c
 * (Copyright (c) 1997-2024 Miller Puckette and others, BSD 3-Clause "Standard Improved").
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#ifndef DCS_FX_H
#define DCS_FX_H

/* Files that include this are built without -ffast-math (dcs/Makefile, dcs/android/Android.mk): Pd's objects
 * compute each step as written (rounding x - wrap(x), say), and a port has to round the same way to match them
 * sample for sample. */
#include "m_pd.h"
#include <limits.h>
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

/* the largest block an effect takes (its scratch buffers live on the stack) */
#define DCS_BLOCKMAX 64

/* ------------------------------------------------------------------ line~ */
typedef struct {
    t_sample value, target;
    t_float inc, biginc, inletwas;
    int ticksleft, retarget;
} dcs_line;

/* A new target, gliding over `ms` (line~ given "target ms"; ms <= 0 jumps there now). */
static inline void dcs_line_set(dcs_line *l, t_float f, t_float ms)
{
    if (ms <= 0) {
        l->target = l->value = f;
        l->ticksleft = l->retarget = 0;
    } else {
        l->target = f;
        l->retarget = 1;
        l->inletwas = ms;
    }
}

/* Start of a block: the first sample's value and the per-sample step (0 when still). Call dcs_line_end after. */
static inline t_sample dcs_line_begin(dcs_line *l, int n, t_float sr, t_float *inc)
{
    if (PD_BIGORSMALL(l->value))
        l->value = 0;
    if (l->retarget) {
        int nticks = l->inletwas * (t_float)(sr / (1000 * n));
        if (!nticks) nticks = 1;
        l->ticksleft = nticks;
        l->biginc = (l->target - l->value) / (t_sample)nticks;
        l->inc = (t_float)(1. / n) * l->biginc;
        l->retarget = 0;
    }
    if (l->ticksleft) {
        *inc = l->inc;
        return l->value;
    }
    *inc = 0;
    return l->value = l->target;
}

static inline void dcs_line_end(dcs_line *l)
{
    if (l->ticksleft) {
        l->value += l->biginc;
        l->ticksleft--;
    }
}

/* The whole block into `out` (for values used more than once). */
static inline void dcs_line_block(dcs_line *l, t_sample *out, int n, t_float sr)
{
    t_float inc;
    t_sample f = dcs_line_begin(l, n, sr, &inc);
    if (inc != 0)
        for (int i = 0; i < n; i++) out[i] = f, f += inc;
    else
        for (int i = 0; i < n; i++) out[i] = f;
    dcs_line_end(l);
}

/* True while the line is still (no glide this block or next): its value is l->target. */
static inline int dcs_line_still(const dcs_line *l)
{
    return !l->ticksleft && !l->retarget;
}

/* ------------------------------------------------------------------ cos~, osc~, phasor~, wrap~ */
#define DCS_COSTAB 2048
#define DCS_UNITBIT32 1572864.  /* 3*2^19: bit 32 has place value 1 */
#if defined(__BYTE_ORDER__) && __BYTE_ORDER__ == __ORDER_BIG_ENDIAN__
#define DCS_HI 0
#else
#define DCS_HI 1
#endif
union dcs_fudge { double d; int32_t i[2]; };

extern float *dcs_costab;
void dcs_costab_make(void);

/* cos~ of one value (phase in cycles). */
static inline t_sample dcs_cos(t_sample in)
{
    union dcs_fudge tf;
    int normhi;
    tf.d = DCS_UNITBIT32;
    normhi = tf.i[DCS_HI];
    tf.d = (double)(in * (float)DCS_COSTAB) + DCS_UNITBIT32;
    float *addr = dcs_costab + (tf.i[DCS_HI] & (DCS_COSTAB - 1));
    tf.i[DCS_HI] = normhi;
    t_float frac = tf.d - DCS_UNITBIT32, f1 = addr[0], f2 = addr[1];
    return f1 + frac * (f2 - f1);
}

/* osc~: phase in table points (double), conv = 2048 / sr. One sample, then the phase moves on by freq. */
typedef struct { double phase; } dcs_osc;

static inline t_sample dcs_osc_tick(double *dphase, t_sample freq, float conv, int normhi)
{
    union dcs_fudge tf;
    tf.d = *dphase;
    *dphase += freq * conv;
    float *addr = dcs_costab + (tf.i[DCS_HI] & (DCS_COSTAB - 1));
    tf.i[DCS_HI] = normhi;
    t_float frac = tf.d - DCS_UNITBIT32, f1 = addr[0], f2 = addr[1];
    return f1 + frac * (f2 - f1);
}

/* osc~ over a block: freq[i] per sample (or a constant when freq == 0 and fconst given). */
static inline void dcs_osc_block(dcs_osc *o, const t_sample *freq, t_sample fconst, t_sample *out, int n, t_float sr)
{
    union dcs_fudge tf;
    int normhi;
    float conv = DCS_COSTAB / sr;
    double dphase = o->phase + DCS_UNITBIT32;
    tf.d = DCS_UNITBIT32;
    normhi = tf.i[DCS_HI];
    for (int i = 0; i < n; i++)
        out[i] = dcs_osc_tick(&dphase, freq ? freq[i] : fconst, conv, normhi);
    tf.d = DCS_UNITBIT32 * DCS_COSTAB;
    normhi = tf.i[DCS_HI];
    tf.d = dphase + (DCS_UNITBIT32 * DCS_COSTAB - DCS_UNITBIT32);
    tf.i[DCS_HI] = normhi;
    o->phase = tf.d - DCS_UNITBIT32 * DCS_COSTAB;
}

/* osc~'s phase moved on over a block without computing the output (an effect that is off). */
static inline void dcs_osc_skip(dcs_osc *o, const t_sample *freq, t_sample fconst, int n, t_float sr)
{
    union dcs_fudge tf;
    float conv = DCS_COSTAB / sr;
    double dphase = o->phase + DCS_UNITBIT32;
    for (int i = 0; i < n; i++) dphase += (freq ? freq[i] : fconst) * conv;
    tf.d = DCS_UNITBIT32 * DCS_COSTAB;
    int normhi = tf.i[DCS_HI];
    tf.d = dphase + (DCS_UNITBIT32 * DCS_COSTAB - DCS_UNITBIT32);
    tf.i[DCS_HI] = normhi;
    o->phase = tf.d - DCS_UNITBIT32 * DCS_COSTAB;
}

/* A line moved on over a block without its values. */
static inline void dcs_line_skip(dcs_line *l, int n, t_float sr)
{
    t_float inc;
    dcs_line_begin(l, n, sr, &inc);
    dcs_line_end(l);
}

/* phasor~: 0..1 ramps, conv = 1 / sr. */
static inline void dcs_phasor_block(double *phase, const t_sample *freq, t_sample fconst, t_sample *out, int n, t_float sr)
{
    union dcs_fudge tf;
    int normhi;
    t_float conv = (t_float)(1. / sr);
    double dphase = *phase + DCS_UNITBIT32;
    tf.d = DCS_UNITBIT32;
    normhi = tf.i[DCS_HI];
    tf.d = dphase;
    for (int i = 0; i < n; i++) {
        tf.i[DCS_HI] = normhi;
        dphase += (freq ? freq[i] : fconst) * conv;
        out[i] = tf.d - DCS_UNITBIT32;
        tf.d = dphase;
    }
    tf.i[DCS_HI] = normhi;
    *phase = tf.d - DCS_UNITBIT32;
}

static inline t_sample dcs_wrap(t_sample f)
{
    f = (f > (t_sample)INT_MAX || f < (t_sample)INT_MIN) ? 0. : f;
    int k = (int)f;
    return k <= f ? f - k : f - (k - 1);
}

/* ------------------------------------------------------------------ delwrite~ + delread4~ */
/* A delay line laid out as delwrite~'s (n + 4 guard samples, the same length rounding), so reads clamp alike. */
typedef struct {
    t_sample *vec;
    int n, phase;       /* phase: where the next sample is written (in vec, 4..n+3) */
    t_float ms;
} dcs_delay;

static inline void dcs_delay_init(dcs_delay *d, t_float ms)
{
    d->vec = 0; d->n = 0; d->phase = 4; d->ms = ms;
}

/* At dsp time: the length delwrite~ would have (ms at sr, rounded up to 4, plus a block). */
static inline void dcs_delay_dsp(dcs_delay *d, t_float sr, int blocksize)
{
    int nsamps = d->ms * sr * (t_float)(0.001f);
    if (nsamps < 1) nsamps = 1;
    nsamps += ((-nsamps) & 3);
    nsamps += blocksize;
    if (d->n != nsamps) {
        d->vec = (t_sample *)resizebytes(d->vec, (d->n + 4) * sizeof(t_sample), (nsamps + 4) * sizeof(t_sample));
        memset(d->vec, 0, (nsamps + 4) * sizeof(t_sample));
        d->n = nsamps;
        d->phase = 4;
    }
}

static inline void dcs_delay_free(dcs_delay *d)
{
    if (d->vec) freebytes(d->vec, (d->n + 4) * sizeof(t_sample));
    d->vec = 0;
}

/* delwrite~ of one block. */
static inline void dcs_delay_write(dcs_delay *d, const t_sample *in, int n)
{
    t_sample *vp = d->vec, *bp = vp + d->phase, *ep = vp + (d->n + 4);
    int phase = d->phase + n;
    while (n--) {
        t_sample f = *in++;
        if (PD_BIGORSMALL(f)) f = 0;
        *bp++ = f;
        if (bp == ep) {
            vp[0] = ep[-4]; vp[1] = ep[-3]; vp[2] = ep[-2]; vp[3] = ep[-1];
            bp = vp + 4;
            phase -= d->n;
        }
    }
    d->phase = phase;
}

/* delread4~ of one block: ms[i] per sample (or msconst when ms == 0). `zerodel` = 0 when this block was already
 * written (delwrite~ sorted first), the block size when not (a feedback loop: the reader comes first). */
static inline void dcs_delay_read4(const dcs_delay *d, const t_sample *ms, t_sample msconst, t_sample *out, int n,
                                   t_float sr, int zerodel)
{
    int nsamps = d->n;
    t_sample limit = nsamps - n, fn = n - 1;
    t_sample *vp = d->vec, *wp = vp + d->phase, *bp;
    t_float srms = sr * 0.001;
    if (limit < 0 || n > DCS_BLOCKMAX) {
        for (int i = 0; i < n && i < DCS_BLOCKMAX; i++) out[i] = 0;
        return;
    }
    for (int i = 0; i < n; i++) {
        t_sample delsamps = srms * (ms ? ms[i] : msconst) - (t_sample)zerodel, frac;
        int idelsamps;
        t_sample a, b, c, dd, cminusb;
        if (!(delsamps >= 1.00001f)) delsamps = 1.00001f;
        if (delsamps > limit) delsamps = limit;
        delsamps += fn;
        fn = fn - 1.0f;
        idelsamps = delsamps;
        frac = delsamps - (t_sample)idelsamps;
        bp = wp - idelsamps;
        if (bp < vp + 4) bp += nsamps;
        dd = bp[-3]; c = bp[-2]; b = bp[-1]; a = bp[0];
        cminusb = c - b;
        out[i] = b + frac * (cminusb - 0.1666667f * (1. - frac) * ((dd - a - 3.0f * cminusb) * frac + (dd + 2.0f * a - 3.0f * b)));
    }
}

/* delread~ of one block (a whole number of samples, ms fixed). */
static inline void dcs_delay_read(const dcs_delay *d, t_float msval, t_sample *out, int n, t_float sr, int zerodel)
{
    int delsamps = (int)(0.5 + sr * 0.001 * msval) + n - zerodel;
    if (delsamps < n) delsamps = n;
    else if (delsamps > d->n) delsamps = d->n;
    int phase = d->phase - delsamps, nsamps = d->n;
    t_sample *vp = d->vec, *bp, *ep = vp + (d->n + 4);
    if (phase < 0) phase += nsamps;
    bp = vp + phase;
    while (n--) {
        *out++ = *bp++;
        if (bp == ep) bp -= nsamps;
    }
}

/* ------------------------------------------------------------------ common to the effects */
#define DCS_SCRATCH(name) t_sample name[DCS_BLOCKMAX]

#endif
