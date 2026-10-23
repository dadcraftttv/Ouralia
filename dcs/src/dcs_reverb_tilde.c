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
 * `style <0|1|2>` (Oct 23; DadCraft: the octaves sound metallic, he wants whimsical, misty, sparkly):
 *   0 Classic: the shifter above. Fine on played notes, metallic on real-world sounds: on a voice its tail grows
 *     sharp fixed peaks (test_dcs: 81x the spectrum's local level, 4.6x without shimmer; on StreetBeatz's break 160x
 *     against 21x). Exact octaves fed back through the combs, the same in both ears.
 *   1 Mist: each ear has its own shifter of 4 grains, each 120-220 ms long, starting a little late at random, 3 in 4
 *     of them an octave up and the rest staying in place, every one a little off (+-12 cents up, +-20 in place:
 *     the narrower +-7 / +-12 first tried still rang a little on a voice):
 *     no fixed rate to flutter, no exact octaves to ring, the two ears different. A darker band (250 Hz - 3.5 kHz).
 *   2 Sparkle (back pocket: off the menu Oct 23, DadCraft "still sounds wrong"): Mist plus glitter: in each ear 4 tiny grains (15-40 ms) two octaves up (+-4 cents), each followed by
 *     20-150 ms of silence, high-passed at 1 kHz, played quietly straight out, never into the tail (the first try,
 *     2 louder 30-80 ms grains that also went round the tail, chirped and climbed: DadCraft "really wrong").
 *   The new styles low-pass what they read at 4.5 kHz first (reading 2-4x fast would fold the top back down).
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
#define MBUF 32768                  /* Mist / Sparkle: each ear's line (power of two; the longest grain at 96 kHz) */
#define NMIST 4                     /* Mist's grains per ear */
#define NTWINK 4                    /* Sparkle's glints per ear */
#ifndef MGAIN
#define MGAIN 1.0                   /* Mist's loop gain at shimmer 1 (0.5, as SHGAIN, rose 20x less than Classic) */
#endif
#ifndef MINPLACE
#define MINPLACE 0.25               /* the share of Mist's grains that stay in place (the rest go an octave up) */
#endif
#define TWOUT 0.25                  /* the glints' level, straight out */
#define MPRE_HZ 4500.
#define MLO_HZ 250.
#define MHI_HZ 3500.
#define TWHP_HZ 1000.

typedef struct _grain
{
    double pos, len, ratio, d0;     /* samples into it, its length, its speed, its delay at the start */
    int wait;                       /* (twinkles) samples of silence left before the next one; 0 = playing */
} t_grain;

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
    int x_style;                    /* 0 Classic, 1 Mist, 2 Sparkle */
    unsigned int x_seed;
    t_sample x_mbuf[2][MBUF];       /* Mist / Sparkle: each ear's sound, low-passed, for its grains */
    int x_mw;
    t_grain x_mg[2][NMIST], x_tw[2][NTWINK];
    t_sample x_mpre[2][2], x_mlo[2], x_mhi[2], x_mth[2];  /* the filters' states */
    t_sample x_mprecoef, x_mlocoef, x_mhicoef, x_mthcoef;
    t_sample x_mnext[2][8192];      /* each ear's shimmer for its combs' next block */
    int x_mn;
    t_sample x_ein[8192];           /* (one ear's comb input, while Mist / Sparkle adds to it) */
} t_dcs_reverb;

static double dcs_rnd(t_dcs_reverb *x)
{
    x->x_seed = x->x_seed * 1664525u + 1013904223u;
    return (x->x_seed >> 8) * (1. / 16777216.);
}

