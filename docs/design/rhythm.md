# The Rhythm (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- **Walking beat:** StepDetector sends `step <interval>` messages; foot drift was fixed and confirmed on the phone.
- **Rhythm section** (renamed from "Walking beat", DadCraft Oct 12: it now holds the found rhythm, not just footsteps).
  **Tempo** (`tempo_mode` Auto / Set, `tempo_bpm` "Set tempo" 60-160, default Auto / 100, noPreset): Set = your BPM
  (`tc_Pu`); standing, the beat glides there by `TEMPO_RETURN` (0.25) per beat; walking, each step moves it
  `TEMPO_NUDGE` (0.3) toward a target `TEMPO_WALK_PULL` (0.5) of the way from your BPM to the step rate in the octave
  nearest your BPM, with no phase pull (two tempos would wobble). Measured: set 90, walking 100 steps/min -> 94.7.
  **Tap Tempo** (Oct 22, ROADMAP 22; phone only): a button under Set Tempo (`SettingRows.addTapTempo`). Each tap, on
  the touch down with a haptic tick, goes to `TapTempo` (test U): from the second tap the BPM is 60000 / the mean of
  the last 5 gaps; a 2 s pause starts over, and a gap 40% off the mean starts over from it (a missed or doubled tap);
  folded into 60-160 by octaves, whole BPM. It sets tempo_mode Set and tempo_bpm (both in presets, The Band). Tempo
  only: the beat's phase is not lined up with the taps (the clock keeps its own downbeat).
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
- **Clear Catches** (Oct 22, `fs_clean`, Rhythm, 0-30 dB, 0 = off by default, in presets with The Band). DadCraft's long session:
  found sounds "grainy, crackling", synth drums clean (his A/B). The log: 663 kicks caught in 41 minutes, half under
  -30 dB, many at the -45 dB floor (FS_MIN_DB). Every catch is levelled to the same drum loudness (its peak to 0.5,
  then FS_MAKEUP into the soft limit), so a faint one brings the room's hiss up with it: a -40 dBFS thump over -66
  dBFS hiss came out with 22 dB more energy above 2 kHz (a 150 Hz thump has none of its own) than a loud one. On,
  only catches at least that many dB above the room's background are kept (first a 30 dB switch; DadCraft: "maybe a
  bit too well", a hello at arm's length went uncaught, so a slider); the background is the kit's own floor
  (`fsfloor`: the mic level's quietest lately, down at once, up 0.6 dB a second, readings under -80 dBFS ignored;
  the music's `mfloor` sinks to nothing at the start, before the mic hears anything, and takes minutes to climb
  back, so it let everything through). Measured: -40 over -66 hiss skipped, -30 kept; over -50 hiss -30 skipped,
  -20 kept. Test: test_patch "Clear Catches". DadCraft (Oct 22): at 30 "maybe a bit too well"; with World in Stereo
  on nothing was caught (its background reads ~20 dB low and sounds arrive 15 dB lower, so FS_MIN_DB blocked them:
  ROADMAP Open now); with it off, "working"; left at 10.4 dB. A sung "ahhh" is never a catch: catches are sudden
  sounds (it was heard: the call and response answered it).
  **In stereo, the room's own background** (Oct 22, after that): with World in Stereo on (the patch knows from
  `st_side` > 0) a catch's least loudness, in both the kit's Classic onset detector (`fs_det`, mono's GC_MIN_DB) and
  its keep test (`fs_role`, FS_MIN_DB), is `fsfloor` + FS_ST_ABOVE_DB (15: mono's -45 sits ~15 dB over an office's
  -60), never above mono's -45 nor under FS_ST_MIN_DB (-65 dBFS); `make_patch.fs_min`. `fsfloor` now ignores only
  readings under -90 dBFS (FS_FLOOR_CUT; was -80, where the Moto's stereo background sits). Mono is unchanged; the
  glitch detector (`gc_det`) keeps its fixed -45. Test: test_patch "a catch in stereo" (-52 dBFS thumps over -86
  hiss: mono 0, stereo 3; over -60 hiss: 0). Realm Hopper's quiet/loud levels left as they are: a quiet room is
  judged by its background, which is just what the Camcorder mode hides, and no single offset fits (calibration's
  background differences were -4 and -21 dB in two rooms after the trim); the loud levels are talk, which the trim
  matches. In stereo the Quiet Realm may come on in rooms with a steady hum (but the hum is gone from what you hear too).
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
