/* dcs_scatter~: Echo scatter, New style (DadCraft, Oct 20: "grabbing each echo ... and throwing those sounds
 * around"). The echo comes back every T ms (the echo time), so a new place is picked every T ms, as the Classic
 * scatter does: at least half the width from the last one (else the other side), times `amount`. Then three things
 * the Classic one doesn't do:
 *   - direction: the far ear hears the repeat a little later (up to 0.65 ms, as a head does), quieter and darker
 *     (the head's shadow), instead of only quieter;
 *   - throw: while a repeat plays it flies across, from its place towards the other side (`throw` 1 = all the way);
 *   - near and far: each repeat gets a distance up to `depth`: farther = quieter, darker, and more of it to the
 *     reverb (the third outlet, added to the reverb's input).
 * Every move glides over 15 ms. Off (`on 0`, 20 ms fade) it outputs silence and does almost nothing.
 *
 * Inlets: the echo~, the echo time in ms~. Outlets: left~, right~, reverb send~.
 * Messages: amount <0..1>, throw <0..1>, depth <0..1>, on <0|1>.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"
#include <math.h>

static t_class *dcs_scatter_class;
static unsigned int dcs_scatter_count;

#define SC_RING 256                /* the ear delay's line: 0.65 ms is 31 samples at 48 kHz */
#define SC_ITD_MS 0.65f
#define SC_FAR_EAR 0.75f           /* the far ear at a hard side: 1 - this (never silent, so the delay is heard) */
#define SC_FAR_GAIN 0.6f           /* the farthest repeat: 1 - this */

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr;
    t_float amount, throw_, depth;
    dcs_line on;
    unsigned int seed;
    double left, len;              /* samples until the next place, and this window's length */
    t_sample last, start, dist;    /* the last pick (-1..1), this repeat's place, this repeat's distance */
    t_sample pos, d;               /* the gliding place and distance */
    t_sample lpl, lpr, lpl2, lpr2; /* each ear's low-pass (two poles: 12 dB an octave) */
    t_sample ring[SC_RING];
    int w;
} t_dcs_scatter;

static t_sample dcs_scatter_rand(t_dcs_scatter *x)   /* 0..1 */
{
    x->seed = x->seed * 1664525u + 1013904223u;
    return (x->seed >> 8) / 16777216.0f;
}

static void dcs_scatter_pick(t_dcs_scatter *x, t_sample ms)
{
    t_sample r = dcs_scatter_rand(x) * 2 - 1;
    if (fabsf(r - x->last) < 0.5f) r = -r;
    x->last = r;
    x->start = r * x->amount;
    x->dist = dcs_scatter_rand(x) * x->depth;
    x->len = (ms < 20 ? 20 : ms) * x->sr * 0.001;
    x->left = x->len;
}

