/* dcs_chorus~: the chorus, sample for sample as the Pd objects it replaces: two reads of a delay line whose times
 * swing +-depth ms around their bases, a quarter cycle apart, averaged and mixed in by the amount.
 * `run 0` stops it as Pd's switch~ stopped its subpatch (the LFO and the line stand still; the output is the input).
 *
 * Messages: rate <Hz>, depth <ms>, mix <0..1>, on <0|1>, smooth <ms>, run <0|1>.
 * Arguments: the two base delays in ms (15 21), the line's length in ms (60).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_chorus_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, base[2];
    int run;
    double phase;
    dcs_delay d;
    dcs_line rate, depth, mix, on;
} t_dcs_chorus;

static t_int *dcs_chorus_perform(t_int *w)
{
    t_dcs_chorus *x = (t_dcs_chorus *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    DCS_SCRATCH(rate); DCS_SCRATCH(depth); DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_line_block(&x->rate, rate, n, x->sr);
    dcs_line_block(&x->depth, depth, n, x->sr);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    if (!x->run) {                      /* switched off: the wet part is silent */
        for (int i = 0; i < n; i++) {
            t_sample f = in[i];
            out[i] = f + (0 - f) * (mix[i] * on[i]);
        }
        return w + 5;
    }
    DCS_SCRATCH(ph); DCS_SCRATCH(ms); DCS_SCRATCH(r0); DCS_SCRATCH(r1);
    dcs_phasor_block(&x->phase, rate, 0, ph, n, x->sr);
    for (int k = 0; k < 2; k++) {
        t_sample off = k ? 0.25f : 0;
        for (int i = 0; i < n; i++) ms[i] = dcs_cos(dcs_wrap(ph[i] + off)) * depth[i] + x->base[k];
        dcs_delay_read4(&x->d, ms, 0, k ? r1 : r0, n, x->sr, n);
    }
    dcs_delay_write(&x->d, in, n);      /* (Pd ran the reads first) */
    for (int i = 0; i < n; i++) {
        t_sample f = in[i], wet = (r0[i] + r1[i]) * 0.5f;
        out[i] = f + (wet - f) * (mix[i] * on[i]);
    }
    return w + 5;
}

static void dcs_chorus_dsp(t_dcs_chorus *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_chorus~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->d, x->sr, sp[0]->s_n);
    dsp_add(dcs_chorus_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_chorus_rate(t_dcs_chorus *x, t_floatarg f) { dcs_line_set(&x->rate, f, x->smooth); }
static void dcs_chorus_depth(t_dcs_chorus *x, t_floatarg f) { dcs_line_set(&x->depth, f, x->smooth); }
static void dcs_chorus_mix(t_dcs_chorus *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_chorus_on(t_dcs_chorus *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_chorus_smooth(t_dcs_chorus *x, t_floatarg f) { x->smooth = f; }
static void dcs_chorus_run(t_dcs_chorus *x, t_floatarg f) { x->run = f != 0; }

static void *dcs_chorus_new(t_floatarg b0, t_floatarg b1, t_floatarg ms)
{
    t_dcs_chorus *x = (t_dcs_chorus *)pd_new(dcs_chorus_class);
    x->smooth = 30;
    x->sr = 44100;
    x->run = 0;                 /* (a switch~ starts off) */
    x->base[0] = b0 > 0 ? b0 : 15;
    x->base[1] = b1 > 0 ? b1 : 21;
    dcs_delay_init(&x->d, ms > 0 ? ms : 60);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_chorus_free(t_dcs_chorus *x) { dcs_delay_free(&x->d); }

void dcs_chorus_tilde_setup(void)
{
    dcs_chorus_class = class_new(gensym("dcs_chorus~"), (t_newmethod)dcs_chorus_new, (t_method)dcs_chorus_free,
        sizeof(t_dcs_chorus), 0, A_DEFFLOAT, A_DEFFLOAT, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_chorus_class, t_dcs_chorus, x_f);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_rate, gensym("rate"), A_FLOAT, 0);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_depth, gensym("depth"), A_FLOAT, 0);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_smooth, gensym("smooth"), A_FLOAT, 0);
    class_addmethod(dcs_chorus_class, (t_method)dcs_chorus_run, gensym("run"), A_FLOAT, 0);
}