static void grain_start(t_dcs_reverb *x, t_grain *g, int twinkle)
{
    double sr = x->x_sr, cents, jit;
    if (twinkle)
    {
        g->len = sr * (0.015 + 0.025 * dcs_rnd(x));
        g->ratio = 4;                                      /* two octaves up */
        cents = 8 * (dcs_rnd(x) - 0.5);
        jit = sr * 0.01 * dcs_rnd(x);
    }
    else
    {
        g->len = sr * (0.12 + 0.10 * dcs_rnd(x));
        g->ratio = dcs_rnd(x) < MINPLACE ? 1 : 2;             /* staying in place, or an octave up */
        cents = (g->ratio == 1 ? 40 : 24) * (dcs_rnd(x) - 0.5);
        jit = sr * 0.03 * dcs_rnd(x);
    }
    g->ratio *= pow(2, cents / 1200);
    if (g->ratio > 1 && (g->ratio - 1) * g->len + jit > MBUF - 16)
        g->len = (MBUF - 16 - jit) / (g->ratio - 1);      /* (only at very high sample rates) */
    g->d0 = (g->ratio > 1 ? (g->ratio - 1) * g->len : 0) + 2 + jit;  /* reading faster, it ends 2+ samples back */
    g->pos = 0;
    g->wait = 0;
}

static t_sample grain_read(const t_sample *buf, int wi, const t_grain *g)
{
    double r = wi - (g->d0 - (g->ratio - 1) * g->pos);
    int r0;
    t_sample frac, a, b;
    while (r < 0) r += MBUF;
    r0 = (int)r; frac = (t_sample)(r - r0);
    a = buf[r0 & (MBUF - 1)]; b = buf[(r0 + 1) & (MBUF - 1)];
    return sh_win[(int)(g->pos / g->len * SHWIN)] * (a + frac * (b - a));
}

/* Mist / Sparkle: each ear's sound into its own line; when on, its grains out of it, into the ear's combs the next
   block (and Sparkle's glints straight into the ear's output, at the amount now). */
