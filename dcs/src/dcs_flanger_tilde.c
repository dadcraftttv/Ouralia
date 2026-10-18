/* dcs_flanger~: the flanger, sample for sample as the Pd objects it replaces: a delay swept between min and
 * min + depth ms ((1 - cos) / 2 of a phasor), fed back by fb, its output scaled by 1 - |fb| / 2, mixed 50/50 with
 * the input at amount 1. `run 0` stops it as Pd's switch~ stopped its subpatch.
 *
 * Messages: rate <Hz>, depth <ms>, fb <-1..1>, mix <0..1>, on <0|1>, smooth <ms>, run <0|1>.
 * Arguments: the shortest delay in ms (2), the line's length in ms (30).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_flanger_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, minms;
    int run;
    double phase;
    dcs_delay d;
    dcs_line rate, depth, fb, k, mix, on;
} t_dcs_flanger;

static t_int *dcs_flanger_perform(t_int *w)
{
    t_dcs_flanger *x = (t_dcs_flanger *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    DCS_SCRATCH(rate); DCS_SCRATCH(depth); DCS_SCRATCH(fb); DCS_SCRATCH(k); DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_line_block(&x->rate, rate, n, x->sr);
    dcs_line_block(&x->depth, depth, n, x->sr);
    dcs_line_block(&x->fb, fb, n, x->sr);
    dcs_line_block(&x->k, k, n, x->sr);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    if (!x->run) {
        for (int i = 0; i < n; i++) {
            t_sample wk = (mix[i] * on[i]) * 0.5f;
            out[i] = 0 * wk + in[i] * (wk * -1 + 1);
        }
        return w + 5;
    }
    DCS_SCRATCH(ph); DCS_SCRATCH(ms); DCS_SCRATCH(rd); DCS_SCRATCH(wr);
    dcs_phasor_block(&x->phase, rate, 0, ph, n, x->sr);
    for (int i = 0; i < n; i++) ms[i] = (dcs_cos(ph[i]) * -0.5f + 0.5f) * depth[i] + x->minms;
    dcs_delay_read4(&x->d, ms, 0, rd, n, x->sr, n);   /* (the reader comes first: it feeds the writer) */
    for (int i = 0; i < n; i++) {
        t_sample f = in[i];
        wr[i] = f + rd[i] * fb[i];
        t_sample wk = (mix[i] * on[i]) * 0.5f;
        out[i] = (rd[i] * k[i]) * wk + f * (wk * -1 + 1);
    }
    dcs_delay_write(&x->d, wr, n);
    return w + 5;
}

static void dcs_flanger_dsp(t_dcs_flanger *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_flanger~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->d, x->sr, sp[0]->s_n);
    dsp_add(dcs_flanger_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_flanger_rate(t_dcs_flanger *x, t_floatarg f) { dcs_line_set(&x->rate, f, x->smooth); }
static void dcs_flanger_depth(t_dcs_flanger *x, t_floatarg f) { dcs_line_set(&x->depth, f, x->smooth); }
/* feedback, and the level that takes the resonance back out: [abs] [* -0.5] [+ 1] */
static void dcs_flanger_fb(t_dcs_flanger *x, t_floatarg f)
{
    t_float a = fabsf(f), b = a * -0.5f, c = b + 1;
    dcs_line_set(&x->fb, f, x->smooth);
    dcs_line_set(&x->k, c, x->smooth);
}
static void dcs_flanger_mix(t_dcs_flanger *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_flanger_on(t_dcs_flanger *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_flanger_smooth(t_dcs_flanger *x, t_floatarg f) { x->smooth = f; }
static void dcs_flanger_run(t_dcs_flanger *x, t_floatarg f) { x->run = f != 0; }

static void *dcs_flanger_new(t_floatarg minms, t_floatarg ms)
{
    t_dcs_flanger *x = (t_dcs_flanger *)pd_new(dcs_flanger_class);
    x->smooth = 30;
    x->sr = 44100;
    x->run = 0;                 /* (a switch~ starts off) */
    x->minms = minms > 0 ? minms : 2;
    dcs_delay_init(&x->d, ms > 0 ? ms : 30);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_flanger_free(t_dcs_flanger *x) { dcs_delay_free(&x->d); }

void dcs_flanger_tilde_setup(void)
{
    dcs_flanger_class = class_new(gensym("dcs_flanger~"), (t_newmethod)dcs_flanger_new, (t_method)dcs_flanger_free,
        sizeof(t_dcs_flanger), 0, A_DEFFLOAT, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_flanger_class, t_dcs_flanger, x_f);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_rate, gensym("rate"), A_FLOAT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_depth, gensym("depth"), A_FLOAT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_fb, gensym("fb"), A_FLOAT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_smooth, gensym("smooth"), A_FLOAT, 0);
    class_addmethod(dcs_flanger_class, (t_method)dcs_flanger_run, gensym("run"), A_FLOAT, 0);
}
