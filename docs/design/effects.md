# Effects and CPU (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- **Reverse reverb** (roadmap item 7, Oct 13; `add_reverse_reverb`, `reverb_rev` Reverb section 0..1, in presets,
  Intensity rule 1/0.8, Ghost realm 0.5): a sudden sound (`gc_det_sel` through `handling_gate`, one swell at a time)
  books a swell: its reverb tail (`rr_buf`, the reverb's mono wet, 7 s) L ms long read backwards so it peaks at B.
  Free: L = 600 + 900 x room size, B = 2L after the sound. Synced (`msync`, DadCraft: "time the swells to the beat"): L =
  one beat (`tc_P`, max 1500), B = the first beat at least 2L away (`r tempo` resets a timer). Delay ramp
  B - 2L + lead -> B + lead over L (`RR_LEAD_MS` 25: the sound is noticed ~60 ms late and the reverb's first echo
  is ~30 ms after it, so the loudest moment lands on time; 70 ended 50 ms early), 30 ms in, 20 ms out. Measured: a
  clap's swell rises x40 over its last 0.6 s; synced it ends 11 ms from the beat. The first try (Reverse echo's
  continuous two-grain reversal fed the reverb) rose nicely but peaked wherever the sound fell, never on the beat.
  Audio group `revrev` (reverb_on x reverb_mix x reverb_rev).
