/* dcs_phaser~: the phaser, sample for sample as the Pd objects it replaces: four allpass biquads swept together,
 * their output fed back (a fixed delay, as delread~ read it) and mixed 50/50 with the input at amount 1. The sweep
 * is control-rate as it was: every 4 ms ([metro]) the LFO's last value ([snapshot~] of osc~) sets
 * centre x 2^(2 x lfo x depth) Hz, and each stage's coefficients follow from it ([expr], in floats).
 * `run 0` stops it as Pd's switch~ stopped its subpatch (and the metro with it).
 *
 * Messages: rate <Hz>, depth <0..1> (read at each sweep step), fb <-1..1>, mix <0..1>, on <0|1>, smooth <ms>,
 * run <0|1>. Arguments: centre Hz (700), q (0.7), the feedback delay in ms (1.4), then the 4 stage multipliers.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_phaser_class;

#define NSTAGE 4
#define UNITS_PER_MS (32. * 441.)
#define METRO_MS 4

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, depth, centre, q, fbms, mult[NSTAGE];
    int run, metro;
    double now, metrotime;      /* Pd's logical time, in its units */
    t_sample snap;              /* [snapshot~]: the LFO's last sample */
    dcs_osc osc;
    dcs_delay d;
    dcs_line rate, fb, mix, on;
    t_sample c[NSTAGE][5], z[NSTAGE][2];
} t_dcs_phaser;

/* one sweep step: [snapshot~] -> [expr centre*pow(2, 2*$f1*$f2)] -> each stage's [expr] -> [biquad~] */
static void sweep(t_dcs_phaser *x)
{
    t_float sw = x->centre * (t_float)pow((double)2, (double)(2 * x->snap * x->depth));
    t_float S = x->sr > 8000 ? x->sr : 8000;
    for (int k = 0; k < NSTAGE; k++) {
        t_float m = sw * x->mult[k];
        t_float mx = m > 20 ? m : 20, lim = 0.45f * S;
        t_float W = 6.283185f * (mx < lim ? mx : lim) / S;
        t_float A = (t_float)sin((double)W) / (2 * x->q), C = (t_float)cos((double)W);
        t_float a0 = 1 + A;
        t_float fb1 = 2 * C / a0, fb2 = -(1 - A) / a0, ff1 = (1 - A) / a0, ff2 = -2 * C / a0, ff3 = (1 + A) / a0;
        t_float disc = fb1 * fb1 + 4 * fb2;
        int stable = disc < 0 ? fb2 >= -1.0f
            : (fb1 <= 2.0f && fb1 >= -2.0f && 1.0f - fb1 - fb2 >= 0 && 1.0f + fb1 - fb2 >= 0);
        if (!stable) fb1 = fb2 = ff1 = ff2 = ff3 = 0;
        x->c[k][0] = fb1; x->c[k][1] = fb2; x->c[k][2] = ff1; x->c[k][3] = ff2; x->c[k][4] = ff3;
    }
}

static void metro_tick(t_dcs_phaser *x, double t)
{
    sweep(x);
    x->metrotime = t + UNITS_PER_MS * METRO_MS;
}

