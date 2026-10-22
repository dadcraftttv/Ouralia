/* dcs_listen~: what kind of place it sounds like: talking, music, traffic, wind.
 *
 * Listens to the last 2 s and says, twice a second, how much it sounds like each (0..1). Cheap measures, no FFT:
 *  - voicing: the sound low-passed (900 Hz), averaged down to 2 kHz, matched against itself at pitch lags
 *    (50-400 Hz) every 40 ms; a frame is voiced when the best normalised match passes 0.6 over -55 dBFS;
 *  - syllables: the 300 Hz - 3 kHz band's level every 10 ms, less its own 250 ms average: how far it swings
 *    (speech's syllables: 6+ dB; music and traffic swing less);
 *  - gaps: the share of 10 ms frames 15 dB under the window's loudest (speech pauses; music rarely);
 *  - steadiness: of consecutive voiced frames, the share whose pitch stays put (music holds notes, speech glides);
 *  - lows: how far the deep lows (< 150 Hz) stand over the voice band (traffic's rumble);
 *  - wind: the second inlet's windiness (dcs_dewind~'s third outlet), averaged.
 * Talking = some voicing, deep syllables, pauses; music = much voicing, steady pitch, few gaps; traffic = little
 * voicing, little swing, heavy lows; wind = the windiness. A quiet place (loudest under -55 dBFS) is none of them.
 *
 *   [dcs_listen~]
 *   inlets:  signal (the mic), signal (windiness 0..1)
 *   outlet:  list <talking> <music> <traffic> <wind>, every 0.5 s
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "m_pd.h"
#include <math.h>
#include <stdlib.h>
#include <string.h>

#define FRAME_MS 10.            /* the level frames */
#define NFR 200                 /* the window: 2 s of them */
#define DEC_HZ 2000.            /* voicing runs on the sound at this rate... */
#define DBUF 256                /* (power of two) */
#define VWIN 80                 /* ...over 40 ms */
#define VLAG_MIN 5              /* 400 Hz */
#define VLAG_MAX 40             /* 50 Hz */
#define VEVERY 4                /* a voicing frame every 4 level frames (40 ms) */
#define NVF (NFR / VEVERY)
#define VOICED 0.6
#define QUIET_DB -55.
#define REPORT_FR 50            /* every 0.5 s */
#define AVG_FR 25               /* the syllable swing's own average: 250 ms */

static t_class *dcs_listen_class;

typedef struct _dcs_listen
{
    t_object x_obj;
    t_float x_f;
    double x_sr;
    double x_lp1, x_lp2, x_dacc; int x_dcount, x_dn;      /* toward the 2 kHz voicing signal */
    double x_dbuf[DBUF]; int x_dw;
    double x_bh, x_bl;          /* the voice band: one-pole states (300 Hz high-pass, 3 kHz low-pass) */
    double x_l1, x_l2;          /* the deep lows */
    double x_emid, x_elow, x_eall, x_wsum; int x_fcount, x_fn;
    double x_fmid[NFR], x_flow[NFR], x_fall[NFR], x_fwind[NFR]; int x_fw, x_filled;
    int x_voiced[NVF], x_lag[NVF]; int x_vw, x_vcount;
    int x_rcount;
    t_float x_out[4];
    t_clock *x_clock;
    t_outlet *x_outlet;
} t_dcs_listen;

static double ramp(double v, double a, double b)
{
    double r = (v - a) / (b - a);
    return r < 0 ? 0 : (r > 1 ? 1 : r);
}

