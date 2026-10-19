/* dcs_dist~: the distortion, sample for sample as the Pd objects it replaces: the input times drive, shaped three
 * ways (soft u / (1 + |u|), hard clip at +-1, fold sin(u pi/2) 2/pi through cos~), the shapes weighed by three
 * gliding weights (the Shape menu), mixed in by the amount. Nothing to compute while the amount is 0.
 *
 * Quality 1 (Oct 19, "Clean"): the shaping runs at 4x the sample rate between two 65-tap windowed-sinc filters
 * (Kaiser, cut at the original Nyquist), so the harmonics a hard drive makes above it are filtered out instead of
 * folding back down as inharmonic fizz. The filters delay the sound 16 samples, so the dry part is delayed alike
 * (no comb when mixed). Quality 0 (the default) is the original, sample for sample.
 *
 * Messages: drive <x>, type <0 soft | 1 hard | 2 fold>, mix <0..1>, on <0|1>, quality <0|1>, smooth <ms>.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"

#define OS 4
#define OS_TAPS 65
#define OS_DELAY 16     /* (OS_TAPS - 1) / 2 at 4x, twice (up and down), in original samples */
#define OS_PH 17        /* the inputs one phase reaches: ceil(OS_TAPS / OS) */

static t_class *dcs_dist_class;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    dcs_line drive, w[3], mix, on;
    int quality;
    float up[OS_PH * 2], down[OS_TAPS * 2], dry[OS_DELAY];   /* histories (doubled rings: straight reads), dry */
    int upos, dpos, ypos;
} t_dcs_dist;

/* the 4x filter: 65 taps, Kaiser beta 8, cut at 1/8 of the 4x rate (the original Nyquist); delay 32 at 4x.
 * os_ph[k] = phase k's taps (k, k+4, ...) oldest input first, zero-padded to OS_PH, times 4 (the zero stuffing) */
static float os_h[OS_TAPS], os_ph[OS][OS_PH];

static double os_i0(double x)
{
    double s = 1, term = 1;
    for (int k = 1; k < 30; k++) { term *= (x / (2 * k)) * (x / (2 * k)); s += term; }
    return s;
}

static void os_design(void)
{
    double sum = 0, beta = 8, m = (OS_TAPS - 1) / 2.0, fc = 0.125;
    for (int i = 0; i < OS_TAPS; i++) {
        double d = i - m, r = d / m;
        double sinc = d == 0 ? 2 * fc : sin(2 * M_PI * fc * d) / (M_PI * d);
        os_h[i] = (float)(sinc * os_i0(beta * sqrt(1 - r * r)) / os_i0(beta));
        sum += os_h[i];
    }
    for (int i = 0; i < OS_TAPS; i++) os_h[i] /= (float)sum;
    for (int k = 0; k < OS; k++)
        for (int b = 0; b < OS_PH; b++) {
            int j = k + OS * (OS_PH - 1 - b);    /* (b = 0 is the oldest input) */
            os_ph[k][b] = j < OS_TAPS ? OS * os_h[j] : 0;
        }
}

/* only the shapes whose weight isn't 0 (the menu's weights sit at 0 or 1 except while gliding) */
static inline t_sample dist_shape_some(t_sample u, t_sample w0, t_sample w1, t_sample w2)
{
    t_sample s = 0;
    if (w0 != 0) { t_sample den = fabsf(u) + 1; s += u / den * w0; }
    if (w1 != 0) s += (u < -1 ? -1 : u > 1 ? 1 : u) * w1;
    if (w2 != 0) s += dcs_cos(dcs_wrap(u * 0.25f - 0.25f)) * 0.63662f * w2;
    return s;
}

static inline t_sample dist_shape(t_sample u, t_sample w0, t_sample w1, t_sample w2)
{
    t_sample hard = u < -1 ? -1 : u > 1 ? 1 : u;
    t_sample den = fabsf(u) + 1;
    t_sample soft = den ? u / den : 0;
    t_sample fold = dcs_cos(dcs_wrap(u * 0.25f - 0.25f)) * 0.63662f;
    return soft * w0 + hard * w1 + fold * w2;
}

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
    if (x->quality) {
        for (int i = 0; i < n; i++) {
            t_sample f = in[i];
            float wet = 0;
            x->up[x->upos] = x->up[x->upos + OS_PH] = f;
            if (++x->upos >= OS_PH) x->upos = 0;
            const float *hx = x->up + x->upos;                   /* the last 17 inputs, oldest first */
            for (int k = 0; k < OS; k++) {
                /* phase k of the zero-stuffed input */
                const float *hk = os_ph[k];
                float a0 = 0, a1 = 0, a2 = 0, a3 = 0;
                for (int b = 0; b < 16; b += 4) {
                    a0 += hk[b] * hx[b]; a1 += hk[b + 1] * hx[b + 1]; a2 += hk[b + 2] * hx[b + 2]; a3 += hk[b + 3] * hx[b + 3];
                }
                float acc = (a0 + a1) + (a2 + a3) + hk[16] * hx[16];
                float s = dist_shape_some(acc * drive[i], w0[i], w1[i], w2[i]);
                x->down[x->ypos] = x->down[x->ypos + OS_TAPS] = s;   /* (a doubled ring: one straight read) */
                if (++x->ypos >= OS_TAPS) x->ypos = 0;
                if (k == 0) {   /* decimate here: 64 fast samples = exactly OS_DELAY behind the input */
                    const float *z = x->down + x->ypos;          /* the last 65 shaped samples, oldest first */
                    float b0 = 0, b1 = 0, b2 = 0, b3 = 0;
                    for (int j = 0; j < 64; j += 4) {
                        b0 += os_h[j] * z[j]; b1 += os_h[j + 1] * z[j + 1]; b2 += os_h[j + 2] * z[j + 2]; b3 += os_h[j + 3] * z[j + 3];
                    }
                    wet = (b0 + b1) + (b2 + b3) + os_h[64] * z[64];
                }
            }
            t_sample d = x->dry[x->dpos];
            x->dry[x->dpos] = f;
            if (++x->dpos >= OS_DELAY) x->dpos = 0;
            out[i] = d + (wet - d) * (mix[i] * on[i]);
        }
        return w + 5;
    }
    for (int i = 0; i < n; i++) {
        t_sample f = in[i], u = f * drive[i];
        t_sample shaped = dist_shape(u, w0[i], w1[i], w2[i]);
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
static void dcs_dist_quality(t_dcs_dist *x, t_floatarg f) { x->quality = f != 0; }

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
    class_addmethod(dcs_dist_class, (t_method)dcs_dist_quality, gensym("quality"), A_FLOAT, 0);
    os_design();
}
