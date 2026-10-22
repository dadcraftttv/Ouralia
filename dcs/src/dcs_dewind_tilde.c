/* dcs_dewind~: pulls wind rumble down and leaves voices and the room alone.
 *
 * Wind on a phone's mic is a rumble far below a voice (mostly under ~250 Hz) that swamps everything above it, in
 * gusts. This watches how far the deep lows (under 150 Hz) stand over the highs (over 500 Hz), each by
 * two one-poles, mean squares over ~30 ms: a voice sits at -5..+3 dB, wind at +20..+30 (its rumble falls ~12 dB an
 * octave). When they stand more than `above` dB over, and are louder than -60 dBFS, that is wind: the sound crossfades toward itself through a
 * steep high-pass at `split` Hz (two Butterworth biquads, 24 dB an octave), fully `above` + 10 dB over, leaving
 * `depth` dB of the plain sound under it at amount 1 (fast: 20 ms in), letting go over 250 ms. A voice keeps
 * its highs, so it is left alone; a knock is too short to move the slow detector much. Wind is noise: lows that
 * repeat at a pitch (a deep voice's fundamental, a bass line: 50-200 Hz) are not wind, whatever their level. The
 * deep lows, averaged down to 1 kHz, are compared with themselves 5-20 ms earlier every 10 ms; the best match
 * (normalised, smoothed over ~50 ms) over 0.8 spares the sound, under 0.5 doesn't. Steady rumble (traffic) is
 * noise too and is pulled down.
 *
 * Two signals in, mid and side (the room in stereo: wind differs at the two mics, so it is loud in the side; with
 * one mic the side is silence): both are judged together and get the same cut.
 *
 *   [dcs_dewind~]
 *   inlets:  signal mid, `amount <0..1>` (0 = untouched, sample for sample), `split <Hz>`, `above <dB>`, `depth <dB>`;
 *            signal side
 *   outlets: mid, side, and (third) how windy it is now, 0..1, a signal (the app can show it)
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "m_pd.h"
#include <math.h>
#include <string.h>

#define ENV_MS 30.              /* the bands' mean squares are averaged over this */
#define ATTACK_MS 20.           /* the cut comes in this fast... */
#define RELEASE_MS 250.         /* ...and lets go this slowly */
#define MIN_LOW 1e-6            /* lows quieter than this (-60 dBFS, mean square) are never wind */
#define SPAN_DB 10.             /* from `above` to fully windy */
#define DET_LO_HZ 150.          /* the detector's deep lows... */
#define DET_HI_HZ 500.          /* ...and highs */
#define DEC_HZ 1000.            /* the periodicity check runs on the deep lows at this rate... */
#define PBUF 64                 /* ...over this many of them (power of two) */
#define PWIN 32                 /* compared over this many */
#define PLAG_MIN 5              /* at these lags (ms at 1 kHz): pitches 50-200 Hz */
#define PLAG_MAX 20
#define PEVERY 10               /* every this many (10 ms) */
#define PER_SPARE 0.8           /* this well matched: periodic, not wind... */
#define PER_WIND 0.5            /* ...this badly: noise */

static t_class *dcs_dewind_class;

typedef struct _dcs_dewind
{
    t_object x_obj;
    t_float x_f;
    t_float x_amount, x_split, x_above, x_depth;
    double x_sr;
    double x_bq[2][2][2];       /* the high-pass: [channel][section][state], transposed direct form II */
    double x_b[2][3], x_a[2][2];/* its coefficients per section */
    double x_bqsplit, x_bqsr;   /* what they were made for */
    double x_dl1[2], x_dl2[2], x_dh1[2], x_dh2[2];   /* the detector's bands (low-pass states; highs = what's left) */
    double x_el, x_eh;          /* the lows' and highs' mean squares */
    double x_gain;              /* the low band's gain now */
    double x_wind;              /* how windy, 0..1 */
    double x_dacc; int x_dcount, x_dn;   /* averaging the deep lows down to DEC_HZ */
    double x_pbuf[PBUF]; int x_pw, x_pcount;
    double x_per, x_pers;       /* the last periodicity, and smoothed */
} t_dcs_dewind;

