# Our own Pd objects: `dcs/` (Oct 16)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- DadCraft chose the prefix `dcs_` (DadCraft Studios: not tied to the app's name, which isn't final) and BSD 3-Clause
  (Pd-compatible, so useful ones can go upstream or onto deken). A library of its own, NOT in the libpd fork (that
  stays tiny: bug fixes only). Lives in this repo for now; it can move to its own repo when published.
- Loaded by `#X declare -lib dcs;` right after the patch's `#N canvas` line (make_patch.py writes it). A `[declare]`
  object alone does nothing while a file loads. Android: `app/src/main/jniLibs/<abi>/libdcs.so` (committed, built by
  `tools/build_dcs_android.sh` with NDK 29 against pd-core's libpd.so, 16 KB-aligned); Pd finds `lib<name>.so` in the
  native lib folder. Desktop/tests: `make -C dcs` -> `dcs/build/dcs.pd_linux`; pdtest adds dcs/build to the search
  path; the desktop app opens libpdnative.so RTLD_GLOBAL via JNA first (Java loads it privately: the library then
  finds no Pd symbols) and run.sh passes `-Douralia.dcs`. After changing dcs/src: `make -C dcs`,
  `tools/build_dcs_android.sh`, `tools/test_dcs.py`. If libdcs is missing, its objects fail to create, and since
  the effect ports (Oct 17) sit in every sound's path **nothing plays**, not even the dry room (measured Oct 19: the
  brain still runs and fires glitches into a dead chain, so a Legacy fallback for one part would be useless).
  Instead the app says so (Oct 19, DadCraft: "the warning is fine"): `[dcs_loaded]` (dcs.c; sends 1 on the patch's
  loadbang and on a bang) -> `[s dcs_ok]` in the built-in patch. The phone (AudioService `dcsCheck`, 1 s after
  Start, built-in patch only) shows status_objects_missing in bold on the status line and logs it; the desktop
  (DesktopApp, 1 s after each patch open, its libpd queues the message) puts DCS_MISSING in the window's messages.
