# SPDX-License-Identifier: GPL-3.0-or-later
"""Traffic permission meets a short rise on the raw mic; no note or gain is generated."""
from .constants import *  # noqa: F401,F403
from .core import *  # noqa: F401,F403


def _ex(P, name, formula):
    return P.obj(name, 'expr ' + formula.replace('$', BS + '$').replace(',', BS + ','), 15500, 100)


def add_traffic(P):
    """50 ms control state, 2 s scalar history. Baseline freezes during candidate/pass.
    traffic_state: 0 reset, 1 listening, 2 permitted, 3 talk (independent of Mind the Gap).
    Windiness is dcs_dewind~'s third outlet, 0..1 even with audible Wind Guard off.
    """
    dt = TRAFFIC_TICK_MS
    ticks = lambda ms: int(ms / dt)
    for recv, var in (('traffic_state', 'tp_state'), ('music_on', 'tp_music'),
                      ('music_stereo', 'tp_stereo')):
        P.obj('tp_r_' + var, 'r ' + recv, 15500, 10)
        P.obj('tp_v_' + var, 'value ' + var, 15500, 25)
        P.c('tp_r_' + var, 'tp_v_' + var)
    # Explicit stop resets even when settingsTick has not yet run.
    P.obj('tp_sk', 'r sk_on', 15500, 10); P.obj('tp_sk0', 'sel 0', 15500, 25)
    P.c('tp_sk', 'tp_sk0'); P.msg('tp_off', '0', 15500, 40); P.c('tp_sk0', 'tp_off')
    P.c('tp_off', 'tp_v_tp_state')
    P.obj('tp_env', 'env~ 1024 512', 15500, 40); P.c('st_mid', 'tp_env')
    P.obj('tp_raw', 'value tp_raw', 15500, 55); P.c('tp_env', 'tp_raw')
    P.obj('tp_wind', 'snapshot~', 15500, 40); P.c('dw', 'tp_wind', 2)
    P.obj('tp_wv', 'value tp_wind', 15500, 55); P.c('tp_wind', 'tp_wv')
    P.obj('tp_hist', f'table tp_history {ticks(TRAFFIC_HISTORY_MS)}', 15500, 60)
    P.obj('tp_lb', 'loadbang', 15500, 70); P.msg('tp_go', '1', 15500, 80)
    P.obj('tp_clock', f'metro {dt}', 15500, 90); P.c('tp_lb', 'tp_go'); P.c('tp_go', 'tp_clock')
    prev = 'tp_clock'

    def step(name, formula, variable):
        nonlocal prev
        trig = 'tp_t_' + name
        P.obj(trig, 't b b', 15500, 100); P.c(prev, trig)
        calc = _ex(P, 'tp_c_' + name, formula); P.c(trig, calc, 1)
        P.obj('tp_v_' + name, 'value ' + variable, 15500, 115); P.c(calc, 'tp_v_' + name)
        prev = trig
        # The next action hangs off the left outlet (the calculation has finished).
        return trig

    # Wire each next step from the previous trigger's left, calculation from its right.
    P.obj('tp_sample', 't b b', 15500, 100); P.c(prev, 'tp_sample'); P.c('tp_sample', 'tp_wind', 1)
    prev = 'tp_sample'
    step('enabled', '(tp_state>0)&&(tp_music>0.5)', 'tp_enabled')
    step('init', 'if(tp_enabled,tp_init+1,0)', 'tp_init')
    a = 1 - math.exp(-dt / TRAFFIC_LEVEL_MS)
    step('level', f'if(tp_init<=1,tp_raw,tp_level+{a}*(tp_raw-tp_level))', 'tp_level')
    step('whi', f'if(tp_wind>{TRAFFIC_WIND_ON},tp_whi+1,0)', 'tp_whi')
    step('wlo', f'if(tp_wind<{TRAFFIC_WIND_OFF},tp_wlo+1,0)', 'tp_wlo')
    step('wveto', f'if(tp_whi>={ticks(TRAFFIC_WIND_IN_MS)},1,if(tp_wlo>={ticks(TRAFFIC_WIND_OUT_MS)},0,tp_wveto))', 'tp_wveto')
    step('veto', '(tp_state==3)||tp_wveto||(hlev>0.15)||(hjolt>0)||(prpk>0.5)', 'tp_veto')
    # Read the two-second-old level before overwriting it.
    P.obj('tp_ht', 't b b', 15500, 120); P.c(prev, 'tp_ht'); prev = 'tp_ht'
    P.obj('tp_ixr', 'value tp_index', 15500, 125); P.c('tp_ht', 'tp_ixr', 1)
    P.obj('tp_read', 'tabread tp_history', 15500, 130); P.c('tp_ixr', 'tp_read')
    P.obj('tp_old', 'value tp_old', 15500, 135); P.c('tp_read', 'tp_old')
    P.obj('tp_write', 'tabwrite tp_history', 15500, 140); P.c('tp_ixr', 'tp_write', 0, 1)
    P.obj('tp_hw', 't b b', 15500, 145); P.c(prev, 'tp_hw'); prev = 'tp_hw'
    P.obj('tp_lr', 'value tp_level', 15500, 150); P.c('tp_hw', 'tp_lr', 1); P.c('tp_lr', 'tp_write')
    step('index', f'int(fmod(tp_index+1,{ticks(TRAFFIC_HISTORY_MS)}))', 'tp_index')
    step('base', f'if(tp_init<={ticks(TRAFFIC_WARMUP_MS)},tp_level,if(tp_candidate>0||tp_active>0,tp_base,tp_base+{1-math.exp(-dt/TRAFFIC_BASE_MS)}*(tp_level-tp_base)))', 'tp_base')
    step('rise', f'(tp_init>{ticks(TRAFFIC_WARMUP_MS)})&&(tp_level-tp_base>={TRAFFIC_RISE_DB})&&(tp_level-if(tp_init<{ticks(TRAFFIC_HISTORY_MS)},tp_base,tp_old)>={TRAFFIC_RISE_DB})', 'tp_rise')
    step('cool', 'max(0,tp_cool-1)', 'tp_cool')
    step('candidate', f'if(!tp_enabled||tp_veto||tp_active||tp_cool>0||tp_level-tp_base<{TRAFFIC_END_DB},0,if(tp_candidate<0,-1,if(tp_candidate>0,if(tp_candidate>={ticks(TRAFFIC_EXPIRE_MS)},-1,tp_candidate+1),if(tp_rise&&!tp_seen,1,0))))', 'tp_candidate')
    step('seen', 'tp_rise', 'tp_seen')
    step('hold', f'if(tp_candidate>0&&tp_level-tp_base>={TRAFFIC_RISE_DB},tp_hold+1,0)', 'tp_hold')
    step('accept', f'(tp_candidate>0)&&(tp_candidate<={ticks(TRAFFIC_EXPIRE_MS)})&&(tp_hold>={ticks(TRAFFIC_HOLD_MS)})&&(tp_state==2)&&!tp_veto', 'tp_accept')
    step('low', f'if(tp_active>0&&tp_level-tp_base<={TRAFFIC_END_DB},tp_low+1,0)', 'tp_low')
    step('was', 'tp_active', 'tp_was')
    step('active', f'if(!tp_enabled||tp_veto||(tp_state!=2)||tp_low>={ticks(TRAFFIC_END_MS)}||tp_active>={ticks(TRAFFIC_MAX_MS)},0,if(tp_accept,1,if(tp_active>0,tp_active+1,0)))', 'tp_active')
    step('rearm', f'if(tp_was>0&&tp_active==0,{ticks(TRAFFIC_REARM_MS)},tp_cool)', 'tp_cool')
    step('clear', 'if(tp_accept||!tp_enabled,0,tp_candidate)', 'tp_candidate')
    step('direction', 'if(!tp_enabled,0,if(tp_accept,1-tp_direction,tp_direction))', 'tp_direction')
    step('pending', 'if(!tp_enabled||tp_active==0||tp_veto||tp_stereo<0.5,0,if(tp_accept,1,tp_pending))', 'tp_pending')
    # Smooth the excess, never add gain. Reset drops the detector immediately but releases the filter gently.
    step('target', f'if(tp_active>0,min(1,max(0,(tp_level-tp_base)/{TRAFFIC_SPAN_DB})),0)', 'tp_target')
    attack, release = (1-math.exp(-dt/t) for t in (TRAFFIC_ATTACK_MS, TRAFFIC_RELEASE_MS))
    step('amount', f'tp_amount+if(tp_target>tp_amount,{attack},{release})*(tp_target-tp_amount)', 'tp_amount')
    # Release on pass end, talk, switch, listener/Music stop, or stereo off.
    P.obj('tp_release_t', 't b b', 15500, 160); P.c(prev, 'tp_release_t'); prev = 'tp_release_t'
    _ex(P, 'tp_release_c', '(tp_active==0)||(tp_stereo<0.5)'); P.c(prev, 'tp_release_c', 1)
    P.obj('tp_release_sel', 'sel 1', 15500, 175); P.c('tp_release_c', 'tp_release_sel')
    P.obj('tp_release', 's tp_release', 15500, 190); P.c('tp_release_sel', 'tp_release')
    # Cutoff recomputes from the live tone each tick, including Calm builds. Off is exactly the old cutoff.
    P.obj('tp_cut_r', 't b', 15500, 190); P.c(prev, 'tp_cut_r')
    P.obj('tp_cut_store', 'value tp_cut', 15500, 190)
    # Store before recomputing, rather than relying on outlet fan-out order.
    P.obj('tp_cut_t', 't b f', 15500, 195); P.c('mu_cut_pad', 'tp_cut_t')
    P.c('tp_cut_t', 'tp_cut_store', 1); P.c('tp_cut_t', 'tp_cut_r')
    # Before the first music_tone, tp_cut is 0: keep lop~'s creation cutoff (1500), as before traffic.
    _ex(P, 'tp_cut_calc', f'if(tp_cut<=0,1500,if(tp_cut>={TRAFFIC_CUTOFF_MAX},tp_cut,min({TRAFFIC_CUTOFF_MAX},tp_cut*(1+{TRAFFIC_OPEN-1}*tp_open))))')
    P.c('tp_cut_r', 'tp_cut_calc'); P.c('tp_cut_calc', 'mu_pad_lp', 0, 1)
    # Interpolate the filter multiplier between 50 ms detector ticks.
    P.obj('tp_open_pk', f'pack f {dt}', 15500, 215); P.c('tp_c_amount', 'tp_open_pk')
    P.obj('tp_open_ln', 'line 0 10', 15500, 230); P.c('tp_open_pk', 'tp_open_ln')
    P.obj('tp_open_t', 't b f', 15500, 245); P.c('tp_open_ln', 'tp_open_t')
    P.obj('tp_open_v', 'value tp_open', 15500, 260); P.c('tp_open_t', 'tp_open_v', 1)
    P.c('tp_open_t', 'tp_cut_r')
    for source, dest in (('tp_cut_calc', 'traffic_cutoff'), ('tp_c_amount', 'traffic_amount'),
                         ('tp_c_active', 'traffic_active'), ('tp_c_accept', 'traffic_accept')):
        P.obj('tp_s_' + dest, 's ' + dest, 15500, 210); P.c(source, 'tp_s_' + dest)


