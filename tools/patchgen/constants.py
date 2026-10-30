# SPDX-License-Identifier: GPL-3.0-or-later
"""Every tuning number and the settings' defaults (moved out of make_patch.py on Oct 22, as a package)."""
import math
import os
import re
import sys

BS = chr(92)  # a literal backslash; Pd writes escaped semicolons as "\;"
# --legacy-fx: the effects as the Pd objects they were before their dcs ports (tools/test_fx_port.py compares the two)
LEGACY_FX = "--legacy-fx" in sys.argv

# Defaults for the loadbang message (lets the patch also run standalone in desktop Pd).
# tools/test_patch.py checks these against Params.java so they cannot drift.
DEFAULTS = [
    ("gain", 1), ("dry", 1), ("echo_on", 1), ("echo_time", 350), ("echo_sync", 2), ("feedback", 0.4),
    ("wet", 0), ("ring_on", 1), ("ring_freq", 80), ("ring_mix", 0), ("smooth", 30),
    ("pitch_on", 1), ("pitch_semi", 0), ("pitch_mix", 0), ("pitch_window", 60),
    ("crush_on", 1), ("crush_mix", 0), ("crush_bits", 8), ("crush_rate", 8000), ("crush_style", 0), ("tune_amount", 0), ("tune_glide", 40),
    ("voc_on", 1), ("voc_mix", 0), ("voc_style", 0), ("voc_carrier", 0), ("voc_wave", 0), ("voc_sib", 0.5), ("voc_octave", 0), ("voc_snap", 1),
    ("kitl_mel", 0), ("kitl_bass", 0), ("kitl_pad", 0), ("kitl_drums", 0),
    ("kitb_mel", 0.5), ("kitb_bass", 0.5), ("kitb_pad", 0.5), ("kitb_drums", 0.5),
    ("space_on", 1), ("space_pan", 0), ("space_shift", 0),
    ("filter_on", 1), ("filter_mix", 0), ("filter_type", 0), ("filter_freq", 1500), ("filter_q", 1),
    ("reverb_on", 1), ("reverb_mix", 0), ("reverb_size", 0.5), ("reverb_tone", 6000), ("reverb_shimmer", 0),
    ("reverb_shimmer_style", 0),
    ("chorus_on", 1), ("chorus_mix", 0), ("chorus_rate", 0.8), ("chorus_depth", 3),
    ("trem_on", 1), ("trem_depth", 0), ("trem_rate", 5),
    ("gate_thresh", -80), ("comp_thresh", -30), ("comp_ratio", 4), ("comp_makeup", 0),
    ("dist_on", 1), ("dist_mix", 0), ("dist_drive", 4), ("dist_type", 0), ("dist_quality", 1),
    ("rvs_on", 1), ("rvs_mix", 0), ("rvs_time", 400),
    ("glitch_on", 1), ("glitch_hold", 0), ("glitch_len", 120),
    ("flange_on", 1), ("flange_mix", 0), ("flange_rate", 0.3), ("flange_depth", 4), ("flange_fb", 0.5),
    ("phaser_on", 1), ("phaser_mix", 0), ("phaser_rate", 0.4), ("phaser_depth", 0.7), ("phaser_fb", 0.4),
    ("beat_on", 1), ("beat_level", 0), ("beat_hats", 0.5), ("beat_snare", 0.6), ("beat_pitch", 55),
    ("music_on", 0), ("music_key", 0), ("music_scale", 0), ("music_pad", 0.33), ("music_arp", 0.4),
    ("music_tone", 0.5), ("music_react", 0.8), ("music_res", 0.8), ("music_harm", 0.6), ("beat_follow", 1), ("beat_when", 1), ("rhythm_src", 1), ("fs_clean", 0), ("melody_src", 1), ("harm_src", 1), ("pad_src", 1), ("bass_src", 0), ("tempo_mode", 0), ("tempo_bpm", 100), ("chime_char", 0.5), ("res_src", 1), ("cue_sound", 1), ("beat_glitch", 0.4), ("gate_attack", 5), ("gate_hold", 0), ("gate_release", 80), ("comp_attack", 40),
    ("comp_release", 40), ("music_drift", 1), ("echo_scatter", 0.5), ("echo_scatter_style", 0), ("echo_throw", 0.5), ("echo_depth", 0.5), ("gc_amount", 0), ("gc_level", 0.7), ("gc_pct_stutter", 35), ("gc_pct_reverse", 25),
    ("gc_pct_shuffle", 20), ("gc_pct_stretch", 20), ("gc_pct_pitch", 0), ("gc_pct_gate", 0), ("music_pad_reg", 2), ("pocket_hush", 0.5), ("pocket_guard", 0.6), ("handling_hush", 0.5), ("intensity", 0.5), ("mix_kick", 1), ("mix_bass", 1), ("mix_chimes", 1), ("mix_walk", 1), ("mix_ring", 1), ("mix_bells", 1), ("reverb_rev", 0), ("music_answer", 0.5), ("beat_break", 0), ("mel_rhythm", 0), ("mel_phrase", 0), ("music_var", 0), ("mel_dev", 0), ("pat_gen", 0), ("chord_color", 0), ("new_pieces", 0), ("walk_ease", 1), ("band_plays", 0), ("chime_answer", 0),
    ("gc_voice", 1), ("fm_ring", 0), ("pocket_rustle", 0), ("wind_guard", 0), ("pocket_steps", 0), ("harm_tracker", 0), ("shift_voice", 0), ("onset_mode", 0),
]