- `dcs_string~` (Found note ring = String, `fm_ring`, Music, noPreset, default Resonator): Karplus-Strong with a
  one-pole loss filter from two decay times (bottom = decay, top = decay x 10^(2b-2.5)), pole capped at 0.9 and
  backed off by bisection so the loop's DC gain stays < 1 without cutting the note short; tuning includes both
  filters' phase (worst 0.03 cents C2-C7). `pluck amp [pos]` adds a zero-mean triangle across one period. In the
  patch the band-passed strike (`{n}_bp`) feeds it and each note plucks it (`FM_PLUCK` 0.3 x the note's loudness):
  a clap alone has nothing at a low note and rang only the 2nd/3rd harmonics (thin); with the pluck the fundamental
  leads ~10x. `FM_STR_GAIN` 40, brightness `FM_STR_BRIGHT` 0.3 + `FM_STR_CHAR_BRIGHT` 0.4 x chime_char, ring =
  cdec x (1 - 0.7 cc). Measured with a clap: same notes as the resonator, tonal 0.71 (resonator 0.39), ~+3 dB.
- `dcs_glitch~` (Glitch voice = New, `gc_voice`, Glitch section, noPreset; **the default since Oct 19**, DadCraft:
  "if the newer one outperforms the old one" it should take over, the old one kept as a fallback, renamed Legacy;
  stored values stay, so existing installs keep their choice): takes the brain's
  lists unchanged (`slice L d0 c1 c2 S place` from gc_wait_l, `stretch L d0 rate place` from gc_spack; spigots
  `gc_vc_*` pick the voice), 4 voices (+ 4 slots for stolen ones fading 4 ms), own 4 s buffer, sample-exact starts
  (vline~'s block timing, the fraction carried), ITD as before. Stretch: 4 Hann grains of 100 ms, each new grain
  placed within +-8 ms where its waveform best matches the playing grain (WSOLA, 5.3 ms compared, every 2nd
  sample): a held tone swings 0.1 dB (the old 2-grain stretch: grains half-cancel, 22 dB in the same test).
  Cost while glitching +11% of the patch (Legacy +7%); in the gcloud switch group, so nothing at Amount 0.
- `dcs_pitch~` (Pitch tracker = New, `harm_tracker`, Music, noPreset, default Classic) and `dcs_shift~` (Pitch shifter
  = New, `shift_voice`, Pitch section, noPreset, default Classic; also Your voice harmony), Oct 17, sharing
  `dcs_yin.h`: YIN at ~11 kHz (25 ms window, 60 Hz up), stopping once the dip under 0.15 bottoms out, every 2nd
  sample, then refined at the full rate over +-(D/2+1) lags; under 0.3 = a pitch. dcs_pitch~ = sigmund~'s outputs
  (pitch, -1500 for none; level in Pd dB; + sureness), as control outlets (in CONTROL_TILDE). Measured: sines
  80 Hz-3 kHz exact, a bright sawtooth no octave errors, noise no pitch; **cost: both ~0.1% of real time at the
  patch's hop** (sigmund~ at hop 512 at half rate is already cheap: the old "32%" was before that), so the win is
  accuracy, not battery. dcs_shift~ = TD-PSOLA: analysis points whole periods apart (in step), grains 2 x max(period,
  spacing) Hann, laid at period / ratio, 30 ms latency, pitch looked for every 10 ms (or `freq` given), 8 ms grains
  for unpitched sound. A synthetic vowel up a fifth: exact pitch, formant stays at ~674 Hz for 700 (a resampling
  shifter: ~1050), level within 0.9 dB. A pure sine shifted down keeps ~88% on the note (PSOLA spacing > period).
  Patch: both trackers in `harmsig`, spigots pick one; the voice harmony's dcs_shift~ reads `delread~ hv_buf 0`
  (a direct line from the harm group into hvoice and back was a DSP loop).
- `dcs_onset~` (Sudden-sound detector = New, `onset_mode`, main, noPreset, default Classic), Oct 17: spectral flux
  over 24 log bands 80 Hz-16 kHz, ~11 ms frames every ~2.7 ms (mayer_realfft), each band floored 30 dB under the
  frame's whole (without it a vibrato tone's leakage in one-bin bands fired 12-32 times), rise counted over the max
  of that band and its neighbours two frames before (SuperFlux); fires one hop after a peak over 1.5 x the 0.5 s
  average + threshold (40), level over 55 Pd dB, 50 ms apart; also outputs level, lows, highs (dB under the whole),
  centroid, jump. Measured: claps in a quiet room 10/10 within 11 ms; claps 8 dB over a roar 9/10; vibrato and a
  0.4 s swell: none. Patch: `add_onset_switch` reroutes every listener of gc_det_sel / fs_det_sel to on_gc / on_fs,
  fed by the classic detectors or dcs_onset~ (on `mic`, always running); `s onset_heard` for the tests. In the
  patch a swell triggers Classic once, New never; a clap both once.
- **Vocoder** (Oct 18, section "Vocoder": `voc_mix`, `voc_on`, `voc_carrier`, `voc_octave`, `voc_snap`, `voc_sib`;
  after Sing in key, before the ring modulator). dcs_vocoder~: 20 bands 120 Hz to 7 kHz (constant-peak band-passes,
  two in series, Q from the band spacing), the voice's band envelopes (3 ms up, 25 down) laid on the carrier's bands,
  each carrier band first divided by its own envelope (10/60 ms) so a saw's tilt and a chord's gaps don't colour the
  words (2-3% noise under the saws keeps every band alive). Carriers: 0 the music's chords (the pad's four notes, each
  as two saws +-6 cents; the music sends `cnote` from each pad degree; with no music yet, or after a key or scale
  change, the key's own triad from C3), 1 your own pitch (YIN every 10 ms, snapped to the key by `voc_snap`, ~10 ms
  glide, two saws), 2 whisper (noise). `sib` adds the voice above 5 kHz (two one-pole high-passes) so s and t stay
  clear. No delay. At amount 0 the voice passes untouched and nothing is computed; on again, the bands start afresh.
  Level about the input's (x1.0 on a vowel). DadCraft (Oct 18) chose console music (the Chiptune kits) over a console
  voice; chip carriers for the vocoder came on Oct 19 (below). Tests: test_dcs.py, test_patch.py 7n.
  **Chip carriers** (Oct 19, `voc_wave`, "Vocoder synth sound", default Saws; both styles, every carrier but
  Whisper): one band-limited pulse per note (two of the polyBLEP saws, the second `duty` later, mean 0) at the mean
  of the two saws' pitches, no detune (the consoles had none): 1 = 25% (NES lead), 2 = 12.5%, 3 = 50% square (Game
  Boy). Saws stay bit-identical. Measured through the talk box with a noise voice (the carrier's own harmonics):
  square 3rd over 2nd +54 dB (saws -3), 25% pulse 4th 40-42 dB under its neighbours; level x1.03 of the saws.
  Approved by ear (DadCraft, Oct 19: "sound fine"); Saws stays the default.
  **Whole octaves** (Oct 19): `octave` is rounded in dcs_vocoder~. The app's slider (decimals 0) shows whole
  numbers but stores where the finger stopped: DadCraft's phone had 1.82, which put the synth 2 semitones off the key.
  **Talk box** (Oct 19, `voc_style` 1; Bands stay the default): DadCraft asked for "a voxbox", the tube-in-the-mouth
  sound played from a keyboard (Daft Punk, Midnight Generations). Linear prediction instead of bands: every 6 ms the
  last 24 ms of the voice (pre-emphasised 0.97, Hann window, 60 Hz lag window) gives a 24-pole filter (Levinson-
  Durbin, reflection coefficients clamped to +-0.995) and the voice's residual level. The carrier is flattened
  (the same pre-emphasis), levelled (30 ms power follower), run through the filter as a lattice (the coefficients
  glide sample by sample over each hop, which keeps it stable), then de-emphasised. Gain 0.72 makes it about the
  voice's level. Measured: a vowel's resonance at 500, 1200 and 2400 Hz comes out at 532, 1116 and 2409 Hz. It costs
  0.7% of a desktop core (the bands cost 2.0%). New carrier 3 for both styles, **the music's melody**: each chime
  note's pitch goes out as `voc_ctl mnote`, held until the next with a ~15 ms slide (before any, the chord's root
  an octave up).
- **Sing in key** (Oct 18, `tune_amount`, `tune_glide`; section "Sing in key", before the ring modulator in the
  chain). dcs_shift~ got a snap mode: `key`, `scale <steps>`, `snap 0..1`, `glide ms`, `run`. Each 10 ms hop the
  tracked pitch picks the nearest note of the scale (it keeps its note until another is nearer by 0.2 semitone: no
  flipping between two), and the correction eases there over `glide` (0 = the hard robotic jump); with no pitch it
  lets go within ~30 ms. The correction used is the one from 2 hops back (the grains are cut 30 ms late). Follows
  the Music key and scale whether or not the music plays. On, the voice is 30 ms late, so the effect replaces the
  plain voice (a crossfade on Amount > 0) instead of mixing (a late copy would comb). Off = `run 0`: nothing computed,
  and a fresh start when on again (no old sound in the grains). Old presets turn it off; not in the randomiser.
  Tests: test_dcs.py (lands within 6 cents, half way at 0.5, other keys, glide), test_patch.py 7m. Planned next
  (DadCraft, Oct 18): **Chip voice** (the pitch tracker plays your voice on NES/Game Boy/Genesis channels, with a
  chip-wave vocoder so words stay clear), then a Chiptune kit. DadCraft: the Crusher styles "don't seem to really make
  my voice or sounds sound like they're coming from the console systems": they copy each console's sample playback,
  not the chip voices people remember.
