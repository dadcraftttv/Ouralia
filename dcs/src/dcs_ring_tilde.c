/* dcs_ring~: the ring modulator, sample for sample as the Pd objects it replaces (osc~ times the amount, plus
 * 1 - amount, times the input), skipping the work while the amount is 0.
 *
 * Messages: freq <Hz>, mix <0..1>, on <0|1>, smooth <ms> (the glide of mix and on).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_ring_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, freq;
    dcs_osc osc;
    dcs_line mix, on;
} t_dcs_ring;

static t_int *dcs_ring_perform(t_int *w)
{
    t_dcs_ring *x = (t_dcs_ring *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    if (dcs_line_still(&x->mix) && dcs_line_still(&x->on) && x->mix.target * x->on.target == 0) {
        /* off: the input as is (x 1 exactly), the oscillator's phase moving on as osc~'s would */
        dcs_osc_skip(&x->osc, 0, x->freq, n, x->sr);
        dcs_line_skip(&x->mix, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        if (in != out) memmove(out, in, n * sizeof(t_sample));
        return w + 5;
    }
    DCS_SCRATCH(osc); DCS_SCRATCH(mix); DCS_SCRATCH(on);
    dcs_osc_block(&x->osc, 0, x->freq, osc, n, x->sr);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    for (int i = 0; i < n; i++) {
        t_sample eff = mix[i] * on[i];
        t_sample carrier = osc[i] * eff + (eff - 1) * -1;
        out[i] = in[i] * carrier;
    }
    return w + 5;
}

static void dcs_ring_dsp(t_dcs_ring *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_ring~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dsp_add(dcs_ring_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_ring_freq(t_dcs_ring *x, t_floatarg f) { x->freq = f; }
static void dcs_ring_mix(t_dcs_ring *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_ring_on(t_dcs_ring *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_ring_smooth(t_dcs_ring *x, t_floatarg f) { x->smooth = f; }

static void *dcs_ring_new(t_floatarg freq)
{
    t_dcs_ring *x = (t_dcs_ring *)pd_new(dcs_ring_class);
    x->smooth = 30;
    x->freq = freq;
    x->sr = 44100;
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_ring_tilde_setup(void)
{
    dcs_ring_class = class_new(gensym("dcs_ring~"), (t_newmethod)dcs_ring_new, 0, sizeof(t_dcs_ring), 0, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_ring_class, t_dcs_ring, x_f);
    class_addmethod(dcs_ring_class, (t_method)dcs_ring_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_ring_class, (t_method)dcs_ring_freq, gensym("freq"), A_FLOAT, 0);
    class_addmethod(dcs_ring_class, (t_method)dcs_ring_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_ring_class, (t_method)dcs_ring_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_ring_class, (t_method)dcs_ring_smooth, gensym("smooth"), A_FLOAT, 0);
}