static t_int *dcs_phaser_perform(t_int *w)
{
    t_dcs_phaser *x = (t_dcs_phaser *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    double next = x->now + (double)((t_float)n / x->sr) * (UNITS_PER_MS * 1000.);
    while (x->metro && x->metrotime < next)      /* (Pd's scheduler: clocks due before this block's end, first) */
        metro_tick(x, x->metrotime);
    x->now = next;
    DCS_SCRATCH(rate); DCS_SCRATCH(fb); DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_line_block(&x->rate, rate, n, x->sr);
    dcs_line_block(&x->fb, fb, n, x->sr);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    if (!x->run) {
        for (int i = 0; i < n; i++) {
            t_sample wk = (mix[i] * on[i]) * 0.5f;
            out[i] = 0 * wk + in[i] * (wk * -1 + 1);
        }
        return w + 5;
    }
    DCS_SCRATCH(lfo); DCS_SCRATCH(rd); DCS_SCRATCH(y);
    dcs_osc_block(&x->osc, rate, 0, lfo, n, x->sr);
    dcs_delay_read(&x->d, x->fbms, rd, n, x->sr, n);   /* (the reader comes first: it feeds the writer) */
    for (int i = 0; i < n; i++) y[i] = in[i] + rd[i] * fb[i];
    for (int k = 0; k < NSTAGE; k++) {                  /* each [biquad~] in turn, over the block */
        t_sample fb1 = x->c[k][0], fb2 = x->c[k][1], ff1 = x->c[k][2], ff2 = x->c[k][3], ff3 = x->c[k][4];
        t_sample last = x->z[k][0], prev = x->z[k][1];
        for (int i = 0; i < n; i++) {
            t_sample o = y[i] + fb1 * last + fb2 * prev;
            if (PD_BIGORSMALL(o)) o = 0;
            y[i] = ff1 * o + ff2 * last + ff3 * prev;
            prev = last; last = o;
        }
        x->z[k][0] = last; x->z[k][1] = prev;
    }
    dcs_delay_write(&x->d, y, n);
    x->snap = lfo[n - 1];
    for (int i = 0; i < n; i++) {
        t_sample wk = (mix[i] * on[i]) * 0.5f;
        out[i] = y[i] * wk + in[i] * (wk * -1 + 1);
    }
    return w + 5;
}

static void dcs_phaser_dsp(t_dcs_phaser *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_phaser~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->d, x->sr, sp[0]->s_n);
    dsp_add(dcs_phaser_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_phaser_rate(t_dcs_phaser *x, t_floatarg f) { dcs_line_set(&x->rate, f, x->smooth); }
static void dcs_phaser_depth(t_dcs_phaser *x, t_floatarg f) { x->depth = f; }
static void dcs_phaser_fb(t_dcs_phaser *x, t_floatarg f) { dcs_line_set(&x->fb, f, x->smooth); }
static void dcs_phaser_mix(t_dcs_phaser *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_phaser_on(t_dcs_phaser *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_phaser_smooth(t_dcs_phaser *x, t_floatarg f) { x->smooth = f; }
/* the switch: the subpatch and its [metro] (which steps at once when started) */
static void dcs_phaser_run(t_dcs_phaser *x, t_floatarg f)
{
    x->run = f != 0;
    x->metro = x->run;
    if (x->run) metro_tick(x, x->now);
}

static void *dcs_phaser_new(t_symbol *s, int argc, t_atom *argv)
{
    t_dcs_phaser *x = (t_dcs_phaser *)pd_new(dcs_phaser_class);
    static const t_float mults[NSTAGE] = {1, 1.7f, 2.9f, 5};
    x->smooth = 30;
    x->sr = 44100;
    x->centre = atom_getfloatarg(0, argc, argv);
    x->q = atom_getfloatarg(1, argc, argv);
    x->fbms = atom_getfloatarg(2, argc, argv);
    if (x->centre <= 0) x->centre = 700;
    if (x->q <= 0) x->q = 0.7f;
    if (x->fbms <= 0) x->fbms = 1.4f;
    for (int k = 0; k < NSTAGE; k++) {
        x->mult[k] = argc > 3 + k ? atom_getfloatarg(3 + k, argc, argv) : mults[k];
        x->c[k][2] = 0;
    }
    x->run = 0;                 /* (a switch~ starts off: the metro runs, as the loadbang started it) */
    x->metro = 1;               /* (the patch's loadbang started the metro) */
    sweep(x);
    x->metrotime = UNITS_PER_MS * METRO_MS;
    dcs_delay_init(&x->d, 20);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_phaser_free(t_dcs_phaser *x) { dcs_delay_free(&x->d); }

void dcs_phaser_tilde_setup(void)
{
    dcs_phaser_class = class_new(gensym("dcs_phaser~"), (t_newmethod)dcs_phaser_new, (t_method)dcs_phaser_free,
        sizeof(t_dcs_phaser), 0, A_GIMME, 0);
    CLASS_MAINSIGNALIN(dcs_phaser_class, t_dcs_phaser, x_f);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_rate, gensym("rate"), A_FLOAT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_depth, gensym("depth"), A_FLOAT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_fb, gensym("fb"), A_FLOAT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_smooth, gensym("smooth"), A_FLOAT, 0);
    class_addmethod(dcs_phaser_class, (t_method)dcs_phaser_run, gensym("run"), A_FLOAT, 0);
}
