/* dcs_trem~: the tremolo, sample for sample as the Pd objects it replaces (the level swings between 1 and
 * 1 - depth as osc~ goes round: gain = 1 - (0.5 - 0.5 osc) x depth), skipping the work while the depth is 0.
 *
 * Messages: rate <Hz>, depth <0..1>, on <0|1>, smooth <ms> (all three glide).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_trem_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    dcs_osc osc;
    dcs_line rate, depth, on;
} t_dcs_trem;

static t_int *dcs_trem_perform(t_int *w)
{
    t_dcs_trem *x = (t_dcs_trem *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    DCS_SCRATCH(rate);
    dcs_line_block(&x->rate, rate, n, x->sr);
    if (dcs_line_still(&x->depth) && dcs_line_still(&x->on) && x->depth.target * x->on.target == 0) {
        dcs_osc_skip(&x->osc, rate, 0, n, x->sr);           /* off: the input x 1, the LFO moving on */
        dcs_line_skip(&x->depth, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        if (in != out) memmove(out, in, n * sizeof(t_sample));
        return w + 5;
    }
    DCS_SCRATCH(osc); DCS_SCRATCH(depth); DCS_SCRATCH(on);
    dcs_osc_block(&x->osc, rate, 0, osc, n, x->sr);
    dcs_line_block(&x->depth, depth, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    for (int i = 0; i < n; i++) {
        t_sample unit = osc[i] * -0.5f + 0.5f;
        t_sample dip = unit * (depth[i] * on[i]);
        out[i] = in[i] * (dip * -1 + 1);
    }
    return w + 5;
}

static void dcs_trem_dsp(t_dcs_trem *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_trem~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dsp_add(dcs_trem_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_trem_rate(t_dcs_trem *x, t_floatarg f) { dcs_line_set(&x->rate, f, x->smooth); }
static void dcs_trem_depth(t_dcs_trem *x, t_floatarg f) { dcs_line_set(&x->depth, f, x->smooth); }
static void dcs_trem_on(t_dcs_trem *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_trem_smooth(t_dcs_trem *x, t_floatarg f) { x->smooth = f; }

static void *dcs_trem_new(void)
{
    t_dcs_trem *x = (t_dcs_trem *)pd_new(dcs_trem_class);
    x->smooth = 30;
    x->sr = 44100;
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_trem_tilde_setup(void)
{
    dcs_trem_class = class_new(gensym("dcs_trem~"), (t_newmethod)dcs_trem_new, 0, sizeof(t_dcs_trem), 0, 0);
    CLASS_MAINSIGNALIN(dcs_trem_class, t_dcs_trem, x_f);
    class_addmethod(dcs_trem_class, (t_method)dcs_trem_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_trem_class, (t_method)dcs_trem_rate, gensym("rate"), A_FLOAT, 0);
    class_addmethod(dcs_trem_class, (t_method)dcs_trem_depth, gensym("depth"), A_FLOAT, 0);
    class_addmethod(dcs_trem_class, (t_method)dcs_trem_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_trem_class, (t_method)dcs_trem_smooth, gensym("smooth"), A_FLOAT, 0);
}
