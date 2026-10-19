/* dcs_vocoder~: a vocoder. The voice (the modulator) is split into 20 bands from 120 Hz to 7 kHz; how loud each band
 * is, moment to moment, is laid onto the same band of a synthesised carrier, so the carrier speaks the words.
 *
 * Carriers: 0 chords = saws on the music's chord (the `cnote`s the patch sends; before any, the key's own triad from
 * `key` and `scale`), 1 your pitch = saws following the voice's pitch (YIN, dcs_yin.h), moved onto the scale by `snap`,
 * 2 whisper = noise, 3 melody = saws on the music's melody (each `mnote` held until the next, with a short slide;
 * before any, the chord's root).
 *
 * `style 1` is a talk box instead of the bands: the mouth's shape (its formants, found by linear prediction from the
 * voice, 24 poles, every 6 ms) filters the carrier as a tube in the mouth would, so vowels come through smooth and
 * whole rather than in bands. Each carrier band is first levelled (divided by its own envelope), so a carrier's tilt or gaps
 * don't colour the words; a little noise under the saws keeps every band alive. `sib` adds the voice's own hiss
 * above 5 kHz back in, so s, t and f stay clear.
 *
 *   inlet:   signal (the voice); carrier <0..3>, wave <0..3> (0 saws, 1-3 chip pulses 25/12.5/50%), cnote <slot 0..3> <Hz>, mnote <Hz>, style <0|1>, key <0..11>, scale <steps...>,
 *            snap <0..1>, sib <0..1>, octave <n> (the carrier n octaves off), mix <0..1>, on <0|1>, smooth <ms>
 *   outlet:  the voice, mixed with the vocoded voice by `mix` (no delay). At mix 0 the input passes through as is
 *            and nothing is computed; turning on again starts the bands afresh.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "dcs_fx.h"
#include "dcs_yin.h"

#define NB 20
#define LO_HZ 120.
#define HI_HZ 7000.
#define NCHORD 4
#define HOP_MS 10.
#define OUT_GAIN 8.5f
#define TB_P 24                 /* the talk box: poles */
#define TB_WIN_MS 24.
#define TB_WINMAX 2048          /* (a power of two: the voice's ring) */
#define TB_PRE 0.97f            /* pre-emphasis before the analysis (de-emphasis after) */
#define TB_GAIN 0.72f        /* (its level matches the voice's) */

static t_class *dcs_vocoder_class;

typedef struct { float b0, b2, a1, a2; } t_bp;           /* a constant-peak band-pass (b1 = 0) */
typedef struct { float x1, x2, y1, y2; } t_bps;

typedef struct {
    t_object x_obj;
    t_float x_f;
    t_float sr, smooth;
    dcs_line mix, on;
    int was_on, carrier, key, inscale[12], scale[12], nscale, havechord;
    float sib, snap, octave;
    double chord[NCHORD], ph[NCHORD][2], vph[2], vhz, vtarget;
    t_bp bp[NB];
    t_bps ms[NB][2], cs[NB][2];
    float menv[NB], cenv[NB], att, rel, catt, crel, bandg;
    float hpx, hpy, hp2x, hp2y, hpk;
    unsigned int seed;
    t_dcs_yin yin;
    int hopcount;
    int style, wave;   /* wave: 0 saws (two, detuned), 1-3 chip pulses (one per note: 25%, 12.5%, 50%) */
    double mtarget, mhz, mph[2];
    float tring[TB_WINMAX], hann[TB_WINMAX], lagw[TB_P + 1], wsum2;   /* the talk box */
    int tpos, twin, thop, tcount;
    float kc[TB_P + 1], dk[TB_P + 1], bst[TB_P + 1], g, dg;
    float vpre, cpre, cpow, cpk, deem;
} t_dcs_vocoder;

static float voc_noise(t_dcs_vocoder *x)
{
    x->seed = x->seed * 1664525u + 1013904223u;
    return (float)((int)(x->seed >> 9) - (1 << 22)) / (float)(1 << 22);
}

static inline float bp_tick(const t_bp *c, t_bps *s, float in)
{
    float y = c->b0 * in + c->b2 * s->x2 - c->a1 * s->y1 - c->a2 * s->y2;
    s->x2 = s->x1; s->x1 = in; s->y2 = s->y1; s->y1 = y;
    return y;
}

static inline double voc_saw(double *ph, double dt)
{
    double t = *ph, blep = 0;
    if (t < dt) { double q = t / dt; blep = q + q - q * q - 1; }
    else if (t > 1 - dt) { double q = (t - 1) / dt; blep = q * q + q + q + 1; }
    *ph = t + dt - floor(t + dt);
    return 2 * t - 1 - blep;
}

