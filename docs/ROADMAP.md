# Roadmap: ideas to work into the app

Gathered from research (Designing Sound "RjDj: crafting Dimensions", CDM's Dimensions write-up, the 99% Invisible
episode). The sources give ideas, not thresholds or timings; tune everything by ear on the phone. DadCraft decides the order.
Suggested order is the numbering. Each one needs a test in `tools/test_patch.py` (or a new test file) and a tester-guide update.

**Agreed order (DadCraft, 2026-10-09):** (1) tester guide catch-up (done Oct 9), (2) glitch engine part 1 (item 5b),
(3) Ghost capture/stretch (3), (4) Unstable world (5), (5) Collective scratch (6), (6) music: density memory, motif
memory, key drift, register from sensors (8, 12), (7) screen polish + accel 100 Hz trial (14), (8) release prep.
The glitch engine must stay cheap: off at Amount 0 (switch~), measured on the phone.
Steps 1-6 done by Oct 10 (Unstable and the scratch were built and then set aside by DadCraft), plus the glitch matrix.

**Next order (DadCraft, 2026-10-12):** (A) mic input shaping (item 15), (B) tempo clock (item 16, part 1), (C) found-sound
rhythm section (item 16, parts 2-6), (D) tabs and Realms, replacing the old step-7 screen polish (item 17), (E) release prep.
Plan first, agreed with DadCraft, then build; test on the phone after every step. A-D built Oct 12-19; E is open (below).

**Keeping track (DadCraft, Oct 22: the outside reviews kept suggesting things already built).** "Open now" below is
the one list of what is not done, checked against the code. In the same commit that builds, removes or sets aside
something: take it off "Open now", and mark its item (BUILT / REMOVED / SET ASIDE, the date, where it lives). A new
idea goes into its item and into "Open now". Every item below keeps its history; "Open now" is the only to-do list.

## Open now (checked against the code, 2026-10-22)

Order (DadCraft, Oct 22): the reviews' release and code-health points first, MainActivity's split chief among them.

