/* dcs_freeze~: the glitch / freeze loop, sample for sample as the Pd objects it replaces: the output is
 * in x (1 - hold) + the line's past (len ms ago) x hold, and goes back into the line, so at hold 1 the last len ms
 * repeat until hold comes down. While hold is 0 it only writes the line.
 *
 * Messages: hold <0..1>, on <0|1>, len <ms>, smooth <ms> (all glide). Argument: the line's length in ms (600).
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

static t_class *dcs_freeze_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    dcs_delay d;
    dcs_line hold, on, len;
} t_dcs_freeze;

static t_int *dcs_freeze_perform(t_int *w)
{
    t_dcs_freeze *x = (t_dcs_freeze *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    DCS_SCRATCH(len);
    dcs_line_block(&x->len, len, n, x->sr);
    if (dcs_line_still(&x->hold) && dcs_line_still(&x->on) && x->hold.target * x->on.target == 0) {
        dcs_line_skip(&x->hold, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        if (in != out) memmove(out, in, n * sizeof(t_sample));   /* in x 1 + the past x 0 */
        dcs_delay_write(&x->d, out, n);
        return w + 5;
    }
    DCS_SCRATCH(rd); DCS_SCRATCH(hold); DCS_SCRATCH(on);
    dcs_delay_read4(&x->d, len, 0, rd, n, x->sr, n);   /* (the reader comes first: it feeds the writer) */
    dcs_line_block(&x->hold, hold, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    for (int i = 0; i < n; i++) {
        t_sample h = hold[i] * on[i];
        out[i] = in[i] * (h * -1 + 1) + rd[i] * h;
    }
    dcs_delay_write(&x->d, out, n);
    return w + 5;
}

static void dcs_freeze_dsp(t_dcs_freeze *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX) {
        pd_error(x, "dcs_freeze~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    x->sr = sp[0]->s_sr;
    dcs_delay_dsp(&x->d, x->sr, sp[0]->s_n);
    dsp_add(dcs_freeze_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_freeze_hold(t_dcs_freeze *x, t_floatarg f) { dcs_line_set(&x->hold, f, x->smooth); }
static void dcs_freeze_on(t_dcs_freeze *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_freeze_len(t_dcs_freeze *x, t_floatarg f) { dcs_line_set(&x->len, f, x->smooth); }
static void dcs_freeze_smooth(t_dcs_freeze *x, t_floatarg f) { x->smooth = f; }

static void *dcs_freeze_new(t_floatarg ms)
{
    t_dcs_freeze *x = (t_dcs_freeze *)pd_new(dcs_freeze_class);
    x->smooth = 30;
    x->sr = 44100;
    dcs_delay_init(&x->d, ms > 0 ? ms : 600);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

static void dcs_freeze_free(t_dcs_freeze *x) { dcs_delay_free(&x->d); }

void dcs_freeze_tilde_setup(void)
{
    dcs_freeze_class = class_new(gensym("dcs_freeze~"), (t_newmethod)dcs_freeze_new, (t_method)dcs_freeze_free,
        sizeof(t_dcs_freeze), 0, A_DEFFLOAT, 0);
    CLASS_MAINSIGNALIN(dcs_freeze_class, t_dcs_freeze, x_f);
    class_addmethod(dcs_freeze_class, (t_method)dcs_freeze_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_freeze_class, (t_method)dcs_freeze_hold, gensym("hold"), A_FLOAT, 0);
    class_addmethod(dcs_freeze_class, (t_method)dcs_freeze_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_freeze_class, (t_method)dcs_freeze_len, gensym("len"), A_FLOAT, 0);
    class_addmethod(dcs_freeze_class, (t_method)dcs_freeze_smooth, gensym("smooth"), A_FLOAT, 0);
}