/* a band-limited pulse of width `duty`: two of the saws above, the second `duty` later (no DC: its mean is 0) */
static inline double voc_pulse(double *ph, double dt, double duty)
{
    double t2 = *ph + duty;
    t2 -= floor(t2);
    double b = 0;
    if (t2 < dt) { double q = t2 / dt; b = q + q - q * q - 1; }
    else if (t2 > 1 - dt) { double q = (t2 - 1) / dt; b = q * q + q + q + 1; }
    return voc_saw(ph, dt) - (2 * t2 - 1 - b);
}

static const double voc_duty[4] = {0, 0.25, 0.125, 0.5};

/* one note of the carrier: two detuned saws (dt for each), or one chip pulse at their mean */
static inline double voc_note(t_dcs_vocoder *x, double *ph, double dt0, double dt1)
{
    if (x->wave > 0)
        return 0.5 * voc_pulse(&ph[0], 0.5 * (dt0 + dt1), voc_duty[x->wave]);
    return 0.5 * (voc_saw(&ph[0], dt0) + voc_saw(&ph[1], dt1));
}

static void voc_design(t_dcs_vocoder *x)
{
    double r = pow(HI_HZ / LO_HZ, 1. / (NB - 1)), Q = sqrt(r) / (r - 1), sr = x->sr;
    for (int b = 0; b < NB; b++)
    {
        double f = LO_HZ * pow(r, b), w = 2 * M_PI * f / sr, al = sin(w) / (2 * Q), a0 = 1 + al;
        x->bp[b].b0 = (float)(al / a0); x->bp[b].b2 = (float)(-al / a0);
        x->bp[b].a1 = (float)(-2 * cos(w) / a0); x->bp[b].a2 = (float)((1 - al) / a0);
    }
    x->att = (float)(1 - exp(-1000. / (3 * sr))); x->rel = (float)(1 - exp(-1000. / (25 * sr)));
    x->catt = (float)(1 - exp(-1000. / (10 * sr))); x->crel = (float)(1 - exp(-1000. / (60 * sr)));
    x->hpk = (float)exp(-2 * M_PI * 5000. / sr);
    x->bandg = (float)(1. / sqrt((double)NB));
    x->twin = (int)(TB_WIN_MS * sr / 1000);
    if (x->twin > TB_WINMAX) x->twin = TB_WINMAX;
    x->thop = x->twin / 4;
    x->wsum2 = 0;
    for (int i = 0; i < x->twin; i++)
    {
        x->hann[i] = (float)(0.5 - 0.5 * cos(2 * M_PI * (i + 0.5) / x->twin));
        x->wsum2 += x->hann[i] * x->hann[i];
    }
    for (int i = 0; i <= TB_P; i++)                     /* (a 60 Hz lag window: no needle-sharp formants) */
        x->lagw[i] = (float)exp(-0.5 * pow(2 * M_PI * 60. * i / sr, 2));
    x->cpk = (float)(1 - exp(-1000. / (30 * sr)));
}

static void voc_reset(t_dcs_vocoder *x)
{
    memset(x->ms, 0, sizeof(x->ms)); memset(x->cs, 0, sizeof(x->cs));
    memset(x->menv, 0, sizeof(x->menv)); memset(x->cenv, 0, sizeof(x->cenv));
    x->hpx = x->hpy = x->hp2x = x->hp2y = 0;
    if (x->sr > 0) dcs_yin_init(&x->yin, x->sr);
    x->hopcount = 0;
    memset(x->tring, 0, sizeof(x->tring)); memset(x->kc, 0, sizeof(x->kc)); memset(x->dk, 0, sizeof(x->dk));
    memset(x->bst, 0, sizeof(x->bst));
    x->tpos = x->tcount = 0;
    x->g = x->dg = x->vpre = x->cpre = x->deem = 0;
    x->cpow = 1e-4f;
}

/* the talk box's analysis, every hop: the last window of the (pre-emphasised) voice -> its all-pole filter as
 * reflection coefficients (Levinson-Durbin) and its gain; the filter glides to them over the next hop */