# Scales as semitone steps above the key note. The patch snaps every note it plays to these.
SCALES = [
    (0, 2, 4, 5, 7, 9, 11),     # Major
    (0, 2, 3, 5, 7, 8, 10),     # Minor
    (0, 2, 4, 7, 9),            # Major pentatonic
    (0, 3, 5, 7, 10),           # Minor pentatonic
    (0, 2, 3, 5, 7, 9, 10),     # Dorian
    (0, 2, 4, 5, 7, 9, 10),     # Mixolydian
    (0, 2, 4, 6, 7, 9, 11),     # Lydian
    (0, 2, 4, 6, 8, 10),        # Whole tone
]
# Pad chords per scale, as scale steps of the chord root (0 = the key chord): the home chord and its two strongest
# neighbours, picked by the brightness of the room, plus one contrast chord now and then (None = no contrast;
# pentatonic and whole-tone chords are all consonant, so they keep every chord). No diminished chords anywhere.
CHORD_POOLS = [
    ((0, 3, 4), 5),            # Major: C F G + Am
    ((0, 3, 4), 2),            # Minor: Cm Fm Gm + Eb
    ((0, 1, 2, 3, 4), None),   # Major pentatonic
    ((0, 1, 2, 3, 4), None),   # Minor pentatonic
    ((0, 3, 4), 6),            # Dorian: Cm F Gm + Bb
    ((0, 3, 6), 4),            # Mixolydian: C F Bb + Gm
    ((0, 1, 4), 5),            # Lydian: C D G + Am
    ((0, 1, 2, 3, 4, 5), None),  # Whole tone
]
CONTRAST_PCT = 20             # chance (in 100) that a chord change picks the contrast chord
# Key drift: every few minutes the home chord moves to a related one on the same notes (C major -> D dorian, G or
# A minor feel), and later comes home. Only moves where every chord of the pool stays free of diminished chords.
DRIFT_MIN_S, DRIFT_MAX_S = 180, 360
DRIFT_HOME_PCT = 60           # away from home, the chance the next drift goes back home


