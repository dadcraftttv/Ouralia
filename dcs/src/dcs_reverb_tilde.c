/* dcs_reverb~: a stereo Freeverb-style reverb (parallel low-passed combs into series all-passes), in one object.
 *
 * The same algorithm a patch can build from delwrite~ / delread~ / lop~ / *~ objects, sample for sample (delays
 * rounded as delread~ rounds them, the damping as lop~ computes it), at a fraction of the cost: one object
 * instead of about a hundred.
 *
 *   [dcs_reverb~ <right ear extra ms> <number of combs> <comb ms ...> <all-pass ms ...>]
 *   inlets:  signal in (mono); signal: comb feedback (0..1); `tone <Hz>`: the damping low-pass in the combs;
 *            `shimmer <0..1>` (Oct 22): the tail rises in octaves (below); 0 = the reverb exactly as without it
 *   outlets: left, right (the right ear's delays are `extra` ms longer, so the two ears differ)
 *   Comb:     y = delay(D)[w]; w = in + feedback x lowpass(y). The combs' outputs are summed.
 *   All-pass: r = delay(D)[w]; w = x + 0.5 r; out = r - 0.5 w.
 *
 * Shimmer: the two ears' output, halved, goes through an octave-up pitch shifter (two 80 ms grains read at twice the
 * speed, crossfaded by Hann windows), a band-pass (200 Hz - 5 kHz one-poles) and back into the combs' input the next
 * block, scaled like the patch scales the input (0.28 sqrt(1 - feedback^2): the tail stays about as loud at any room
 * size) times the amount, through a soft clip (tanh) so no setting can run away. Each trip round adds an octave
 * above, quieter, the reverb's own damping taking the highest away. At 0 nothing is added (the shifter still listens,
 * so turning it on starts from the sound, not from silence).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "m_pd.h"
#include <math.h>
#include <string.h>

#define MAXCOMBS 16
#define MAXALLP 8
#define SHBUF 16384                 /* the shimmer's delay line (power of two; > 2 grains at 96 kHz) */
#define SHGRAIN_S 0.08              /* each grain */
#define SHLO_HZ 200.
#define SHHI_HZ 5000.
#ifndef SHGAIN
#define SHGAIN 0.5                  /* the loop's gain at shimmer 1 (tests: the biggest, brightest room still dies away) */
#endif
#define SHWIN 1024                  /* the grains' Hann window, as a table */
static t_sample sh_win[SHWIN + 1];

static t_class *dcs_reverb_class;

typedef struct _line
{
    t_sample *buf;
    int len, w;
    t_sample lp;                /* the comb's low-pass state */
} t_line;

typedef struct _dcs_reverb
{
    t_object x_obj;
    t_float x_f;
    int x_nc, x_na;
    double x_extra, x_cms[MAXCOMBS], x_ams[MAXALLP];
    t_line x_c[2][MAXCOMBS], x_a[2][MAXALLP];
    double x_sr;
    t_float x_tone;
    t_sample x_coef;
    t_outlet *x_outr;
    t_sample x_shbuf[SHBUF];        /* shimmer: what the reverb played, for the shifter */
    int x_shw;
    double x_shphase, x_shgrain;    /* the grains' phase 0..1, their length in samples */
    t_sample x_shlo, x_shhi;        /* the band-pass's states */
    t_sample x_shlocoef, x_shhicoef;
    t_float x_shtarget;             /* the amount asked for... */
    t_sample x_shamt;               /* ...and the amount now, gliding there */
    t_sample x_shnext[8192];        /* the shifted sound for the next block's input */
    int x_shn;                      /* how many of x_shnext are ready */
} t_dcs_reverb;

static void line_alloc(t_line *l, int len)
{
    if (l->buf) freebytes(l->buf, l->len * sizeof(t_sample));
    if (len < 1) len = 1;
    l->buf = (t_sample *)getbytes(len * sizeof(t_sample));
    l->len = len;
    l->w = 0;
    l->lp = 0;
}

static void dcs_reverb_coef(t_dcs_reverb *x)
{
    t_sample c = (t_sample)(x->x_tone * (2 * 3.14159265358979 / x->x_sr));
    x->x_coef = c > 1 ? 1 : (c < 0 ? 0 : c);
}

/* Every delay is at least a block long, so each line can run a whole block at once (nothing it reads in this block
   was written in this block): one tight loop per line instead of all of them per sample. */
