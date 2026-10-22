# The app (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- **Intensity** (roadmap item 4, Oct 13; `Intensity.java`, test I): `intensity` (main section, first row, and a
  the dial in the player sheet since Oct 19, "The look"; noRandom, movable; DadCraft: the user's own control, Realms and built-in presets
  never touch it; saved in your presets since Oct 21, DadCraft: "Intensity should be saved"). 0.5 = as set. `Modulation.output` (the one path every value takes to Pd) applies the mover, then
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
  **Redesign + tour (Oct 21; `Walkthrough.java`, `Tour.java`, `MainActivity.startTour`).** DadCraft: "it gets the job
  done but it doesn't quite pop"; wanted arrows to the small unlabelled Sound-tab buttons, then a live try of Echo
  with the screen dimmed, the app visible but untouchable until Next, and everything reset at the end. Now five pages
  (welcome, headphones, mic, it starts quiet, "Let's look around"), each with a badge (icon on a disc in a slowly
  turning ring in the dial's two colours, a little bounce per page), progress pills, a soft accent glow, pages that
  slide; the live lines are chips (tick or warning). The last page's "Show me around" starts the tour: a `Tour`
  overlay on the main screen dims everything (even-odd path with rounded holes, a pulsing ring per hole), curved
  arrows run from the card to each spot, numbered badges on the spot's far edge match the card's lines (bold before
  the colon). Steps: Save/Export/Import; Randomize/Nudge/Undo; Echo's heading (Movers, switch, open); the player
  (Start, now playing, Save sound); the five tab icons; then "try it" steps whose lit spot passes touches through
  (`dispatchTouchEvent` returns false on DOWN inside it): press Start (auto-next on `AudioService.isRunning`, a line
  asks for headphones), tap Echo (auto-next when its body shows), turn it up (Echo time row, and Echo feedback to
  Echo level: two spots, so Echo follows your steps stays dark, its sheet would open under the overlay; Next lights
  once Echo level >= 0.15); "Now it's yours" (feedback email). At the start Echo is set to its defaults (level 0,
  on, movers off) and folded; at the end however it ends (Done, Skip tour, Back, onDestroy) Echo's values, movers and
  fold go back to what they were: on a new install Echo level 0 (Hard rule: nothing else resets settings). While a
  scene is chosen the try steps are left out. Checked on the phone by screenshots (no headphones: the Echo steps
  with a temporary build that skipped Start).
- **Stems player: REMOVED Oct 18** (DadCraft, asked whether loops should become a kit part first: "Just remove stems". Kits
  replace it; its decoder and zip import live on in `AudioFiles` for kits; old files/packs folders are left on the
  phone, unused). As it was: (roadmap 9 in ROADMAP.md, Oct 14; `StemPack` parse, `StemArrangement`, `PackPlayer`, `PackLibrary`,
  test K; `add_stems`, Pd test "stems:"; `tools/make_test_pack.py` writes a synth test pack): packs live in
  files/packs/<name>/ with pack.txt (title, bpm, key "A minor", bars; "file role: x [bars: n] [section: B]", _A/_B
  file suffixes = sections; max 8 stems). Import zip/folder (Music tab "Stem packs"); non-WAV stems are decoded once
  with MediaExtractor/MediaCodec into `.decoded-<file>.wav`; loop lengths are checked (warnings). Selected pack in
  prefs `ouralia_packs`, not in presets (DadCraft); built-in sound only. While playing, `Modulation.output` lets
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
- **Send feedback** (Setup, About; Oct 14): a mailto email to dadcraftstudios@gmail.com, subject with the version,
  body with a prompt, phone/Android/app version, what's playing, and (box ticked by default, explained) the last 200
  event-log lines in the body (no attachment, no FileProvider). 0.9.0-rc2 = versionCode 10, 0.9.0-rc3 (Oct 16) = 11 (posted to testers; built on the snapshot pd-core). 0.9.0-rc4 (Oct 20) = 12
  (`./gradlew assembleRelease`, signed with the debug key so it installs over earlier builds; DadCraft sends the APK;
  shipped with user kits, kit layers and the Pocket Realm untested on his phone, by his choice: testers try them). For rc4: our own
  pd-core and the unplug fix (b4d0473, 2fd2344). 0.9.0-rc5 (Oct 21) = 13: the first as Ouralia (a new app), the first
  public beta, for GitHub Releases. 0.9.0-rc5.1 (Oct 21) = 14: a hotfix of rc5 (DadCraft: "i don't want this to
  be rc6"), presets saving The Band, Intensity and the effects' voices, the Kit menus refreshing, and the cards'
  on/off switches back and Notes and Chimes Level in Mix (both added by replacing the rc5.1 APK, 0 downloads,
  DadCraft's pick over an rc5.2).
- **Dropdowns keep their value** (fixed Oct 12): rows are created at their stored value and a Spinner selects it
  before its listener is set; a Spinner reports its first entry as selected on first layout, and folded rows aren't
  refreshed, so key and scale kept snapping back to C major.
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
- Presets store: sliders, movers, music pad/notes/tone/follow, beat, dry. A slider kept out of presets keeps its mover out too
  (`Params.withMovers`), or loading a preset or world switched it off. Not stored: gain/gate/compressor, smoothing/nudge,
  music on/key/scale, locks, worlds choices, audio device, scenes, appearance.
