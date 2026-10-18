/* dcs_dist~: the distortion, sample for sample as the Pd objects it replaces: the input times drive, shaped three
 * ways (soft u / (1 + |u|), hard clip at +-1, fold sin(u pi/2) 2/pi through cos~), the shapes weighed by three
 * gliding weights (the Shape menu), mixed in by the amount. Nothing to compute while the amount is 0.
 *
 * Messages: drive <x>, type <0 soft | 1 hard | 2 fold>, mix <0..1>, on <0|1>, smooth <ms>.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_dist_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    dcs_line drive, w[3], mix, on;
} t_dcs_dist;

static t_int *dcs_dist_perform(t_int *w)
{
    t_dcs_dist *x = (t_dcs_dist *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    if (dcs_line_still(&x->mix) && dcs_line_still(&x->on) && x->mix.target * x->on.target == 0) {
        dcs_line_skip(&x->drive, n, x->sr);
        for (int k = 0; k < 3; k++) dcs_line_skip(&x->w[k], n, x->sr);
        dcs_line_skip(&x->mix, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        if (in != out) memmove(out, in, n * sizeof(t_sample));
        return w + 5;
    }
    DCS_SCRATCH(drive); DCS_SCRATCH(w0); DCS_SCRATCH(w1); DCS_SCRATCH(w2); DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_line_block(&x->drive, drive, n, x->sr);
    dcs_line_block(&x->w[0], w0, n, x->sr);
    dcs_line_block(&x->w[1], w1, n, x->sr);
    dcs_line_block(&x->w[2], w2, n, x->sr);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    for (int i = 0; i < n; i++) {
        t_sample f = in[i], u = f * drive[i];
        t_sample hard = u < -1 ? -1 : u > 1 ? 1 : u;
        t_sample den = fabsf(u) + 1;
        t_sample soft = den ? u / den : 0;
        t_sample fold = dcs_cos(dcs_wrap(u * 0.25f - 0.25f)) * 0.63662f;
        t_sample shaped = soft * w0[i] + hard * w1[i] + fold * w2[i];
        out[i] = f + (shaped - f) * (mix[i] * on[i]);
    }
    return w + 5;
}

static void dcs_dist_dsp(t_dcs_dist *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_dist~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dsp_add(dcs_dist_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_dist_drive(t_dcs_dist *x, t_floatarg f) { dcs_line_set(&x->drive, f, x->smooth); }
/* the Shape menu: [sel 0 1 2] -> "1 0 0" / "0 1 0" / "0 0 1" -> each weight glides */
static void dcs_dist_type(t_dcs_dist *x, t_floatarg f)
{
    int k = f == 0 ? 0 : f == 1 ? 1 : f == 2 ? 2 : -1;
    if (k < 0) return;
    for (int j = 2; j >= 0; j--)   /* ([unpack f f f] sends right to left) */
        dcs_line_set(&x->w[j], j == k, x->smooth);
}
static void dcs_dist_mix(t_dcs_dist *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_dist_on(t_dcs_dist *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_dist_smooth(t_dcs_dist *x, t_floatarg f) { x->smooth = f; }

static void *dcs_dist_new(void)
{
    t_dcs_dist *x = (t_dcs_dist *)pd_new(dcs_dist_class);
    x->smooth = 30;
    x->sr = 44100;
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_dist_tilde_setup(void)
{
    dcs_dist_class = class_new(gensym("dcs_dist~"), (t_newmethod)dcs_dist_new, 0, sizeof(t_dcs_dist), 0, 0);
    CLASS_MAINSIGNALIN(dcs_dist_class, t_dcs_dist, x_f);
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_drive, gensym("drive"), A_FLOAT, 0);
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_type, gensym("type"), A_FLOAT, 0);
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_smooth, gensym("smooth"), A_FLOAT, 0);
}