#define MAXBLOCK 8192
static t_int *dcs_reverb_perform(t_int *w)
{
    t_dcs_reverb *x = (t_dcs_reverb *)(w[1]);
    t_sample *in = (t_sample *)(w[2]), *fb = (t_sample *)(w[3]);
    t_sample *out[2] = { (t_sample *)(w[4]), (t_sample *)(w[5]) };
    int n = (int)(w[6]), i, s, k;
    t_sample coef = x->x_coef, keep = 1 - coef;
    t_sample acc[MAXBLOCK], vin[MAXBLOCK], vfb[MAXBLOCK], left[MAXBLOCK];
    if (n > MAXBLOCK) n = MAXBLOCK;
    memcpy(vin, in, n * sizeof(t_sample));                  /* (in and out may share memory) */
    memcpy(vfb, fb, n * sizeof(t_sample));
    t_sample sh0 = x->x_shamt, sh1 = x->x_shtarget, shstep;
    if (sh1 > sh0 + 0.002f) sh1 = sh0 + 0.002f;            /* (glides: 0 to 1 in ~500 blocks, ~0.7 s) */
    else if (sh1 < sh0 - 0.002f) sh1 = sh0 - 0.002f;
    shstep = (sh1 - sh0) / n;
    if ((sh0 > 0 || sh1 > 0) && x->x_shn == n)
        for (i = 0; i < n; i++)
        {
            t_sample f = vfb[i], k = 0.28f * sqrtf(f < 1 ? 1 - f * f : 0), a = sh0 + shstep * (i + 1);
            vin[i] += tanhf(a * (t_sample)SHGAIN * k * x->x_shnext[i]);
        }
    x->x_shamt = sh1;
    for (s = 0; s < 2; s++)
    {
        memset(acc, 0, n * sizeof(t_sample));
        for (k = 0; k < x->x_nc; k++)
        {
            t_line *l = &x->x_c[s][k];
            t_sample *buf = l->buf, lp = l->lp;
            int wi = l->w, len = l->len;
            for (i = 0; i < n; i++)
            {
                t_sample y = buf[wi];
                lp = coef * y + keep * lp;
                buf[wi] = vin[i] + vfb[i] * lp;
                if (++wi >= len) wi = 0;
                acc[i] += y;
            }
            if (PD_BIGORSMALL(lp)) lp = 0;
            l->lp = lp; l->w = wi;
        }
        for (k = 0; k < x->x_na; k++)
        {
            t_line *l = &x->x_a[s][k];
            t_sample *buf = l->buf;
            int wi = l->w, len = l->len;
            for (i = 0; i < n; i++)
            {
                t_sample r = buf[wi], wv = acc[i] + 0.5f * r;
                buf[wi] = wv;
                if (++wi >= len) wi = 0;
                acc[i] = r - 0.5f * wv;
            }
            l->w = wi;
        }
        memcpy(out[s], acc, n * sizeof(t_sample));
        if (s == 0) memcpy(left, acc, n * sizeof(t_sample));  /* (the left ear, kept for the shimmer) */
    }
    /* shimmer: the ears' sound into the shifter, an octave up out of it for the next block. Off (and gliding to
       nothing), the shifter only listens: a copy into its line, nothing read. */
    if (sh1 <= 0 && x->x_shtarget <= 0)
    {
        int wi = x->x_shw;
        for (i = 0; i < n; i++)
        {
            x->x_shbuf[wi] = 0.5f * (left[i] + out[1][i]);
            wi = (wi + 1) & (SHBUF - 1);
        }
        x->x_shw = wi;
        x->x_shn = 0;
    }
    else
    {
        t_sample *buf = x->x_shbuf, lo = x->x_shlo, hi = x->x_shhi, lc = x->x_shlocoef, hc = x->x_shhicoef;
        double ph = x->x_shphase, g = x->x_shgrain, inc = 1. / g;
        int wi = x->x_shw;
        for (i = 0; i < n; i++)
        {
            int t;
            t_sample y = 0;
            buf[wi] = 0.5f * (left[i] + out[1][i]);
            for (t = 0; t < 2; t++)
            {
                double p = ph + 0.5 * t, d, r;
                int r0;
                t_sample frac, win;
                if (p >= 1) p -= 1;
                d = (1 - p) * g + 1;                          /* the delay shrinks one sample a sample: 2x speed */
                r = wi - d;
                while (r < 0) r += SHBUF;
                r0 = (int)r; frac = (t_sample)(r - r0);
                win = sh_win[(int)(p * SHWIN)];
                y += win * (buf[r0 & (SHBUF - 1)] + frac * (buf[(r0 + 1) & (SHBUF - 1)] - buf[r0 & (SHBUF - 1)]));
            }
            ph += inc;
            if (ph >= 1) ph -= 1;
            wi = (wi + 1) & (SHBUF - 1);
            lo += lc * (y - lo);                              /* 5 kHz low-pass... */
            hi += hc * (lo - hi);                             /* ...minus a 200 Hz low-pass = the band */
            x->x_shnext[i] = lo - hi;
        }
        if (PD_BIGORSMALL(lo)) lo = 0;
        if (PD_BIGORSMALL(hi)) hi = 0;
        x->x_shlo = lo; x->x_shhi = hi; x->x_shphase = ph; x->x_shw = wi;
        x->x_shn = n;
    }
    return (w + 7);
}