- **Effect ports** (Oct 17, DadCraft: "dcs versions of the effects we have. I want the performance benefits"; the
  crusher "more accurate 8 or 16 bit sounds", as a Style menu). Each effect becomes one dcs object that does what its
  Pd objects did **sample for sample** (`dcs/src/dcs_fx.h`: line~ in whole blocks, osc~/cos~ with Pd 0.55's 2048-point
  table and the tabfudge phase, phasor~, wrap~, delwrite~/delread4~ laid out alike, [line]'s clock in Pd time units:
  a clock fires before the block whose end is past it), so no switch: nothing changes to the ear. The in-line effects
  (ring, crusher, distortion, filter, tremolo) ran even at amount 0 (they sit in the chain, so a Pd switch~ couldn't
  stop them); a port passes the input through and only keeps its clocks, LFO phases and filter state moving.
  **Exact float**: files including dcs_fx.h build without -ffast-math (it rewrote `x - (x - k)` as k: the crusher's
  rounding flipped a step; the clang pragma is ignored on armv7, so the Makefile and Android.mk build them apart).
  **Test**: `tools/test_fx_port.py` builds the patch both ways (`make_patch.py --legacy-fx <file>` keeps the old Pd
  chains) and plays the same input and setting changes (on, glides, smoothing changes, off long enough for a switch
  to stop it, back on) through both; it pins echo_scatter at 0 (its [random] draws differ between two runs in one
  process). Limit -80 dB: most ports match to the bit or a float step (ring, tremolo 0; others 3e-8..2.4e-7; spatial
  1e-6); the phaser drifts to 4.8e-5 because libpd itself is built with -ffast-math, so expr's maths and biquad~
  round as that compiler chose (the sweep steps land on the same blocks: a timing slip shows as 1e-3 or more).
  **All 13 ported** (Oct 17): ring, tremolo, crusher, distortion, filter, echo (dcs_echo~: the time still glides in
  Pd, which the scatter reads), freeze (dcs_freeze~), spatial (dcs_space~), chorus, flanger, phaser, and dcs_taps~
  for both the Classic pitch shifter and the reverse echo. The switched ones (chorus, flanger, phaser, pitch, reverse)
  keep the patch's gate (`add_switch(..., dsp=False)`) and take `sw_<g>` as `run`: off they stand still as switch~ left
  them (LFO, buffer, the phaser's metro; output silent) and start off, as a switch~ does. Pd ran the chorus's reads
  before its write (a read that crosses 1024 samples rounds differently the other way); the delays in feedback loops
  (echo, freeze, flanger, phaser) read first, a block behind, as delread~ did. **Measured** (bench_effects, 20 s, best
  of 3): at rest 0.167 -> 0.151 s (-10%), DadCraft-like settings 0.305 -> 0.261 s (-14%), the phaser on +0.051 -> +0.019 s
  (its 4 [expr]s every 4 ms were the cost); chorus, pitch and reverse about as before (their per-sample work, delay
  interpolation and the cos table, was already tight C in Pd's objects: an exact port can't skip it; a looser one
  could, as a switch). **Crusher Style** (`crush_style`, noPreset, dcs,
  Classic the default; models by ear and by the machines' known numbers, not emulators): 8-bit sampler 11025 Hz
  unsigned 8-bit truncation, 4-pole anti-alias in and 2-pole out; SP-1200 26040 Hz 12-bit, no input filter, 12 kHz out;
  NES the DPCM channel (steps of 2 on a 7-bit counter at 33144 Hz) and the console's HP 90 / HP 440 / LP 14 kHz;
  Game Boy 4-bit 8192 Hz, a DC block only; SNES 32 kHz BRR (best of 4 predictors x 13 shifts per 16-sample block,
  decoded as the S-DSP does) then the Gaussian interpolation's taps at rest 0.18 / 0.64 / 0.18; Genesis 8-bit ~13.3 kHz
  with the YM2612 ladder step either side of zero and a 6 kHz first-order low-pass. Bit depth and Sample rate apply
  to Classic only. Measured (test_dcs): every style in tune at the input's level (NES -4 dB: its 440 Hz high-pass),
  the sampler and SNES under 0.1% of their energy over 7 kHz, the Game Boy's images ~100x more, the NES channel
  can't follow a loud 3 kHz tone (-21 dB, as on the console).
- **CPU round, Oct 17** (desktop, 20 s of noise; process time): whole patch with defaults 0.205 -> 0.172 s, with
  DadCraft-like settings (reverb 0.6, phaser 0.4, chorus 0.5, pitch 0.25, glitch 1, music, beat 0.6, echo 0.4)
  0.603 -> 0.493 s (2.5% of a desktop core; the phone is ~10x slower). (1) **The prototype twins no longer both
  run**: each new object has its own switch group needing its effect AND its switch (`onsetn`: onset_mode;
  `pshiftn`: pitch x shift_voice; `harmsign`: harm_run x harm_tracker; `hvnew`: sw_hvoice x shift_voice), the
  Classic ones need `<name>_c` (= 1 - switch, sent by `inv_<name>`); dcs_onset~ alone was ~20% of the idle patch.
  (2) **dcs_reverb~** replaces the ~100-object Freeverb (8 low-passed combs + 4 all-passes per ear, COMB_MS /
  ALLPASS_MS / RIGHT_EXTRA_MS as args): identical sample for sample (test_dcs builds the Pd-object version and
  compares: worst 1.3e-6), ~55-60% of its cost (the maths is the same; the saving is per-object overhead: Pd's own
  objects are already tight C). Reverb at DadCraft's settings 0.069 -> 0.039 s. Still always on: 338 small signal
  objects (117 *~, 84 line~ smoothers, 62 +~...), ~0.9% of a desktop core idle.
  **Shimmer** (Oct 22, ROADMAP 18's "shimmer mode"; `reverb_shimmer` "Shimmer" in Reverb, 0-1, default 0, noRandom,
  in presets, " *"): inside dcs_reverb~ (`shimmer <0..1>`, gliding ~0.7 s). The two ears' output, halved, goes through
  an octave-up shifter (two 80 ms grains read at 2x, Hann-crossfaded from a 1024-point table), a 200 Hz - 5 kHz band
  (one-poles) and back into the combs' input the next block, times 0.28 sqrt(1 - feedback^2) (how the patch scales
  the input) x SHGAIN 0.5 x the amount, through tanh. Each trip adds an octave, quieter; the damping takes the top.
  Off, the shifter only copies into its line (nothing read: the output identical to before, the cost as before:
  test_dcs's sample-for-sample match and cost checks still pass). SHGAIN measured in the patch at the worst case
  (size 1, brightness 16000, shimmer 1, loud noise, 30 s after): gain 1 never died (rms 0.47, held by the tanh), 0.7
  lingered (3e-4 at 30 s), 0.5 dies (2e-5 at 20 s) with the octaves still twice the note's energy in the tail; 0.35
  0.9x. Tests: test_dcs (660+1320 Hz over 330 Hz in the tail: 67 vs 0.0002 off; no runaway at feedback 0.98),
  test_patch "reverb Shimmer" (octaves, and the biggest room with Shimmer 1 within +-1 and decaying). Phone only
  (the desktop gets the slider with the shared Params).
  **Shimmer Style** (Oct 23; `reverb_shimmer_style` "Shimmer Style" in Reverb: Classic / Mist, default
  Classic, noRandom, in presets, " *"; dcs_reverb~ `style <0|1|2>`). The user's choice: Realms and built-in
  presets leave it, an older preset without it doesn't reset it (Params.STYLES and realmsLeave; DadCraft Oct 23, a
  Realm change had put it back to Classic). DadCraft: Classic "sounds fine on played notes",
  but on real-world sounds metallic; he wanted whimsical, misty, sparkly. Measured (no mic: a voice-like buzz gliding
  100-180 Hz through 700 / 1800 Hz formants, footsteps, a clap, a wind swell, StreetBeatz's break, through the
  reverb at feedback 0.93): the tail's long-term spectrum, its 99th percentile over the local median (sharp fixed
  peaks = metallic). Classic on the voice 81x (4.6x without shimmer), on the break 160x (21x); on claps, steps and
  wind it hardly shows (why notes and claps sound fine). **Mist**: each ear its own shifter, 4 grains of 120-220 ms,
  random start delays (0-30 ms), 3 in 4 an octave up (+-12 cents) and the rest in place (+-20 cents), a
  250 Hz - 3.5 kHz band, into that ear's combs at MGAIN 1.0. The first Mist (half in place, MGAIN 0.5, as SHGAIN)
  rose 10x less than Classic (20x less in the patch: octave share 0.10 vs 2.2): on the phone DadCraft heard no
  difference from Classic, only quieter, under his Pitch +12 at 25%, chorus, phaser, echo and a Reverb Level mover.
  Swept: MGAIN 1.0 with 1 in 4 in place rises like Classic (6.95 vs 6.39 in test_dcs; 1.14 vs 2.2 in the patch, was
  0.10), voice peaks 6.0x, the biggest room still dies (2e-6 at 22 s); 1.5 rises far more (43) and lingers more. On the voice 5.5x; with +-7 / +-12 cents it was 7.1x (still
  rang a little), +-15 / +-30 5.5x but the octave starts to sound out of tune. Ears' correlation ~0 (Classic feeds both
  the same). **Sparkle** (style 2, back pocket: off the menu Oct 23, DadCraft "still sounds wrong... I don't know
  that it's worth pursuing"): Mist plus 4 glints per ear: 15-40 ms grains two octaves up (+-4 cents), 20-150 ms of
  silence after each, high-passed at 1 kHz, straight out at TWOUT 0.25, never into the tail. The first Sparkle (2
  louder 30-80 ms grains, two octaves or an octave and a fifth, 0.8 out and 0.3 into the tail): DadCraft "something
  went really wrong": its highs as loud as the whole tail, chirps, and fed back they climbed. Both new styles read
  their line through a 4.5 kHz low-pass (twice; read 2-4x fast, the top would fold down). The biggest room still
  dies away for both (test_dcs). Clips for listening were rendered from these sounds (not from the mic).
  Mist's verdict open (ROADMAP Open now).
- **Kits** (Oct 17, roadmap 19 step a+b; DadCraft: kits for acoustic / synth / EDM / future bass / low end, mix and
  match, loadable, to replace the stems player; first new style: Acoustic). The music brain is untouched; a kit says
  what each part sounds like. **Classic = today's Pd voices**, nothing ported: a part's kit engine 0 leaves it as it
  was. `dcs_voice~` (one per part: `mu_kv_mel`, `ba_kv`, `mu_kv_pad`, in the music group) has engines 1 pluck
  (Karplus-Strong as dcs_string~, x1.5), 2 bowed (the string kept up by a pulse once a period + 0.1 breath noise:
  plain noise wandered 6 dB; x6; released strings die over `release`), 3 modal (bell / marimba / bowl partial banks,
  struck once, gain ~ sin w); `note hz vel ms [bright]` one-shots, `pnote slot hz` held notes that crossfade when the
  slot moves. Patch (`add_kits`): `kit_<part>` receivers feed the voices; `route engine` -> melody `value kmel`
  (the synth's vsyn and the found twin's gate x (kmel==0); each voice's note also goes out as `note cfreq cvel cdec
  cbright`), bass/pad `kit_<part>_cl` (== 0, 50 ms line~) on the Classic sound (synth + found), and
  `kit_<part>_new` resends the held notes (`ba_kf`, `mu_pad<i>_kf`) so a kit chosen mid-chord sounds at once.
  Java: `Kits.java` reads `assets/kits/<name>.txt` (one line per part: `mel: engine pluck, bright 0.45, level 4.3`;
  unknown parts/settings skipped), `Kits.BUILT_IN` = the Kit menu; Param `kit` (Music, noPd, in presets); on a
  change and in `ParamStore.sendAllToPd` every part gets the voice's defaults, the kit's settings, the engine last
  (PdBase.sendMessage). Desktop reads assets/kits from the repo (run.sh/test.sh SHARED + Kits). Tests: test_dcs
  (each engine: pitch, harmonics, a held note's level within 2.4 dB in 100 ms frames, slot moves, poff, bell's
  2.76x partial, engine 0 silent), Java Q, test_patch "kits" (reads acoustic.txt; each part sounds on the same
  notes within +-6 dB of Classic; Classic within the natural run-to-run spread, ~12%). Acoustic levels set to
  match Classic (melody 4.3, bass 5, pad 0.05). Pd lessons: in add_music `ex()` doesn't escape ";" (a 4-output expr
  became 1 output and three stray messages: `note 0 0.5 0 0`); two runs in one process never match sample for
  sample (random choices carry on). **Mix and match** (Oct 17): "Melody from" / "Bass from" / "Pad from"
  (`kitp_<part>`, Music, noPd, in presets; 0 = the Kit, else BUILT_IN[n - 1]); `Kits.send(kit, from, force, out)`
  sends only parts whose kit changed (a resent part's held notes would start over), all of them from
  `ParamStore.sendAllToPd`. Each part's menu (and layer menu) marks a kit with nothing for that part, which plays
  Classic's sound there: "Synth (Classic's drums)" (Oct 19, `Kits.fromChoices(part)`; DadCraft tried parts on the
  desktop and heard no change). The labels need the kits' text, so the phone reads the built-in kits at launch
  (`AudioService.builtInKits`) and the desktop refreshes the menus once its kit folder is known. **Kit settings are not resetWhenAbsent**: a Realm's preset (no kit in it) would have
  reset the Kit to Classic whenever a Realm took over the Music ingredient; now a preset that doesn't mention them
  leaves them. That never helped Realms: every built-in preset starts from `defaults()`, so each Realm held Classic
  for the kit and every part and switched them back (DadCraft, Oct 20: "the kit or user selected kit parts should not
  change"). Now a Realm never sets a `kit*` setting (Kit, from, layer, blend; `Worlds.realmChanges`, test W); its
  music levels and the Mix still change. Loading a preset by hand still sets them. **Layers** (Oct 19, DadCraft: "running both the NES pad and synthwave pad simultaneously and being
  able to control how much of each is present"): "<Part> layer" (`kitl_<part>`: 0 = None, else BUILT_IN[n - 1],
  same numbering as "from", in Kits.PARAMS so presets carry a user kit used as a layer) and "<Part> blend"
  (`kitb_<part>`, 0 = the first kit, 1 = the layer, default 0.5). Both go to Pd; Kits sends the layer's kit to a
  second dcs_voice~ per part (`kit2_<part>`; no layer = the voice's defaults, engine 0). add_kits works out three
  shares from the two engines, the layer switch and the blend, equal power (cos/sin): Classic's (a side on Classic
  gives its share to the Classic sound, at most 1), the first kit's and the layer's, each gliding 50 ms. Every note
  goes to both voices (a voice on engine 0 ignores it); the Classic melody's notes stop while its share is 0
  (`kmelcl`, as `kmel==0` did). The kit voices' sum for melody, bass and pad must be built inside the music
  subpatch (names `mu_...`): built outside, music -> top -> music is a DSP loop and the whole patch goes silent. The
  drums' pump comes from the first kit only. No layer = exactly as before (tests: test_patch "a sample kit", Q).
  **Synth kit** (Oct 17, step 1 of "one by one"): `dcs_voice~` engine 4 analog = two PolyBLEP saws
  `detune` cents apart + a PolyBLEP sub square (`sub`) into a 4-pole TPT ladder (zero-delay feedback solved,
  tanh on the input, k = 3.9 x `reso`), cutoff = note x `cutoff` x (1 + `fenv` x env), the env falling over the
  note (or `decay` for held notes), cutoff per block. Measured: in tune; brightness 218 / 341 / 819 Hz at cutoff
  1.5 / 4 / 16; reso 0.9 lifts the 6th harmonic 20x; stable at full resonance; a 3 kHz saw 100% on its harmonics;
  a held note within 0.09 dB (detune off: detuned saws beat on purpose). synth.txt: melody cutoff 2.5 reso 0.35
  fenv 8, bass cutoff 1.2 reso 0.5 sub 0.7, pad cutoff 1.6 detune 14 attack 1.5 s; levels 7 / 13 / 0.5 to sit
  near Classic. The kits test now checks every kit plays in key at Classic's level (each run in a process hears
  other random chords; a dying pluck's FFT lobe had ripples just under the note: take the 3 strongest peaks 20 Hz
  apart). **Marker**: `Param.dcs()` -> `shown()` adds " *" on phone and desktop to every setting using our C
  objects (DadCraft, Oct 17; content descriptions stay plain). **EDM kit** (Oct 17, step 2): drums are a 4th part
  (`kit_drums`, `kd_voice`, "Drums from" `kitp_drums`); dcs_voice~ engine 5 drums: `hit <0 kick|1 snare|2 hat>
  <vel> [hat decay]` (kick: a sine diving from 4x `ktune` over 30 ms, `kdecay`, a 3 ms click, tanh `kdrive`;
  snare: `stone` and 1.83x tones over 80 ms + one-pole high-passed noise over `sdecay`; hat: noise high-passed
  twice, `hdecay` or the hit's own); it plays the pattern engine's `drum_kick` / `drum_snare` and `rh_h_v` (vel,
  decay), scaled by mix_kick, beat_snare / 0.6, beat_hats / 0.5 (`add_kit_drums`). In "Your steps" (beat_follow 0)
  the old step drums feed it the same way (Oct 19): each step's kick or snare as drum_kick / drum_snare 1, the hat
  half a step later from `h_pack` (step-strength level and decay); before, a kit with drums was silent there
  (test_patch "kit drums in Your steps": EDM 0.000 before). The Classic beat (synth + found:
  `beat_sum00`) x `kit_drums_cl`. Supersaw: `saws` 1..7 spread across `detune` (2 = the note and +detune, as
  before), 1.41 / sqrt(n) each, every saw from a random phase (no "zap"). **Pump**: `pump` depth and `pumprel` ms
  come with the drums part (`route pump pumprel` in the patch, ignored by the voice); each drum_kick (while
  beat_level > 0) snaps `kit_pump` (vline~) to 1 - depth in 4 ms and back over pumprel, on `mu_mix0` (pad, bass,
  chimes; not room notes or harmony). edm.txt: supersaw lead (7 saws, 22 c), sub bass (sub 0.9), supersaw pad
  (7 saws, 30 c), drums ktune 52 kdecay 380 kdrive 0.6, pump 0.55 / 220 ms; drums level 0.38 (0.068 rms = Classic's).
  Measured in the patch: the music dips 5.3 dB right after each kick (median of 10), +0.4 without the pump.
  **Future bass kit** (Oct 17, step 3): analog `wobble` 0..1 / `wrate` cycles a beat = one LFO shared by the
  voice's notes, restarted by `tempo <bpm>` (the patch sends `r tempo` to the melody/bass/pad kit voices on every
  beat): each cycle the level dips (1 - 0.8 x wobble x phase) and the cutoff closes (x (1 + 2 x wobble x (1 -
  phase))); `bend` semitones / `bendms`: each note starts off and glides in. Engine 6 chop: a note plays `chop` ms
  of the newest found-pad take (reads fpad_n [count, newest], fpad_<n>, fpad_f0, fpad_g with garray_getfloatwords),
  from 0.2-0.3 s in, at note / take pitch folded into 0.75-1.5, 3 ms in, 25 ms out, x fpad_g x `CHOP_GAIN` 0.15;
  nothing kept yet: the note plays on analog with the same settings. Tests (test_dcs): 2 a beat at 120 BPM pulses
  at 4.00 Hz (158x vs none), a -2 bend starts at 206 Hz and lands on 220, a 220 Hz take plays a 330 Hz note for
  178 ms. futurebass.txt: chop melody (saws 5 fallback), sub bass, pad 7 saws 35 c wobble 0.7 wrate 2 bend -2,
  drums with pump 0.7 / 320 ms. The test runner now silences checks run only as another section's dependency
  (they ran under other random choices and counted), and the glitch cost check interleaves its three setups.
  **Chiptune kits** (Oct 18; DadCraft heard the Crusher styles and wanted "the console music sound" more than a
  console voice: a kit, since the chips played notes, not processed sound). Engine 8 `chip`: `chip` 1 NES / 2 Game
  Boy / 3 Genesis, `wave` pulse (PolyBLEP, `duty`) / triangle (NES: 32 steps, 15 down to 0 and back) / wave (Game
  Boy: 32 four-bit samples, a soft ramp) / fm (two operators, modulator feedback `fmfb`, `fmratio`, `fmindex`
  falling with the note, output to 9 bits as the YM2612's DAC). Pitch through the chip's period register (NES
  CPU/(16 or 32 x (t+1)), Game Boy 131072/(2048-x), the wave channel half that): high notes land slightly off, as on
  the hardware (3520 Hz plays 12 cents flat). Volume in 16 steps (not Genesis): `sus` = where it falls to over
  `decay`. `vib` cents after 150 ms. One note per part at a time (a channel: the new note takes over the old one's
  phase). `arp` ms: held notes (the pad's chord) take turns on one channel, ranked by slot; a chord change is
  instant. Output stage per console: NES high-passes 90 and 440 Hz, low-pass 14 kHz (the bass triangle comes out
  thin, as it did: its level is 30 to match Classic); Game Boy HP 30, LP 12 kHz; Genesis LP 7 kHz. Drums on a chip
  (`hit`): the noise channel (15-bit LFSR at the console's rates; NES hats in short mode, metallic), the kick a
  sweep on the triangle (NES, cut rather than faded), a pulse (Game Boy) or a sine (Genesis), plus a noise click.
  Kits chipnes, chipgb, chipgenesis (Genesis has six FM channels: its chords stay chords). Levels matched to
  Classic by the kits test. Adding built-in kits moves user kits' menu numbers up (DadCraft's imported kit needs
  choosing again; presets carry the text, so they find it).
  **Low end kit** (Oct 17, step 4, the last style): engine 7 sub (an 808): a sine x `drive` through tanh, falling
  60 dB over `decay` from each note; a held slot that moves glides there (`glide` ms, ~ x 0.3 time constant) and
  starts its level again; `retrig` restarts held 808 notes, sent by the patch on each kick while beat_level > 0,
  else on each beat (an 808 note otherwise died long before the next chord change: silence). Any engine:
  `octave` (notes n octaves off), `chance` (a one-shot plays with this chance), `hchance` (the same for hats).
  lowend.txt: marimba melody at chance 0.45 (level 8), 808 bass decay 2.5 s drive 0.35 glide 90 (level 2.5), pad
  3 saws an octave down, attack 2 s, drums ktune 42 kdecay 900, hats at hchance 0.4, no pump. Kit tests play every
  note (chance and hchance dropped there; sparseness is test_dcs's, over 200 notes: 55-64 of 200 at 0.3).
  **User kits** (Oct 17, step d, stage 1: text only; DadCraft chose kit.txt over kit.json, text kits before samples,
  and presets that carry the kit's text): `files/kits/<NNN title>/kit.txt` (phone and desktop:
  `~/.local/share/ouralia/kits`; `Kits.userDir`, set in `ParamStore.loadOnce`), numbered in import order and listed
  after the built-in kits (menu index BUILT_IN.length + n; Params `kit`/`kitp_*` ranges have room for MAX_USER 99,
  `Kits.refreshChoices()` sets the menus; Android rows and desktop combos swap their adapter when `p.choices`
  changes). Music tab "Your kits": Import kit (a kit.txt or a zip), Import kit folder (kit.txt at the top or one
  folder down; the whole folder is kept, for samples later), How to make a kit, Delete a kit (its users go back to the
  first choice, later kits move up: `Kits.afterDelete`, `ParamStore.kitDeleted`). An import plays at once as the Kit
  and reports what won't play (`Kits.check`: unknown parts, settings, engines). The same text twice is one kit; a
  copy of a built-in kit is the built-in one. **Presets carry user kits**: `toJson` adds `"kits": {paramId: text}`,
  `parse` installs each (or finds an identical one) and points the setting at it; a user kit index without its text
  plays Classic. So a deleted kit comes back when a preset that uses it is loaded. `Kits.send` now remembers each
  part's settings, not the kit index: an edited kit resends only the parts that changed, a renumbering resends
  nothing. **Desktop bench**: each second the app compares `Kits.signature()` (folders, kit.txt times and sizes)
  and on a change refreshes the menus and resends (a saved kit plays at once). Tests: Q (install, reuse, menus,
  edit, delete, preset round trip, check, folder one level down). Since built: samples (stage 2, below), the format page (stage 3,
  `docs/KIT-FORMAT.md`), register Low for kit pads (Oct 19: Low already muted a kit's pad
  with Classic's and the kit's bass part plays the bass voice there, but the kit pad's voices kept running unheard
  and doubled the patch's cost, 0.29 -> 0.58 s per 20 s; now groups `kpad` / `kbass` (switch~) run a kit's pad
  voices only at Mid/Both and its bass voices only at Low/Both, and only with a kit or layer on the part:
  `krun_<part>` -> `k<part>_run`; test_patch "a kit's pad and bass voices run only while heard"), kit drums in
  "Your steps" mode (the old step drums sent no drum_* messages, so a kit with drums played no drums at all
  there: fixed Oct 19, below).
  **Stage 2, samples** (Oct 18). Engine 9 `sample` in dcs_voice~ reads WAV itself (dcs_wav.h: PCM 8/16/24/32,
  float 32/64, extensible; channels averaged; kept at the file's rate, played at file rate / Pd rate x the note's
  ratio, linear between samples): `sample <path> <root>` (up to 16 a part; a note takes the nearest root),
  `dsample <role> <path>` (kick, snare, hat, open hat: a hat over 150 ms takes the open hat), `oneshot` (default 1:
  ring to the end; 0: the note's length, then release), `loop`/`loopstart`/`loopend` (held notes loop, 30 ms
  crossfade; loopend 0 = 50 ms before the end), `clear` (sent with the defaults on every part change), `sgain` (per
  part, Kits.SAMPLE_GAIN 6.7/6/0.26/6, measured so a normalised recording at level 1 is about Classic's loudness).
  Limits: 30 s a file, 120 s (at 48 kHz) a part. kit.txt: `sample piano c4.wav C4` (the file may have spaces; the
  last word is the root if it is a note: C4 = 60, F#3, Bb2, 72; default C4), `kick kick.wav`. Files stay inside the
  kit's folder (no ".." or absolute paths). Android decodes non-WAV at import (Kits.decoder = PackLibrary.decode,
  to `.decoded-<name>.wav` beside it); the desktop plays WAV only. Missing files: the part stays Classic and the
  import report lists them (`Kits.missingFiles`). A preset brings the kit's text only; when the kit is later
  imported with its files, they fill in the text-only copy (same index). Loading happens on the message (the Pd
  thread): a long kit can glitch the sound once as it loads. Tests: test_dcs.py (pitch, nearest root, 44.1 kHz
  file, oneshot, loop, 16/24-bit/float alike, drums, missing file, clear), test_patch.py "a sample kit" (every part
  in key at Classic's level), Q (note names, paths, Classic when missing, the text-only copy filled in).
- **Status (Oct 19):** DadCraft approved by ear every object above (Glitch voice New, Found note ring String, Pitch
  tracker / Pitch shifter / Sudden-sound detector New, the kits, Sing in key, Vocoder, Talk box). All stay behind
  their switches with the old sound the default (he wants them opt-in), except Glitch voice, where New became the
  default and the old voice stays as Legacy. `dcs_derustle~` (Oct 17): see Pocket rustle.
  **dcs_dewind~** (Oct 22, ROADMAP 18; `wind_guard` "Wind Guard" in Input & output, 0-1, default 0, noPreset, noRandom,
  " *"): on the mic's mid and side together (add_stereo_in: before gain, gate and compressor, so gusts can't pump them;
  the side's chain starts from its second outlet). Detector: deep lows (two one-pole LPs at 150 Hz) over highs (two
  HPs at 500 Hz), mean squares over 30 ms, both channels: voices -5..+3 dB, wind +20..+30 (its rumble falls ~12 dB an
  octave). Wind = clamp((d - 6) / 10) when the lows pass -60 dBFS, times "noisy": the deep lows averaged to 1 kHz are
  matched against themselves 5-20 ms back every 10 ms (pitches 50-200 Hz), smoothed 50 ms: a best match over 0.8 is a
  voice's fundamental or a bass line (spared), under 0.5 noise. Cut: a crossfade toward a 4th-order Butterworth
  high-pass at 250 Hz, down to 24 dB of the plain sound left, 20 ms in, 250 ms out. Amount 0: the input copied, sample
  for sample. Found on the way (test_dcs): the first split (the sound minus two one-pole lows) leaked rumble through
  its phase (-6.5 dB at most) and squeezed every sound's ratio toward 0 dB; a ratio threshold alone cut a deep voice
  by 11.7 dB and passed wind under a strong tone: hence the steep high-pass, the separate detector bands, threshold 6
  and the pitch check. Measured: wind alone -16.4 dB, the side -15.5, wind with a 2 kHz tone 18 dB under it -13.9
  (the tone -0.07), a voice -0.01, a deep voice -0.13, a bass line -0.15, a knock -0.7; in the patch (test_patch
  "Wind Guard") wind -15.8, a voice -0.01. Steady traffic rumble is noise too and is cut. Third outlet: how windy,
  0..1, feeds the room listener (below). Cost (desktop, 20 s of quiet noise, best of 3): 0.0124 s alone (an empty
  patch 0.0033): off, the high-pass rests and the wind is judged every 32 samples (the logarithm per sample cost more);
  with one mic the silent side is skipped.
  **dcs_listen~** (Oct 22, ROADMAP 18; DadCraft: "show it first", so it only listens): the room listener. From
  add_stereo_in: the mic's mid as it comes in, and dcs_dewind~'s windiness; twice a second `room_kind` <talking>
  <music> <traffic> <wind> <crowd> (0..1) to the app. Over the last 2 s (10 ms frames): voicing (the sound low-passed at
  900 Hz, averaged to 2 kHz, matched against itself at 50-400 Hz lags every 40 ms: voiced over 0.6), syllable swing
  (the 300 Hz - 3 kHz band's level less its 250 ms average, its spread in dB), gaps (frames 15 dB under the window's
  loudest), pitch steadiness (consecutive voiced frames within a lag of each other), lows (under 150 Hz over the
  voice band), wind (the windiness averaged). Talking = voicing 0.15-0.95, swing 3-6 dB, gaps 4-12%; music = voicing
  over 0.5-0.75, steady 0.4-0.7, few gaps; traffic = little voicing, little swing, heavy lows, not wind; quiet (the
  loudest under -55 dBFS) = none. `RoomListener` (test A) turns judgements into a kind: leading over 0.5 twice in a
  row, let go after 4 under 0.3. Shown on the Now line ("music nearby"), in Diagnostics (the four numbers), and each
  change goes to the event log ("Room listener: music (talking 0.00, music 0.90, ...)"). Tests: test_dcs
  "dcs_listen~" on synthetic places (talk 0.90, music 0.80, traffic 1.00, wind 1.00, quiet none; talk also scores
  music 0.29); by hand, once, a real recording (a game soundtrack from DadCraft's Downloads, not kept): music 0.90.
  **First real place** (Oct 22, DadCraft's son's jiu jitsu dojo, kids and coaches, mats): heard as traffic (0.52-
  0.68 at times). A second outlet now gives the measures (`room_feat`, logged to logcat, tag RoomListener, not the
  event log); the dojo: voicing 0.02-0.16 (many voices cancel each other's pitch), swing 3-5 dB, gaps 0-0.78 (the
  thumps), lows -1..-6 dB, i.e. the voice band over the lows: traffic's lows were judged from -6 dB. Now traffic needs
  lows 0..+8 dB over the voice band, and a fifth kind, **crowd** = little voicing (under 0.25-0.45), the voice band
  over the lows (lows under -1..+4), swing 2-3 dB up (under 8-12), not wind, not talking. Tested on the dojo's real
  rows (test_dcs, 18 of them as numbers through the object's `measures` message): crowd in 18 of 18, traffic 0. A
  synthetic crowd (eight fake talkers) came out too clean (voiced and steady: music 0.93), so it is not a test. The
  synthetic traffic only just passes (0.53): a real road is the next place to check. One "talking 0.82" moment
  there had swing 14-24 dB and voicing to 0.42 (likely someone talking near the phone).
  Cost 0.0106 s per 20 s alone. Next, once his walks show how often it is right: what it drives (Realm Hopper:
  Flux for music and voices, not traffic or wind; auto-key from the room; a beat locked to music nearby). Of the Oct 17 ideas list,
  built: the analog-style ladder filter (dcs_voice~ engine `analog`), a lighter pitch tracker (dcs_pitch~), a dcs
  phaser (the effect ports), oversampled distortion (Oct 19, "Distortion quality", below). The crusher stays as
  it is: its Classic rate reduction aliases on purpose (that is the effect) and the console styles already model
  their filters. More glitch kinds: built Oct 19 (below).
- **More glitch kinds** (Oct 19, DadCraft wanted to hear each before choosing. He kept **Pitch jump** and **Gate**;
  **Ratchet** and **Spray** went into the back pocket: `GC_POCKET` in make_patch.py, no Param, their matrix inlet
  stays 0, the brain's formulas and dcs_glitch~'s shrink/spray stay, so bringing one back is a Param plus taking it
  out of GC_POCKET; `gc_pct_ratchet`, `gc_pct_pitch`,
  `gc_pct_gate`, `gc_pct_spray`, glitch matrix, default 0%, noRandom, in presets): modes 4-7 of the brain
  (`GC_KINDS` in make_patch.py = the matrix by mode). dcs_glitch~'s `slice` takes three optional extras (shrink,
  gate, spray); Legacy reads the first six (`gc_vc_6`), so there a ratchet is a plain stutter and a gate or spray
  a plain replay. **Ratchet**: a stutter from min(2S, 300) ms, each repeat 0.7x the last (down to 12 ms), L =
  3.3x the first. **Pitch jump**: a stutter (S5 = min(1.5S, 250)) read at 2x, 0.5x or 1.5x (40/30/30%: octave up,
  down, fifth up), also on Legacy (its c2 multiplier); a read that runs ahead of the sound is clamped to the live
  input. **Gate**: a forward replay (400-800 ms) on for the first half of each G ms (walking: half a step, at
  least 40; standing 50-110), 2 ms edges. **Spray**: 4 grains of 60 ms at once, a new one every 15 ms, each from
  somewhere in a 150-400 ms window starting at the sound (playing at its own speed), each placed at random around
  the glitch's place (+-1); 30 ms in, 400 ms out, 600-1200 ms. Tests: test_dcs "dcs_glitch~ new kinds" (ratchet
  gaps 100, 70, 49, 34, 24 ms; gate 52% on; octave 883 Hz from 440; spray L/R 0.80), test_patch "more glitch
  kinds" (each picked, played by New and Legacy; pitch 1 kHz -> 500 Hz; spray L/R 0.70).
- **Distortion quality** (Oct 19, `dist_quality`, Distortion, noPreset; **default Smooth** since Oct 19, DadCraft,
  stored values kept, so existing installs stay on Classic until switched; the tests pin Classic for the older
  distortion checks and test_fx_port): Smooth = dcs_dist~
  shapes at 4x between two 65-tap Kaiser (beta 8) windowed-sinc filters cut at the original Nyquist (polyphase up,
  decimated on the first of each 4 fast samples), the dry part delayed the same 16 samples (mixing is comb-free:
  below the clip the output is the input 16 samples late within 1e-5). Fizz (energy off the harmonics) on a 4.5
  kHz tone at drive 20: hard -17 -> -39 dB, soft -20 -> -44 dB, level unchanged; in the whole patch a 3.7 kHz hard
  clip 4.4% -> 0.38%, its 3rd harmonic kept. Wavefold makes most of its sound above hearing, so Smooth is quieter
  there (x0.74 at drive 5 on that high tone, x0.27 at 20): the level lost was fizz. Only the shapes with a weight
  are computed, 4-way unrolled sums: +0.043 s per 20 s of audio on the desktop (Classic +0.010; first draft +0.146).
  Classic still matches the Pd chain sample for sample (test_fx_port). Tests: test_dcs "dcs_dist~ quality",
  test_patch 7i.
