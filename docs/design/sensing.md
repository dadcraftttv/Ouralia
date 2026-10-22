# What the phone senses (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

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
- **Pocket hush** (`pocket_hush`, main section, noPreset, default 0.5 = -12 dB; 0.9 hid a finger snap next to the pocket): AudioService sends `pocket 0|1` to the built-in
  patch whenever `PhoneContext.inPocket()` flips, whether or not Auto Worlds is on. The mic is turned down after the compressor (so it
  cannot lift the rustle back up), Room notes hear the hushed mic, and rustle-triggered chimes are scaled by the hush. Footstep
  arpeggio, beat and pad are untouched. Needed because walking with the phone pocketed picks Kinetic, not Pocket, so the Pocket
  world alone never quieted the rustle (DadCraft heard all the cloth noise at the store). Pocket and world changes, plus a
  "Hearing:" line every 30 s (level, floor, swing), go to `files/events.log` (64 KB, one rollover):
  `adb shell run-as io.github.dadcraftttv.ouralia cat files/events.log`. Phone logcat only holds ~10 minutes.
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
