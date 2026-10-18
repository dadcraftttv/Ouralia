/* dcs_taps~: the two-tap delay that the Classic pitch shifter and the reverse echo are made of, sample for sample
 * as the Pd objects it replaces: a phasor at ra x rb Hz sweeps two reads (half a cycle apart) across win ms of the
 * line, 4 ms behind the write, each under a Hann window (0.5 - 0.5 cos), summed. Reading a delay that changes at
 * rate (1 - ratio) shifts pitch by ratio; one that grows 2 ms per ms plays backwards. `run 0` stops it as Pd's
 * switch~ stopped its subpatch (silent).
 *
 * Messages: ra <Hz>, rb <x> (1 unless set), win <ms>, smooth <ms>, run <0|1>. Argument: the line's length in ms.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_taps_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    int run;
    double phase;
    dcs_delay d;
    dcs_line ra, rb, win;
} t_dcs_taps;

static t_int *dcs_taps_perform(t_int *w)
{
    t_dcs_taps *x = (t_dcs_taps *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    DCS_SCRATCH(ra); DCS_SCRATCH(rb); DCS_SCRATCH(win);
    dcs_line_block(&x->ra, ra, n, x->sr);
    dcs_line_block(&x->rb, rb, n, x->sr);
    dcs_line_block(&x->win, win, n, x->sr);
    if (!x->run) {
        for (int i = 0; i < n; i++) out[i] = 0;
        return w + 5;
    }
    DCS_SCRATCH(rate); DCS_SCRATCH(p1); DCS_SCRATCH(p2); DCS_SCRATCH(ms); DCS_SCRATCH(r1); DCS_SCRATCH(r2);
    for (int i = 0; i < n; i++) rate[i] = ra[i] * rb[i];
    dcs_phasor_block(&x->phase, rate, 0, p1, n, x->sr);
    for (int i = 0; i < n; i++) p2[i] = dcs_wrap(p1[i] + 0.5f);
    dcs_delay_write(&x->d, in, n);      /* (the reads are 4 ms or more behind: Pd's order didn't matter) */
    for (int i = 0; i < n; i++) ms[i] = p1[i] * win[i] + 4;
    dcs_delay_read4(&x->d, ms, 0, r1, n, x->sr, 0);
    for (int i = 0; i < n; i++) ms[i] = p2[i] * win[i] + 4;
    dcs_delay_read4(&x->d, ms, 0, r2, n, x->sr, 0);
    for (int i = 0; i < n; i++)
        out[i] = r1[i] * (dcs_cos(p1[i]) * -0.5f + 0.5f) + r2[i] * (dcs_cos(p2[i]) * -0.5f + 0.5f);
    return w + 5;
}

static void dcs_taps_dsp(t_dcs_taps *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_taps~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->d, x->sr, sp[0]->s_n);
    dsp_add(dcs_taps_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_taps_ra(t_dcs_taps *x, t_floatarg f) { dcs_line_set(&x->ra, f, x->smooth); }
static void dcs_taps_rb(t_dcs_taps *x, t_floatarg f) { dcs_line_set(&x->rb, f, x->smooth); }
static void dcs_taps_win(t_dcs_taps *x, t_floatarg f) { dcs_line_set(&x->win, f, x->smooth); }
static void dcs_taps_smooth(t_dcs_taps *x, t_floatarg f) { x->smooth = f; }
static void dcs_taps_run(t_dcs_taps *x, t_floatarg f) { x->run = f != 0; }

static void *dcs_taps_new(t_floatarg ms)
{
    t_dcs_taps *x = (t_dcs_taps *)pd_new(dcs_taps_class);
    x->smooth = 30;
    x->sr = 44100;
    x->run = 0;                 /* (a switch~ starts off) */
    dcs_line_set(&x->rb, 1, 0);
    dcs_delay_init(&x->d, ms > 0 ? ms : 400);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_taps_free(t_dcs_taps *x) { dcs_delay_free(&x->d); }

void dcs_taps_tilde_setup(void)
{
    dcs_taps_class = class_new(gensym("dcs_taps~"), (t_newmethod)dcs_taps_new, (t_method)dcs_taps_free,
        sizeof(t_dcs_taps), 0, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_taps_class, t_dcs_taps, x_f);
    class_addmethod(dcs_taps_class, (t_method)dcs_taps_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_taps_class, (t_method)dcs_taps_ra, gensym("ra"), A_FLOAT, 0);
    class_addmethod(dcs_taps_class, (t_method)dcs_taps_rb, gensym("rb"), A_FLOAT, 0);
    class_addmethod(dcs_taps_class, (t_method)dcs_taps_win, gensym("win"), A_FLOAT, 0);
    class_addmethod(dcs_taps_class, (t_method)dcs_taps_smooth, gensym("smooth"), A_FLOAT, 0);
    class_addmethod(dcs_taps_class, (t_method)dcs_taps_run, gensym("run"), A_FLOAT, 0);
}
