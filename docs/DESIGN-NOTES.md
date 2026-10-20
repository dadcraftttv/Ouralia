# PdScenes / Ouralia — design notes and history

The full record of how each part works and why (moved out of CLAUDE.md on Oct 17 so it is not loaded into every
session). Search it for the feature you are touching (`grep -n "Glitch cloud" docs/DESIGN-NOTES.md`) and keep it up
to date in the same commit when behaviour changes.

## How we work
- You can build and install yourself now: `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  (adb wireless debugging is fine). Read crashes with `adb logcat`. Do this instead of asking DadCraft to copy files.
- Only DadCraft can judge the sound (pad level, chime density, walking feel). Make a change, tell him exactly what to listen for, wait for his report.
- Commit small, descriptive commits. Do not push or publish anything unless asked.

## Layout
- `app/src/main/java/app/pdscenes/` — Java, plain Views (no Compose; do not rewrite).
  Param/Params/ParamStore/PresetManager/Modulation (slider registry, presets, movers), Worlds/WorldSelector (Auto Worlds),
  StepDetector (walking beat), AudioService/AudioDevices/AudioSettings (audio engine and devices), SceneSettings, MainActivity.
- `app/src/main/assets/patches/test_scene.pd` — the built-in effects patch. **Generated.** Edit `tools/make_patch.py`, then run
  `python3 tools/make_patch.py`. Never hand-edit the .pd.
- `app/src/main/assets/scene_lib` — bundled rjlib. `docs/RJDJ-NOTES.md` — notes on the original format.
- `app/src/main/assets/builtin_scenes/` — the four official RjDj example scenes (GPLv3), copied to the scenes folder on first start by
  `SceneLibrary.installBuiltIns`. Their `rj/` folders keep only files that differ from `scene_lib/rj`. Reset everything restores deleted ones.
  `scene_lib/rj/s_fplaytable.pd` carries a fix for current Pd (`[list split]` output order); keep it when refreshing rjlib.
- Player-side helpers for scenes live in `scene_lib/`: `closebang`, `rj_time`, `rj_compass`, `rj_loc`, `rjdj` (from PdParty, BSD), our own `rj_text` and `scene_lib/rj/rj_image`,
  and stand-ins for externals some imported scenes use: `declip`, `avg~`, `aubiopitch~` (sigmund~, MIDI out) and a silent `keys`.
  `SceneEvents` answers their `#pdparty` requests (time); compass is streamed all the time; there is no location permission, so `rj_loc` stays quiet;
  **Scene graphics** (roadmap item 9, Oct 14; `SceneGraphics.java` test G, `tools/test_scene_graphics.py`): `rj_image`/`rj_text`
  send `rj_gfx <$0> <command> ...` (`[pdcontrol]` args for the text's words); AudioService subscribes before opening a
  scene (the load-time `image`/`text` arrive then) and clears on open/stop; ScenePad draws visible items over the cover
  on the 320 grid (images at their file size, one pixel = one grid step, centred; white text with a shadow), checking
  `version()` 30x a second. Six of DadCraft's 55 scenes use them (Atsuke, Moogarina, MoonBootSynthPad, Showtime, Smile,
  TouchVocoder). Left unfixable: Gem objects (Atsuke, desktop only), objects the authors disabled with a leading "-",
  and abstractions missing from the scenes themselves (s-loader, 808_state, tr808-bd~). `pdtest.Engine.lists` keeps
  whole lists heard (floats and symbols).
- `app/src/main/res/values/strings.xml` — escape apostrophes as `\'` (an unescaped one broke the aapt build once).