static void dcs_reverb_mist(t_dcs_reverb *x, t_sample **ear, t_sample **out, int n, int on,
    t_sample a0, t_sample astep)
{
    int s, i, v, wi = x->x_mw;
    t_sample pc = x->x_mprecoef, lc = x->x_mhicoef, hc = x->x_mlocoef, tc = x->x_mthcoef;
    for (s = 0; s < 2; s++)
    {
        t_sample *buf = x->x_mbuf[s], *e = ear[s], *o = out[s], *nx = x->x_mnext[s];
        t_sample p1 = x->x_mpre[s][0], p2 = x->x_mpre[s][1], lo = x->x_mlo[s], hi = x->x_mhi[s], th = x->x_mth[s];
        wi = x->x_mw;
        for (i = 0; i < n; i++)
        {
            t_sample y = 0, tw = 0;
            p1 += pc * (e[i] - p1);                       /* 4.5 kHz, twice: nothing left to fold down */
            p2 += pc * (p1 - p2);
            buf[wi] = p2;
            if (on)
            {
                for (v = 0; v < NMIST; v++)
                {
                    t_grain *g = &x->x_mg[s][v];
                    y += grain_read(buf, wi, g);
                    if (++g->pos >= g->len) grain_start(x, g, 0);
                }
                lo += lc * (0.5f * y - lo);                 /* (4 grains overlap: about twice one) */
                hi += hc * (lo - hi);
                y = lo - hi;
                if (x->x_style == 2)
                {
                    for (v = 0; v < NTWINK; v++)
                    {
                        t_grain *g = &x->x_tw[s][v];
                        if (g->wait > 0)
                        {
                            if (--g->wait == 0) grain_start(x, g, 1);
                            continue;
                        }
                        tw += grain_read(buf, wi, g);
                        if (++g->pos >= g->len) g->wait = (int)(x->x_sr * (0.02 + 0.13 * dcs_rnd(x))) + 1;
                    }
                    th += tc * (tw - th);                   /* high-passed at 1 kHz: glints, not thumps */
                    tw -= th;
                    o[i] += (a0 + astep * (i + 1)) * (t_sample)TWOUT * tw;
                }
                nx[i] = y;
            }
            wi = (wi + 1) & (MBUF - 1);
        }
        if (PD_BIGORSMALL(p1)) p1 = 0;
        if (PD_BIGORSMALL(p2)) p2 = 0;
        if (PD_BIGORSMALL(lo)) lo = 0;
        if (PD_BIGORSMALL(hi)) hi = 0;
        if (PD_BIGORSMALL(th)) th = 0;
        x->x_mpre[s][0] = p1; x->x_mpre[s][1] = p2; x->x_mlo[s] = lo; x->x_mhi[s] = hi; x->x_mth[s] = th;
    }
    x->x_mw = wi;
    x->x_mn = on ? n : 0;
}

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
    if (!x->x_style && (sh0 > 0 || sh1 > 0) && x->x_shn == n)
        for (i = 0; i < n; i++)
        {
            t_sample f = vfb[i], k = 0.28f * sqrtf(f < 1 ? 1 - f * f : 0), a = sh0 + shstep * (i + 1);
            vin[i] += tanhf(a * (t_sample)SHGAIN * k * x->x_shnext[i]);
        }
    x->x_shamt = sh1;
    for (s = 0; s < 2; s++)
    {
        t_sample *ein = vin;
        if (x->x_style && (sh0 > 0 || sh1 > 0) && x->x_mn == n)  /* Mist / Sparkle: each ear its own */
        {
            ein = x->x_ein;
            for (i = 0; i < n; i++)
            {
                t_sample f = vfb[i], kk = 0.28f * sqrtf(f < 1 ? 1 - f * f : 0), a = sh0 + shstep * (i + 1);
                ein[i] = vin[i] + tanhf(a * (t_sample)MGAIN * kk * x->x_mnext[s][i]);
            }
        }
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
                buf[wi] = ein[i] + vfb[i] * lp;
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
    if (x->x_style)
    {
        t_sample *ear[2] = { left, out[1] };
        dcs_reverb_mist(x, ear, out, n, !(sh1 <= 0 && x->x_shtarget <= 0), sh0, shstep);
        x->x_shn = 0;
    }
    else if (sh1 <= 0 && x->x_shtarget <= 0)
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
        x->x_mprecoef = (t_sample)(1 - exp(-2 * 3.14159265358979 * MPRE_HZ / x->x_sr));
        x->x_mlocoef = (t_sample)(1 - exp(-2 * 3.14159265358979 * MLO_HZ / x->x_sr));
        x->x_mhicoef = (t_sample)(1 - exp(-2 * 3.14159265358979 * MHI_HZ / x->x_sr));
        x->x_mthcoef = (t_sample)(1 - exp(-2 * 3.14159265358979 * TWHP_HZ / x->x_sr));
        x->x_mn = 0;
        for (s = 0; s < 2; s++)
        {
            for (k = 0; k < NMIST; k++)                     /* (staggered: each partway through its first grain) */
            {
                grain_start(x, &x->x_mg[s][k], 0);
                x->x_mg[s][k].pos = (int)(dcs_rnd(x) * x->x_mg[s][k].len);
            }
            for (k = 0; k < NTWINK; k++)
                x->x_tw[s][k].wait = 1 + (int)(dcs_rnd(x) * 0.15 * x->x_sr);
        }
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

static void dcs_reverb_style(t_dcs_reverb *x, t_floatarg f)
{
    int s = (int)f;
    x->x_style = s < 0 ? 0 : (s > 2 ? 2 : s);
    x->x_shn = x->x_mn = 0;                                 /* (the other style's next block is stale) */
}

static void dcs_reverb_clear(t_dcs_reverb *x)
{
    int s, k;
    memset(x->x_shbuf, 0, sizeof(x->x_shbuf));
    x->x_shlo = x->x_shhi = 0;
    x->x_shn = 0;
    memset(x->x_mbuf, 0, sizeof(x->x_mbuf));
    memset(x->x_mpre, 0, sizeof(x->x_mpre));
    x->x_mlo[0] = x->x_mlo[1] = x->x_mhi[0] = x->x_mhi[1] = x->x_mth[0] = x->x_mth[1] = 0;
    x->x_mn = 0;
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
    x->x_seed = 12345;
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
    class_addmethod(dcs_reverb_class, (t_method)dcs_reverb_style, gensym("style"), A_FLOAT, 0);
}