static void dcs_dewind_coefs(t_dcs_dewind *x, double sr)
{
    static const double q[2] = { 0.54119610, 1.30656296 };   /* (a 4th-order Butterworth as two sections) */
    int k;
    for (k = 0; k < 2; k++)
    {
        double w0 = 2 * 3.14159265358979 * x->x_split / sr, cw = cos(w0), al = sin(w0) / (2 * q[k]), a0 = 1 + al;
        x->x_b[k][0] = (1 + cw) / 2 / a0; x->x_b[k][1] = -(1 + cw) / a0; x->x_b[k][2] = (1 + cw) / 2 / a0;
        x->x_a[k][0] = -2 * cw / a0; x->x_a[k][1] = (1 - al) / a0;
    }
    x->x_bqsplit = x->x_split; x->x_bqsr = sr;
}

static t_int *dcs_dewind_perform(t_int *w)
{
    t_dcs_dewind *x = (t_dcs_dewind *)(w[1]);
    t_sample *in[2] = { (t_sample *)(w[2]), (t_sample *)(w[3]) };
    t_sample *out[2] = { (t_sample *)(w[4]), (t_sample *)(w[5]) };
    t_sample *windout = (t_sample *)(w[6]);
    int n = (int)(w[7]), i, c;
    double sr = x->x_sr > 0 ? x->x_sr : 48000;
    if (x->x_bqsplit != x->x_split || x->x_bqsr != sr) dcs_dewind_coefs(x, sr);
    double al = 1 - exp(-2 * 3.14159265358979 * DET_LO_HZ / sr), ah = 1 - exp(-2 * 3.14159265358979 * DET_HI_HZ / sr);
    double ke = 1 - exp(-1000. / (ENV_MS * sr));
    double katt = 1 - exp(-1000. / (ATTACK_MS * sr)), krel = 1 - exp(-1000. / (RELEASE_MS * sr));
    double kp = 1 - exp(-1000. / (50. * sr));
    x->x_dn = (int)(sr / DEC_HZ + 0.5);
    if (x->x_dn < 1) x->x_dn = 1;
    double amount = x->x_amount, floor = pow(10, -x->x_depth / 20), above = x->x_above;
    double el = x->x_el, eh = x->x_eh, g = x->x_gain, wind = x->x_wind;
    for (i = 0; i < n; i++)
    {
        double lo[2], hi[2], sv[2] = { in[0][i], in[1][i] }, ls = 0, hs = 0, target;   /* (read first: outs may share memory) */
        for (c = 0; c < 2; c++)
        {
            double s = sv[c];
            {
                double v = s;
                int k;
                for (k = 0; k < 2; k++)
                {
                    double *z = x->x_bq[c][k], y = x->x_b[k][0] * v + z[0];
                    z[0] = x->x_b[k][1] * v - x->x_a[k][0] * y + z[1];
                    z[1] = x->x_b[k][2] * v - x->x_a[k][1] * y;
                    v = y;
                }
                hi[c] = v;                      /* the sound through the high-pass... */
                lo[c] = s;                      /* ...and as it is */
            }
            {
                double h1, h2;
                x->x_dl1[c] += al * (s - x->x_dl1[c]);
                x->x_dl2[c] += al * (x->x_dl1[c] - x->x_dl2[c]);
                x->x_dh1[c] += ah * (s - x->x_dh1[c]);
                h1 = s - x->x_dh1[c];
                x->x_dh2[c] += ah * (h1 - x->x_dh2[c]);
                h2 = h1 - x->x_dh2[c];
                ls += x->x_dl2[c] * x->x_dl2[c];
                hs += h2 * h2;
            }
            if (c == 0) x->x_dacc += x->x_dl2[0];
        }
        if (++x->x_dcount >= x->x_dn)              /* the deep lows at 1 kHz: is there a pitch in them? */
        {
            x->x_pbuf[x->x_pw] = x->x_dacc / x->x_dcount;
            x->x_pw = (x->x_pw + 1) & (PBUF - 1);
            x->x_dacc = 0; x->x_dcount = 0;
            if (++x->x_pcount >= PEVERY)
            {
                int L, j;
                double best = 0;
                x->x_pcount = 0;
                for (L = PLAG_MIN; L <= PLAG_MAX; L++)
                {
                    double sxy = 0, sxx = 0, syy = 0;
                    for (j = 1; j <= PWIN; j++)
                    {
                        double a0 = x->x_pbuf[(x->x_pw - j) & (PBUF - 1)], b0 = x->x_pbuf[(x->x_pw - j - L) & (PBUF - 1)];
                        sxy += a0 * b0; sxx += a0 * a0; syy += b0 * b0;
                    }
                    if (sxx > 0 && syy > 0 && sxy / sqrt(sxx * syy) > best) best = sxy / sqrt(sxx * syy);
                }
                x->x_per = best;
            }
        }
        x->x_pers += kp * (x->x_per - x->x_pers);
        el += ke * (ls - el);
        eh += ke * (hs - eh);
        if (el > MIN_LOW)
        {
            double d = 10 * log10(el / (eh + 1e-12));
            double noisy = (PER_SPARE - x->x_pers) / (PER_SPARE - PER_WIND);
            noisy = noisy < 0 ? 0 : (noisy > 1 ? 1 : noisy);
            wind = (d - above) / SPAN_DB;
            wind = (wind < 0 ? 0 : (wind > 1 ? 1 : wind)) * noisy;
        }
        else wind = 0;
        target = 1 - amount * wind * (1 - floor);
        g += (target < g ? katt : krel) * (target - g);
        if (amount <= 0 && g >= 1 - 1e-9)
        {
            g = 1;
            out[0][i] = (t_sample)sv[0];        /* (off: the sound itself, not lows + highs added back) */
            out[1][i] = (t_sample)sv[1];
        }
        else
            for (c = 0; c < 2; c++)
                out[c][i] = (t_sample)(hi[c] + g * (lo[c] - hi[c]));   /* (g 1: the sound; floor: mostly high-passed) */
        windout[i] = (t_sample)wind;
    }
    for (c = 0; c < 2; c++)
    {
        int k;
        for (k = 0; k < 2; k++)
        {
            if (PD_BIGORSMALL(x->x_bq[c][k][0])) x->x_bq[c][k][0] = 0;
            if (PD_BIGORSMALL(x->x_bq[c][k][1])) x->x_bq[c][k][1] = 0;
        }
        if (PD_BIGORSMALL(x->x_dl1[c])) x->x_dl1[c] = 0;
        if (PD_BIGORSMALL(x->x_dl2[c])) x->x_dl2[c] = 0;
        if (PD_BIGORSMALL(x->x_dh1[c])) x->x_dh1[c] = 0;
        if (PD_BIGORSMALL(x->x_dh2[c])) x->x_dh2[c] = 0;
    }
    x->x_el = el; x->x_eh = eh; x->x_gain = g; x->x_wind = wind;
    return (w + 8);
}