static void dcs_listen_voicing(t_dcs_listen *x)
{
    int L, j, bestlag = 0;
    double best = 0, e = 0;
    for (j = 1; j <= VWIN; j++)
    {
        double a = x->x_dbuf[(x->x_dw - j) & (DBUF - 1)];
        e += a * a;
    }
    for (L = VLAG_MIN; L <= VLAG_MAX; L++)
    {
        double sxy = 0, sxx = 0, syy = 0;
        for (j = 1; j <= VWIN; j++)
        {
            double a = x->x_dbuf[(x->x_dw - j) & (DBUF - 1)], b = x->x_dbuf[(x->x_dw - j - L) & (DBUF - 1)];
            sxy += a * b; sxx += a * a; syy += b * b;
        }
        if (sxx > 0 && syy > 0)
        {
            double r = sxy / sqrt(sxx * syy);
            if (r > best) best = r, bestlag = L;
        }
    }
    x->x_voiced[x->x_vw] = best > VOICED && 10 * log10(e / VWIN + 1e-12) > QUIET_DB - 6;
    x->x_lag[x->x_vw] = bestlag;
    x->x_vw = (x->x_vw + 1) % NVF;
}

static void dcs_listen_judge(t_dcs_listen *x)
{
    int i, n = x->x_filled < NFR ? x->x_filled : NFR, nv = n / VEVERY;
    double top = -200, sw = 0, gaps = 0, lows = 0, wind = 0, vf = 0, steady = 0, pairs = 0;
    double talk, music, traffic, windy;
    if (n < AVG_FR * 2) return;
    for (i = 0; i < n; i++)
        if (x->x_fall[i] > top) top = x->x_fall[i];
    for (i = 0; i < n; i++)
    {
        int k = (x->x_fw - 1 - i + NFR) % NFR, m;
        double avg = 0;
        for (m = 0; m < AVG_FR; m++) avg += x->x_fmid[(k - m + NFR) % NFR];
        avg /= AVG_FR;
        sw += (x->x_fmid[k] - avg) * (x->x_fmid[k] - avg);
        if (x->x_fall[k] < top - 15) gaps += 1;
        lows += x->x_flow[k] - x->x_fmid[k];
        wind += x->x_fwind[k];
    }
    sw = sqrt(sw / n); gaps /= n; lows /= n; wind /= n;
    for (i = 0; i < nv; i++)
    {
        int k = (x->x_vw - 1 - i + NVF) % NVF, p = (k - 1 + NVF) % NVF;
        vf += x->x_voiced[k];
        if (i + 1 < nv && x->x_voiced[k] && x->x_voiced[p])
        {
            pairs += 1;
            if (abs(x->x_lag[k] - x->x_lag[p]) <= 1) steady += 1;
        }
    }
    vf = nv ? vf / nv : 0;
    steady = pairs > 2 ? steady / pairs : 0;
    if (top < QUIET_DB)
        talk = music = traffic = windy = 0;
    else
    {
        windy = ramp(wind, 0.2, 0.5);
        talk = ramp(vf, 0.15, 0.3) * (1 - ramp(vf, 0.8, 0.95)) * ramp(sw, 3, 6) * ramp(gaps, 0.04, 0.12);
        music = ramp(vf, 0.5, 0.75) * ramp(steady, 0.4, 0.7) * (1 - ramp(gaps, 0.25, 0.45));
        traffic = (1 - ramp(vf, 0.1, 0.3)) * (1 - ramp(sw, 2.5, 5)) * ramp(lows, -6, 3);
        traffic *= 1 - windy;
    }
    x->x_out[0] = (t_float)talk; x->x_out[1] = (t_float)music; x->x_out[2] = (t_float)traffic; x->x_out[3] = (t_float)windy;
    clock_delay(x->x_clock, 0);
}

static void dcs_listen_tick(t_dcs_listen *x)
{
    t_atom a[4];
    int i;
    for (i = 0; i < 4; i++) SETFLOAT(a + i, x->x_out[i]);
    outlet_list(x->x_outlet, &s_list, 4, a);
}