def traffic_voice(P, name, voice):
    """Order: cancel old glide, latch ordinary place, place the note, then take pending pass.
    Called by music_stereo.image instead of its direct pick -> clip connection.
    """
    n = f'tpv{voice}'
    def obj(suffix, text):
        return (P.msg if text in ('stop', '0', '1') else P.obj)(n + suffix, text, 15600, 100 + voice * 200)
    obj('_note', 't b f b'); P.c(name + '_pick', n + '_note')
    obj('_stop', 'stop'); P.c(n + '_note', n + '_stop', 2)
    obj('_line', 'line 0 20'); P.c(n + '_stop', n + '_line')
    obj('_send', f's music_pan{voice}'); P.c(n + '_line', n + '_send')
    obj('_zero', '0'); P.c(n + '_note', n + '_zero', 2)
    obj('_moving', 'value ' + n + '_moving'); P.c(n + '_zero', n + '_moving')
    obj('_place', 't f f'); P.c(n + '_note', n + '_place', 1)
    obj('_home', 'f'); P.c(n + '_place', n + '_home', 1, 1)
    P.c(n + '_place', name + '_clip')
    _ex(P, n + '_eligible', '(tp_pending>0)&&(tp_active>0)&&(tp_state==2)&&(tp_stereo>0.5)&&!(mel_src[0]==1&&(fs_filled[0]+fs_filled[1]+fs_filled[2]>0))')
    P.c(n + '_note', n + '_eligible')
    obj('_sel', 'sel 1'); P.c(n + '_eligible', n + '_sel')
    obj('_take', 't b b b'); P.c(n + '_sel', n + '_take')
    obj('_clear', '0'); obj('_pending', 'value tp_pending'); P.c(n + '_take', n + '_clear', 2); P.c(n + '_clear', n + '_pending')
    obj('_one', '1'); P.c(n + '_take', n + '_one', 1); P.c(n + '_one', n + '_moving')
    _ex(P, n + '_start', f'if(tp_direction>0,{-MUSIC_PAN_CAP},{MUSIC_PAN_CAP})'); P.c(n + '_take', n + '_start')
    obj('_start_t', 't f f'); P.c(n + '_start', n + '_start_t')
    P.c(n + '_start_t', n + '_line', 1)
    obj('_neg', '* -1'); P.c(n + '_start_t', n + '_neg')
    obj('_ramp', f'pack f {TRAFFIC_GLIDE_MS}'); P.c(n + '_neg', n + '_ramp'); P.c(n + '_ramp', n + '_line')
    obj('_release', 'r tp_release')
    _ex(P, n + '_check', n + '_moving'); P.c(n + '_release', n + '_check')
    obj('_ret_sel', 'sel 1'); P.c(n + '_check', n + '_ret_sel')
    obj('_return', 't b b'); P.c(n + '_ret_sel', n + '_return')
    P.c(n + '_return', n + '_zero', 1); P.c(n + '_return', n + '_home')
    obj('_back', f'pack f {TRAFFIC_RETURN_MS}'); P.c(n + '_home', n + '_back'); P.c(n + '_back', n + '_line')