static void dcs_dewind_dsp(t_dcs_dewind *x, t_signal **sp)
{
    x->x_sr = sp[0]->s_sr;
    dsp_add(dcs_dewind_perform, 7, x, sp[0]->s_vec, sp[1]->s_vec, sp[2]->s_vec, sp[3]->s_vec, sp[4]->s_vec,
        (t_int)sp[0]->s_n);
}

static void dcs_dewind_amount(t_dcs_dewind *x, t_floatarg f) { x->x_amount = f < 0 ? 0 : (f > 1 ? 1 : f); }
static void dcs_dewind_split(t_dcs_dewind *x, t_floatarg f) { x->x_split = f < 50 ? 50 : (f > 1000 ? 1000 : f); }
static void dcs_dewind_above(t_dcs_dewind *x, t_floatarg f) { x->x_above = f; }
static void dcs_dewind_depth(t_dcs_dewind *x, t_floatarg f) { x->x_depth = f < 0 ? 0 : (f > 60 ? 60 : f); }

static void *dcs_dewind_new(void)
{
    t_dcs_dewind *x = (t_dcs_dewind *)pd_new(dcs_dewind_class);
    x->x_amount = 0;
    x->x_split = 250;
    x->x_above = 6;
    x->x_depth = 24;
    x->x_gain = 1;
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    return (x);
}

void dcs_dewind_tilde_setup(void)
{
    dcs_dewind_class = class_new(gensym("dcs_dewind~"), (t_newmethod)dcs_dewind_new, 0,
        sizeof(t_dcs_dewind), CLASS_DEFAULT, 0);
    CLASS_MAINSIGNALIN(dcs_dewind_class, t_dcs_dewind, x_f);
    class_addmethod(dcs_dewind_class, (t_method)dcs_dewind_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_dewind_class, (t_method)dcs_dewind_amount, gensym("amount"), A_FLOAT, 0);
    class_addmethod(dcs_dewind_class, (t_method)dcs_dewind_split, gensym("split"), A_FLOAT, 0);
    class_addmethod(dcs_dewind_class, (t_method)dcs_dewind_above, gensym("above"), A_FLOAT, 0);
    class_addmethod(dcs_dewind_class, (t_method)dcs_dewind_depth, gensym("depth"), A_FLOAT, 0);
}