## Dependencies
- **pd-core is our own build** (Oct 16): `io.github.libpd.android:pd-core:1.4.2-pdscenes.1` from `third_party/maven` (committed;
  `settings.gradle.kts` takes the libpd group only from there, exclusiveContent). `tools/build_pdcore.sh` rebuilds it from
  our public forks at a pinned commit: **dadcraftttv/pd-for-android `pdscenes`** (2eb5545 = upstream master d3ec6c9 +
  PR #198 setAutoReopen, version bumped, libpd submodule -> our fork) and **dadcraftttv/libpd `pdscenes`** (ebc0b83 = libpd
  3fc60ea, Pd 0.55.2, the commit pd-core 1.4.2-SNAPSHOT used, on no upstream branch, + PR #428 the Oboe race fix).
  NDK 29.0.14206865 (pinned in the fork's libs.versions.toml), JDK 21; clones live in `tools/.pdcore` (gitignored).
  Same .so set as the snapshot (only libpdnativeoboe grew ~600 bytes), 64-bit libs 16 KB-aligned. Why: pd-for-android's
  last numbered release is 1.2.1-rc6 (2021), the snapshot can change under us, and the fix isn't merged upstream yet.
  **Fork rules (DadCraft agreed):** keep our changes small, one commit each, offer general ones upstream; new C objects
  only *added* with a `dcs_` prefix (in `dcs/`, not the fork) (Pd prefers a built-in class over a scene's own abstraction of the same name), never
  change a standard object (scenes would change). When moving Pd (0.56 breaks StreetBeatz): rebase both branches,
  bump the version (`1.4.2-pdscenes.N` in the fork's build.gradle files and build_pdcore.sh), rebuild, test the scenes.
  setAutoReopen(false) + DISCONNECT_SETTLE_MS 0 are in (Oct 16). The tests and the desktop app use the same fork
  (Oct 16): `tools/build_libpd.sh` pins dadcraftttv/libpd ebc0b83 (was upstream tag 0.15.0); `desktop/run.sh`
  rebuilds desktop/lib when `lib/libpd-commit` differs from the pin. Moving Pd = move both pins together.

## Our own Pd objects: `dcs/` (Oct 16)
- DadCraft chose the prefix `dcs_` (DadCraft Studios: not tied to the app's name, which isn't final) and BSD 3-Clause
  (Pd-compatible, so useful ones can go upstream or onto deken). A library of its own, NOT in the libpd fork (that
  stays tiny: bug fixes only). Lives in this repo for now; it can move to its own repo when published.
- Loaded by `#X declare -lib dcs;` right after the patch's `#N canvas` line (make_patch.py writes it). A `[declare]`
  object alone does nothing while a file loads. Android: `app/src/main/jniLibs/<abi>/libdcs.so` (committed, built by
  `tools/build_dcs_android.sh` with NDK 29 against pd-core's libpd.so, 16 KB-aligned); Pd finds `lib<name>.so` in the
  native lib folder. Desktop/tests: `make -C dcs` -> `dcs/build/dcs.pd_linux`; pdtest adds dcs/build to the search
  path; the desktop app opens libpdnative.so RTLD_GLOBAL via JNA first (Java loads it privately: the library then
  finds no Pd symbols) and run.sh passes `-Dpdscenes.dcs`. After changing dcs/src: `make -C dcs`,
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
  leaves them. **Layers** (Oct 19, DadCraft: "running both the NES pad and synthwave pad simultaneously and being
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
  `~/.local/share/pdscenes/kits`; `Kits.userDir`, set in `ParamStore.loadOnce`), numbered in import order and listed
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
  default and the old voice stays as Legacy. `dcs_derustle~` (Oct 17): see Pocket rustle. Of the Oct 17 ideas list,
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

## Tests (run these after touching patch or Java logic)
1. Desktop libpd is needed for the Pd tests: `tools/build_libpd.sh` builds it into `tools/.libpd/libpd.so` (needs git, make, gcc).
   It is pinned to our libpd fork's commit ebc0b83 (Pd 0.55.2), the same libpd as the app's pd-core. Pd 0.56 breaks StreetBeatz.rj
   (`hip~` multichannel error); move the pin only together with pd-core. numpy lives in `tools/.venv` (run the tests with `tools/.venv/bin/python`).
2. `python3 tools/make_patch.py && tools/.venv/bin/python tools/test_patch.py tools/.libpd/libpd.so` — must end `ALL PASSED`.
   Also `tools/test_scene_loader.py`, `test_scene_sensors.py`, `test_rj_analysis.py`, `test_bundled_scenes.py`, `test_scene_helpers.py`, `test_dcs.py` (same libpd argument; dcs needs `make -C dcs`).
3. Java logic tests (no Android): `tools/javatests/run.sh` (or one at a time: `tools/javatests/run.sh W`; D = diagnostics, H = handling, I = intensity, F = first run, B = calm build, G = scene graphics). All must pass
   (C and P print no summary line, just no `FAIL:`). Android classes are stand-ins in `tools/javatests/stubs*`: when app code
   starts using a new Android method, add it there. Java is not on PATH: `export JAVA_HOME=~/.jdks/jbr-21.0.11` and put its
   `bin` on PATH (Android Studio's bundled JDK 25 is too new for Gradle 8.14). adb: `~/Android/Sdk/platform-tools/adb -s ZD222T44PP`.
4. Android build: `./gradlew assembleDebug`. Real-device check is DadCraft's.

## Pd gotchas
- In message boxes write commas as space + `\,`.
- A `[value]` that feeds several destinations fires them all on a bang: use one reader object per consumer.
- `[t b b f]` fires right to left.
- No `;` inside comments in Params.java (the test regex stops at `;`).

## Design decisions to keep
- **Output never goes to the phone speaker.** Headphones only (wired first, then Bluetooth). `startEngine` throws `err_no_headphones`
  without them; `outputWatcher` switches or stops when they are unplugged. It first mutes (`computeAudio(false)`) and waits
  `DISCONNECT_SETTLE_MS` (600 ms): libpd's `OboeEngine::onErrorAfterClose` closes and reopens the streams on Oboe's thread with no lock,
  and stopping at the same moment crashed in `OboeEngine::closeStream`. Reported upstream as https://github.com/libpd/libpd/issues/427; drop the delay once a fixed pd-core release is used.
  Upstream fix tested Oct 14 (branch `oboe-fix-test`, pd-core built locally into mavenLocal as 1.4.2-oboefix from
  pd-for-android PR #198 6306ff8 + libpd PR #428 56c7021 cherry-picked onto libpd 3fc60ea = Pd 0.55.2, NDK 29): build A
  (fix + 600 ms delay) and build B (fix, delay 0, `PdAudio.setAutoReopen(false)`) each 10 wired unplugs while playing
  (Music + beat on) and 10 unplugs right after Stop on the Moto G: 40/40 clean, no crash, no sound on the speaker;
  B stopped ~20 ms after the unplug. Bluetooth not tested. **Done Oct 16 with our own pd-core (see Dependencies):**
  `PdAudio.setAutoReopen(false)` before initAudio, DISCONNECT_SETTLE_MS 0 (the mute first stays); the event log says
  "Headphones disconnected" then "Carried on with the headphones still connected" or "Stopped: no headphones left".
  Retested Oct 16 on the Moto G (one app process throughout, event log): wired unplugged while playing 5/5 and right
  after Stop 5/5, **Bluetooth (JLab GO Pods ANC) disconnected while playing 5/5**: each stopped within ~0.5 s, no
  crash, nothing on the speaker. Not yet tried: wired + Bluetooth together (unplugging wired should carry on in BT).
- **Bluetooth mic = call mode** (mono, poor quality). Uses MODE_IN_COMMUNICATION and `setCommunicationDevice` matched by type + address
  (the mic and speaker are separate devices with separate ids; matching by id failed). LE Audio headsets can be better. The UI hints about it.
- **Soft limiter** at the output (`clip~` base plus cubic-eased excess, never above 1.0). Hard clipping sounded fuzzy.
- **Defaults:** input gain 1 (was 2.5 until Oct 17: DadCraft wants a tempered start, the user can turn it up; existing installs keep theirs), compressor -30 dB 4:1. Gain, gate, compressor, smoothing, nudge and music on/key/scale are `noPreset`.
- **Auto Worlds:** Tranquil / Flux / Kinetic / Ghost / Pocket / Unstable, priority Unstable > Ghost > Kinetic > Flux > Tranquil > Pocket.
  **Unstable** (roadmap 5): shaking the phone (`PhoneContext.secondsSinceShake`: 3 jolts past 1.2 g, gravity taken out,
  within 1.5 s; one bump or walking bumps don't count) while not walking brings it *at once* (no dwell or min hold,
  0.3 s fade instead of 3 s) and keeps it until 5 s after the last shake; then the normal 5 s dwell hands back.
  Preset: glitch cloud at full (no stretch), crush, ring mod, pitch +7, echo scatter 1, with Shake = higher movers on
  ring freq, pitch and crush rate. Now line says "shaken". **Off by default** (DadCraft: it adds little; opt-in in the
  world list, `World.onByDefault`). Worlds skip locked params and `*_on`
  switches and never flip `music_on`.
- **Sensors / Pocket world:** `PhoneContext` (pure logic, test X) holds brightness/pocket/climb and the rotation-vector heading; `SensorInput` feeds it. Mover shapes 13-15 are Brighter/In pocket/Climbing.
  Brightness is *room-relative* (DadCraft: "it should respond to the world/room/situation around the user"): 75% where the light sits between
  the darkest/brightest of the last ~90 s (min span 10x lux, steady = 0.5), 25% the fixed sun scale. Pocket detection still uses absolute lux. Pocket is a *layer*, not a rival world (DadCraft's idea): in a pocket the world still follows night/walking/room, the pocket hush
  rides on top, and the Pocket world only plays when nothing else fits. In a pocket, loudness is judged from the room's floor
  (quietest moments, rising <= 3 dB/s, +6 dB for cloth muffling) so rustle bursts don't count as a loud room. Tuning of the Pocket world preset and the climb scale (`CLIMB_FULL_HPA`) is untested on a real phone.
- **Mic input shaping** (roadmap 15, DadCraft Oct 12; section "Gate and compressor shape", folded, noPreset/noRandom/
  `noMover()`): the gate has its own fast meter (`env~ 256 64`: 5 ms window every 1.3 ms; the shared 21 ms meter
  still drives the compressor, triggers and movers) so it opens on time without a lookahead delay (the room is heard
  live: Android already adds 20-40 ms). It opens at the threshold, starts closing only `GATE_HYST_DB` (4) below it,
  stays open `gate_hold` ms, opens over `gate_attack` (default 5) and fades over `gate_release` (80). The compressor's
  gain moves at `comp_attack` going down and `comp_release` going up (both 40 = before); an unchanged target keeps
  its last speed, and changes under ~0.1 dB don't count (the meter ripples). Measured: defaults open 3 ms after a
  sound, rise 3 ms, fade 64 ms; attack 30/hold 150/release 400 -> 23/196/320 ms; a level hovering at the threshold
  doesn't flutter; the fastest release doesn't chop harmony. +6% of the idle patch.
- **Pocket hush** (`pocket_hush`, main section, noPreset, default 0.5 = -12 dB; 0.9 hid a finger snap next to the pocket): AudioService sends `pocket 0|1` to the built-in
  patch whenever `PhoneContext.inPocket()` flips, whether or not Auto Worlds is on. The mic is turned down after the compressor (so it
  cannot lift the rustle back up), Room notes hear the hushed mic, and rustle-triggered chimes are scaled by the hush. Footstep
  arpeggio, beat and pad are untouched. Needed because walking with the phone pocketed picks Kinetic, not Pocket, so the Pocket
  world alone never quieted the rustle (DadCraft heard all the cloth noise at the store). Pocket and world changes, plus a
  "Hearing:" line every 30 s (level, floor, swing), go to `files/events.log` (64 KB, one rollover):
  `adb shell run-as app.pdscenes cat files/events.log`. Phone logcat only holds ~10 minutes.
- **Handling noise** (roadmap item 3, Oct 13; `HandlingDetector.java`, test H; `add_handling`, `handling_gate`):
  taps, grips and rubbing on the phone. Measured on DadCraft's phone (jerk = change of acceleration between readings at
  200 Hz): table ~0, held still 0.1-0.3, walking in hand 0.4-0.9 (once 2.0), rubbing/fiddling 0.6-1.7 steadily, taps
  4-7, grips 2.5-8 (a 15 Hz high-pass could NOT tell walking from taps). A jolt = jerk > 3 (walking or not, one per
  100 ms): SensorInput sends `jolt` straight to Pd (built-in patch only). Handling level = max(jolt fading over 0.3 s,
  fiddling = the jerk's recent peak 0.7..2.0 while NOT walking), sent as `handling` from the mover tick. Pd: `hush_ln`
  = pocket hush x handling duck (`handling_hush`, main, noPreset, default 0.5 = -12 dB at full; 15 ms down, 300 up)
  for everything that hears the hushed mic (mic_comp, found kit, glitch buffer, room notes, harmony tracker). A jolt
  sets `value hjolt` for 200 ms; chimes and found room notes skip a sudden sound while hjolt or handling > 0.3 (no
  wait); glitches check 40 ms later (`HND_CHECK_MS`); a found-sound slice is dropped (`fs_caught 4`, "Skipped a
  handling noise") when a jolt was within 200 ms before its start or during it (`fs_jv`). Diagnostics count skips in
  the last minute; the Now line says "handling". Untested on the phone: whether the jolt reaches Pd before the mic's
  onset (the chimes assume so).
- **Intensity** (roadmap item 4, Oct 13; `Intensity.java`, test I): `intensity` (main section, first row, and a
  the dial in the player sheet since Oct 19, "The look"; noPreset, noRandom, movable; DadCraft: the user's own control, Realms and presets
  never touch it). 0.5 = as set. `Modulation.output` (the one path every value takes to Pd) applies the mover, then
  `Intensity.apply` to the params in its RULES: only what is already there is scaled (0 stays 0), down by `calm` x the
  value at 0, up by `lift` x at 1, capped at max(value, randMax). Wet/reverb/scatter/room notes 0.6/0.6, feedback
  0.5/0.3, glitch amount/glitch loops/reverse echo 1/0.8, colour effects and tremolo 1/0.6, music react 0.6/0.5,
  harmony 0.5/0.3, chimes level and beat level 0.4/0.3. Not scaled: dry, pad, input chain, hushes, key/tempo.
  A stronger top (lifts doubled, up to each slider's top, more glitches above the middle) was tried and reverted:
  DadCraft, Oct 13: "too much... I'm good with it as it was before".
  Moving it (by hand: `ParamStore.set`; by its mover: `Modulation.tick`, on a change > 0.003) resends every scaled
  param. The patch hears `intensity` too: the beat's drive +-0.3 (`INT_DRIVE`), measured standing in a quiet room
  4.6 / 4.7 / 7.6 hits a second at 0 / 0.5 / 1 (a quiet room's drive is already near the bottom).
- **First run** (roadmap item 5, Oct 13; `Walkthrough.java`, `FirstRun.java` test F): DadCraft: "everything should be
  off ... the only thing they should hear on pressing start is the dry audio". Echo level (`wet`) now defaults to 0,
  the last audible default (Realms, music, beat, glitch were already off); a Pd test sends every Params default and
  checks a click comes back once. Existing installs keep their stored values. Six full-screen pages (welcome,
  headphones with a live check, privacy + "Allow microphone" (REQ_MIC, doesn't start the sound), it starts quiet:
  explore one thing at a time, the card and tabs, ready: Start + feedback email dadcraftstudios@gmail.com). Opens
  by itself only on a fresh install (firstInstallTime == lastUpdateTime) and once (`UiSettings` walkthrough_seen;
  Reset everything leaves it); Setup, About has "Show the walkthrough again" and the feedback email (DadCraft: email
  only, no Discord).
- **Mix** (DadCraft, Oct 13: "every musical and rhythmic element should either have a volume slider or on/off
  switch"): section `mix` on the Music tab, in presets (DadCraft: shared with a preset), a Realm ingredient (`Worlds.GROUPS`
  "mix"). It holds the existing beat_snare, beat_hats, beat_glitch, music_pad, music_harm (moved from Rhythm/Music,
  ids unchanged) and new trims 0-1.5 (default 1 = as before, noRandom, NOT resetWhenAbsent so older presets and the
  built-in Realms leave them alone): `mix_kick` (synth kick and found kick; the found one is `fs_ks_kick`, named into
  the fsound group: outside it the trim made a DSP loop between the toplevel and that group), `mix_bass` (synth +
  found bass, `ba_sm`), `mix_chimes` / `mix_walk` (the chimes and walking notes share the 4 voices, so the trims scale
  each note's velocity: chimes at `mu_deg` and motif replays, the arpeggio writes its own trimmed velocity on each
  note from `apvel` (`mu_arp_t` outlet 3; before, an arpeggio note after a chime played at the chime's velocity) and
  the groove note), `mix_ring` / `mix_bells` (`rn_out` / `rnf_out`). At 0 a part is silent but still runs (no extra
  switch~ factors). Turning Auto realms on asks first ("Let Realms change your sound?": they set the sliders from
  their own preset, the mix too; "What it changes" keeps a part).
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
- **Calm builds** (roadmap item 7, Oct 14; `CalmBuild.java`, test B; `calm_build` toggle in Music, off by default,
  noPreset, noPd: DadCraft, "keeping it your own works best"): calm = switch on, music on, not walking, room not
  loud-and-lively. After `WAIT_S` 30 s of calm, the level rises over 180 s, holds 60, falls over 120, then rests 180
  before another; not calm -> it fades within `DROP_S` 5 s and waits for 30 s of calm again. Layers join along the
  rise (pad from 0, room notes from 0.25, standing-still chimes from 0.5, harmony and reverb from 0.75) and each is
  lifted up to +40% at the top in `Modulation.output` after Intensity (only what is on, never past the slider's top;
  resent on a change > 0.005). The chimes layer goes to Pd as `calm_chimes`: an extra still-pattern chance of
  `CALM_CHIME_P` (40 in 1000 per 0.25 s at the top, about one soft chime every ~6 s in a quiet room). Event log
  "Calm build starts/ends"; the Now line says "building".
- **Stems player: REMOVED Oct 18** (DadCraft, asked whether loops should become a kit part first: "Just remove stems". Kits
  replace it; its decoder and zip import live on in `AudioFiles` for kits; old files/packs folders are left on the
  phone, unused). As it was: (roadmap 9 in ROADMAP.md, Oct 14; `StemPack` parse, `StemArrangement`, `PackPlayer`, `PackLibrary`,
  test K; `add_stems`, Pd test "stems:"; `tools/make_test_pack.py` writes a synth test pack): packs live in
  files/packs/<name>/ with pack.txt (title, bpm, key "A minor", bars; "file role: x [bars: n] [section: B]", _A/_B
  file suffixes = sections; max 8 stems). Import zip/folder (Music tab "Stem packs"); non-WAV stems are decoded once
  with MediaExtractor/MediaCodec into `.decoded-<file>.wav`; loop lengths are checked (warnings). Selected pack in
  prefs `pdscenes_packs`, not in presets (DadCraft); built-in sound only. While playing, `Modulation.output` lets
  `PackPlayer` override tempo_mode (2 = locked: the clock sits on tc_Pu, steps change nothing), tempo_bpm, music_key,
  music_scale, and beat_level x 0.3 while the pack's drums play (DadCraft agreed). Pd: STEM_SLOTS 8, each two tables via
  soundfiler (`[list]` after `[route]`: route leaves a path as a selector), restarted by vline~ (N / file rate, so any
  sample rate plays at its speed) on downbeats where bar % bars == 0 (`stem_start` -> next downbeat is bar 0), level
  taken at the downbeat and faded over a bar; stereo into in_l/in_r before the spatial stage, x `stems_level` (Mix,
  0.8). Arrangement: pad always, bass walking/lively/loud, drums walking, lead lively or brisk (pace >= 0.6), texture
  night/pocket/Ghost; 8 s hold per role; sections every 4 loops of the longest stem. Import refuses a pack over
  `StemPack.MAX_STEREO_S` (240 s of stereo, mono counts half, ~92 MB as Pd floats) and gives up decoding a file after
  60 s (`DECODE_LIMIT_S`), from the Oct 14 hardening review. Health check that day (phone): audio thread 31-41% of a
  core with DadCraft's settings, 45-60% with everything on and Intensity full, no underruns while running; battery
  ~0.5% per 30 min for the app in the background (12.6 mAh CPU of a 266 mAh, 6% drop). Untested on the phone (DadCraft
  has no packs yet; the test pack was put on his phone and selected).
- **Call and response** (roadmap 12, Oct 14; `CallResponse.java`, test L; `music_answer` Music 0..1 default 0.5, in
  presets): the Harmony tracker (now also run for it: `fpc_run` includes music_answer) sends `heard_deg <step>` at each
  note-on and `heard_off` at note-off; a phrase = notes with pauses < 1.5 s (max 6); after `ANSWER_AFTER_S` 1.6 s of
  quiet (DadCraft: 0.7 was "answering too soon") it is answered once (cooldown 3 s) through the motif path (`motif
  <step> <vel>`, velocity 0.55 x level): same rhythm, +2 steps (-2 if it reached step 14), 40% the last note on the key
  note's octave. The "Hearing:" harmony count only counts while Harmony > 0. Tests set music_answer 0 unless testing it.
- **Send feedback** (Setup, About; Oct 14): a mailto email to dadcraftstudios@gmail.com, subject with the version,
  body with a prompt, phone/Android/app version, what's playing, and (box ticked by default, explained) the last 200
  event-log lines in the body (no attachment, no FileProvider). 0.9.0-rc2 = versionCode 10, 0.9.0-rc3 (Oct 16) = 11 (posted to testers; built on the snapshot pd-core). For rc4: our own
  pd-core and the unplug fix (b4d0473, 2fd2344).
- **Rustle detector: REMOVED** (Oct 14, DadCraft: "take it out completely... hold on to it in our backpocket"). The full
  version (`add_rustle`, `rustle_guard`, tests, Now line/Diagnostics) is in commit ea57dde. Why: on DadCraft's phone a
  quiet room already reads 12-20k zero crossings/s and highs only 5-7 dB under the whole (mic hiss); sleeve rubbing
  read as a few loud thumps (+33 dB, highs -6..-13, 1 burst), talking and music as 2-3 bursts a few dB over the room:
  no cheap feature separated them. Motion handling (fiddle) and pocket hush/guard are the protection.
- **Pocket rustle: squash and step duck** (Oct 17; kept Oct 19 though DadCraft hasn't needed them: the New
  sudden-sound detector does the job alone; DadCraft: even with the hushes maxed he still got "tons
  of rustle", and turning gain/dry down lost the world, the point of the app; his event log: in a pocket while walking
  -38 dB against a -58 dB room, so a flat hush cuts both alike). `add_pocket_rustle`, both main section, noRandom,
  noPreset, default 0 = as before, phone-only on the desktop. Their gain joins `hush_ln` (everything that hears the
  hushed mic). **Rustle squash** (`pocket_rustle`): `dcs_derustle~ 0 6 80` on `mic_comp1` (after the compressor,
  before the hush: no loop), amount = pocket x slider: a 1/10 ms mean square judged against a floor built on a 50 ms
  average (drops into gaps over 40 ms, climbs 3 dB/s, or 60 dB/s after 1.5 s over the line without a gap: a louder
  room, not rustle; at amount 0 and its first 50 ms the floor is simply the room); more than 6 dB over it is pulled
  back by (excess x amount), at once, let go over 80 ms. Measured: bursts 23 dB over a noise room -22 dB, the room
  between them -1 dB; a steady tone untouched. **Step duck** (`pocket_steps`): each `step <interval>` books a vline~:
  -18 dB x pocket x slider now for `SD_NOW_MS` 120, and from `SD_LEAD_MS` 100 before the next step is due for
  `SD_HOLD_MS` 220 (none past a 1.5 s interval), back up over 80 ms. Lead/hold are guesses: the step message's lag
  after the footfall is unmeasured. Untested in a real pocket. Earbud mic for pockets: NOT made a default (DadCraft:
  non-LE Bluetooth mics are call-mode).
- **Mic-down protection** (`pocket_guard`, main, noPreset, default 0.6; 0 = no change): DadCraft found mic-end-down pockets
  rustly but mic-up pockets fine. `PhoneContext.micDown()` (gravity along +y averaged ~1 s, hysteresis 0.5/0.3) and
  AudioService sends `pocket_down` = pocket && micDown. The patch adds guard*12 dB more cut and crossfades in a 2x `hip~ 300`.
- **Flux needs a lively room:** loud AND the level swinging >3 dB around its average (leave <2). Running water hit Flux and
  became pure fuzz; a steady roar now leaves the user's own settings. Flux preset was softened (dist 0.2 soft, crush 0.15,
  ring 0.15, freeze mover depth 0.4) so the room still comes through.
- **Walking beat:** StepDetector sends `step <interval>` messages; foot drift was fixed and confirmed on the phone.
- **Rhythm section** (renamed from "Walking beat", DadCraft Oct 12: it now holds the found rhythm, not just footsteps).
  **Tempo** (`tempo_mode` Auto / Set, `tempo_bpm` "Set tempo" 60-160, default Auto / 100, noPreset): Set = your BPM
  (`tc_Pu`); standing, the beat glides there by `TEMPO_RETURN` (0.25) per beat; walking, each step moves it
  `TEMPO_NUDGE` (0.3) toward a target `TEMPO_WALK_PULL` (0.5) of the way from your BPM to the step rate in the octave
  nearest your BPM, with no phase pull (two tempos would wobble). Measured: set 90, walking 100 steps/min -> 94.7.
- **Tempo clock** (roadmap 16 part 1, step B; `add_tempo_clock`): a steady beat in Pd that follows the steps like a
  band follows its drummer: each step moves the next beat 35% of the way to it, the beat length 10% of the error plus
  15% toward the step interval; one step = one beat, folded into 80-160 BPM (an amble at 60 steps/min plays at 120).
  A step after >2 s (+a beat) starts a fresh walk on its own beat. It sends `pulse <beat s>` while walking (until
  1.25 step intervals after the last step, so stopping still stops the drums) and `tempo <bpm>` on every beat; it
  keeps ticking at the last tempo. `beat_follow` (Walking beat section, noPreset, default 1 = clock; 0 = "Your steps",
  the old behaviour: steps passed straight on as pulses). Drums, arpeggio notes, chime queue, bass duck, echo sync
  and the glitch step lock listen to `pulse`; the arpeggio's pace and the stop breath still use the real `step`.
  The Now line says "walking 112 BPM". Measured: steps wobbling +-40 ms -> beats within 13 ms of even, a pace change
  followed within ~2 steps, a missed step leaves no hole. Tests run with `beat_follow=0` unless they test the clock.
  **Pd lessons:** a [t] outlet feeding two objects has no defined order (one read a stale time and the tempo ran away);
  an interval of 0 made a 0 ms beat that rescheduled itself forever (guarded). `CLOCK_DEBUG=1` prints P, N, I, now.
- **Pattern engine** (roadmap 16 step C1, `add_rhythm`; DadCraft: the old kick-snare-hat "never changes, grows stale"):
  the clock also sends `tick16 <0-15>` (a metro at a quarter beat, restarted on each beat; no 5th tick when slowing).
  Bars from `KICK_PATS`/`SNARE_PATS`/`HAT_PATS` (calm -> busy; hats > 2 = open) play the drum voices (which now take a
  velocity). On tick 15 the next bar picks per lane a pattern near the drive (or a neighbour), the kick never the same
  twice; hits may drop (8%), extra 8th kicks and ghost snares come with the drive, every 4th bar ends in a snare fill
  when the drive is up. Drive: walking 0.55 + 0.45 x pace; standing 0.1 + 0.5 x the room's liveliness (slow mic
  level vs a creeping floor). `beat_when` (Walking beat, noPreset): 1 = Always (default; standing still at
  0.35-0.75 of the level, DadCraft: "the rhythm should be constant... always making a rhythm out of what it hears"),
  0 = only while walking (the clock's `clock_walking` turns it off, 150 ms fade, ~0.75 s after the last step).
  Calmed after DadCraft's first walk ("slightly too busy/random"): a choice holds `RH_HOLD_BARS` (2) bars, drops 4%,
  extra kicks (1-3%/8th) and ghost snares (~1-2%/16th) rarer, fills every `RH_FILL_EVERY` (8) bars, walking drive
  0.4 + 0.45 x pace. The clock now starts ticking at load (50 ms, then 100 BPM), so standing still from Start has a pulse.
  Only with `beat_follow` 1; "Your steps" keeps the old one-hit-per-step drums. The level moves only on real changes
  (a ramp restarted every 100 ms never arrived). Tests hear `drum_kick/snare/hat` and `rh_bar_patterns`.
  C2/C3 (found sounds from the mic become the kit, synth drums the fallback) were built next: see below.
- **Breakbeat** (Oct 15, from DadCraft's other chat; `beat_break` Rhythm 0..1, default 0, in presets; Pd test
  "breakbeat:"): at 0 nothing changes. Each pick takes `BREAK_KICK_PATS`/`BREAK_SNARE_PATS` (stored after the usual
  pools in `rh_kpat`/`rh_spat`, index + 5) with chance `BB_POOL` (0.8) x the slider. Per bar (decided at tick 15 into
  `rh_*_next`, applied at tick 0 like `rh_brk`; never in a sparse bar): a chop (`BB_CHOP` 0.25 x slider: tick t
  reads the patterns' step `CHOP_MAPS[c][t]`, downbeat kept, all three lanes), a roll (`BB_ROLL` 0.2: 3-4 snares a
  32nd apart from tick 5, 9 or 13; the in-between hits are `rh_hits` outlet 4 through a `[pipe]` of tc_P/8; never
  inside a fill) and a long fill (`BB_FILL` 0.1, or a usual fill made long with chance `BB_FILL_LONGER` 0.4 x slider: `FILL_LONG` from
  tick 8). Chimes, learned groove and glitch loops keep the straight tick; hats and tempo stay with the pace (the
  other chat's open choice: pace stays in charge). `rh_bb_bar` reports roll, chop, long fill. Measured at 1 vs 0
  (150 BPM, 42 bars): 29 vs 18 distinct loud kick+snare bars, kicks off the beat 77% vs 49%. expr `random()` is
  used for these (no new [random] objects, so other tests' random sequences are unchanged). DadCraft, Oct 16: "sound great".
- **Found-sound kit** (roadmap 16 C2/C3, `add_found_kit`; DadCraft: "the sounds around you become the rhythm"): on each
  sudden sound (the glitch cloud's detector, `gc_det_sel`, always running; never while in a pocket) a `FS_SLICE_S` (0.25 s) slice of the hushed
  mic from `FS_PREROLL_MS` (15 ms) earlier is recorded (`tabwrite~ fs_stage` from a 100 ms `fs_buf`) while env~ maxima
  of all/lows (<150 Hz)/highs (>4 kHz) are kept; then filed: below `FS_MIN_DB` (-45 dBFS) dropped, lows within 6 dB of
  the whole -> kick, highs within 6 dB (3 caught no real taps) and lows 15 dB under -> hat, else snare; copied (`array get/set`) into the
  role's next slot `fs_<role><slot>` (`FS_SLOTS` 5 per role since Oct 12, was 3; round robin, memory only) with a gain levelling its peak to 0.5
  (`fs_gains`) but its loudest 10 ms no higher than `FS_RMS_TARGET` (0.3; a snap at the mic overloaded it into a
  clipped, dense slice that played as a "deafening boom"), and the found drums get `FS_MAKEUP` (3x) into a tanh
  limit at `FS_LIMIT` (0.9): a thump kit ~5 dB under the synth kit, a clipped boom at its level, never above
  (DadCraft, Oct 12: "not quite present enough"). `fs_filled` counts. Voices (one per role): a random filled slot of
  its role through `tabread4~` at rate 0.8/1/1.3, lop 3.5k / hip 160 / hip 1.5k, level 1/0.8/0.7, decay
  260/200/hat's own; a role with nothing of its own borrows any caught slot, so one catch makes the whole kit found.
  Each hit varies (DadCraft, Oct 12: "a little too predictable"): pitch +-`FS_VAR_SEMI` (1) semitone, 30% start up
  to 10 ms into the slice (`FS_VAR_START`), loudness 0.9-1.1 (`FS_VAR_VEL`). **Swing** (step 2): odd ticks reach
  the pattern engine `rh_sw` of a 16th late (`[pipe]` before `rh_on`; the clock's own tick16 stays straight, so
  chimes, the learned groove and glitch loops keep to it); every `SWING_EVERY_BARS` (8) a new amount from
  `SWING_CHOICES` (0-0.26) is glided to by `SWING_GLIDE` (0.4) per bar; `rh_swing` reports, `rh_swing_set` (tests)
  sets it. **Arrangement moments** (step 3): after each bar's pick, at least `BREAK_MIN_BARS` (8) apart, chance
  `BREAK_CHANCE` (0.2), never on a fill bar, the next bar may be sparse (`rh_brk` 1 hats only, 2 no kick, 3 downbeat
  kick + hats), applied from tick 0 (`rh_brk_next`: the engine picks the next bar before tick 15's own hits).
  `rh_break` reports. **Flourishes** (step 4): per 1000 found hits `FS_FLOURISH` (25, 25) play backwards
  (read end -> start, the envelope swelling over its length then a 15 ms stop) or at half speed (twice as long, fading
  twice as long); the chances are values `fs_rv`/`fs_hf` (set at load; `fs_flourish_set` sets both, tests). Test lesson: Pd seeds every [random] from a process-wide counter, so adding objects reshuffles
  which ghost snares (0.22, ~1-2%/16th) land where in later tests: count learned snares by their velocity.
  The slot switch is a `[set $1(` message (a `makefilename set ...` symbol was ignored: until Oct 12 every voice
  played fs_00 only, and the snare/hat were silent without a kick). `rhythm_src` ("Beat sounds", default 1 =
  found; 0 synth, 2 both): found mode plays the synth at `FS_PLACEHOLDER` (0.25) until the first catch, then never.
  A mic pop is not kept: louder than `FS_POP_DB` (-6 dBFS) with the lows within 6 dB of the whole (DadCraft's
  "explosive" snap logged at +8 dBFS, lows 0, highs -18; real knocks -16 to -42 dB; snaps at the mic -1 to -14 dB but
  carried by their highs, kept as hats). Each catch's levels go to the event log (`fs_levels`: all, lows, highs), a dropped pop as `fs_caught 3` ("Skipped a mic pop").
  The catcher has its own detector (`fs_det`): in a loud room (average > -35 dBFS) a 6 dB jump counts (else 10,
  like the glitch cloud's, which is unchanged). Its audio is switched (`fsound`,
  on while rhythm_src x beat_level > 0). Reports `fs_caught <role>`: the Now line shows "kit K2 S1 H3", the event
  log each catch with "kit K3 S2 H1 · 24 caught" (the slots fill to 3, the total keeps counting). Pd lessons again: a running max needs its own output fed back; a stored decay never banged was 0.
- **Found melody** (DadCraft, Oct 12: "can we use the found sound for melodies"; `add_found_note`, `melody_src` "Melody
  sounds" in Music, noPreset, 0 synth / 1 found (default) / 2 both): every melodic note (chimes, arpeggio, groove,
  motif) goes through the 4 chime voices; each has a found twin `fm<k>_*`. A note plays a caught slice (snares/hats
  preferred, kicks only if nothing else) at note / `FM_REF_HZ` (600; kicks / `FM_KICK_REF_HZ` 150, so a knock is
  pitched up to the note; rate 0.5-4) as a short strike (5 ms attack, band-passed around the note, `vcf~` Q 1.2,
  so neither its own low pitch nor its harsh top fights the note, `FM_RAW` 2.5) that rings one `vcf~` resonator on
  the note (Q from the chime's fade, 60 dB over cdec; drive `FM_RES` 18 x Q, kicks x `FM_KICK_GAIN` 0.45). DadCraft
  (Oct 12): in key, but "could sound rounder" and "a little loud" -> the octave resonator went, level down. A
  delay-line string was not possible: Pd's feedback needs a block (64 samples), too slow above ~750 Hz. An automatic
  level (`env~` every 5 ms, only down, 0.9 of the way to `FM_LEVEL_DB` 85) keeps a clap and a knock 2-6 dB under the
  synth chimes, then tanh at 0.7 (without it every found note hit the limiter). **Chime character** (`chime_char`,
  Music, in presets, default 0.5; DadCraft: the found sounds should *be* the chimes): toward 1 the hit gets
  x(1 + `FM_CHAR_RAW` 6) louder and its band widens (Q 1.2 -> 0.3), the ring `FM_CHAR_RING` (0.85) quieter and
  `FM_CHAR_SHORT` (0.7) shorter. Measured with a clap: the note's share in the tuned ring 0.67 -> 0.05; at 1 the
  notes are ~4 dB quieter by rms (short hits at the limiter; more lift only squashed them). Found mode plays
  the synth until the first catch. Catching and the recording audio now run when the beat OR the melody needs them
  (`fs_need` = rhythm_src x beat_level or music_on x melody_src). Each voice is its own switch~ group `fmv<k>`, on
  at its note and off cdec + 600 ms later (like the glitch voices); the mix is group `fmelody`. Cost (desktop,
  everything on): the found melody ~8% of the patch, the whole patch ~10% less than with always-on voices. Phone,
  everything on (Music, beat, found kit and melody, before the per-voice switches): audio thread 65-74% of a core. Tests use
  melody_src 0 unless they test it.
- **Learned groove** (roadmap 16 C4, replacing "rhythm memory": with the beat always on it never stopped cold
  anyway): at each catch the spot in the bar is noted (the last `tick16`, +1 past half a 16th: `fs_pos`), and when the
  slice is filed, `rh_heard[role*16+spot]` gains `FS_HEARD_ADD` (0.8, max 1). Every bar all 48 spots keep
  `FS_HEARD_KEEP` (0.55); a spot >= `FS_HEARD_MIN` (0.3) plays that role at 0.9x its value on top of the pattern. So
  claps on one off-beat put snares there from the next bar, echoing ~2 bars after the clapping stops. Tests sync on
  the clock's own ticks (the harness runs a little audio while setting a patch up, so bar 0 isn't at load).
- **Glitch loops** (roadmap 16 C5, `add_glitch_loop`; `beat_glitch` "Glitch loops woven into the beat", Walking beat,
  default 0.4, in presets): on `gc_mode` (a glitch fired) one bar of the glitch voices' own sound (`gc_pre_mono`,
  before the glitch level) is recorded into `gl_stage`, noting its 16th; when done, a take whose peak is >=
  `GL_MIN_PEAK` (0.02) is copied to `gl_loop` (quiet free-clock takes on near-silence used to overwrite good loops),
  and from then on it plays one bar every bar at that 16th inside the beat mix, keeping `GL_KEEP` (0.65) per bar,
  until a new loud take replaces it. Audio switched (`gloop`: beat_glitch x beat_level x gc_amount > 0).
- **Dropdowns keep their value** (fixed Oct 12): rows are created at their stored value and a Spinner selects it
  before its listener is set; a Spinner reports its first entry as selected on first layout, and folded rows aren't
  refreshed, so key and scale kept snapping back to C major.
- **Music (off by default):** key + scale snap the notes; pad is soft glue (default 0.33, DadCraft likes ~0.20); chimes/arpeggio follow the room.
  Pad chords come from `CHORD_POOLS` per scale (DadCraft chose: home chord + its two strongest neighbours, picked by brightness, plus one
  contrast chord ~20% of changes; e.g. C major = C F G + Am; pentatonic/whole tone keep all). No diminished chords. Chord changes
  crossfade between two oscillator sets (no pitch slides, which sounded like an eerie slide into minor). Chosen roots go to `s pad_chord`.
  Listening uses an *adaptive noise floor* so a quiet room is quiet (chime chance ~ eff^3). Walking arpeggio swells with pace.
  **Room notes** (`music_res`, `add_room_notes`): the mic through 6 `vcf~` resonators on the pad chord's root/third/fifth, 1 and 2
  octaves up (octave = scale size, so pentatonic stays in range). Input opens from the 5 ms level meter vs the room floor (20 ms
  lookahead), soft-limited at 0.3. Ring length follows what was heard (`rn_int`: loudness jumps it, duration builds it over 1.5 s,
  relaxes over 8 s): soft tap ~0.4 s chime, clap ~0.9 s, long loud sound ~2.2 s bell (20 dB fades). Longer rings are fed harder so
  notes start as loud. Default 0.8 (DadCraft found 0.4 too quiet). Worlds: Flux 1.0, Tranquil/Ghost 0.6, Kinetic 0.5, Pocket 0.
  Variety (DadCraft found the chimes repetitive): chimes walk a melody (+-1/2 scale steps, never the same note twice, within 4 steps
  of the brightness note), each chime gets a random fade (0.9-2.6 s) and overtone colour, footsteps switch among 5 eight-step
  `ARP_PATTERNS` every bar with ~12% rests, and the pad's chord pick wanders more (jitter 0.9). Notes played go to `s chime_deg`.
  While walking (a step in the last 2.5 s), a sudden-sound chime waits for the next footstep or half-step so it falls
  in with the arpeggio (latest one wins); standing still it plays at once. DadCraft liked it on a walk (Oct 9).
  **Synced to the beat** (DadCraft, Oct 12: "it should be on the same quantization as the drums"): while the walking
  beat runs on its tempo clock (`beat_on`, `beat_level` > 0, `beat_follow` 1; `s msync`), walking or standing,
  sudden-sound chimes wait for the clock's next 8th (tick16 even), the still pattern rolls on the 8ths (its chance
  scaled by 8th/250 ms, so the density holds), and motif notes go to the nearest 16th (`pipe`). Arpeggio and groove
  were already on the pulse. Harmony answers stay live, pad changes free. Beat off or "Your steps": as before.
  **Pad register** (`music_pad_reg` Low/Mid/Both, default Both, noPreset; DadCraft's idea, he likes it: deep without overpowering): a bass voice (sine + 0.35 2nd + 0.15
  3rd harmonic, folded to 65-131 Hz) on the key root, moving to the chord root on 40% of chord changes; note changes dip out
  and back in (no slides); each footstep ducks it 60% while the walking beat is audible. It rides the pad level/swell.
  **Pace tiers:** `ARP_PATTERNS` run calm -> busy; each bar picks one near the pace (`mu_pace_f`) or a neighbour, never
  the same twice; rests 25% strolling -> 5% brisk. **Step strength:** StepDetector compares each bump's peak with the
  walk's average and sends `step_strength` (0.5 usual, one step of lag) before `step`: it opens the hi-hat (louder,
  25-95 ms), lifts kick/snare (0.8-1.2x) and the arpeggio note. **Groove:** after 30 s of steady steps (within 15% of
  the usual interval; >30% off or a pause starts over) the app sends `groove 1`: a soft note (45% of the arpeggio,
  fading in over 4 s) joins on every half-step, a third above the last arpeggio note.
  The arpeggio's pace swell starts at 40% (was 18%; DadCraft found strolling too quiet). The hat uses `step_hit`, sent
  once the step's own bump is over (before the hat plays), so a stomp opens its own hat (amp 0.1+1.2s^2, decay 20-220 ms).
  **Uneven walking** (DadCraft: drums "get tripped up"): a bump sooner than 65% of the usual interval is skipped as extra
  (scuff, heel-toe, pocket knock), unless skipped bumps sat halfway (+-7%) between steps, which means the pace doubled.
  `beat_sure` (0.4..1, -0.3 per step >30% off, +0.15 per even step) softens the drums while the rhythm is uneven.
  **Room notes sound** (`res_src` Ring / Found (default) / Both, Music, noPreset; DadCraft Oct 12: the found sounds
  should be the bells): Found = each sudden sound (`fs_det_sel`, not in a pocket, at most one per `RNF_GAP_MS` 180)
  plays a caught sound on a chord tone from `RES_NOTES` (`mchord` + octaves x `scale_n` + step) through
  `add_found_note(prefix="rf")` (2 voices, groups `rfv<k>`, the Chime character applies), with its own level control
  and soft limit (`RNF_GAIN` 0.08: level with the ring) times the Room notes slider. The ring resonators run only
  unless Found only (`rn_ring` in the roomnotes switch). Catching (`fs_need`) also runs for it.
  **Harmony** (`music_harm`, Music section, default 0.6, in presets; Pocket world 0; 0 = nothing answered): `sigmund~` tracks
  the (hushed) mic; a pitch in C2-G#7 (sigmund~ reads hiss as ~MIDI 123), steady within 0.6 semitone for 12 frames (~130 ms;
  up to 2 misread frames in a row are forgiven, only good frames set the pitch) and 6 dB above the room's quiet (`mthr`) gets
  an answer from the pad's chord (`mchord`; chord notes = root, +2, +4 steps; heard pitch snapped via the `pc_deg` table):
  the nearest chord note at least 2 steps above it plus an octave (below E5) or at least 2 steps below it minus an octave
  (whistles, birds). DadCraft asked for this so it never doubles his note (the first version folded into C4-C5 and blended).
  Soft organ (1, 0.5, 0.3, 0.15 harmonics), held while the sound lasts (150 ms in, 1.8 s out), two voices taking turns.
  Notes go to `s harm_deg`; the "Hearing:" log line counts them. Pd's console goes to logcat tag `Pd`;
  `HARM_DEBUG=1 python3 tools/make_patch.py` prints pitch/loudness 3x a second (do not ship it).
  Test harness: call `e.listen(...)` once with all names (a second call leaks the first binding into later engines).
  **Your voice as the harmony** (DadCraft, Oct 12: close harmony, one voice, default "Your voice"; `harm_src` "Harmony
  sound" Organ / Your voice / Both, Music, noPreset): the same detection, but the answer is a pitch-shifted copy of
  the mic (`hv_*`, a two-grain delay shifter like the Pitch effect) on the close chord note: the organ's choice
  without the octave jump (`mu_hm_vdeg`: a third or more above a voice below `HV_SPLIT` (60, middle C), a third or
  more below from there up and for whistles: DadCraft heard a chipmunk singing high, and most women's and children's
  voices sit there; a downward shift only darkens a little). The interval (heard
  pitch -> chord note, `harm_semi`) is set at note-on and held, so the copy follows vibrato. Grains are an even
  number of the heard note's periods near `HV_WINDOW_MS` (40): fixed-length grains on a steady tone half-cancelled
  at the crossfades (energy at 258/266 Hz instead of 262, a warble); in step they are clean and a third louder.
  `mu_hm_vwr` writes `hv_buf` all the time; the grains are group `hvoice`, on at note-on, off `HV_RELEASE_MS`
  (400; the organ's 1.8 s would shift the room too long) + window + 100 later. `HV_GAIN` 0.7 x Harmony. The organ
  voices are group `horgan` (music_on x music_harm x `harm_organ` = harm_src != 1). Tests use harm_src 0 unless
  they test it.
  **Found pad / bass** (DadCraft, Oct 12; `add_found_pad`; "Pad sounds" `pad_src` Synth / Found (default) / Both,
  "Bass sound" `bass_src` Synth (default) / Found, Music, noPreset): when the Harmony tracker hears a steady pitch
  (its note-on), a `FP_TAKE_S` (0.6 s) take from `FP_PREROLL_MS` (180) earlier is recorded from `hv_buf` (group
  `fprec`) and kept if the note is still held at its end, with its pitch (`fpad_f0`), a peak gain (`fpad_g`), in
  `FP_SLOTS` (2) round-robin tables `fpad_<n>` (`fpad_n`: count, newest); never in a pocket. Each pad set
  (`fpad<k>` groups, 4 voices `fp<k><i>_*`) takes the newest take when it takes its chord; each voice is two
  `FP_GRAIN_MS` (120) grains, a new one every half grain, starting a whole number of the take's periods from where
  the playing grain is (so a steady note stays steady), wandering in `FP_LOOP_S`, rate = chord note / take pitch
  folded within a tritone (`FP_GAIN` 0.075: as loud as the synth pad). Each set's sound (synth or found, `fpw_m`)
  is latched at its chord change (a new take joins at the next one; a fading set is never cut; a Pad sounds change
  re-latches at once); the switches (`fpw_*`: active x register != Low x mode) run only what is heard, so with Pad
  register Low no pad set runs. The found bass (`fb_*`, group `fbass`) is one such voice on the bass note, not
  folded, `FB_GAIN` 2.8; it starts on the current note when switched on; the synth bass gives way. The tracker and
  detection now run while Harmony is up OR a found pad/bass wants takes (`harm_run`; the voice harmony still only
  while Harmony > 0). The event log says "Kept a held note for the found pad (A3)". The found bass has faint grain
  sidebands (~16 dB down) when pulled far down; untested on the phone.
  **Melody rhythm** (Oct 15, from DadCraft's other chat, levels 1+2; `mel_rhythm` Music, Off (as before, default) /
  Straight / Dotted / Mixed, noPreset; `add_melody_rhythm`; Pd test "melody rhythm:"): one-bar cells
  (`MEL_CELLS_STRAIGHT`/`_DOTTED`, note lengths in 16ths, negative = rest; Mixed = both sorted by onsets) on the
  clock's tick16 (always running). Each bar picks one near `rh_drive` (+-1, never the same twice). On an onset:
  `mlen` = the ring (len x 16th x 1 + 200 ms, 250-3000; `mu_tim_dec` uses it instead of the random 0.9-2.6 s, and
  the found voices read the same cdec), then walking (`mwalk`, from mu_q_walk/stop) the arpeggio plays its next
  note (the per-pulse trigger is closed: `mu_mr_arp`), standing the still pattern rolls (its own 250 ms / 8th
  rolls closed: `mu_mr_stf`/`mu_mr_sts`) with `mu_p` inlet 2 = bar / onsets, so the density holds. Sound-triggered
  chimes, motif replays, groove, density memory unchanged. Reports `mel_cell`, `mel_note <tick> <len> <ring>`.
  Found on the way and fixed (DadCraft: "fix the chime density first"): `mu_p_len` (the still pattern's "8th / 250"
  scaling when synced) was written into its cold inlet, so synced chimes came ~17-25% more often at 150 BPM (fewer
  under 120); Pd test "still pattern:" (free vs synced at 150 and 90 BPM within 0.85-1.1). DadCraft, Oct 16: "sound great".
  **Melody phrases** (Oct 15, level 3; `mel_phrase` Music toggle, default off, noPreset, needs Melody rhythm;
  `add_phrases`; Pd test "melody phrases:"): bars count A A' B A (`mph_bar` 0-3, -1 while off, set at tick 0 before
  the cell pick); A' and the last A reuse A's cell (`ph_row`). Before each cell note the phrase stage sets
  `mphdeg`/`mphon` (cleared with mlen): A and B make a note one step from the last (two `PH_STEP2_PCT` 30% of the
  time), within CHIME_SPAN of the register middle (walking: scale_n + mchord + 3, the arpeggio's; standing: the
  brightness note), snapped to the nearest chord tone on ticks 0 and 8; A stores it in `ph_a[tick]`; A' and the
  last A replay ph_a, A''s last onset (`mel_lo`) goes to the chord root nearest A's last note. `mu_pick_ph` and
  `mu_arp_ph` take mphdeg while mphon (sound-triggered chimes and motifs never set it). Standing, only the chimes
  whose chance comes up play, so a phrase is heard in part. Reports `ph_note <phase> <tick> <step> <mchord> <n>`.
  DadCraft, Oct 16: Breakbeat, Melody rhythm and Melody phrases all "sound great".
  **Density memory** (roadmap 8, no slider): `value mdens` = notes played lately (a chime +1, an arpeggio note +0.3
  so the walking bed doesn't silence chimes on the beat), fading over 8 s. Sudden-sound and still-pattern chimes play
  with chance 1/(1+(mdens/10)^2); the arpeggio rests up to 30% more past 16. Busy test room: 1.45 -> 1.0 notes/s.
  `density_on 0` (tests only) turns it off.
  **Motif memory** (roadmap 12, `MotifMemory.java`, test W): the patch reports chimes (not arpeggio notes or replays)
  on `s chime_note`; the app keeps the last phrase of 3-6 chimes less than 2.5 s apart. After 8 s without chimes,
  standing still, at most once per quiet spell and per 30 s, with chance 0.6, it sends `motif <step> 0.45` for each
  note: the phrase moved -2..+2 scale steps (sometimes 0), one note bent +-1 (40%), the rhythm x0.8-1.25. It counts
  for density memory. The event log says "Motif comes back". (Java's Random: small seeds all start near 0.73.)
  **Key drift** (`music_drift`, Music, default on, noPreset; roadmap 12): every 3-6 min while the music plays the
  home chord moves to a related one on the same notes (`value mdrift` scale steps added to each pad pick, mod n), 60%
  of the time back home when away. Allowed homes per scale (`drift_options`): every pool chord moved there must not be
  diminished (C major: C, D, G, A minor feel; minor: 0, 2, 3, 6). No contrast chord while drifted; the bass's home
  note follows. A scale change resets it; switching it off brings home back at once. `drift_now` (tests) drifts now.
  Pattern picker fix: a repeated pattern now moves toward the pace's own pattern (it used to bump upward, so a stroll
  sometimes got a mid-level pattern).
  **Register from sensors** (`music_follow`, Music, default on, noPreset, app-only `noPd`; `NoteRegister.java`, test W):
  room-relative light < 0.25 -> 2 scale steps down + warm, > 0.75 -> 2 up; a pocket -> an octave down + warm; climbing
  (climb > 0.5) +2. A new register must hold 3 s and changes come at most every 8 s. The app sends `music_register
  <steps>` (added to chimes, arpeggio, groove and motif notes on their way to the voices; the reported degrees stay
  unshifted) and `music_warm 0|1` (chime low-pass x0.45). Pad and bass stay put. Compass-picks-the-bass was left out.
  **Stop breath:** 1.2 s after the last step the pad+bass ease to 55% over 2.5 s, then return over 8 s (a step
  brings them back in 0.8 s); only steps start it.
  Music joins after the filter, so chorus/flanger/phaser/tremolo/reverse/glitch/echo/reverb/spatial shape it; pitch/ring/crush/dist/filter do not.
  It no longer follows the Dry level (DadCraft, Oct 13): Dry scales the mic just before the music joins (`filtered_dry`;
  the chain from there to `voice` is linear, so the mic sounds as before) and the echo gets the part Dry took away
  (`echo_mic_rest` = mic x (1 - dry)), so at Dry 0 it still repeats the mic. Only the real room tells us if the tuning is right — expect further tweaks from DadCraft's reports.
- **Tabs** (step D, Oct 12): Sound (presets/shortcuts, XY pad, input, effects), Music (Rhythm, Music), Realms, Scenes,
  Setup (audio devices, appearance, behaviour, reset, about); the tab bar sits at the bottom under the docked player
  (since Oct 19, "The look": icons, the shown tab widens into a pill with its name), each tab keeps its scroll, the last tab is remembered (`UiSettings.tab`). While a scene plays, Sound/Music/
  Realms show a notice. The pad view moves between Sound (XY pad) and Scenes. **Realms** = Auto worlds renamed on
  screen only: internal ids stay "world"; built-in presets are now "X realm" and `Worlds.renamedBuiltIn` migrates a
  stored "X world" choice on load (test W). **Ingredients** (`Worlds.GROUPS`: Rhythm, Music, Echo, Reverb, Glitch +
  reverse, Color, Motion, Space and dry level; `uses`/`setUses`, prefs `skip_<world>_<group>`, cleared by Reset): a
  Realm leaves a group it doesn't use at the user's values; `touched` tracks what a Realm actually set, so going home,
  a stop, or the next Realm leaving a group alone gives back only that (test W). Under each Realm "What it changes:
  everything / all but ..." folds open to the switches. **Polish:** a dot after a slider's label while a mover
  pushes it (`Modulation.isMoving`, checked with the Now line once a second); a Realm cue (`realm_cue 1|0` from
  AudioService on each Realm change; DadCraft asked for "a dreamy warp": a tone gliding A4 -> A5 over 900 ms into a
  Realm, A5 -> A4 back home, Realm to Realm (`realm_cue 2`, DadCraft: "a lateral, sideways warp") a swerve around E5
  (-6%, +6%, back) sweeping from one ear to the other (random way; the others stay centred), with a 1.007 detuned twin and a quiet fifth, 300 ms swell, 1.2 s fade, a touch of echo
  (320 ms, feedback 0.4, wet 0.4; DadCraft), peak ~0.05 after the limiter, group `cue`; `cue_sound` "Realm change sound" in Behavior/Setup, noPreset, default on); content descriptions on
  tabs, sliders, lock boxes and the pad; the step accelerometer tried at 100 Hz and put back to 200 (DadCraft, Oct 13:
  walking was noticed too late).
- **Screen:** a bar pinned to the bottom (thumb reach while walking) holds the status, a "Now: <world> · why" line
  (night/walking/pocket/room words from `Worlds.room*()`, refreshed 1/s while visible), Start/Stop, Hold world
  (`Worlds.setHeld`: the selector keeps listening but nothing changes; a stop lets go) and Save sound (a preset named
  like "Thu 8-12" from the live values). Only Input & output and Music start unfolded. `refreshRows` skips rows in
  folded sections (they catch up when opened).
- **Reset everything button:** `ParamStore.resetAll` + `Worlds.resetSettings` + `AudioSettings.reset` + `SceneSettings.resetAll`, only after confirmation.
  Keeps saved presets, imported scenes and appearance (and brings back any deleted built-in scene). Nothing may ever reset settings except that button.
- **Diagnostics** (roadmap item 2, Oct 13; `add_clip_watch`, `ClipWatch.java`, test D): once a second the patch
  sends `diag_clip_in`, `diag_clip_gain`, `diag_clip_out` (out last) = how many `CLIP_WINDOW` (512-sample, ~12 ms)
  stretches held a sample at/over full scale: the adc~ as it arrives (`CLIP_IN` 0.99), after Input gain (1.0; float,
  the compressor soaks it up, so it is informational) and the output limiter's input (1.0). abs~, `-~ thr`, `*~ 1e9`,
  `clip~ 0 1`, one env~ each. ClipWatch: a warning starts at 3 stretches within 5 s and ends after 5 s without any;
  "episodes" = runs of seconds with any in the last minute. Setup tab "Diagnostics" (folded): the mic really in use
  (`AudioDevices.inputInUse`, from this app's own `getActiveRecordingConfigurations`), call-mode note, output label +
  rate, mic level (after gain) with a bar and the gate, episode counts. The status line says "Mic overloading" while
  the input warning is on (only the mic's own overload: Input gain can't cause real clipping, it is applied in float).
  Event log: "Mic overloading", "Mic overloaded for a moment" (at most every 10 s), "Output limiter working hard".
  Scenes send no counts (the section says so).
- **Calls and other audio** (DadCraft's rule): the app never requests audio focus (Start must not pause the user's music).
  `AudioService.checkForCall` reads the audio mode every 0.5 s: call/ringing/VoIP -> `computeAudio(false)` until it ends
  (MODE_IN_COMMUNICATION is ignored while our own Bluetooth mic set it). An `AudioPlaybackCallback` counts the listed players
  (Android lists only playing ones and hides uid/device from other apps; Moto G: 1 alone, 2 with Musicolet); more than our own -> `duck 0.3` (~3 dB, 600 ms ramp, after the limiter; scenes
  get `#volume` scaled instead). Only slight: the app's effects must still be heard.
- **Start path:** the Bluetooth-mic link wait runs on a background thread (`starting`, `startGeneration` cancels it on
  stop). `patches` and `scene_lib` are copied only when the install changes (`.copied` stamp = lastUpdateTime).
- **Input gain/gate stay one setting for all mics** (DadCraft, Oct 9): per-device values were rejected because Android names
  USB/Bluetooth mics generically ("USB-C HEADSET"), so two headsets could be confused. Earbud mics read ~15-20 dB quieter
  than the phone mic (USB-C earbuds: -65 vs -45 dB) and need more gain and a lower gate. The event log records the devices
  asked for and every listed mic with its rates/channels at each start.
- **No recording audio** to files (DadCraft, Oct 9): the mic never touches disk. Revisit only for a feature that unlocks a lot.
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
- Presets store: sliders, movers, music pad/notes/tone/follow, beat, dry. A slider kept out of presets keeps its mover out too
  (`Params.withMovers`), or loading a preset or world switched it off. Not stored: gain/gate/compressor, smoothing/nudge,
  music on/key/scale, locks, worlds choices, audio device, scenes, appearance.

## The look (Ouralia redesign, from Oct 19)
DadCraft (Oct 19): "very plain"; wants "a solid, finished product": sleek, modern, clean but not bland, light on
battery, Light/Dark/AMOLED kept, and one day an iOS app. Mockups (claude.ai canvas "Ouralia look directions", page
"A + B mashup") chose: A's cards and controls (Material You), B's Intensity ring dial and pill tab bar, **Ouralia
violet** (#8B6CFF, his own pick before he saw it). Built tab by tab; he judges each on the phone.
- **`Look.java`** (plain Java, no Android: tests, desktop and a future iOS port share it): every theme colour from one
  accent (hue + colourfulness); each theme sets the lightness, then `fit()` steps it until the text reads at 4.5:1
  on what it sits on. Test V checks every text pair for every hue (3 degree steps), colourfulness 0-1 and theme.
  Shapes and type sizes live there too (cards 24 dp, tiles 16, pills 24).
- **Fonts** (assets/fonts, OFL, licence under About): Sora (headings, the title), Inter (text), JetBrains Mono (the
  dial's number). Variable fonts: one file each, the weight chosen in `Styler`.
- **`Styler.java`** puts the palette on plain Views: section cards (s1 surface, header row with a chevron),
  Material 3 switches (52x32 track; thumb a LayerDrawable, since an InsetDrawable's padding made the Switch shrink its
  track), tinted sliders, pick-one menus on a rounded tile with a chevron, tonal pill buttons, Reset everything as a
  red outline. `Styler.fonts()` gives every other text Inter and the palette's colours (run on each section when it
  first fills).
- **The bottom** (DadCraft: in thumb reach while one-handed): the docked player (small dial, Start/Stop inside it,
  status on one line with warnings in the error colour, scene + Intensity, Save sound) above the pill tab bar. Tap
  the player: the player sheet slides up (300 ms) with the big dial (`DialView`: drag around the ring for
  Intensity; screen readers get it as a slider in steps of 5), Start/Stop in its middle, the full status, the realm
  line, Hold realm and Save sound. Back, the grab bar or the dimmed screen close it. Replaced the floating bottom
  card and its AMOLED accent glow. Motion only while something slides: nothing animates at rest.
- **Appearance** (Oct 19): Theme Phone/Light/Dark/AMOLED (`UiSettings.THEME_PHONE` = 3 resolves to Light or Dark by
  the phone's night mode; the activity is rebuilt when that changes). Accent (`UiSettings.accent`, default "violet",
  not in presets): `Look.ACCENTS` (violet, blue, teal, green, amber, coral, pink, graphite), "wall" (Android 12+:
  `system_accent1_500`'s hue and colourfulness), "custom" (hue + colourfulness, set in the colour ring sheet whose
  middle previews the Start button in this theme). A change calls `recreate()`; the sound keeps playing. Existing
  installs show violet (before the redesign the accent was the wallpaper's on Android 12+).
- **XY pad** (Oct 19): on a card; `ScenePad.drawXyPad` (only with no scene: a scene's pad keeps its look) draws a
  quarter grid, the point it holds (`Modulation.padPoint`, what the Pad X / Pad Y movers follow) as an accent dot
  with a halo and guide lines, "PAD X" / "PAD Y" in the corners, and an accent ring under a held finger.
- **Realms, Scenes, Setup** (Oct 19): each Realm is a block (name, when it plays, its preset on a tile opening a
  list sheet, "What it changes" folding open to switches); the audio Input/Output pickers are tiles with list
  sheets; the Scenes tab's scene and buttons sit on a card, the scene picker is a list sheet (press and hold to
  delete), RjDj timing is a switch. No Spinner is left in the app. A sheet opens scrolled to its chosen row.
- **Dialogs** (Oct 19): every AlertDialog is built with `LookDialog` (rounded on the sheet colour, the fonts,
  buttons in the accent; text fields via `boxed()`); Presets is a list sheet (tap loads, press and hold one of
  yours to delete); the walkthrough takes the look through `Walkthrough.applyLook`.
- **Key and Scale** (Oct 19; DadCraft found the mockup's 12-key strip easy to mis-tap): two tiles side by side in
  Music, each opening a sheet: Key = the 12 notes as big buttons (4 x 3), Scale = one row per scale with 12 dots
  (filled = the scale's notes above the key; `Params.SCALE_STEPS`, which test V checks against make_patch.py
  SCALES). One tap picks and closes. The sheet is one shared bottom sheet (`openSheet`), also used by the player.
  Closing it: back, a tap on the dimmed screen or the grab bar, or a drag down (`SheetLayout`: from anywhere a
  control isn't using the touch; past a quarter of its height or a flick closes, less springs back). DadCraft found
  the grab bar didn't drag (Oct 19), so this was added.
- **Every other pick-one setting** (Oct 19, `addChoiceRow`): two choices with short names up to 18 letters, or three
  up to 12, are side-by-side buttons (the short name is the words before " (", the chosen one's bracketed
  explanation shows under them: e.g. Pad register, Pad sounds, the Classic/New switches, Distortion type); longer
  lists are a tile opening a list sheet (kits, Crusher style, Melody rhythm, the movers' Movement). **Both in the
  middle** (DadCraft, Oct 20: "left side as the original, Both in the middle as a sort of unity, the other option on
  the right"): a three-way row ending in "Both" shows as 0, 2, 1 (`displayOrder`; Pad register reads Low | Both |
  Mid, his pick). Only the screen order: stored values and presets are unchanged. Long lists
  scroll in the sheet (up to 72% of the screen); a drag down closes it only once the list is at its top. The
  Realms menus and Setup's device pickers are still Spinners (their own code).

## Desktop stream mode (`desktop/`, Oct 16)
- DadCraft wants the app on Linux as background music for his stream (no sensors, Set tempo, Realms by room level).
  Step 1 done: `desktop/run.sh` runs the built-in patch in libpd for Java (`make javalib` in tools/.libpd, via
  `desktop/fetch_libs.sh`, with JNAJack 1.4.0 / JNA 5.14.0 / org.json 20240303 from Maven Central, SHA-1 checked,
  not in git) through PipeWire's JACK layer (`pw-jack`). Mic (+ optional game sound = a sink's monitor) mixed to the
  patch's one input; output to its own null sink "pdscenes" (description "PdScenes", made with pactl, removed on
  exit), which OBS records with Audio Output Capture. The JACK client is "PdScenes Engine" (JACK port names are
  device *descriptions*, so the sink's "PdScenes" would clash). Routes (output, mic, game, --listen) are re-checked
  every 2 s and reconnected; devices match by name or part of name/description. Never hears itself: the sink is
  never an input, --listen refused/skipped while the game sound comes from that sink. Typed lines change settings.
- DadCraft's machine: PreSonus AudioBox Go (its USB link drops now and then; nodes come back renamed ".3"/".4"),
  EasyEffects on the mic (OBS reads Easy Effects Source). Through the patch the EasyEffects source (peaks at 0 dBFS)
  sounded overdriven; the AudioBox directly with the compressor off "sounds amazing" -> `DESKTOP_DEFAULTS`
  comp_thresh 0. Pd 0.55.2's expr prints "realloc called with N bytes" to stdout (filtered in run.sh).
- **Window** (Oct 16; `DesktopApp` tick + Pd receiver, `DesktopWindow` Swing, `DesktopEngine` routes): compiles the
  phone's 15 logic classes unchanged (`run.sh` SHARED list) with `desktop/shim/` stand-ins: android.content.Context
  (files dir ~/.local/share/pdscenes, prefs ~/.config/pdscenes/<store>.json via JsonPrefs) and AudioService
  (isRunning). Tabs Sound/Music/Realms/Presets/Setup built from Params (sections as on the phone; mover params,
  section mover switches and `PHONE_ONLY` hidden), rows refreshed 4x a second from ParamStore. The tick is the
  phone's mover tick minus sensors: `PdBase.pollPdMessageQueue()` first (desktop libpd queues messages; without it
  mic_level/tempo never arrived), morph, Modulation, realm_cue, Worlds.tick(walking/pocket false, no shake),
  CalmBuild + calm_chimes, call and response, motif. `DESKTOP_DEFAULTS` (first start and Reset): comp_thresh 0,
  tempo_mode Set. First start connects no mic (DadCraft's laptop mic reads 0 dBFS nonstop) and opens Setup. Device
  menus / `mic_device`, `game_device`, `listen` commands call `engine.apply`. A loop the engine can't see: OBS
  monitoring PdScenes onto the output the game sound is taken from (UI and README say: give the game its own
  output). `desktop/test.sh` (DesktopTest): phone preset in (unknown keys ignored, clamped, noPreset not taken,
  sensor mover kept), out in the same format, settings file written. `--snapshot prefix` draws each tab to a png.
- Responsiveness (DadCraft: sliders and dB readings "sluggish"): FlatLaf 3.4.1 instead of the GTK look (slow to
  draw), screen changes sent to ParamStore from a "settings" thread (latest value per id), one ParamStore.snapshot
  per screen refresh, meters 20x a second (falling 30 dB/s), 256-sample cycles (`PIPEWIRE_LATENCY=256/<graph
  rate>`; `pw-jack -p` forced 48 kHz while Pd ran at 44.1 = 9% sharp; the engine reopens Pd on a rate change).
  Pd took ~0.6 ms of a 23 ms cycle, so its lock was not the cause.
- Echo scatter in a quiet room: steady mic hiss in the echo hops ear to ear each repeat (DadCraft heard it on the
  desktop; outdoors on the phone it was masked). Not a bug (only with the echo on). Fix: raise the noise gate (~-55 dB): DadCraft, "raising the noise gate works fine" (README tip).
- Realms tab: "What it changes" per Realm (Worlds.uses/setUses, as the phone), phone-only Realms greyed. Cleanup
  round 1: window size/place, last tab and open sections kept (pdscenes_desktop), wider label column, the glitch
  budget line, "Mic in"/"Out" meters. DadCraft (Oct 16): stem packs and scenes are not part of the desktop app; it is
  for showing the engine on stream.
- App menu launcher (Oct 16): `desktop/install-launcher.sh` writes ~/.local/share/applications/pdscenes-desktop.desktop
  (no app icon yet: the theme's audio-x-generic), `desktop/launch.sh` = flock on ~/.local/state/pdscenes/running.lock
  (a second start: notify-send and exit), log to desktop.log (one rotation), zenity on an error exit. Installed on
  DadCraft's machine. The desktop stays in this repo (it compiles the phone's sources in place; split later with a
  shared library if it gets its own releases). Movers: not wanted for now (DadCraft).
- Locks (Oct 16, DadCraft: "the Mix elements need locks so those elements can stay where they should be even when a
  realm takes over"): `Params.lockable` = every slider the dice or a Realm can move (randomizable or inPreset, not
  switches/menus/movers); the phone showed locks only on randomizable sliders, so the Mix trims had none; the
  desktop had none at all. Both apps use lockable now. DesktopTest checks a locked Mix level through Randomize and
  through a Realm whose preset sets it.
- Game sound from an app (Oct 16; DadCraft agreed to "give the game its own output", built instead as app capture:
  no pavucontrol step, no loopback delay): `Setup.gameOutput` "app:<name>" = a route of kind "apps" linking that
  JACK client's `output_*` ports (any playing stream shows as "<name>:output_FL") to our game ports, reconnected
  when the game restarts. `DesktopEngine.apps()` leaves out PdScenes, anything with "obs" (its monitoring carries
  our music), EasyEffects' ee_ filters, speech-dispatcher, MIDI. A whole output's monitor can still be picked
  (loop guard as before). Tested with a pw-cat tone into a throwaway sink: listed, linked, lost on quit, back.
  DadCraft's Minecraft (Prism Launcher Flatpak) plays as a stream named just "java": the menu labels apps from
  `pactl --format=json list sink-inputs` (portal app id -> "java (PrismLauncher)"; pw-dump's JSON repeats keys and
  org.json refuses it). Picking an entry ticks "Also hear a game" (he picked it, the box stayed off, nothing came).
  Device names match without PipeWire's ".N" suffix (`DesktopEngine.base`), and menus store the plain name.
- No walking stand-in on the desktop (DadCraft, Oct 19: the Rhythm's BPM setting is enough there).

## Status log (moved out of CLAUDE.md on Oct 19)
CLAUDE.md keeps only what is open now; its old "Current state" list, as it stood on Oct 19:
- **Approved by ear (DadCraft, Oct 19)**, all still behind their switches with the old sound the default until he says
  to change the defaults (Oct 19: he wants them to stay opt-in, except Glitch voice: New is the default now, the old
  one kept as Legacy): the kits (Acoustic, Synth, EDM, Future bass, Low end, 3 Chiptune), Glitch voice New, Found
  note ring String, Pitch tracker / Pitch shifter / Sudden-sound detector New, Sing in key, Vocoder, Talk box. Rustle
  squash + Step duck: kept though he hasn't needed them (the New detector does the job alone). Crusher style (Oct
  18: "don't sound like the consoles", kept; Chip voice is the answer). Settings that use our C objects show " *"
  after their name. Each kit's sound is one line per part in `app/src/main/assets/kits/<name>.txt`.
- **Desktop: "<Part> from" reported not working** (Oct 19, DadCraft away from his mic, untested by ear): headless with
  the desktop's own classes the patch takes Melody/Bass from correctly (values kemel/kebass). Not yet known what he
  chose. Acoustic and Synth have no drums line, so "Drums from" either of them plays Classic's drums.
- **Effect ports done** (DadCraft, Oct 17: dcs versions of every effect, for CPU): all 13 match the old Pd chains
  (`tools/test_fx_port.py`, DESIGN-NOTES "Effect ports"); at rest -10%, DadCraft-like settings -14%, phaser -63%.
- Sing in key (dcs_shift~ snap), Vocoder (dcs_vocoder~) and Talk box (LPC) are off by default; approved Oct 19.
  Chip carriers for the vocoder built Oct 19 (`voc_wave` "Vocoder synth sound", default Saws): approved by ear later on Oct 19.
  **Distortion quality** Smooth (`dist_quality`, 4x oversampled dcs_dist~) is the default since Oct 19 (his call).
  **Glitch kinds** Pitch jump and Gate kept (Oct 19, glitch matrix, each from 0%); Ratchet and Spray in the back
  pocket (`GC_POCKET` in make_patch.py: no setting, code kept).
- **User kits stage 2 (samples)** built Oct 18: engine `sample` (WAV read by dcs_voice~, other formats decoded at
  import), kit.txt `sample <file> <note>` / `kick <file>`...: waiting on DadCraft's phone check. Stage 3 done: `docs/KIT-FORMAT.md` (the format page
  for sound designers: keep it in step with Kits.java and dcs_voice~).
- **User kits** stage 1 built (text kit.txt: import, Kit menus, delete, presets carry the kit text, desktop bench
  reloads on save; Music tab "Your kits"). Waiting on DadCraft's phone check.
- **Kit layers** built Oct 19 (DadCraft: blend two kits on one part; "<Part> layer" + "<Part> blend", Music tab):
  waiting on his phone check.
- **Kit plan done** (ROADMAP 19): stages 1-3 built, the stems player removed Oct 18 (DadCraft). Register Low for kit pads and kit drums in "Your
  steps" done Oct 19.
- **Release:** rc3 is with testers; rc4 not built yet (our pd-core, the unplug fix, input gain 1, and whatever of
  the above DadCraft approves). Rename to Ouralia (no USPTO/EUIPO hits) waits for release prep.
- Check `git status` / `git log origin/main..HEAD` for unpushed commits: push only when DadCraft asks.
- **The name** (Oct 17): **Ouralia**, chosen by DadCraft; no USPTO or EUIPO results for it (he checked), though Oura
  the ring maker has fought similar marks. The final name and applicationId must not contain "RjDj". Renaming gives a
  new applicationId (e.g. com.dadcraftstudios.ouralia), which testers get as a new app (they export presets first).

## Open / next
- **Ideas queue with details: `docs/ROADMAP.md`.** Its numbered items are built, removed or set aside by DadCraft
  (Oct 19); still open there: the small items in 11 (location input, unported scene objects, `rj_zcr~` if a scene
  needs it, a beat-offset slider if drift returns).
- The Oct 17 list of our own objects is done (Oct 19: Pitch jump and Gate kept, Ratchet and Spray pocketed, Smooth
  distortion the default).
- Untested on the phone: wired + Bluetooth headphones together, the Pocket Realm / climb tuning, handling's jolt
  timing, the found bass pulled far down.
- Release prep: final name + applicationId, `source_url` in strings.xml, privacy policy, Play foreground-service + microphone declarations, icon, store listing.
- Ideas, not requested: beat-offset slider if drift returns,
  location (`#loc`) scene input, unported scene objects.
- The Beta Tester Guide lives in this repo: `docs/TESTER-GUIDE.md` (tester-facing: "Dad"/"DadCraft", plain words; moved
  from DadCraft's Claude Docs on Oct 12, where an older Oct 10 copy remains). When behavior changes, update it in the
  same commit or tell DadCraft it needs an update.