static void tb_analyse(t_dcs_vocoder *x)
{
    double r[TB_P + 1], a[TB_P + 1], tmp[TB_P + 1], k[TB_P + 1], e;
    float fr[TB_WINMAX];
    int n = x->twin, p = TB_P;
    for (int i = 0; i < n; i++) fr[i] = x->tring[(x->tpos - n + i) & (TB_WINMAX - 1)] * x->hann[i];
    for (int l = 0; l <= p; l++)
    {
        double sum = 0;
        for (int i = l; i < n; i++) sum += fr[i] * fr[i - l];
        r[l] = sum * x->lagw[l];
    }
    r[0] = r[0] * 1.0001 + 1e-12;
    e = r[0];
    a[0] = 1;
    for (int i = 1; i <= p; i++) a[i] = 0;
    for (int i = 1; i <= p; i++)
    {
        double acc = r[i];
        for (int j = 1; j < i; j++) acc += a[j] * r[i - j];
        k[i] = -acc / e;
        if (k[i] > 0.995) k[i] = 0.995; else if (k[i] < -0.995) k[i] = -0.995;
        for (int j = 1; j < i; j++) tmp[j] = a[j] + k[i] * a[i - j];
        for (int j = 1; j < i; j++) a[j] = tmp[j];
        a[i] = k[i];
        e *= 1 - k[i] * k[i];
    }
    for (int i = 1; i <= p; i++) x->dk[i] = (float)((k[i] - x->kc[i]) / x->thop);
    x->dg = (float)((sqrt(e / x->wsum2) - x->g) / x->thop);
}

/* one sample of the talk box: the carrier, flattened and levelled, through the mouth's filter (a lattice: stable
 * while the coefficients glide) */
static inline float tb_tick(t_dcs_vocoder *x, float v, float car)
{
    float ex = car - TB_PRE * x->cpre, f, y;
    x->cpre = car;
    x->tring[x->tpos & (TB_WINMAX - 1)] = v - TB_PRE * x->vpre;
    x->vpre = v;
    x->tpos++;
    if (++x->tcount >= x->thop) { x->tcount = 0; tb_analyse(x); }
    x->cpow += (ex * ex - x->cpow) * x->cpk;
    for (int i = 1; i <= TB_P; i++) x->kc[i] += x->dk[i];
    x->g += x->dg;
    f = ex / sqrtf(x->cpow + 1e-9f) * x->g;
    for (int i = TB_P; i >= 1; i--)
    {
        f -= x->kc[i] * x->bst[i - 1];
        x->bst[i] = x->bst[i - 1] + x->kc[i] * f;
    }
    x->bst[0] = f;
    y = f + TB_PRE * x->deem;
    x->deem = y;
    return y * TB_GAIN;
}

/* the key's own triad an octave below middle C, for the chord carrier before the music sends a chord */
static void voc_triad(t_dcs_vocoder *x)
{
    if (x->havechord) return;
    int steps[3] = {0, 2, 4};
    for (int k = 0; k < 3; k++)
    {
        int s = x->nscale > steps[k] ? x->scale[steps[k]] : 4 * k - (k > 1);
        x->chord[k] = 440 * pow(2, (48 + x->key + s - 69) / 12.);
    }
    x->chord[3] = 0;
}

/* the nearest scale note to MIDI pitch m */
static double voc_snapnote(t_dcs_vocoder *x, double m)
{
    for (int d = 0; d <= 6; d++)
    {
        int lo = (int)floor(m) - d, hi = (int)ceil(m) + d;
        int okl = x->inscale[((lo - x->key) % 12 + 12) % 12], okh = x->inscale[((hi - x->key) % 12 + 12) % 12];
        if (okl && (!okh || m - lo <= hi - m)) return lo;
        if (okh) return hi;
    }
    return m;
}