static t_int *dcs_scatter_perform(t_int *w)
{
    t_dcs_scatter *x = (t_dcs_scatter *)w[1];
    t_sample *in = (t_sample *)w[2], *ms = (t_sample *)w[3];
    t_sample *outl = (t_sample *)w[4], *outr = (t_sample *)w[5], *send = (t_sample *)w[6];
    int n = (int)w[7];
    DCS_SCRATCH(on);
    dcs_line_block(&x->on, on, n, x->sr);
    if (dcs_line_still(&x->on) && x->on.target == 0) {
        for (int i = 0; i < n; i++) outl[i] = outr[i] = send[i] = 0;
        x->lpl = x->lpr = x->lpl2 = x->lpr2 = 0;
        return w + 8;
    }
    t_sample glide = 1 - expf(-1.0f / (0.015f * x->sr));
    t_sample itd = SC_ITD_MS * 0.001f * x->sr;
    /* each ear's darkening for this block: the head's shadow on the far ear, the distance on both */
    t_sample a = fabsf(x->pos), far = powf(0.1f, x->d);
    t_sample fcl = 20000 * far * (x->pos > 0 ? powf(0.2f, a) : 1);
    t_sample fcr = 20000 * far * (x->pos < 0 ? powf(0.2f, a) : 1);
    t_sample nyq = x->sr * 0.45f;
    t_sample kl = 1 - expf(-2 * 3.14159265f * (fcl < nyq ? fcl : nyq) / x->sr);
    t_sample kr = 1 - expf(-2 * 3.14159265f * (fcr < nyq ? fcr : nyq) / x->sr);
    for (int i = 0; i < n; i++) {
        if (x->left <= 0) dcs_scatter_pick(x, ms[i]);
        t_sample phase = 1 - (t_sample)(x->left / x->len);
        x->left -= 1;
        t_sample target = x->start * (1 - 2 * x->throw_ * phase);
        x->pos += glide * (target - x->pos);
        x->d += glide * (x->dist - x->d);
        t_sample f = in[i];
        if (PD_BIGORSMALL(f)) f = 0;
        x->ring[x->w] = f;
        /* the far ear reads a little behind */
        t_sample p = x->pos, back = itd * fabsf(p);
        int ib = (int)back;
        t_sample frac = back - ib;
        t_sample now = f;
        t_sample late = (1 - frac) * x->ring[(x->w - ib) & (SC_RING - 1)]
            + frac * x->ring[(x->w - ib - 1) & (SC_RING - 1)];
        x->w = (x->w + 1) & (SC_RING - 1);
        t_sample l = p > 0 ? late * (1 - SC_FAR_EAR * p) : now;
        t_sample r = p < 0 ? late * (1 + SC_FAR_EAR * p) : now;
        x->lpl += kl * (l - x->lpl);
        x->lpr += kr * (r - x->lpr);
        x->lpl2 += kl * (x->lpl - x->lpl2);
        x->lpr2 += kr * (x->lpr - x->lpr2);
        if (PD_BIGORSMALL(x->lpl2)) x->lpl = x->lpl2 = 0;
        if (PD_BIGORSMALL(x->lpr2)) x->lpr = x->lpr2 = 0;
        t_sample g = (1 - SC_FAR_GAIN * x->d) * on[i];
        outl[i] = x->lpl2 * g;
        outr[i] = x->lpr2 * g;
        send[i] = f * x->d * on[i];
    }
    return w + 8;
}

static void dcs_scatter_dsp(t_dcs_scatter *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_scatter~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dsp_add(dcs_scatter_perform, 7, x, sp[0]->s_vec, sp[1]->s_vec, sp[2]->s_vec, sp[3]->s_vec, sp[4]->s_vec,
        (t_int)sp[0]->s_n);
}

static t_float clamp01(t_floatarg f) { return f < 0 ? 0 : f > 1 ? 1 : f; }
static void dcs_scatter_amount(t_dcs_scatter *x, t_floatarg f) { x->amount = clamp01(f); }
static void dcs_scatter_throw(t_dcs_scatter *x, t_floatarg f) { x->throw_ = clamp01(f); }
static void dcs_scatter_depth(t_dcs_scatter *x, t_floatarg f) { x->depth = clamp01(f); }
static void dcs_scatter_on(t_dcs_scatter *x, t_floatarg f) { dcs_line_set(&x->on, f != 0, 20); }

static void *dcs_scatter_new(void)
{
    t_dcs_scatter *x = (t_dcs_scatter *)pd_new(dcs_scatter_class);
    x->sr = 44100;
    x->amount = 0.5f;
    x->throw_ = 0.5f;
    x->depth = 0.5f;
    x->seed = ++dcs_scatter_count * 2654435761u + 12345u;
    x->left = 0;
    x->len = 1;
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_scatter_tilde_setup(void)
{
    dcs_scatter_class = class_new(gensym("dcs_scatter~"), (t_newmethod)dcs_scatter_new, 0,
        sizeof(t_dcs_scatter), 0, 0);
    CLASS_MAINSIGNALIN(dcs_scatter_class, t_dcs_scatter, x_f);
    class_addmethod(dcs_scatter_class, (t_method)dcs_scatter_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_scatter_class, (t_method)dcs_scatter_amount, gensym("amount"), A_FLOAT, 0);
    class_addmethod(dcs_scatter_class, (t_method)dcs_scatter_throw, gensym("throw"), A_FLOAT, 0);
    class_addmethod(dcs_scatter_class, (t_method)dcs_scatter_depth, gensym("depth"), A_FLOAT, 0);
    class_addmethod(dcs_scatter_class, (t_method)dcs_scatter_on, gensym("on"), A_FLOAT, 0);
}
