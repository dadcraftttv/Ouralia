/* dcs_filter~: the filter, sample for sample as the Pd objects it replaces: one biquad (low-pass, high-pass or
 * band-pass, audio EQ cookbook) whose frequency glides in log steps every 3 ms ([log] -> [line 0 3] -> [expr] ->
 * [biquad~], the same arithmetic in floats, the same clock), mixed in by the amount. While the amount is 0 only the
 * biquad runs (its state carries on, so turning on again matches).
 *
 * Messages: freq <Hz>, q <q>, type <0 low-pass | 1 high-pass | 2 band-pass>, mix <0..1>, on <0|1>, smooth <ms>.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_filter_class;

#define UNITS_PER_MS (32. * 441.)   /* Pd's logical time units */
#define GRAIN_MS 3

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, q, type;
    dcs_line mix, on;
    /* [line 0 3]: Pd's own clock arithmetic, in Pd's time units */
    double now, prevtime, targettime, overtimediff, clocktime;
    t_float setval, targetval, lineout;
    int clockset;
    /* [biquad~] */
    t_sample fb1, fb2, ff1, ff2, ff3, x1, x2;
} t_dcs_filter;

/* [expr] of the cookbook coefficients, as the patch had it (ints where expr had ints), then [biquad~]'s list */
static void dcs_filter_coefs(t_dcs_filter *x)
{
    t_float f1 = x->lineout, S = x->sr > 8000 ? x->sr : 8000;
    t_float e = (t_float)exp((double)f1);
    t_float mx = e > 20 ? e : 20, lim = 0.45f * S;
    t_float mn = mx < lim ? mx : lim;
    t_float W = 6.283185f * mn / S;
    t_float qq = x->q > 0.1f ? x->q : 0.1f;
    t_float A = (t_float)sin((double)W) / (2 * qq), C = (t_float)cos((double)W);
    t_float a0 = 1 + A;
    int lp = x->type < 0.5f, hp = (x->type > 0.5f) * (x->type < 1.5f), bp = x->type > 1.5f;
    t_float fb1 = 2 * C / a0, fb2 = -(1 - A) / a0;
    t_float l = lp * (1 - C), h = hp * (1 + C);
    t_float ff1 = (l / 2 + h / 2 + bp * A) / a0, ff2 = (l - h) / a0, ff3 = (l / 2 + h / 2 - bp * A) / a0;
    t_float disc = fb1 * fb1 + 4 * fb2;
    int stable = disc < 0 ? fb2 >= -1.0f : (fb1 <= 2.0f && fb1 >= -2.0f && 1.0f - fb1 - fb2 >= 0 && 1.0f + fb1 - fb2 >= 0);
    if (!stable) fb1 = fb2 = ff1 = ff2 = ff3 = 0;
    x->fb1 = fb1; x->fb2 = fb2; x->ff1 = ff1; x->ff2 = ff2; x->ff3 = ff3;
}

static void line_out(t_dcs_filter *x, t_float f)
{
    x->lineout = f;
    dcs_filter_coefs(x);
}

/* [line]'s clock tick at Pd time `t` */
static void line_tick(t_dcs_filter *x, double t)
{
    double msectogo = (x->targettime - t) / UNITS_PER_MS;
    x->clockset = 0;
    if (msectogo < 1E-9)
        line_out(x, x->targetval);
    else {
        line_out(x, x->setval + x->overtimediff * (t - x->prevtime) * (x->targetval - x->setval));
        x->clocktime = t + UNITS_PER_MS * (GRAIN_MS > msectogo ? msectogo : GRAIN_MS);
        x->clockset = 1;
    }
}

static t_int *dcs_filter_perform(t_int *w)
{
    t_dcs_filter *x = (t_dcs_filter *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    /* Pd's scheduler: every clock due before the end of this block fires first, then the block is computed */
    double next = x->now + (double)((t_float)n / x->sr) * (UNITS_PER_MS * 1000.);
    while (x->clockset && x->clocktime < next)
        line_tick(x, x->clocktime);
    x->now = next;
    int off = dcs_line_still(&x->mix) && dcs_line_still(&x->on) && x->mix.target * x->on.target == 0;
    t_sample last = x->x1, prev = x->x2, fb1 = x->fb1, fb2 = x->fb2, ff1 = x->ff1, ff2 = x->ff2, ff3 = x->ff3;
    if (off) {
        for (int i = 0; i < n; i++) {   /* the biquad carries on; the output is the input */
            t_sample o = in[i] + fb1 * last + fb2 * prev;
            if (PD_BIGORSMALL(o)) o = 0;
            prev = last; last = o;
        }
        x->x1 = last; x->x2 = prev;
        dcs_line_skip(&x->mix, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        if (in != out) memmove(out, in, n * sizeof(t_sample));
        return w + 5;
    }
    DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    for (int i = 0; i < n; i++) {
        t_sample f = in[i];
        t_sample o = f + fb1 * last + fb2 * prev;
        if (PD_BIGORSMALL(o)) o = 0;
        t_sample filt = ff1 * o + ff2 * last + ff3 * prev;
        prev = last; last = o;
        out[i] = f + (filt - f) * (mix[i] * on[i]);
    }
    x->x1 = last; x->x2 = prev;
    return w + 5;
}

static void dcs_filter_dsp(t_dcs_filter *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_filter~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_filter_coefs(x);        /* (the patch's [samplerate~] at load) */
    dsp_add(dcs_filter_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

/* [log] -> [pack f <smooth>] -> [line 0 3] */
static void dcs_filter_freq(t_dcs_filter *x, t_floatarg hz)
{
    t_float f = hz <= 0 ? -1000 : logf(hz), ms = x->smooth;
    double timenow = x->now;
    if (ms > 0) {
        if (timenow > x->targettime) x->setval = x->targetval;
        else x->setval = x->setval + x->overtimediff * (timenow - x->prevtime) * (x->targetval - x->setval);
        x->prevtime = timenow;
        x->targettime = timenow + UNITS_PER_MS * ms;
        x->targetval = f;
        line_tick(x, timenow);   /* (line_float's own call: the value now) */
        x->overtimediff = 1. / (x->targettime - timenow);
        x->clocktime = timenow + UNITS_PER_MS * (GRAIN_MS > ms ? ms : GRAIN_MS);
        x->clockset = 1;
    } else {
        x->clockset = 0;
        x->targetval = x->setval = f;
        line_out(x, f);
    }
}

static void dcs_filter_q(t_dcs_filter *x, t_floatarg f) { x->q = f; dcs_filter_coefs(x); }
static void dcs_filter_type(t_dcs_filter *x, t_floatarg f) { x->type = f; dcs_filter_coefs(x); }
static void dcs_filter_mix(t_dcs_filter *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_filter_on(t_dcs_filter *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_filter_smooth(t_dcs_filter *x, t_floatarg f) { x->smooth = f; }

static void *dcs_filter_new(void)
{
    t_dcs_filter *x = (t_dcs_filter *)pd_new(dcs_filter_class);
    x->smooth = 30;
    dcs_filter_coefs(x);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_filter_tilde_setup(void)
{
    dcs_filter_class = class_new(gensym("dcs_filter~"), (t_newmethod)dcs_filter_new, 0, sizeof(t_dcs_filter), 0, 0);
    CLASS_MAINSIGNALIN(dcs_filter_class, t_dcs_filter, x_f);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_freq, gensym("freq"), A_FLOAT, 0);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_q, gensym("q"), A_FLOAT, 0);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_type, gensym("type"), A_FLOAT, 0);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_filter_class, (t_method)dcs_filter_smooth, gensym("smooth"), A_FLOAT, 0);
}