static t_int *dcs_vocoder_perform(t_int *w)
{
    t_dcs_vocoder *x = (t_dcs_vocoder *)w[1];
    t_sample *in = (t_sample *)w[2], *out = (t_sample *)w[3];
    int n = (int)w[4];
    if (dcs_line_still(&x->mix) && dcs_line_still(&x->on) && x->mix.target * x->on.target == 0)
    {
        dcs_line_skip(&x->mix, n, x->sr);
        dcs_line_skip(&x->on, n, x->sr);
        x->was_on = 0;
        if (in != out) memmove(out, in, n * sizeof(t_sample));
        return w + 5;
    }
    if (!x->was_on) { voc_reset(x); x->was_on = 1; }
    DCS_SCRATCH(mix); DCS_SCRATCH(on); DCS_SCRATCH(dry);
    dcs_line_block(&x->mix, mix, n, x->sr);
    dcs_line_block(&x->on, on, n, x->sr);
    memcpy(dry, in, n * sizeof(t_sample));
    double sr = x->sr, oct = pow(2, x->octave), hop = HOP_MS * sr / 1000;
    double cdt[NCHORD][2], vdt[2];
    for (int k = 0; k < NCHORD; k++)
        for (int j = 0; j < 2; j++)
            cdt[k][j] = x->chord[k] > 0 ? x->chord[k] * oct * (j ? 1.0035 : 0.9965) / sr : 0;
    for (int i = 0; i < n; i++)
    {
        float v = dry[i], car = 0, o = 0;
        if (x->carrier == 1)
        {
            dcs_yin_write(&x->yin, v);
            if (++x->hopcount >= hop)
            {
                double sure, P = dcs_yin_period(&x->yin, &sure);
                x->hopcount = 0;
                if (P > 0)
                {
                    double m = 69 + 12 * log2(sr / P / 440.), t = m;
                    if (x->nscale > 0 && x->snap > 0) t = m + (voc_snapnote(x, m) - m) * x->snap;
                    x->vtarget = 440 * pow(2, (t - 69) / 12.);
                    if (x->vhz <= 0) x->vhz = x->vtarget;
                }
            }
            if (x->vhz <= 0) x->vhz = x->vtarget = 220;
            x->vhz += (x->vtarget - x->vhz) * 0.002;              /* (~10 ms glide) */
            vdt[0] = x->vhz * oct * 0.997 / sr; vdt[1] = x->vhz * oct * 1.003 / sr;
            car = (float)voc_note(x, x->vph, vdt[0], vdt[1]);
            car += 0.03f * voc_noise(x);
        }
        else if (x->carrier == 2)
            car = voc_noise(x);
        else if (x->carrier == 3)
        {
            if (x->mhz <= 0) x->mhz = x->mtarget > 0 ? x->mtarget : 2 * x->chord[0];
            x->mhz += ((x->mtarget > 0 ? x->mtarget : 2 * x->chord[0]) - x->mhz) * 0.0015;   /* (~15 ms slide) */
            car = (float)voc_note(x, x->mph, x->mhz * oct * 0.997 / sr, x->mhz * oct * 1.003 / sr);
            car += 0.03f * voc_noise(x);
        }
        else
        {
            for (int k = 0; k < NCHORD; k++)
                if (cdt[k][0] > 0)
                    car += (float)(0.5 * voc_note(x, x->ph[k], cdt[k][0], cdt[k][1]));
            car += 0.03f * voc_noise(x);
        }
        if (x->style == 1)
            o = tb_tick(x, v, car);
        else
        {
            for (int b = 0; b < NB; b++)
            {
                float mb = bp_tick(&x->bp[b], &x->ms[b][1], bp_tick(&x->bp[b], &x->ms[b][0], v));
                float cb = bp_tick(&x->bp[b], &x->cs[b][1], bp_tick(&x->bp[b], &x->cs[b][0], car));
                float am = fabsf(mb), ac = fabsf(cb);
                x->menv[b] += (am - x->menv[b]) * (am > x->menv[b] ? x->att : x->rel);
                x->cenv[b] += (ac - x->cenv[b]) * (ac > x->cenv[b] ? x->catt : x->crel);
                o += cb * x->menv[b] / (x->cenv[b] + 1e-4f);
            }
            o *= x->bandg * OUT_GAIN;
        }
        {                                                       /* the hiss above 5 kHz, twice high-passed */
            float h1 = x->hpk * x->hpy + (v - x->hpx) * 0.5f * (1 + x->hpk), h2;
            x->hpx = v; x->hpy = h1;
            h2 = x->hpk * x->hp2y + (h1 - x->hp2x) * 0.5f * (1 + x->hpk);
            x->hp2x = h1; x->hp2y = h2;
            o += x->sib * h2;
        }
        float e = mix[i] * on[i];
        out[i] = v + (o - v) * e;
    }
    for (int i = 0; i <= TB_P; i++)
        if (PD_BIGORSMALL(x->bst[i])) x->bst[i] = 0;
    if (PD_BIGORSMALL(x->deem)) x->deem = 0;
    for (int b = 0; b < NB; b++)
        for (int j = 0; j < 2; j++)
        {
            if (PD_BIGORSMALL(x->ms[b][j].y1)) x->ms[b][j].y1 = 0;
            if (PD_BIGORSMALL(x->ms[b][j].y2)) x->ms[b][j].y2 = 0;
            if (PD_BIGORSMALL(x->cs[b][j].y1)) x->cs[b][j].y1 = 0;
            if (PD_BIGORSMALL(x->cs[b][j].y2)) x->cs[b][j].y2 = 0;
        }
    return w + 5;
}