def drift_options(iv, pool):
    """Scale steps the home chord may drift to: those where every pool chord moved there is not diminished."""
    n = len(iv)
    def note(d):
        return iv[d % n] + 12 * (d // n)
    def ok(r):
        return note(r + 4) - note(r) != 6 or note(r + 2) - note(r) != 3
    return [o for o in range(n) if all(ok(o + r) for r in pool)]
# Footstep arpeggios: scale steps above the current chord root, one per footstep. Every bar (8 steps) a new
# pattern is picked at random, and now and then a step rests.
# Ordered calm -> busy: each bar picks one near the walking pace (a stroll stays near the top, a brisk walk near
# the bottom), with a little randomness and never the same one twice.
ARP_PATTERNS = (
    (0, 2, 4, 5, 4, 2, 1, 2),        # a little melody around the root (small steps)
    (0, 2, 4, 7, 4, 2, 0, 2),        # up and back
    (0, 4, 2, 4, 7, 4, 2, 4),        # broken chord
    (7, 4, 2, 0, 2, 4, 6, 4),        # down and up, with a passing note
    (4, 2, 7, 4, 9, 7, 4, 2),        # high and leaping
)
ARP_REST_SLOW_PCT = 25       # chance (in 100) that a footstep plays no note when strolling...
ARP_REST_FAST_PCT = 5        # ...and when walking briskly
# Density memory: the music counts the notes it played lately (each fades over DENSITY_S) and makes room when busy
DENSITY_S = 8.0
DENSITY_BUSY = 10            # notes "in memory" at which half the chimes are skipped (about 1.25 a second)
DENSITY_ARP_FROM = 16        # the walking arpeggio only starts resting more past this...
DENSITY_ARP_PCT = 2          # ...by this many percent per note over it...
DENSITY_ARP_MAX = 30         # ...up to this much
DENSITY_ARP_WEIGHT = 0.3     # an arpeggio note counts this much (the steady bed must not silence the chimes on the beat)
CHIME_SPAN = 4               # chime melodies wander this many scale steps either side of the brightness note
CHIME_DECAY_MS = (900, 2600)     # each chime's fade, picked at random
# Melody rhythm (mel_rhythm, Music: 0 off = as before, 1 straight, 2 dotted, 3 mixed; Oct 15): one-bar rhythm cells
# on the clock's 16ths for the walking arpeggio and the standing-still chimes. A cell is note lengths in 16ths (a
# negative number is a rest), adding up to a bar; each pool runs calm -> busy and every bar picks one near the drive.
MEL_CELLS_STRAIGHT = (
    (16,),                          # a whole note
    (8, 8),                         # two halves
    (4, 4, 8),                      # quarter, quarter, half
    (4, 4, 4, 4),                   # quarters
    (2, 2, 2, 2, 8),                # four 8ths and a half
    (2, 2, 4, 2, 2, 4),             # 8ths and quarters
    (2, 2, 2, 2, 2, 2, 2, 2),       # 8ths
)
MEL_CELLS_DOTTED = (
    (12, 4),                        # dotted half, quarter
    (6, 2, 8),                      # dotted quarter, 8th, half
    (-2, 6, 8),                     # a late start: an 8th's rest, dotted quarter, half
    (6, 6, 4),                      # 3 + 3 + 2 (tresillo)
    (2, 4, 2, 8),                   # syncopated: 8th, quarter, 8th, half
    (3, 1, 3, 1, 4, 4),             # dotted 8th + 16th, twice, then quarters
    (3, 3, 2, 3, 3, 2),             # 3 + 3 + 2 in 16ths, twice
)
MEL_RING = (1.0, 200, 250, 3000)    # a note's ring (ms) = its length x this + this, kept within these
# Melody phrases (mel_phrase, Music, with Melody rhythm on; Oct 15): four bars A A' B A. A is made up (stepwise, chord
# tones on beats 1 and 3), A' repeats it but ends on the chord's root, B is new, then A comes back; A's rhythm cell
# comes back with it.
PH_STEP2_PCT = 30                   # a made-up note moves one scale step, or two this often (in 100)
CHIME_OVERTONE = (0.12, 0.45)    # each chime's 2nd-harmonic level (3rd is a third of it): mellow to bright
BASS_FOLLOW_PCT = 40    # chance (in 100) that a chord change moves the bass to the chord's root; else the key's root
BASS_DIP_MS = 600       # a bass note change fades out over this...
BASS_RISE_MS = 1500     # ...and the new note fades in over this
BASS_GAIN = 0.11
BASS_DUCK = 0.6         # a footstep takes the bass down this share while the walking beat plays...
BASS_DUCK_MS = 220      # ...and it comes back over this
PAD_GLIDE_MS = 3500     # the pad crossfades from one chord to the next over this time (no pitch slides)

# Room notes: resonators that make the mic's own sound ring the current chord: root, third and fifth (scale steps
# 0, 2, 4 above the chord root), one and two octaves above the pad. (octaves, steps) pairs, so pentatonic and
# whole-tone scales get real octaves too.
RES_NOTES = ((1, 0), (1, 2), (1, 4), (2, 0), (2, 2), (2, 4))
# How long they ring follows what was heard: a short soft sound gives a quick chime, a loud one rings longer, and a
# sound that goes on builds up to a long bell. A note rings for about ring * sqrt(262 / Hz) seconds (time constant,
# so low notes longer, high notes shorter), with ring from RES_RING_SHORT_S to RES_RING_LONG_S.
RES_RING_SHORT_S = 0.08
RES_RING_LONG_S = 2.0
RES_LOUD_DB = 30        # this far above the room's quiet counts as fully loud
RES_LOUD_SHARE = 0.5    # how far loudness alone moves the ring toward long (the rest comes from duration)
RES_BUILD_S = 1.5       # a fully loud sound that lasts this long reaches the long bell
RES_RELAX_S = 8.0       # afterwards the ring length relaxes back toward short (time constant)
RES_STEADY = 0.4        # ring length with react = 0 (ignores the room)
RES_RING_REF_S = 0.3    # the ring length RES_GAIN was tuned at
RES_GLIDE_MS = 400
RES_GAIN = 20.0     # make-up: a narrow band holds little of a broad sound
RES_MAX = 0.3       # soft ceiling for the whole voice
RES_OPEN_DB = 10    # the input opens fully this many dB above the room's quiet
RES_LOOKAHEAD_MS = 20
POCKET_HUSH_DB = 24         # pocket hush at 1 turns the mic down this far (0.5, the default: 12 dB)
ECHO_WALK_HOLD_MS = 2500   # echo stays locked to the steps this long after the last one
RES_FADE_IN_MS = 200       # Room notes fade in this long each time their block switches on
STOP_AFTER_MS = 1200       # no step for this long after walking = you stopped
STOP_DIP = 0.55            # the pad and bass breathe down to this share...
STOP_DIP_MS = 2500         # ...over this long...
STOP_RECOVER_MS = 8000     # ...then drift back to full over this long
STOP_RETURN_MS = 800       # walking again brings them straight back
WALK_EASE_VEL = (0.18, 0.21)  # walk_ease: a walking note's loudness from ambling to brisk (as before 0.4 to 1.0)...
WALK_EASE_PACE = 0.3         # ...the pace it follows moves this share of the way to each new step's...
WALK_EASE_START, WALK_EASE_STEPS = 0.35, 8   # ...and a walk starts at this share, full after this many steps
GROOVE_VEL = 0.45           # groove notes are this share of the arpeggio's loudness...
GROOVE_FADE_IN_MS = 4000   # ...reached over this long
# Harmony: a held, clearly pitched sound (humming, whistling, a bird, a horn) gets an in-key note answering it.
HARM_HOP = 512             # the pitch tracker looks every this many samples, at half the sample rate (its own group
HARM_NPTS = 1024           # "harmsig"): ~21 ms frames over ~43 ms windows. At full rate with hop 512 it was ~30% of the patch
HARM_STABLE_FRAMES = 7     # pitch frames (~21 ms each) the pitch must hold steady before it is answered
HARM_STEADY_ST = 0.6       # frame to frame wobble allowed, in semitones (singing vibrato passes)
HARM_ABOVE_DB = 6          # how far above the room's quiet the sound must be
HARM_HIGH = 76             # a pitch from E5 up (a whistle, a bird) is answered below it; lower ones (voices) above
HARM_RANGE = (36, 104)     # heard pitches outside C2..G#7 are hiss or rumble, not a voice, whistle or bird
HARM_ATTACK_MS = 150
HV_WINDOW_MS = 40          # your voice as the harmony: grain length of its pitch shifter, made an even number of the
                           # heard note's periods (the two grains then stay in step on it instead of warbling)
HV_RELEASE_MS = 400        # its fade when you stop (the organ's 1.8 s would shift the room for too long)
HV_SPLIT = 60              # a voice from middle C up is answered below it (shifting a high voice up makes a chipmunk:
                           # DadCraft heard it singing high; most women's and children's voices sit up there)
HV_GAIN = 0.7              # its level against your own voice, before the Harmony slider
# Glitch cloud: bits of what the mic heard (kept in memory only, a few seconds) come back mangled, around you.
# Drum patterns for the tempo clock (16 steps = one bar of 16th notes), calm -> busy; the value is the hit's
# velocity, 0 = rest. Hats above 2 are open hats (velocity = value - 2). Each bar a pattern near the "drive" (pace when
# walking, liveliness of the room when standing) is picked per lane, never the same kick pattern twice in a row.
KICK_PATS = [
    (1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    (1, 0, 0, 0, 0, 0, 0, 0, .8, 0, 0, 0, 0, 0, 0, 0),
    (1, 0, 0, 0, 0, 0, .6, 0, .85, 0, 0, 0, 0, 0, 0, 0),
    (1, 0, 0, 0, 0, 0, .6, 0, .85, 0, .6, 0, 0, 0, 0, 0),
    (1, 0, 0, .5, 0, 0, .7, 0, .9, 0, 0, 0, 0, .6, 0, 0),
]
SNARE_PATS = [
    (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, .6, 0, 0, 0),
    (0, 0, 0, 0, .9, 0, 0, 0, 0, 0, 0, 0, .9, 0, 0, 0),
    (0, 0, 0, 0, .9, 0, 0, 0, 0, 0, 0, 0, .9, 0, 0, .35),
    (0, 0, 0, 0, .9, 0, 0, .3, 0, .3, 0, 0, .9, 0, 0, 0),
    (0, 0, 0, 0, 1, 0, 0, .3, 0, 0, .35, 0, 1, 0, .3, 0),
]
HAT_PATS = [
    (.6, 0, 0, 0, .5, 0, 0, 0, .6, 0, 0, 0, .5, 0, 0, 0),
    (.6, 0, .35, 0, .55, 0, .35, 0, .6, 0, .35, 0, .55, 0, .35, 0),
    (0, 0, .6, 0, 0, 0, .6, 0, 0, 0, .6, 0, 0, 0, 2.5, 0),
    (.6, .25, .4, .25, .55, .25, .4, .25, .6, .25, .4, .25, .55, .25, 2.45, 0),
    (.6, 0, .4, .3, .55, 0, 2.4, 0, .6, 0, .4, .3, .55, .3, 2.5, 0),
]
FILL_PAT = (.45, .6, .75, .95)     # snare over the last beat of every 8th bar (when the drive is up)
RH_HOLD_BARS = 2                   # a pattern choice lasts this many bars
# Found-sound kit (roadmap 16 C2/C3): sudden sounds are caught (memory only) and become the drums.
FS_SLICE_S = 0.25                  # a caught slice...
FS_PREROLL_MS = 15                 # ...starting this long before the sound was noticed (keeps its attack)
FS_SLOTS = 5                       # slices kept per role (kick, snare, hat); the oldest is replaced (3 felt samey)
FS_VAR_SEMI = 1.0                  # each hit: its pitch moved up to this many semitones either way...
FS_VAR_START = (0.3, 10)           # ...this share of hits starting up to this many ms into the slice (a softer attack)...
FS_FLOURISH = (25, 25)             # per 1000 found hits: played backwards (swelling up to its attack), at half speed
FS_VAR_VEL = 0.2                   # ...and its loudness varied by this much (0.9-1.1): no two hits quite the same
FM_REF_HZ = 600                   # found melody: a caught slice plays at note / this (clipped to FM_RATE)...
FM_KICK_REF_HZ = 150              # (a kick's slice: about a thump's pitch, so a knock is pitched up to the note,
FM_KICK_GAIN = 0.45               #  where it rings its resonators much harder: this much)
FM_RATE = (0.5, 4.0)
FM_RAW = 2.5
FM_RES = 18
FM_CHAR_RAW = 6.0                 # "Chime character" at 1 (your sound): the hit this much louder (x1 + this)...
FM_CHAR_RING = 0.85               # ...the ring this much quieter...
FM_CHAR_SHORT = 0.7               # ...and this much shorter, the hit's band widened to Q 0.3
FM_STRIKE_Q = 1.2                 # the strike is band-passed around the note (rounder than high-passed: DadCraft)
FM_ATTACK_MS = 5
FM_STR_GAIN = 40                 # Found note ring = String (dcs_string~): the string's level...
FM_STR_BRIGHT = 0.3              # ...its brightness (0 dark .. 1 bright), at Chime character 0...
FM_STR_CHAR_BRIGHT = 0.4          # ...and this much brighter at 1
FM_PLUCK = 0.3                    # each note's pluck, x the note's loudness (velocity x the slice's gain)
CUE_LOW_HZ, CUE_HIGH_HZ = 440, 880     # the Realm cue: a warp gliding between A4 and A5 (up into a Realm)...
CUE_SIDE_HZ = 659.3               # ...or, Realm to Realm, a sideways swerve around E5...
CUE_GLIDE_MS = 900                # ...over this long...
CUE_DETUNE = 1.007                # ...a detuned twin beating against it (a slow shimmer)...
CUE_LEVEL = 0.022                 # ...this loud per tone (after the limiter: the three together about -26 dBFS)...
CUE_SWELL_MS, CUE_FADE_MS = 300, 1200   # ...swelling in, then melting away...
CUE_ECHO_MS, CUE_ECHO_FB, CUE_ECHO_WET = 320, 0.4, 0.4   # ...with a touch of echo (DadCraft), which dies away within
CUE_ECHO_TAIL_MS = 2500                 # this long after the tone
RNF_GAP_MS = 180                  # Room notes, found: at most one note per this long...
RNF_GAIN = 0.08                   # ...at this level into its soft limit (the Room notes slider scales it after)
FM_LIMIT = 0.7                    # the found melody's soft limit (tanh)
FM_LEVEL_DB = 85                  # its level is brought toward this (dB, 100 = full scale; a little under a synth chime)...
FM_LEVEL_RATIO = 0.9              # ...by this share of the difference (a clap and a knock ring very differently)
FP_SLOTS = 2                      # found pad: held pitched sounds kept (the newest is used at each chord change)
FP_TAKE_S = 0.6                   # each take, from FP_PREROLL_MS before the steady pitch was noticed
FP_PREROLL_MS = 180
FP_GRAIN_MS = 120                 # its stretch grains (two, half overlapping), started a whole number of the caught
FP_LOOP_S = (0.08, 0.35)          # note's periods apart so a steady note stays steady, wandering in this part
FP_GAIN = 0.075                    # per chord voice (the synth pad's oscillator pairs are ~0.03-0.04 each)
FB_GAIN = 2.8                     # the found bass against the synth bass's (1 + 0.35 + 0.15) oscillators
FS_POP_DB = 94                    # louder than this (-6 dBFS) and mostly lows: a mic pop, not kept
FS_MIN_DB = 55                     # quieter than this (-45 dBFS at its loudest) is not worth keeping
FS_FLOOR_RISE = 0.02               # (its floor creeps up this many dB a reading: 0.6 dB a second)
# Room in Stereo (Oct 22): the Camcorder mode of some phones (the Moto) hides a quiet room's steady background, ~20 dB
# under the mono mic's, and the trim that matches talk and claps lowers soft sounds with it: a fixed -45 dBFS misjudges
# it. In stereo a catch must instead stand FS_ST_ABOVE_DB over the room's own background (fsfloor), never needing
# more than FS_MIN_DB (mono's) nor less than FS_ST_MIN_DB
FS_ST_ABOVE_DB = 15                # (mono's -45 is ~15 dB over an office's -60)
FS_ST_MIN_DB = 35                  # (-65 dBFS)
FS_FLOOR_CUT = 10                  # mic readings under this (-90 dBFS) are no sound yet, not a room


def fs_min(mono_db):
    """The found kit's least loudness (dB, 100 = full scale) as an expr: mono_db, or in stereo the room's own."""
    return f"if(fsstereo>0,max({FS_ST_MIN_DB},min({mono_db},fsfloor+{FS_ST_ABOVE_DB})),{mono_db})"
# Clear Catches (fs_clean, dB, 0 = off): a catch is kept only that far above the room's quiet floor (fsfloor): it is
# levelled to a drum's loudness, its background with it (Oct 22: a -40 dBFS thump over -66 dBFS hiss came out with
# 22 dB more grain than a loud one)
FS_RMS_TARGET = 0.3                # a slice's loudest 10 ms is brought no higher than this (a clipped snap was a boom)
FS_LOUD_DB = 65                   # a room louder than this (-35 dBFS on average)...
FS_ONSET_LOUD_DB = 6               # ...needs only this jump for a catch (else GC_ONSET_DB, 10)
FS_PLACEHOLDER = 0.25             # before anything is caught, the synth kit plays this softly (a pulse to start from)
FS_MAKEUP = 3.0                    # real sounds are short and thin next to synth drums: lifted this much...
FS_LIMIT = 0.9                     # ...into a soft limit at this (tanh), which also thickens them
GL_KEEP = 0.65                     # a glitch loop keeps this much of its level each bar
GL_MAX_S = 3.2                     # the longest bar a loop can hold (80 BPM = 3 s)
GL_MIN_PEAK = 0.02                 # a recorded glitch quieter than this (the free clock on near-silence) is not kept
FS_HEARD_ADD = 0.8                 # the learned groove: a catch adds this at its spot in the bar...
FS_HEARD_KEEP = 0.55               # ...which keeps this much each bar (a few bars of echo, then gone)
FS_HEARD_MIN = 0.3                 # below this a spot plays nothing
FS_ROLE_SPEC = (                   # role: playback rate (pitch), filter, level, decay ms
    ("k", 0.8, "lop~ 3500", 1.0, 260),   # (gentle filters: a borrowed sound must keep its character)
    ("s", 1.0, "hip~ 160", 0.8, 200),
    ("h", 1.3, "hip~ 1500", 0.7, 90),
)
RH_FILL_EVERY = 8                  # bars
BREAK_MIN_BARS = 8                 # arrangement moments: at least this many bars between sparse bars...
BREAK_CHANCE = 0.2                 # ...then this chance each bar (not on a fill bar): hats only, no kick, or downbeat only
SWING_CHOICES = (0, 0.08, 0.14, 0.2, 0.26)   # swing: off-beat 16ths this share of a 16th late, a new pick every...
SWING_EVERY_BARS = 8               # ...this many bars (DadCraft: "a little too predictable"), glided toward...
SWING_GLIDE = 0.4                  # ...by this much each bar
# Breakbeat (beat_break, Rhythm, 0 = exactly as before; Oct 15): as the slider rises the bars lean on these kick and
# snare pools (off-beat kicks, snares on the "and", ghost notes; calm -> busy like the others, picked by the same drive)...
BREAK_KICK_PATS = [
    (1, 0, 0, 0, 0, 0, 0, 0, 0, 0, .8, 0, 0, 0, 0, 0),
    (1, 0, .7, 0, 0, 0, 0, 0, 0, 0, .85, 0, 0, 0, 0, 0),
    (1, 0, .7, 0, 0, 0, 0, 0, 0, 0, .85, .6, 0, 0, 0, 0),
    (1, 0, 0, 0, 0, 0, .6, 0, 0, .7, 0, 0, 0, 0, .6, 0),
    (1, 0, .75, 0, 0, 0, 0, .55, 0, .7, .8, 0, 0, 0, 0, .5),
]
BREAK_SNARE_PATS = [
    (0, 0, 0, 0, .9, 0, 0, .25, 0, 0, 0, 0, .9, 0, 0, 0),
    (0, 0, 0, 0, .9, 0, 0, .3, 0, .3, 0, 0, .9, 0, 0, 0),
    (0, 0, 0, 0, .9, 0, 0, .3, 0, 0, 0, 0, .9, 0, .7, 0),
    (0, 0, 0, 0, .9, 0, 0, .3, 0, .3, 0, .25, .9, 0, 0, .75),
    (0, .2, 0, 0, .95, 0, 0, .35, 0, .35, 0, .25, .95, 0, .3, .6),
]
BB_POOL = 0.8                      # ...each pick taking them with this chance x the slider (the top stays mixed)
CHOP_MAPS = [                      # a chopped bar: tick t plays the pattern's step map[t] (the downbeat always stays)
    tuple(range(16)),
    (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 8, 9, 8, 9, 14, 15),        # a stutter
    (0, 9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 3, 4, 5, 6, 7),    # halves swapped
    (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 4, 5, 6, 7),        # the last beat repeats the second
    (0, 1, 2, 3, 0, 1, 2, 3, 8, 9, 10, 11, 12, 13, 14, 15),    # the second beat repeats the first
    (0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 12, 12, 12),    # the last step repeats
]
BB_CHOP = 0.25                     # chance per bar at the slider's top that a bar is chopped...
BB_ROLL = 0.2                      # ...gets a short roll (3-4 snares 32nds apart, from one of ROLL_STARTS)...
ROLL_STARTS = (5, 9, 13)
ROLL_VELS = (.35, .45, .55, .7)
BB_FILL = 0.1                      # ...or a long fill (FILL_LONG over the last two beats)...
BB_FILL_LONGER = 0.4               # ...and a usual fill becomes long with this chance x the slider (each lasts 2 bars)
FILL_LONG = (.3, .35, .4, .5, .55, .65, .8, .95)
CLOCK_MIN_MS, CLOCK_MAX_MS = 375, 750   # beats are folded into 80-160 BPM
CLOCK_DEFAULT_MS = 600       # 100 BPM before any walking
CLOCK_PHASE_GAIN = 0.35      # each step moves the next beat this much of the way to it...
CLOCK_FREQ_GAIN = 0.1        # ...and the beat length this much of the error...
CLOCK_PACE_GAIN = 0.15       # ...and this much toward the step interval itself
TEMPO_NUDGE = 0.3           # Tempo "Set": each step moves the beat this much of the way to its walking target...
TEMPO_WALK_PULL = 0.5       # ...which is this far from your BPM toward the step rate (in the octave nearest your BPM)...
TEMPO_RETURN = 0.25         # ...and standing still it glides back to the set BPM by this much each beat
CLOCK_FRESH_MS = 2000        # no step for this long (plus a beat): the next step starts a fresh walk on its beat
CLIP_IN = 0.99                # diagnostics: the mic as it arrives counts as overloaded from here (some phones stop just short of 1)
CLIP_WINDOW = 512             # ...counted in stretches of this many samples (~12 ms)
GATE_HYST_DB = 4            # the gate opens at its threshold and only closes this far below it (no flutter)
GC_KINDS = ("gc_pct_stutter", "gc_pct_reverse", "gc_pct_shuffle", "gc_pct_stretch",   # the glitch matrix, by mode
            "gc_pct_ratchet", "gc_pct_pitch", "gc_pct_gate", "gc_pct_spray")      # (Oct 19: from 0%, to try)
# Kept in the back pocket (DadCraft, Oct 19: he kept Pitch jump and Gate): no setting, so their share stays 0, but
# the brain and dcs_glitch~ still know them. To bring one back: take it out of here and give it a Param again.
GC_POCKET = ("gc_pct_ratchet", "gc_pct_spray")
GC_BUF_MS = 4000
GC_ONSET_DB = 10           # a sudden sound: the 5 ms level jumps this far above its recent average...
GC_MIN_DB = 55             # ...and is at least this loud (-45 dBFS), so a quiet room's flicker doesn't count
GC_COOL_MS = (600, 2400)   # rest after a glitch: at Amount 1 / near 0 (also keeps the two voices from overlapping)
GC_STEP_WAIT_MS = 2500     # a step this recently = walking: a glitch waits for the next step or half-step
GC_NEW_VOICES = 4          # Glitch voice = New (dcs_glitch~): this many glitches can sound at once
GC_PREROLL_MS = 15         # a glitch of a sudden sound starts this long before it was noticed (5 ms meter), so its attack stays
HARM_RELEASE_MS = 1800


def pc_to_degree(iv):
    """For each semitone above the key note (0..11), the nearest scale step (len(iv) = the key note an octave up)."""
    marks = list(iv) + [12]
    return [min(range(len(marks)), key=lambda i: (abs(marks[i] - pc), marks[i])) for pc in range(12)]
DUCK_MS = 600           # how quickly the output dips (and comes back) when other audio plays
RR_MIN_MS = 600             # reverse reverb: a stretch is this long in the smallest room...
RR_SIZE_MS = 900            # ...plus this much more at the largest
RR_MAX_MS = 1500            # ...and never longer (a beat below 40 BPM is capped)
RR_BUF_MS = 7000            # the reverb's tail is kept this long (a swell can come up to ~4 slow beats later)
RR_FADE_IN_MS = 30          # the swell opens this fast (its own rise does the rest)...
RR_FADE_OUT_MS = 20         # ...and closes this fast at the moment of the sound
RR_LEAD_MS = 25             # the read ends this long before the sound was noticed (~60 ms after it started: meter,
                            # handling check), at the reverb's first echo (~30 ms after the sound), so the loudest moment is on time
RR_GAIN = 1.5               # its level at reverb_rev 1 (the windows and the backwards tail lose a little)
CALM_CHIME_P = 40           # a calm build at its peak: a standing-still chime about every 6 s even in a quiet room
BAND_PHRASE = 8             # The Band Plays: a phrase of this many bars builds up and drops back...
BAND_ARC = (-0.15, 0.4, 1.6)  # ...the drive moved from -0.15 at its first bar to +0.25 at its last (curve power 1.6)...
BAND_LIFT = 0.06            # ...each phrase this much bigger, smaller or the same as set...
BAND_FLOOR = 0.3            # ...standing still the drive never below this, however calm the room...
BAND_CHIME_P = 30           # ...and standing-still chimes get this chance (in 1000 per 0.25 s), half to 1.5x over the phrase
INT_DRIVE = 0.6             # Intensity 0..1 moves the beat's drive by -0.3..+0.3 (calmer / busier patterns)
HND_HUSH_DB = 24            # handling hush at 1 turns the mic down this much at full handling (the app's 0-1 level)
HND_ATTACK_MS = 15          # ...going down this fast (a tap rings on for a while; its very start can't be caught)
HND_RELEASE_MS = 300        # ...and back up over this
HND_TRIG = 0.3              # sudden sounds while handling is above this start nothing (no chime, glitch, catch)
HND_JOLT_MS = 200           # a jolt (tap or grip, from the app) makes the sounds of the last/next this many ms handling
HND_CHECK_MS = 40           # a glitch waits this long for a jolt reported just after the sound (sensor vs mic delay)
POCKET_HUSH_MS = 400        # how fast the hush fades in and out
POCKET_GUARD_DB = 12        # mic-down protection at 1 turns the mic down this much more...
POCKET_GUARD_HP_HZ = 300    # ...and filters out the cloth thump below about this (12 dB/octave)
RUSTLE_ABOVE_DB = 6         # Rustle squash (pocket_rustle, dcs_derustle~): bursts this far over the room's floor are
RUSTLE_RELEASE_MS = 80      # ...pulled back toward it (amount = the slider), letting go over this
SD_DB = 18                  # Step duck (pocket_steps): at 1 the mic dips this far around each footfall...
SD_NOW_MS = 120             # ...for this long when a step is reported (its own rustle is mostly past by then)...
SD_LEAD_MS = 100            # ...and from this long before the next step is due (step interval from now)...
SD_HOLD_MS = 220            # ...for this long, then back up over SD_RELEASE_MS
SD_RELEASE_MS = 80

# Flanger: the delay sweeps from this minimum up to minimum + depth (ms). [delread4~] cannot go
# below one audio block (1.35 ms at 48 kHz) and the feedback loop needs at least that, so 2 ms.
FLANGE_MIN_MS = 2.0

# Phaser: four allpass sections whose centre frequencies are these multiples of the swept
# frequency; the sweep is centre * 2^(+-2 * depth).
PHASER_CENTRE_HZ = 700
PHASER_STAGES = (1.0, 1.7, 2.9, 5.0)
PHASER_Q = 0.7

# Chorus: two delay taps around these base delays (ms), swept by a shared LFO a quarter cycle
# apart. The sweep depth (ms) is a parameter and must stay below the smaller base delay minus
# about 1.5 ms (the shortest delay [delread4~] can do).
CHORUS_BASE_MS = (15, 21)

# Reverb (a Freeverb-style design): parallel feedback combs, then series allpasses. The delay
# times are in milliseconds, so the sound is the same at any sample rate. The right channel
# uses slightly longer delays so the two channels decorrelate and the reverb sounds wide.
COMB_MS = [25.3, 26.9, 29.0, 30.7, 32.2, 33.8, 35.3, 36.7]
ALLPASS_MS = [12.6, 10.0, 7.7, 5.1]
RIGHT_EXTRA_MS = 0.52

# A short [delread4~] cannot go below about one audio block (1.35 ms at 48 kHz). Both ears are
# therefore delayed by this much, plus the ear-delay offset on one side, so the neutral setting
# is centred and identical in both ears. tools/test_patch.py expects this latency.
EAR_BASE_MS = 1.5