**Before advertising rc5 (item 22):** fresh README screenshots (all three still say "PdScenes"; after MainActivity's
split), GitHub repo topics, maybe a short demo GIF. (`PRIVACY.md` and the About screen's source link DONE Oct 22.)

**Code health (item 22):** (MainActivity's split DONE Oct 22: 3,753 -> 1,237 lines,
DESIGN-NOTES "MainActivity's split"; maybe later: split DESIGN-NOTES and make_patch.py the same way); (DONE Oct 22: motif and
answer notes are dropped after a restart, not only after Stop; `tools/test_release.py` guards the permissions, backup
and the name, in both test tiers); CI running the quick tests on push,
CONTRIBUTING.md and an issue template; a long session on the phone (30-60 min: dropouts, CPU, battery, heat) and a
cost per feature, so new sounds land with a known CPU price.

**Release to Play (release prep, item 14 Release):** a release keystore (rcs use the debug key) and Play App Signing,
R8 on with keep rules for the `PdAudio.setDevicesId` / `audioRuntimeInfo` reflection, a real notification icon
(today `android.R.drawable.ic_btn_speak_now`), Play foreground-service + microphone declarations, Data safety form,
store listing, a release checklist with each APK's SHA-256.

**Phone checks waiting on DadCraft:** call pause and ducking (14), the pocket shaping (15), the found-sound rhythm
walk (16), wired + Bluetooth headphones together, the Pocket Realm / climb tuning, handling's jolt timing, the found
bass pulled far down.

**Sound and music ideas (each behind a switch, the old sound the default):** dcs_listen~ (what kind of room), then
auto-key from the room (22), dcs_dewind~, dcs_grain~ (18); a shimmer mode for the reverb (18); more drum hits, a
moments part, kits for Harmony and Room notes, a second melody (20); glitch phase 2's per-slice filter, lanes and a
chance per kind (5b); user kits with his own samples (MP3/OGG/FLAC import), kit layers, kit drums in "Your steps".

**App ideas (item 22):** sleep fade, tap tempo, a home-screen widget, MIDI in (much later); a simple walk-mode
screen (14 UI); the Bluetooth hint on the input picker itself (Diagnostics already shows call mode).

**Scenes:** location (`#loc`) input, unported scene objects, `rj_zcr~` if a scene needs it (11); the `index.rdf`
fallback for older packs; check whether rjlib's state saving (`sssad`, `RJ_SCENE_SAVE`) persists in the app
(Compatibility, below). Also a beat-offset slider if drift returns (11), and his desktop "<Part> from" report.

1. **(BUILT 2026-10-08 as "Room notes", `music_res`; needs tuning by ear) The room plays the notes (Dimensions "Flux").** Run the mic through resonators/filterbank tuned to the chosen
   key and scale; filter frequencies move with pitch changes and onsets, so sounds around you ring notes in key.
   Likely a new voice in the Music section (level slider + on/off) or part of the Flux world. Joins the output after
   the filter like the other music voices, so echo/reverb/spatial shape it.
2. **(BUILT 2026-10-09 as the Harmony slider) Pitch-following harmony (Dimensions "Travelling Dream", RjDj "Eargasm").** Track the pitch of what the mic hears
   (rjlib `analysepitch` / the fiddle external, already in `scene_lib` and the native libs) and answer with in-key notes or
   harmony. Needs a confidence gate so noise does not produce random notes.
3. **(BUILT 2026-10-09 as the Stretch glitch kind, on in the Ghost world; reverse reverb BUILT 2026-10-13, Reverb section) Ghost: capture and stretch.** On a detected onset, record the moment and replay it granularly stretched in time,
   with reverse reverb; optionally a very short randomized mic delay for atonal pitched noise. Extends the Ghost world
   (which today only has reverse echo).
4. **(BUILT 2026-10-09 with step strength and the cadence groove) Kinetic pattern tiers.** Dimensions switches among five patterns by accelerometer level, with random variation. Ours
   now has five 8-step patterns picked at random each bar, with rests (2026-10-08); pace tiers BUILT (DESIGN-NOTES "Pace tiers"). Stopping must still drop the drums immediately.
5. **(BUILT 2026-10-09: shake the phone, at once, 5 s hold; off by default, DadCraft found it adds little) Unstable world.** Jolt-triggered stack of many ordinary effects on the mic at once (glitch, pitch shift, ring mod,
   crush, delay), glitchy by design. We have all the effects; it needs a trigger and a preset.
5b. **(PHASE 1 BUILT 2026-10-09: buffer, stutter/reverse/tape stop/shuffle, sound trigger + free clock + step lock, Amount. Phase 2: stretch, Gate and Unstable BUILT, tape stop REMOVED Oct 10 (DadCraft), Ratchet and Spray in the back pocket Oct 19; open: per-slice filter, lanes, chance per kind) DadCraft's idea (Oct 9): "glitch cloud" — Ableton warp + the Glitch VST (v1.3).** Random bits of what the mic hears
   are grabbed into a RAM-only ring buffer (never to disk) and played back around the listener: extreme time-stretch
   (granular, like Ableton's warp pushed very far), tape stop (playback rate falling to 0), stutter/retrigger (a
   slice repeated at step-synced divisions, getting shorter), reverse slices, pitch drops, scattered across the
   stereo field so "the stuttering glitched sound echoes all around". Could tie to steps (stutters on the beat) and to
   jolts (Unstable world, item 5) and share code with Ghost capture (3) and the Collective scratch (6). Today's
   `glitch_hold`/`glitch_len` freeze is the only piece that exists.
   Researched Oct 9: dblue Glitch 1.x is a tempo-synced slicer: it cuts the incoming audio into slices and a
   64-step sequencer (or its random mode) applies one of nine effects per slice: retrigger, shuffler, reverser,
   tape stop, stretcher, crusher, gater, delay, modulator (each with its own filter). Glitch2 (illformed, now free)
   adds pitch bend, distortion, ring mod, beat repeat, trance gate, a randomizer and scenes. Plan sketch: footsteps
   are our "host tempo" (standing still: onsets or a slow clock); a RAM ring buffer of ~2-4 s; one reader that can
   jump (shuffle), loop short (retrigger, shrinking), run backwards (reverse), slow to 0 (tape stop) or granulate
   (stretch); per slice a random pick weighted by an Amount slider, effects switchable; crusher, delay, ring mod and
   filters already exist and can join per slice; gater = step-synced chopping; Worlds act like Glitch2 scenes.
   Phase 1: buffer + retrigger/reverse/tape stop/shuffle. Phase 2: stretch, gater, per-slice filter, Unstable world.
   Glitch2 extras worth taking: lanes (several effects on one slice, in series), a Randomizer with a chance per effect,
   Mutate (small variations of the current settings: our nudge), per-effect pan (the "all around you" spread), gater
   volume steps, one-shot vs looping. Not useful on a phone: MIDI scene triggers, a drawn pattern editor.
   Clock without walking (DadCraft asked): a free clock (own tempo, or slow and loose), sudden sounds as triggers
   (the room plays the sequencer), random timing by density, phone gestures (twist = tape stop, shake = stutter), and
   later maybe the tempo of music heard nearby. Walking locks it to steps; stopping hands over to the other clocks,
   as the chime queue already does.
   DadCraft's pick (Oct 9): sudden sounds as the trigger first, then the free clock (walking still locks to steps).
6. **(Built 2026-10-09 as a Scratch glitch kind, then removed with tape stop on Oct 10: DadCraft didn't want them) Collective world.** Capture a buffer and "scratch" it back and forth like a turntablist. A new scratch effect.
7. **(BUILT 2026-10-14: the "Calm builds" switch in Music, off by default; CalmBuild.java, DESIGN-NOTES "Calm builds")
   Optional: "calm builds" mode.** Dimensions' Tranquil adds layers the longer you stay quiet and lets noise make the music
   shrink away. Opposite of the current behavior (quiet room = quiet notes), so only as an option, if DadCraft wants it.
8. **(BUILT 2026-10-09: chimes thin out when lots was played in the last ~8 s) Density memory.** Generative rule sets that look at how dense the recent music has been and adapt (avoids too many
   notes in a row).
9. **(BUILT 2026-10-14, REMOVED 2026-10-18 for kits) Stems player (hybrid approach).** Dimensions used stems made in a DAW, layered and faded by sensor data; all-Pd
   synthesis was too heavy. DadCraft makes synthwave stems, so the app could play his loops. Plan: ship compressed audio,
   decode once with MediaExtractor/MediaCodec into WAV in the app's cache, stream with `readsf~` (from disk, not RAM).
   Needs a size budget for the APK/AAB and a layering/fade rule set.
10. **Skipped: weather and GPS contexts** ("Sunny" world). The app has no internet permission by design.
11. **Other small items:** location (`#loc`) scene input (no location permission, so rj_loc stays quiet), unported
    scene objects, (BUILT: Setup's Diagnostics, the mic and headphones in use, mic level and overloads) on-screen
    route/clipping diagnostics, beat-offset slider if drift returns, (BUILT 2026-10-13) music independent of the Dry
    level, `rj_zcr~` port if a scene needs it,
    (DONE 2026-10-19: none has it; the problem was `set` reaching `tabplay~` after the play message, and
    s_fplaytable is the only `tabplay~` in scene_lib and the bundled scenes; s_playtable's `set` goes to tabread4~, which
    doesn't stop; the rest use `[list split]` as list tools) check other rjlib abstractions for the same
    `[list split]` output-order problem fixed in `s_fplaytable`.

12. **Music ideas from DadCraft's Oct 8 test notes** (after the bass pad, built 2026-10-08 as "Pad register"):
    - (BUILT 2026-10-09) Lock chimes to the walking pulse: while walking, onset chimes wait for the next step or half-step.
    - (BUILT 2026-10-10) Motif memory: remember a short phrase of recent chime notes and bring it back later with variation.
    - (BUILT 2026-10-10, Key drift switch) Slow key-centre drift every few minutes (keeps the chosen key's notes, moves the home chord).
    - (BUILT 2026-10-10 without the compass part) Register from sensors: brighter = higher octave, dark/pocket = lower and warmer; climbing raises notes;
      compass heading picks the bass root.
    - Call-and-response: answer a clearly pitched sound a beat later with a harmony note (= item 2).
13. **Pocket rustle, other options** (mic-down protection was built 2026-10-08): footstep-synchronised ducking (the step is
    detected after the strike, and walking rustle is continuous, so likely weak alone) and a rustle detector (spectral
    flatness / zero crossings) that tightens a high-pass only while the sound looks like cloth.

14. **Outside reviews (Gemini and Grok, 2026-10-09), checked against the code.** Accepted then; built as marked:
    - *Hardening pass* (BUILT 2026-10-09, needs a phone check of call pause and ducking): audio focus (other audio -> duck the app only slightly so its
      effects still come through; a phone call -> always pause/stop), event log on a background thread with one date
      format, `PhoneContext.inPocket()` under the lock, no per-event `float[3]` in `SensorInput.toScene`, no per-tick
      HashMap in `Modulation.tick`, Bluetooth-mic wait (up to ~3.3 s of `Thread.sleep`) off the main thread, copy
      `patches`/`scene_lib` assets only when a version stamp changes (393 files on every Start today), a step-detector
      test for shaking the phone while sitting.
    - *CPU* (BUILT 2026-10-09: switch~ groups; accel 100 Hz still to try): measured on the phone (built-in patch, music on): ~50% of one core, the audio thread ~44% (desktop 2.4%).
      Effects at 0 or off cost the same as on. Wrap effects and music voices in `switch~` subpatches (reverb and pitch
      shifter first) via a helper in make_patch.py, with tests that an off effect is unchanged and toggling doesn't click.
      Try the accelerometer at 100 Hz instead of 200 Hz if the beat still lands.
    - *UI* (BUILT 2026-10-09: why-line, pinned bar, folded sections, hold, snapshot): a "why this world" status line (`Worlds.hearing()`), Start/Stop and status pinned (sticky), effect sections
      collapsed by default, a mover indicator on moving sliders, a "hold this world" control, a one-tap snapshot preset
      (time-stamped, name later), an optional quiet earcon on world change, SeekBar content descriptions, the Bluetooth
      hint on the input picker, a short first-run flow, later a simple "walk mode" screen.
    - *Sound* (BUILT 2026-10-09: step-synced echo, stop breath, dice morph): echo time synced to steps, dice morph (Randomize glides over 5-10 s), shake-to-scatter while still
      (overlaps item 5), cadence groove reward, an Intensity macro, step strength (harder step opens the hat), a
      2-3 s thin-out of pad and chimes when you stop, a wind/handling detector that ducks only the reactive voices
      (overlaps item 13).
    - *UI leftovers*: the mover dot and the Warp Sound earcon BUILT; content descriptions on most controls; open: walk mode,
      the Bluetooth hint on the input picker.
    - *Release* (OPEN, see "Open now"): R8 keep rules for the `PdAudio.setDevicesId` and `audioRuntimeInfo` reflection when minify is on,
      a real notification icon, Data safety form (mic used on-device, not collected), mention `events.log` in the privacy policy.
    - Rejected: pocket hush before the compressor (the compressor would undo it), head tracking from the phone's
      rotation (the phone isn't on the head), GPS/weather. Already done: low cut in a pocket (mic-down protection),
      16 KB page alignment (all 64-bit .so files are 0x4000), world hysteresis test.
    - Not now (DadCraft): anything that records audio ("sound capsule", "remember this minute"). Only if it would unlock a
      lot; he isn't ready to make that concession.

15. **(BUILT 2026-10-12, needs DadCraft's pocket check) Mic input shaping: attack, hold, release (DadCraft, Oct 12; the order's step A).** Like Ableton's Gate and Compressor,
    the way he shaped drums in his mixes. Today (make_patch.py input stage): `env~ 1024 256` meter (21 ms window,
    every 5 ms); the gate opens in 5 ms and closes over 80 ms, one threshold, no hold, so it can flutter near it; the
    compressor's gain moves with a fixed `pack f 40` both ways. Plan:
    - Gate: hysteresis (open at the threshold, close a few dB below), a hold time, and a **faster detector of its own**
      (3-5 ms window) so it opens on time. No lookahead delay on the audible path: the user hears the room through
      the headphones live, Android already adds ~20-40 ms, and 20 ms more would make claps feel late and smear against
      the real sound leaking past earbuds (Room notes can delay; it's an effect).
    - Compressor: separate attack (gain going down) and release (coming back up).
    - Sliders folded under Input & output ("Gate and compressor shape"): gate attack, hold, release; compressor attack,
      release. noPreset like gain/gate/compressor, one setting for all mics. Defaults = today (5/0/80 ms, 40/40 ms).
    - Triggers (glitch onsets, chimes, Room notes' opening) already use the meter and stay that way. Note: the glitch
      buffer, harmony's pitch tracker and Room notes' resonators take the gated sound itself, so a slow gate attack
      also blunts what they get. Check harmony isn't chopped by a fast release or short hold.
    - Test: a step signal into the patch, measuring the real attack, hold and release times.
    - Purpose: pocket rustle (hold + slightly slower attack + faster release should shut between cloth bursts; it
      complements pocket hush and mic-down protection, and the mic-up pocket sound DadCraft likes must not get worse)
      and earbud mics (15-20 dB quieter than the phone's).

16. **(BUILT 2026-10-12, parts 1-6; DadCraft's walk test open) Found-sound rhythm section (DadCraft, Oct 12; steps B and C).** The walking beat is "neat but not my favorite"
    (SETTLED: it was the pattern, which was very same-y, DadCraft confirmed Oct 19; fixed by the pattern engine's
    calming and the per-hit variation, see DESIGN-NOTES "pattern engine" and "Found-sound kit"). The idea: the room supplies the drum sounds and the walking pace sets a tempo, the
    RjDj premise itself. Today there is no BPM or grid: drums fire on each detected step, chimes wait for the next step
    or half-step (`mu_q_*`), the glitch cloud's "free clock" is random on purpose. Pieces that exist: StepDetector's
    averaged interval (the pace), the glitch cloud's 4 s RAM buffer, the sudden-sound detector (gc meter onsets),
    MotifMemory, density memory, ARP_PATTERNS tiers.
    1. **(BUILT 2026-10-12, needs DadCraft's walk) Tempo clock** (step B): its own steady beat, nudged by each footstep (phase-locked, like following a drummer),
       so a grid with subdivisions exists, uneven steps don't throw it, and it holds (or settles to a default) when
       you stop. Fold very slow paces up into a musical range. Useful on its own: drums, chimes and arpeggio on it.
    2. **(BUILT 2026-10-12 with C3, the found voices; needs DadCraft's ears) Hit catcher:** on a sudden sound, copy a short slice from the RAM buffer into a small kit (never to disk),
       sorted by simple traits (low and thumpy -> kick, mid and noisy -> snare, high and short -> hat), keeping the
       best few recent ones per role. Pocket hush stays in front of it. In a pocket, footfall thumps will be caught and
       land on the tempo: maybe natural kicks, maybe mush.
    3. **(BUILT 2026-10-12 as the pattern engine, C1, on the synth kit; always on, softer standing still) Sequencer:** slices on the grid, patterns busier with pace (like ARP_PATTERNS), shaped to sit in the mix
       (pitch drop for kicks, high-pass for hats, a short envelope: ties in with item 15).
    4. **(BUILT 2026-10-12 as the learned groove: where sounds fall in the bar is learned and echoes for a few bars) Rhythm memory:** like MotifMemory but looping and decaying: the last bar or two of hits carry on a few bars
       after the room goes quiet, varied each pass, thinning with density memory, then fading out.
    5. **(BUILT 2026-10-12) Loop layering:** glitch-cloud results (stutter, reverse, stretch) captured and layered into the rhythm.
    6. **(BUILT: Beat Sounds, Synth drums / Found sounds / Both, default Found) "Rhythm source" setting:** Synth drums / Found sounds / Both; the current walking beat stays as an option.
    Watch: sounds can't land on the beat as they happen, so they come back in later bars as echoes on the grid; a quiet
    room means an empty kit (fall back to soft synth drums or silence); slice choice and shaping decide musical vs
    messy; measure phone CPU (Music on is ~39% of a core).

17. **(BUILT: tabs, Realms, Realm ingredients `Worlds.GROUPS`, the rename; accelerometer 100 Hz tried and put back to 200 on Oct 13) Tabs and Realms (DadCraft, Oct 12; step D, replaces the step-7 screen polish).** The one long scroll has outgrown
    itself. Tabs, roughly Sound, Music, Scenes, Realms, Setup (devices, appearance, reset, about); five at most. The
    bottom bar (status, Start/Stop, Hold, Save) stays on every tab. A tab that does nothing right now says so ("Realms
    are paused while a scene is playing"). Plain Views, no new libraries: a tab strip switching containers.
    - **Realms** = Auto worlds renamed. Change only what is displayed: keep internal ids and built-in preset names
      ("Kinetic world"...) or migrate them, so nobody's choices reset (the reset-button rule).
    - **Realm ingredients:** each Realm expands to show its groups (Beat, Music, Echo, Reverb, Glitch...), each with a
      "this Realm uses it" switch; off = the Realm leaves that section at the user's own settings (an extension of the
      locked-param skip Worlds already do). Editing values inside a Realm stays preset editing (preset dropdown, Save
      sound), so Realms don't become a second settings screen.
    - Fold in the leftover polish ideas where they fit (mover dot, earcon on a Realm change, Bluetooth hint on the
      input picker, content descriptions) and the 100 Hz accelerometer trial.
    - Build in stages, each checked on the phone: split screens with no behaviour change, then tabs, then ingredient
      switches, then the rename. The tester guide gets rewritten once, after this lands.

Release prep (separate list): final name + applicationId (DONE Oct 21: Ouralia), icon (DONE Oct 19-21), numbered pd-core
release (DONE: 1.4.2-pdscenes.1); open, see "Open now": `source_url`, privacy policy, Play foreground-service and
microphone declarations, store listing.

18. **Our own objects (`dcs/`), the queue (DadCraft, Oct 17: "i'd like to do all of this").** Each one behind a switch,
    with its own tests (tools/test_dcs.py), the old sound the default until DadCraft's ears say otherwise. Built so far:
    dcs_string~, dcs_glitch~, dcs_derustle~, dcs_pitch~, dcs_shift~, dcs_onset~ (Oct 17; dcs_pitch~ turned out an
    accuracy win, not a battery one: sigmund~ at the patch's hop already costs ~0.1% of real time); then dcs_voice~
    (engines incl. analog's ladder and modal), dcs_freeze~, dcs_dist~ (Smooth), dcs_reverb~ and dcs_phaser~ (the effect
    ports; all added Oct 17, Smooth distortion Oct 19). NOT BUILT (checked Oct 22): dcs_listen~, dcs_dewind~, dcs_grain~, the reverb's shimmer mode, deken.
    - Voices: **dcs_shift~** (clean pitch shifter for Pitch and "Your voice" harmony, keeping the voice's own tone
      colour: no chipmunk, no warble). **dcs_pitch~** (a light pitch tracker to replace sigmund~, about half of Harmony's
      cost: battery).
    - Listening: **dcs_onset~** (sudden sounds by change of tone colour, not loudness only: fewer false triggers, the
      found kit sorted by its real sound). **dcs_listen~** (talking / music / traffic / wind: Realms follow what the
      room is like; music nearby could lock the beat to it). **dcs_dewind~** (wind on the mic outdoors, like rustle).
    - Tone: **dcs_ladder~** (Moog-style filter: resonance that growls), **dcs_drive~** (oversampled distortion and
      crush: no fizz), **dcs_reverb~** (moving internal echoes, a shimmer mode for Ghost; the reverb is ~13% of the
      patch), **dcs_phaser~** (per-sample, ~10% of the patch now).
    - Instruments: **dcs_modal~** (tuned resonator banks: bells, bowls, marimba from caught sounds), **dcs_freeze~**
      (hold a moment as a drone), **dcs_grain~** (the room as a particle cloud). Several of these are kit voices (19).
    - When a few are solid: publish dcs on deken (Pd's package manager) for Pd users and scene authors.

19. **Kits: the sound of the music, chosen and mixed per part (DadCraft, Oct 17; to replace the stems player).**
    The music brain (patterns, chords, melody, rhythm, call and response) stays one engine; a **kit** says what each
    part sounds like. Styles DadCraft named: acoustic, fully synth, EDM-adjacent, future bass, low end; mixing and
    matching parts between kits; users and sound designers making their own and loading them in.
    - **Parts (roles):** kick, snare, hat, percussion, bass, pad, chimes/lead, walking notes (arpeggio), harmony,
      texture. Each part in a kit names a **voice engine** and its settings.
    - **Voice engines** (C, `dcs_voice~`: one polyphonic object per part, notes in as `note <pitch> <velocity>
      <length>`, engine and settings by message, so a kit is data, not patch code): found (the mic's caught sounds, as
      now), sample (one-shots and multi-samples from the kit's files, pitched across the keyboard), string
      (dcs_string~), modal (bells, bowls, marimba), analog (oscillators into dcs_ladder~), FM (bells, electric piano,
      basses), supersaw (detuned saw stacks: EDM and future bass chords), sub (808-style sine with glide and drive),
      noise/drum synth (kicks, snares, hats), grain (textures).
    - **Kit-wide character:** drive, filter, a sidechain pump (future bass and EDM: everything ducks to the kick),
      chord voicing (spread, octave doubling), pitch bends (future bass wobbles), the kit's own mix.
    - **Built-in kits** (first set): Classic (today's sounds, exactly), Acoustic (string, modal bells, found
      percussion, plucked bass), Synth (analog through the ladder), EDM (909-style drums, supersaw, pump), Future
      bass (supersaw chords with wobble, chopped found voice, pump), Low end (808 sub, deep pad, sparse top).
    - **Mix and match:** Music tab "Kit" picks a whole kit; each part can be taken from another kit (drums from
      Acoustic, bass from Low end...). Saved in presets, so a shared preset sounds the same for the friend who loads it.
    - **User kits:** a kit is a folder or zip: kit.txt (the built-in kits' format; DadCraft, Oct 17) + its sample files.
      Stage 1 built Oct 17: text kits (import, menus, delete, presets carry them, the desktop bench); stage 2 built
      Oct 18 (engine `sample`: WAV in dcs_voice~, other formats decoded at import); stage 3 the format page (`docs/KIT-FORMAT.md`, Oct 18). Imported
      like stem packs today (reusing PackLibrary's import and decoding), shared like presets, and listed beside the
      built-in ones. A documented format and a page for sound designers; the desktop app doubles as a kit-making
      bench (hear a kit on the big speakers while editing kit.json).
    - **Stems player:** removed Oct 18 (DadCraft: "Just remove stems", rather than make loops a kit part first).
    - The mic still never touches disk: kits may bring their own files, and "found" parts stay in memory.
    - **Order** (DadCraft, Oct 17: the quick wins dcs_shift~, dcs_pitch~, dcs_onset~ first, then kits; first new kit
      style: Acoustic; **(a) and the first of (b) built Oct 17**: Classic = the Pd voices themselves, dcs_voice~ with
      pluck / bowed / modal, Acoustic; see DESIGN-NOTES "Kits"): (a) dcs_voice~ with the engines Classic needs, so today's sound is reproduced behind a Kit switch,
      (b) the first built-in kits one style at a time (each one DadCraft's ears pass before the next), (c) mix and match
      per part, (d) user kits and the format page, (e) retire stems.

21. **Music that doesn't go stale (DadCraft, Oct 21: with E minor pentatonic and a kit he likes, "the band keeps
    playing much of the same things").** Behind Music variety (Classic / Evolving), built in C (`dcs_composer`).
    1. **Chords and sections** (done Oct 21; DadCraft: "familiar without being something I can expect ... I like it"): progressions, Home / Lift / Calm / Turn,
       borrowed chords for the pentatonics, the full chord set for the seven-note scales.
    2. **Melodies that develop** (built Oct 21, `mel_dev`, waiting on DadCraft's ears): a motif per section, played
       in statements (again, answered, moved, upside down, backwards, cut short, carried on); its place travels.
       The chimes only: rhythm still comes from what triggers them; the walking arpeggio is stage 3.
    3. **Patterns made by rules** (built Oct 21, `pat_gen`, waiting on DadCraft's ears): the walking notes and the
       drums, a bar made by rules each bar (evenly spread hits, a figure of the chord's tones), each keeping most of
       the last and changing one thing, every 4th bar the section's own again. Not done: the chimes' rhythm (still
       set by what triggers them) and Melody rhythm's cells.
    4. **Chord colours and Melody rhythm by rules** (built Oct 21, `chord_color`; Melody rhythm's bar under
       `pat_gen`; waiting on DadCraft's ears).
    Left: the chimes' own timing when Melody rhythm is off (set by what triggers them, by design).

20. **More kit parts (DadCraft, Oct 20: "lets plan on doing at least one and two, maybe three"; keep all four).**
    Today a kit has four parts (mel, bass, pad, drums). A kit only picks instruments for notes the band already
    writes, so a new part needs the band to write something for it too. Each costs only while it plays.
    1. **More drum hits** (open): clap, tom, shaker, crash, rim beside kick, snare and hat, with the beat writing
       patterns for them. Recorded drums play as recorded, so each house sound gets its own role in the beat.
    2. **A "moments" part** (open): one-shot sounds at moments, not on notes: a Realm change, the start of a phrase,
       a sudden sound in the room, now and then while standing still. The best home for sounds with no note (a door
       chime, a squeak).
    3. **Kits for the parts the band already plays** (maybe): Harmony (the note answering a hum) and the Room notes'
       found bells. The easiest: their notes already exist.
    4. **A second melody** (later): an arpeggio or counter-melody around the main one; needs new music-writing rules.

22. **Outside reviews (ChatGPT, Grok, Kimi, 2026-10-22, from the public repo), checked against the code.** Accepted
    (all open, listed in "Open now"):
    - *All three:* a privacy policy; the release key and R8 before Play; MainActivity is too big (DadCraft's first pick).
    - *One or two:* README screenshots still say PdScenes (Kimi); no CI, CONTRIBUTING or issue template (Kimi, Grok);
      repo topics and F-Droid (Grok, Kimi); a demo GIF (Grok); a manifest check for INTERNET and the name (ChatGPT);
      delayed music callbacks after Stop, a long-session phone check, a release checklist with checksums (ChatGPT);
      a cost per feature (Kimi); sleep fade, tap tempo, a widget, MIDI in, auto-key from the room (Kimi).
    - *Already done, so dropped:* the first-run walkthrough (Walkthrough, Tour), step-synced echo, the tempo clock and
      found-sound rhythm (16), glitch stretch and gate (5b), the Realm warp sound, the per-Realm rules (Realms and
      their ingredients, 17), 16 KB alignment. Threads: PdBase's send methods are `synchronized`, so the sensor and
      mover threads can't collide (only the after-Stop callbacks are worth checking).
    - *Already shot down by DadCraft, so dropped:* twist = tape stop (tape stop removed Oct 10), bringing back Ratchet
      and Spray (pocketed Oct 19). Replacing libpd or the plain Views: none of them asked for it.
    - Out of date in the reviews: Grok gave the version as rc4 (it is rc5.1).

## Compatibility and scene findings (PdParty, rjlib PDF, composer pack)

- **Real scenes for testing.** `danomatika/PdParty` ships RjDj scenes in `res/patches/samples/rjdj/` (ApolloVoice, Atsuke, Boomerang,
  CanOfBeats, Eargasm, bouncy) and `res/patches/tests/rjdj/` (Sensors, Widgets, NoBackground). On desktop libpd with only our
  `scene_lib` all of them open without "couldn't create" errors; all but ApolloVoice (needs a touch) and the test scenes (need
  sensors/touch) make sound from a test input. Eargasm prints `no such object` for a few unconnected `[s]` names (compress,
  inputvolume, notechan1-4): harmless sends with no receiver. DadCraft can import them himself on his own phone with Import folder
  (point it at the `samples/rjdj` folder).
- **Licences decide what we may bundle.** Safe (GPLv3 text and "or later" in the patch): **Boomerang** (voice echo, 283 ms, by Luke
  Loeffler and Dan Wilcox), **ApolloVoice** (touch plays Quindar tones, Dan Wilcox) and **Atsuke** (Frank Barknecht, built on the RjDj
  template). No licence given, so do not bundle: Eargasm (Damian Stewart), CanOfBeats (Chris McCormick), bouncy (Georg Bosch).
- **Player-provided wrappers (DONE: ported `closebang`, `rj_time`, `rj_compass`, `rj_loc`, `rj_text`, `rjdj`; drawing `rj_text`/`rj_image` on screen BUILT Oct 14 as scene graphics; still open: `rj_loc` needs `#loc`).** PdParty supplies `rj_text`, `rj_time`, `rj_compass`, `rj_loc`, `rjdj` and `closebang`
  (`res/patches/lib/pdparty`, BSD). Scenes that rely on the player to provide them would fail in our app. Port the missing ones
  (rj_time from `#time`, rj_compass from `#compass`, rj_text as a no-op or an on-screen caption, rj_loc once `#loc` exists).
- **Older scene packs may use `index.rdf`** for their name and author instead of `Info.plist` (rjlib PDF). Add a fallback reader.
- **State saving.** rjlib scenes can save and restore parameters (`u_dispatch`, SSSAD, `u_loader`, a send to `RJ_SCENE_SAVE`). Check that
  our copies of `rjsave-ex`, `rjloader` and `sssad` actually persist state in the app, or document that they do not.
- **PdParty also lists `rj_zcr~`** among its externals, but no scene in its samples uses it, so keep skipping it unless one turns up.
- **Sample rate:** the RjDj material says 22050 Hz (`u_samplebank` defaults to it). Our RjDj timing setting already does this.
- **Perspective.** The rjlib PDF says its analysis section was the least finished part and held only basic onset detection. Our
  adaptive noise floor and brightness tracking already go beyond what RjDj shipped.
- **Composer pack cookbook** (`doc/composerpack/cookbook`: accel_tilt, accel_smooth, accel_pitch_roll) is a source of ideas for the Kinetic
  pattern tiers and for tilt-driven movers.
- **PdParty's User Guide** (danomatika.com/code/pdparty/guide) is the reference for exact sensor receiver names, value ranges and
  quirks. Ask DadCraft to paste it into the chat so it can be read (tools can only open links he sends).

## Research batch (ChatGPT/Gemini/PDF notes)
Mostly confirmation of the Designing Sound / CDM material. New small ideas:
- Compass trick: sound "behind" the listener gets low-passed (cheap spatial cue, needs `#compass`). Rejected with head
  tracking in item 14: the phone isn't on the head.
- Granular playback of one captured table, scaled by a control value (proximity-style), for Ghost/Collective. Built as
  the Stretch glitch kind (item 3).
- More Android sensors worth considering: step counter, rotation vector, proximity, light, barometer.
- Mic conditioning check: high-pass the mic before analysis; duck or gate it before long feedback delays.
- Keep one obvious reactive behavior per layer (the Dimensions lesson).
- Pointers: Pure Data forum thread "Dimensions augmented audio app", the libpd paper, Brinkmann's Making Musical Apps.

## Sensor integration (done, needs on-phone tuning)
Fused compass (rotation vector), light, proximity, barometer; `PhoneContext`; mover shapes Brighter/In pocket/Climbing; Pocket auto world;
scene messages `#light #proximity #pocket #pressure`; sensor list in About. Still to consider: GPS `#loc` (permission), hardware step
counter (permission), significant-motion/activity (Play Services), linear-acceleration/gravity sensors for cleaner motion.