static void dcs_reverb_dsp(t_dcs_reverb *x, t_signal **sp)
{
    if (sp[0]->s_sr != x->x_sr)
    {
        int s, k;
        x->x_sr = sp[0]->s_sr;
        for (s = 0; s < 2; s++)
        {
            double extra = s ? x->x_extra : 0;
            for (k = 0; k < x->x_nc; k++)                   /* (rounded as delread~ rounds) */
                line_alloc(&x->x_c[s][k], (int)(0.5 + x->x_sr * 0.001 * (x->x_cms[k] + extra)));
            for (k = 0; k < x->x_na; k++)
                line_alloc(&x->x_a[s][k], (int)(0.5 + x->x_sr * 0.001 * (x->x_ams[k] + extra)));
        }
        dcs_reverb_coef(x);
        x->x_shgrain = SHGRAIN_S * x->x_sr;
        x->x_shlocoef = (t_sample)(1 - exp(-2 * 3.14159265358979 * SHHI_HZ / x->x_sr));
        x->x_shhicoef = (t_sample)(1 - exp(-2 * 3.14159265358979 * SHLO_HZ / x->x_sr));
        x->x_shn = 0;
    }
    dsp_add(dcs_reverb_perform, 6, x, sp[0]->s_vec, sp[1]->s_vec, sp[2]->s_vec, sp[3]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_reverb_tone(t_dcs_reverb *x, t_floatarg f)
{
    x->x_tone = f;
    if (x->x_sr > 0) dcs_reverb_coef(x);
}

static void dcs_reverb_shimmer(t_dcs_reverb *x, t_floatarg f)
{
    x->x_shtarget = f < 0 ? 0 : (f > 1 ? 1 : f);
}

static void dcs_reverb_clear(t_dcs_reverb *x)
{
    int s, k;
    memset(x->x_shbuf, 0, sizeof(x->x_shbuf));
    x->x_shlo = x->x_shhi = 0;
    x->x_shn = 0;
    for (s = 0; s < 2; s++)
    {
        for (k = 0; k < x->x_nc; k++)
            memset(x->x_c[s][k].buf, 0, x->x_c[s][k].len * sizeof(t_sample)), x->x_c[s][k].lp = 0;
        for (k = 0; k < x->x_na; k++)
            memset(x->x_a[s][k].buf, 0, x->x_a[s][k].len * sizeof(t_sample));
    }
}

static void *dcs_reverb_new(t_symbol *sel, int argc, t_atom *argv)
{
    t_dcs_reverb *x = (t_dcs_reverb *)pd_new(dcs_reverb_class);
    int k;
    x->x_extra = argc > 0 ? atom_getfloat(argv) : 0;
    x->x_nc = argc > 1 ? (int)atom_getfloat(argv + 1) : 0;
    if (x->x_nc > MAXCOMBS) x->x_nc = MAXCOMBS;
    if (x->x_nc < 0 || argc < 2 + x->x_nc) x->x_nc = 0;
    for (k = 0; k < x->x_nc; k++)
        x->x_cms[k] = atom_getfloat(argv + 2 + k);
    x->x_na = argc - 2 - x->x_nc;
    if (x->x_na > MAXALLP) x->x_na = MAXALLP;
    if (x->x_na < 0) x->x_na = 0;
    for (k = 0; k < x->x_na; k++)
        x->x_ams[k] = atom_getfloat(argv + 2 + x->x_nc + k);
    if (!x->x_nc)
        pd_error(x, "dcs_reverb~: give it <right extra ms> <number of combs> <comb ms ...> <all-pass ms ...>");
    x->x_tone = 6000;
    x->x_sr = 0;
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    x->x_outr = outlet_new(&x->x_obj, &s_signal);
    return (x);
}

static void dcs_reverb_free(t_dcs_reverb *x)
{
    int s, k;
    for (s = 0; s < 2; s++)
    {
        for (k = 0; k < MAXCOMBS; k++)
            if (x->x_c[s][k].buf) freebytes(x->x_c[s][k].buf, x->x_c[s][k].len * sizeof(t_sample));
        for (k = 0; k < MAXALLP; k++)
            if (x->x_a[s][k].buf) freebytes(x->x_a[s][k].buf, x->x_a[s][k].len * sizeof(t_sample));
    }
}

void dcs_reverb_tilde_setup(void)
{
    int k;
    for (k = 0; k <= SHWIN; k++)
    {
        double v = sin(3.14159265358979 * k / SHWIN);
        sh_win[k] = (t_sample)(v * v);
    }
    dcs_reverb_class = class_new(gensym("dcs_reverb~"), (t_newmethod)dcs_reverb_new,
        (t_method)dcs_reverb_free, sizeof(t_dcs_reverb), CLASS_DEFAULT, A_GIMME, 0);
    CLASS_MAINSIGNALIN(dcs_reverb_class, t_dcs_reverb, x_f);
    class_addmethod(dcs_reverb_class, (t_method)dcs_reverb_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_reverb_class, (t_method)dcs_reverb_tone, gensym("tone"), A_FLOAT, 0);
    class_addmethod(dcs_reverb_class, (t_method)dcs_reverb_clear, gensym("clear"), 0);
    class_addmethod(dcs_reverb_class, (t_method)dcs_reverb_shimmer, gensym("shimmer"), A_FLOAT, 0);
}
