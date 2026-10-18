/* dcs_echo~: the echo, sample for sample as the Pd objects it replaces: a delay line (delwrite~ / delread4~) fed by
 * the voice, the rest of the mic (what Dry took away) and its own output times the feedback; out comes the delayed
 * sound times the wet level. The delay time comes in as a signal (the patch glides it and reads it). While wet and
 * feedback are both 0 it only writes the line (so turning on again finds the same past).
 *
 * Inlets: voice~, rest~, time~ (ms). Messages: fb <0..1>, wet <0..1>, on <0|1>, smooth <ms>.
 * Argument: the line's length in ms (2000).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_echo_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    dcs_delay d;
    dcs_line fb, wet, on;
} t_dcs_echo;

static t_int *dcs_echo_perform(t_int *w)
{
    t_dcs_echo *x = (t_dcs_echo *)w[1];
    t_sample *voice = (t_sample *)w[2], *rest = (t_sample *)w[3], *ms = (t_sample *)w[4], *out = (t_sample *)w[5];
    int n = (int)w[6];
    DCS_SCRATCH(in);
    int quiet = dcs_line_still(&x->fb) && x->fb.target == 0 && dcs_line_still(&x->wet) && dcs_line_still(&x->on)
        && x->wet.target * x->on.target == 0;
    if (quiet) {
        for (int i = 0; i < n; i++) in[i] = voice[i] + rest[i];   /* (+ the feedback, x 0: the same float) */
        dcs_delay_write(&x->d, in, n);
        dcs_line_skip(&x->fb, n, x->sr);
        dcs_line_skip(&x->wet, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        for (int i = 0; i < n; i++) out[i] = 0;
        return w + 7;
    }
    DCS_SCRATCH(rd); DCS_SCRATCH(fb); DCS_SCRATCH(wet); DCS_SCRATCH(on);
    /* the reader comes first in Pd's order (its output feeds the writer): a block behind, as delread4~ was */
    dcs_delay_read4(&x->d, ms, 0, rd, n, x->sr, n);
    dcs_line_block(&x->fb, fb, n, x->sr);
    dcs_line_block(&x->wet, wet, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    for (int i = 0; i < n; i++) {
        in[i] = (voice[i] + rest[i]) + rd[i] * fb[i];
        out[i] = rd[i] * (wet[i] * on[i]);
    }
    dcs_delay_write(&x->d, in, n);
    return w + 7;
}

static void dcs_echo_dsp(t_dcs_echo *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_echo~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->d, x->sr, sp[0]->s_n);
    dsp_add(dcs_echo_perform, 6, x, sp[0]->s_vec, sp[1]->s_vec, sp[2]->s_vec, sp[3]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_echo_fb(t_dcs_echo *x, t_floatarg f) { dcs_line_set(&x->fb, f, x->smooth); }
static void dcs_echo_wet(t_dcs_echo *x, t_floatarg f) { dcs_line_set(&x->wet, f, x->smooth); }
static void dcs_echo_on(t_dcs_echo *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_echo_smooth(t_dcs_echo *x, t_floatarg f) { x->smooth = f; }

static void *dcs_echo_new(t_floatarg ms)
{
    t_dcs_echo *x = (t_dcs_echo *)pd_new(dcs_echo_class);
    x->smooth = 30;
    x->sr = 44100;
    dcs_delay_init(&x->d, ms > 0 ? ms : 2000);
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    inlet_new(&x->x_obj, &x->x_obj.ob_pd, &s_signal, &s_signal);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_echo_free(t_dcs_echo *x)
{
    dcs_delay_free(&x->d);
}

void dcs_echo_tilde_setup(void)
{
    dcs_echo_class = class_new(gensym("dcs_echo~"), (t_newmethod)dcs_echo_new, (t_method)dcs_echo_free,
        sizeof(t_dcs_echo), 0, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_echo_class, t_dcs_echo, x_f);
    class_addmethod(dcs_echo_class, (t_method)dcs_echo_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_echo_class, (t_method)dcs_echo_fb, gensym("fb"), A_FLOAT, 0);
    class_addmethod(dcs_echo_class, (t_method)dcs_echo_wet, gensym("wet"), A_FLOAT, 0);
    class_addmethod(dcs_echo_class, (t_method)dcs_echo_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_echo_class, (t_method)dcs_echo_smooth, gensym("smooth"), A_FLOAT, 0);
}