- **Echo follows your steps** (`echo_sync` Off/1/half/quarter step, default half, in presets): while walking the echo
  time is the averaged step interval (20 ms grid, so step wobble doesn't warble it); 2.5 s after the last step the
  slider's time returns.
- **Dice morph:** with audio running, Randomize glides there over 6 s (`ParamStore.morphTick` from the service tick,
  smoothstep; switches/menus flip halfway). A slider moved meanwhile (hand or world) drops out of the glide; undo,
  nudge, presets and reset stop it. Stopped audio: Randomize jumps as before.
- **Glitch cloud** (Glitch section; `gc_amount` default 0 = off and no CPU, `gc_level` 0.7, kinds by the glitch matrix below;
  roadmap 5b, after dblue Glitch): the hushed mic goes into a 4 s ring buffer (memory only). Triggers: a sudden sound (5 ms
  meter jumps 10 dB over its ~0.25 s average and > -45 dBFS; chance = Amount; then a rest of 2.4 s at Amount 0 -> 0.6 s at 1)
  and a free clock ((2-8 s)*0.6/(0.2+Amount), reset by sounds; it never starts the rest, so a sound always wins). Walking
  (a step in the last 2.5 s): it waits for the next step or half-step, and a stutter slice is a quarter step. Kinds:
  stutter (slice 60-180 ms, or quarter step, x4-7), reverse (plays back the last ~2L ms backwards, after waiting until
  the sound was heard), shuffle (a random slice 0.25-3.25 s old). One voice:
  read position = d0 + a*t - c*fmod(t,S) from plain objects (expr~ cost 3x more), 3 ms slice windows, a new
  glitch fades the old one 4 ms first. Random pan per glitch; it joins both ears before the reverb and feeds it too.
  **Stretch** (`gc_stretch`, 5th kind, roadmap 3 "Ghost capture"; own voice `gc2_*`): the sound smeared at rate
  0.08-0.25 (4-12x slower) for 1.5-3.5 s (capped by the 4 s buffer) in two 100 ms Hann grains, pitch unchanged,
  40 ms in and 600 ms out. The Ghost world turns on stretch + reverse only (Amount 0.45, level 0.8).
  **Glitch matrix** (DadCraft, Oct 10): `gc_pct_stutter/reverse/shuffle/stretch` (0-100 %, defaults 35/25/20/20)
  share each chance; together at most 100 and what's left is the chance nothing happens. `Param.budget("glitch_matrix")`:
  the UI stops a slider at `ParamStore.budgetRoom` (others never move), the dice scale picked shares to fit (locked ones
  kept); past 100 (presets, movers) the patch treats them as proportions. Amount = how often a chance comes. Ghost world
  50 reverse / 50 stretch. Under the sliders a bold "Glitch budget: 80% used · 20% left to silence" line
  (`MainActivity.budgetLines`, updated on slider moves and in refreshRows). No `;` in Params.java comments (the test parser stops there; bit us again).
  **Tape stop and a turntable Scratch were built and removed** (DadCraft, Oct 10: not wanted in the mix; git has them).
  **Voices switch on only while they play** (groups `gcv` = gc0_*, `gcs` = gc2_*, `sw_gcv`/`sw_gcs` sent at each
  glitch, off L+100/L+700 ms later); only the 4 s buffer runs all the time. Two voices work fine this way.
  Width (DadCraft: "around me but not wide enough"): each glitch sits 45-100% out to one side (balance law: near ear
  full, far ear quieter) and the far ear is up to 0.6 ms later (`delread~`, not `delread4~`: that tripled the cost).
  Cost (desktop, vs the patch before it): Amount 0 +3% (echo scatter), glitching nonstop +11% (reverb on +70%, music +310%);
  phone with Music on: 37-39% of a core at Amount 0 and at full, i.e. lost in the noise.
- **Echo scatter** (`echo_scatter`, Echo section, default 0.5, in presets; DadCraft's idea): every repeat lands
  somewhere else between the ears. The echo returns every T ms, so a new place is picked every T ms (snapshot of the
  delay time): at least half the width from the last one (else the other side), 15 ms move. The wet signal now joins
  both ears after the beat (and still feeds the reverb); at 0 it is centred as before. ~+5% of the idle patch, always on.
  **Pd lessons from it:** a value stored with `[f]`'s right inlet never reaches anyone: send the message to every
  consumer's cold inlet. `[pipe]` with several arguments splits a list into outlets (use `[list split]` + `[del]` + `[list]`).
  A second voice in the same switch~ group was silently lost (never explained); voices in their own groups work.
- **Scatter style** (`echo_scatter_style`, Oct 20, DadCraft: "grabbing each echo ... and throwing those sounds
  around"; Classic default, New = `dcs_scatter~`, noPreset like the other style switches): the same place-per-echo-period
  pick as Classic, then (1) direction: the far ear 0.65 ms x |place| later (linear read of a 256-sample ring), at
  1 - 0.75 |place| (never silent, so the delay is heard) and low-passed for the head's shadow (20 kHz x 0.2^|place|);
  (2) `echo_throw`: within the window the place glides from p to p(1 - 2 throw); (3) `echo_depth`: a distance per
  repeat, uniform 0..depth: gain 1 - 0.6 d, both ears darker (x 0.1^d), and wet x d sent to the reverb's input
  (third outlet into `rev_src1`). The ear filters are two one-poles (12 dB/oct; one pole was only 7% darker on
  noise). All moves glide 15 ms. The Classic gains fade out as New fades in (20 ms); off, the object writes zeros.
  Measured (test_patch "Scatter style New"): lag -19..27 samples on the far side, Throw 0.79 gliding steps vs 0.20,
  depth 1 halves the level and the share above 4 kHz 0.78 -> 0.62; cost +6% of the near-empty patch
  (0.01 s per 20 s of audio). Throw and Near and far default 0.5, in presets, faded on the phone while Classic.
- **Switchable DSP:** make_patch.py groups reverb, pitch, chorus, flanger, phaser, reverse echo, Room notes and music
  into `[switch~]` subpatches (`Patch.group` + `add_switch`), on while their level > 0, off 3x smoothing + tail after.
  Room notes fade in 200 ms on switch-on. Phone: music on ~39% of a core on the audio thread, music off ~18%.
- **CPU (Oct 12, DadCraft: "is there any way we can optimize it"):** measured by stripping one object type at a time
  (desktop, everything on): `sigmund~` was ~32% of the patch, oscillators ~13%, `vcf~` ~10%, `delread4~` ~6% (all
  moving delays, kept), `expr~` ~3%. Now: Harmony's tracker runs at half the sample rate in its own group `harmsig`
  (`add_switch(..., block="64 1 0.5")`, a `lop~ 6000` before it; `HARM_HOP` 512 / `HARM_NPTS` 1024 there = ~21 ms
  frames over ~43 ms, 7 steady frames ~150 ms; an 80 ms blip still plays nothing) and Harmony is its own group
  `harm` (music_on x music_harm); each synth chime voice
  is a group `chv<k>` (on at its note, off cdec + 300 ms later), each pad crossfade set a group `pads<k>` (with its
  `mu_xf<k>` ramp: leaving the ramp in the music group made a DSP loop between groups, "DSP loop detected"), the
  limiters are `soft_limit` (rational tanh from plain objects) and the zero-crossing sign is `*~ 1e9` + `clip~`.
  Result: -34% everything on, -49% with Harmony 0; with DadCraft's own settings (reverb 0.6, phaser 0.4, chorus 0.5,
  pitch 0.25, glitch cloud full, music, beat) -22%. What's left there: music 37% (Harmony 18 of it), reverb 13%,
  phaser 10% (its coefficients every 4 ms with [expr]; 8 ms would zipper at 5 Hz), glitch cloud 7%, pitch 7%. Room notes stay always on (with react < 1 their input never
  closes). The step accelerometer stays at 200 Hz (~7.5% of a core, own thread): 100 Hz noticed walking too late (Oct 13).