static void dcs_vocoder_dsp(t_dcs_vocoder *x, t_signal **sp)
{
    if (sp[0]->s_n > DCS_BLOCKMAX)
    {
        pd_error(x, "dcs_vocoder~: block size over %d", DCS_BLOCKMAX);
        return;
    }
    if (sp[0]->s_sr != x->sr)
    {
        x->sr = sp[0]->s_sr;
        voc_design(x);
        voc_reset(x);
    }
    dsp_add(dcs_vocoder_perform, 4, x, sp[0]->s_vec, sp[1]->s_vec, (t_int)sp[0]->s_n);
}

static void dcs_vocoder_carrier(t_dcs_vocoder *x, t_floatarg f) { x->carrier = (int)f >= 0 && (int)f <= 3 ? (int)f : 0; }
static void dcs_vocoder_mnote(t_dcs_vocoder *x, t_floatarg hz) { if (hz > 20) x->mtarget = hz; }
static void dcs_vocoder_style(t_dcs_vocoder *x, t_floatarg f) { x->style = f != 0; }
static void dcs_vocoder_wave(t_dcs_vocoder *x, t_floatarg f) { x->wave = (int)f >= 0 && (int)f <= 3 ? (int)f : 0; }
static void dcs_vocoder_cnote(t_dcs_vocoder *x, t_floatarg slot, t_floatarg hz)
{
    int k = (int)slot;
    if (k < 0 || k >= NCHORD) return;
    x->chord[k] = hz > 20 ? hz : 0;
    x->havechord = 1;
}
/* (a new key or scale: the key's triad until the music sends its next chord) */
static void dcs_vocoder_key(t_dcs_vocoder *x, t_floatarg f) { x->key = ((int)f % 12 + 12) % 12; x->havechord = 0; voc_triad(x); }
static void dcs_vocoder_scale(t_dcs_vocoder *x, t_symbol *s, int argc, t_atom *argv)
{
    memset(x->inscale, 0, sizeof(x->inscale));
    x->nscale = argc < 12 ? argc : 12;
    for (int i = 0; i < x->nscale; i++)
    {
        x->scale[i] = ((int)atom_getfloat(argv + i) % 12 + 12) % 12;
        x->inscale[x->scale[i]] = 1;
    }
    x->havechord = 0;
    voc_triad(x);
}
static void dcs_vocoder_snap(t_dcs_vocoder *x, t_floatarg f) { x->snap = f < 0 ? 0 : (f > 1 ? 1 : f); }
static void dcs_vocoder_sib(t_dcs_vocoder *x, t_floatarg f) { x->sib = f < 0 ? 0 : (f > 1 ? 1 : f); }
static void dcs_vocoder_octave(t_dcs_vocoder *x, t_floatarg f) { x->octave = f < -2 ? -2 : (f > 2 ? 2 : f); }
static void dcs_vocoder_mix(t_dcs_vocoder *x, t_floatarg f) { dcs_line_set(&x->mix, f, x->smooth); }
static void dcs_vocoder_on(t_dcs_vocoder *x, t_floatarg f) { dcs_line_set(&x->on, f, x->smooth); }
static void dcs_vocoder_smooth(t_dcs_vocoder *x, t_floatarg f) { x->smooth = f; }

static void *dcs_vocoder_new(void)
{
    t_dcs_vocoder *x = (t_dcs_vocoder *)pd_new(dcs_vocoder_class);
    x->smooth = 30;
    x->sib = 0.5f;
    x->snap = 1;
    x->seed = 12345;
    dcs_line_set(&x->on, 1, 0);
    for (int i = 0; i < 12; i++) x->inscale[i] = 1;
    voc_triad(x);
    outlet_new(&x->x_obj, &s_signal);
    return x;
}

void dcs_vocoder_tilde_setup(void)
{
    dcs_vocoder_class = class_new(gensym("dcs_vocoder~"), (t_newmethod)dcs_vocoder_new, 0, sizeof(t_dcs_vocoder), 0, 0);
    CLASS_MAINSIGNALIN(dcs_vocoder_class, t_dcs_vocoder, x_f);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_dsp, gensym("dsp"), A_CANT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_carrier, gensym("carrier"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_wave, gensym("wave"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_cnote, gensym("cnote"), A_FLOAT, A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_mnote, gensym("mnote"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_style, gensym("style"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_key, gensym("key"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_scale, gensym("scale"), A_GIMME, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_snap, gensym("snap"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_sib, gensym("sib"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_octave, gensym("octave"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_mix, gensym("mix"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_on, gensym("on"), A_FLOAT, 0);
    class_addmethod(dcs_vocoder_class, (t_method)dcs_vocoder_smooth, gensym("smooth"), A_FLOAT, 0);
}
