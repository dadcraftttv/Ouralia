/* dcs: Pd objects by DadCraft Studios. Load them all with [declare -lib dcs].
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * Copyright (c) 2026 dadcraft (DadCraft Studios)
 */
#include "m_pd.h"
#include <math.h>

#define DCS_VERSION "0.1.0"

void dcs_glitch_tilde_setup(void);
void dcs_string_tilde_setup(void);
void dcs_derustle_tilde_setup(void);
void dcs_pitch_tilde_setup(void);
void dcs_shift_tilde_setup(void);
void dcs_onset_tilde_setup(void);
void dcs_reverb_tilde_setup(void);
void dcs_voice_tilde_setup(void);
void dcs_ring_tilde_setup(void);
void dcs_vocoder_tilde_setup(void);
void dcs_trem_tilde_setup(void);
void dcs_crush_tilde_setup(void);
void dcs_dist_tilde_setup(void);
void dcs_filter_tilde_setup(void);
void dcs_echo_tilde_setup(void);
void dcs_freeze_tilde_setup(void);
void dcs_space_tilde_setup(void);
void dcs_chorus_tilde_setup(void);
void dcs_flanger_tilde_setup(void);
void dcs_phaser_tilde_setup(void);
void dcs_taps_tilde_setup(void);

/* the cos~ table (dcs_fx.h): Pd 0.55's own, the same values */
float *dcs_costab;

void dcs_costab_make(void)
{
    if (dcs_costab)
        return;
    dcs_costab = (float *)getbytes(sizeof(float) * (2048 + 1));
    for (int i = 0; i < 2048 + 1; i++)
        dcs_costab[i] = cos(2 * M_PI * i / (double)2048);
    dcs_costab[0] = dcs_costab[2048] = 1;
    dcs_costab[2048 / 4] = dcs_costab[3 * 2048 / 4] = 0;
    dcs_costab[2048 / 2] = -1;
}

/* [dcs_loaded]: sends 1 when the patch it is in has loaded (and on a bang). Where the library didn't load, this box
 * can't be made and nothing is sent: the patch falls back to its plain Pd parts (the Legacy glitch voice). */
static t_class *dcs_loaded_class;

typedef struct {
    t_object x_obj;
} t_dcs_loaded;

static void dcs_loaded_bang(t_dcs_loaded *x)
{
    outlet_float(x->x_obj.ob_outlet, 1);
}

static void dcs_loaded_loadbang(t_dcs_loaded *x, t_floatarg action)
{
    if (action == 0)   /* LB_LOAD */
        dcs_loaded_bang(x);
}

static void *dcs_loaded_new(void)
{
    t_dcs_loaded *x = (t_dcs_loaded *)pd_new(dcs_loaded_class);
    outlet_new(&x->x_obj, &s_float);
    return x;
}

void dcs_setup(void)
{
    dcs_costab_make();
    dcs_loaded_class = class_new(gensym("dcs_loaded"), (t_newmethod)dcs_loaded_new, 0, sizeof(t_dcs_loaded),
        CLASS_DEFAULT, 0);
    class_addbang(dcs_loaded_class, dcs_loaded_bang);
    class_addmethod(dcs_loaded_class, (t_method)dcs_loaded_loadbang, gensym("loadbang"), A_DEFFLOAT, 0);
    dcs_glitch_tilde_setup();
    dcs_string_tilde_setup();
    dcs_derustle_tilde_setup();
    dcs_pitch_tilde_setup();
    dcs_shift_tilde_setup();
    dcs_onset_tilde_setup();
    dcs_reverb_tilde_setup();
    dcs_voice_tilde_setup();
    dcs_ring_tilde_setup();
    dcs_vocoder_tilde_setup();
    dcs_trem_tilde_setup();
    dcs_crush_tilde_setup();
    dcs_dist_tilde_setup();
    dcs_filter_tilde_setup();
    dcs_echo_tilde_setup();
    dcs_freeze_tilde_setup();
    dcs_space_tilde_setup();
    dcs_chorus_tilde_setup();
    dcs_flanger_tilde_setup();
    dcs_phaser_tilde_setup();
    dcs_taps_tilde_setup();
    post("dcs %s: dcs_loaded dcs_glitch~ dcs_string~ dcs_derustle~ dcs_pitch~ dcs_shift~ dcs_onset~ dcs_reverb~ dcs_voice~ dcs_ring~ dcs_trem~ dcs_crush~ dcs_dist~ dcs_filter~ dcs_echo~ dcs_freeze~ dcs_space~ dcs_chorus~ dcs_flanger~ dcs_phaser~ dcs_taps~ dcs_vocoder~", DCS_VERSION);
}