static t_int *dcs_listen_perform(t_int *w)
{
    t_dcs_listen *x = (t_dcs_listen *)(w[1]);
    t_sample *in = (t_sample *)(w[2]), *win = (t_sample *)(w[3]);
    int n = (int)(w[4]), i;
    double sr = x->x_sr > 0 ? x->x_sr : 48000;
    double a9 = 1 - exp(-2 * 3.14159265358979 * 900 / sr), a3 = 1 - exp(-2 * 3.14159265358979 * 300 / sr);
    double a30 = 1 - exp(-2 * 3.14159265358979 * 3000 / sr), a15 = 1 - exp(-2 * 3.14159265358979 * 150 / sr);
    for (i = 0; i < n; i++)
    {
        double s = in[i], b;
        x->x_lp1 += a9 * (s - x->x_lp1);
        x->x_lp2 += a9 * (x->x_lp1 - x->x_lp2);
        x->x_dacc += x->x_lp2;
        if (++x->x_dcount >= x->x_dn)
        {
            x->x_dbuf[x->x_dw] = x->x_dacc / x->x_dcount;
            x->x_dw = (x->x_dw + 1) & (DBUF - 1);
            x->x_dacc = 0; x->x_dcount = 0;
        }
        x->x_bh += a3 * (s - x->x_bh);
        b = s - x->x_bh;
        x->x_bl += a30 * (b - x->x_bl);
        x->x_l1 += a15 * (s - x->x_l1);
        x->x_l2 += a15 * (x->x_l1 - x->x_l2);
        x->x_emid += x->x_bl * x->x_bl;
        x->x_elow += x->x_l2 * x->x_l2;
        x->x_eall += s * s;
        x->x_wsum += win[i];
        if (++x->x_fcount >= x->x_fn)
        {
            int c = x->x_fcount;
            x->x_fmid[x->x_fw] = 10 * log10(x->x_emid / c + 1e-12);
            x->x_flow[x->x_fw] = 10 * log10(x->x_elow / c + 1e-12);
            x->x_fall[x->x_fw] = 10 * log10(x->x_eall / c + 1e-12);
            x->x_fwind[x->x_fw] = x->x_wsum / c;
            x->x_fw = (x->x_fw + 1) % NFR;
            if (x->x_filled < NFR) x->x_filled++;
            x->x_emid = x->x_elow = x->x_eall = x->x_wsum = 0; x->x_fcount = 0;
            if (++x->x_vcount >= VEVERY) { x->x_vcount = 0; dcs_listen_voicing(x); }
            if (++x->x_rcount >= REPORT_FR) { x->x_rcount = 0; dcs_listen_judge(x); }
        }
    }
    if (PD_BIGORSMALL(x->x_lp1)) x->x_lp1 = 0;
    if (PD_BIGORSMALL(x->x_lp2)) x->x_lp2 = 0;
    if (PD_BIGORSMALL(x->x_bh)) x->x_bh = 0;
    if (PD_BIGORSMALL(x->x_bl)) x->x_bl = 0;
    if (PD_BIGORSMALL(x->x_l1)) x->x_l1 = 0;
    if (PD_BIGORSMALL(x->x_l2)) x->x_l2 = 0;
    return (w + 5);
}

static void dcs_listen_dsp(t_dcs_listen *x, t_signal **sp)
{
    x->x_sr = sp[0]->s_sr;
    x->x_dn = (int)(x->x_sr / DEC_HZ + 0.5); if (x->x_dn < 1) x->x_dn = 1;
    x->x_fn = (int)(x->x_sr * FRAME_MS / 1000 + 0.5); if (x->x_fn < 1) x->x_fn = 1;
    dsp_add(dcs_listen_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void *dcs_listen_new(void)
{
    t_dcs_listen *x = (t_dcs_listen *)pd_new(dcs_listen_class);
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    x->x_outlet = outlet_new(&x->x_obj, &s_list);
    x->x_clock = clock_new(x, (t_method)dcs_listen_tick);
    x->x_dn = 24; x->x_fn = 480;
    return (x);
}

static void dcs_listen_free(t_dcs_listen *x)
{
    clock_free(x->x_clock);
}

void dcs_listen_tilde_setup(void)
{
    dcs_listen_class = class_new(gensym("dcs_listen~"), (t_newmethod)dcs_listen_new, (t_method)dcs_listen_free,
        sizeof(t_dcs_listen), CLASS_DEFAULT, 0);
    CLASS_MAINSIGNALIN(dcs_listen_class, t_dcs_listen, x_f);
    class_addmethod(dcs_listen_class, (t_method)dcs_listen_dsp, gensym("dsp"), A_CANT, 0);
}
