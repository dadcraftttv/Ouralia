/* dcs_space~: the spatial stage, sample for sample as the Pd objects it replaces: each ear's sound through its own
 * short delay (the base ms, plus up to 25 ms for the ear the sound should seem farther from: `shift` > 0 delays
 * the right ear), then a balance pan (centre: both at full level; hard right: the left ear silent).
 *
 * Inlets: left~, right~. Outlets: left~, right~. Messages: shift <ms>, pan <-1..1>, on <0|1>, smooth <ms>.
 * Arguments: the base delay in ms (1.5), each line's length in ms (100).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_space_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth, base;
    dcs_delay dl, dr;
    dcs_line shift, pan, on;
} t_dcs_space;

static inline t_sample clip01(t_sample f, t_sample hi) { return f < 0 ? 0 : f > hi ? hi : f; }

static t_int *dcs_space_perform(t_int *w)
{
    t_dcs_space *x = (t_dcs_space *)w[1];
    t_sample *inl = (t_sample *)w[2], *inr = (t_sample *)w[3], *outl = (t_sample *)w[4], *outr = (t_sample *)w[5];
    int n = (int)w[6];
    DCS_SCRATCH(shift); DCS_SCRATCH(pan); DCS_SCRATCH(on); DCS_SCRATCH(msl); DCS_SCRATCH(msr);
    DCS_SCRATCH(rl); DCS_SCRATCH(rr);
    dcs_line_block(&x->shift, shift, n, x->sr);
    dcs_line_block(&x->pan, pan, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    dcs_delay_write(&x->dl, inl, n);      /* (the base delay is over a block: Pd's order didn't matter) */
    dcs_delay_write(&x->dr, inr, n);
    for (int i = 0; i < n; i++) {
        t_sample s = shift[i] * on[i];
        msl[i] = clip01(s * -1, 25) + x->base;
        msr[i] = clip01(s, 25) + x->base;
    }
    dcs_delay_read4(&x->dl, msl, 0, rl, n, x->sr, 0);
    dcs_delay_read4(&x->dr, msr, 0, rr, n, x->sr, 0);
    for (int i = 0; i < n; i++) {
        t_sample p = pan[i] * on[i];
        t_sample gl = clip01(p, 1) * -1 + 1, gr = clip01(p * -1, 1) * -1 + 1;
        outl[i] = rl[i] * gl;
        outr[i] = rr[i] * gr;
    }
    return w + 7;
}

static void dcs_space_dsp(t_dcs_space *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_space~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->dl, x->sr, sp[0]->s_n);
    dcs_delay_dsp(&x->dr, x->sr, sp[0]->s_n);
    dsp_add(dcs_space_perform, 6, x, sp[0]->s_vec, sp[1]->s_vec, sp[2]->s_vec, sp[3]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_space_shift(t_dcs_space *x, t_floatarg f) { dcs_line_set(&x->shift, f, x->smooth); }
static void dcs_space_pan(t_dcs_space *x, t_floatarg f) { dcs_line_set(&x->pan, f, x->smooth); }
static void dcs_space_on(t_dcs_space *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_space_smooth(t_dcs_space *x, t_floatarg f) { x->smooth = f; }

static void *dcs_space_new(t_floatarg base, t_floatarg ms)
{
    t_dcs_space *x = (t_dcs_space *)pd_new(dcs_space_class);
    x->smooth = 30;
    x->sr = 44100;
    x->base = base > 0 ? base : 1.5;
    dcs_delay_init(&x->dl, ms > 0 ? ms : 100);
    dcs_delay_init(&x->dr, ms > 0 ? ms : 100);
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_space_free(t_dcs_space *x) { dcs_delay_free(&x->dl); dcs_delay_free(&x->dr); }

void dcs_space_tilde_setup(void)
{
    dcs_space_class = class_new(gensym("dcs_space~"), (t_newmethod)dcs_space_new, (t_method)dcs_space_free,
        sizeof(t_dcs_space), 0, A_DEFFLOAT, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_space_class, t_dcs_space, x_f);
    class_addmethod(dcs_space_class, (t_method)dcs_space_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_space_class, (t_method)dcs_space_shift, gensym("shift"), A_FLOAT, 0);
    class_addmethod(dcs_space_class, (t_method)dcs_space_pan, gensym("pan"), A_FLOAT, 0);
    class_addmethod(dcs_space_class, (t_method)dcs_space_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_space_class, (t_method)dcs_space_smooth, gensym("smooth"), A_FLOAT, 0);
}
