# Ouralia (first PdScenes) — design notes and history

The full record of how each part works and why (moved out of CLAUDE.md on Oct 17 so it is not loaded into every
session). Search it for the feature you are touching (`grep -rn "Glitch cloud" docs/DESIGN-NOTES.md docs/design/`:
since Oct 22 this file is the index and each area has its file, see "The design notes' parts" below) and keep it up
to date in the same commit when behaviour changes.

## How we work
- You can build and install yourself now: `./gradlew assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  (adb wireless debugging is fine). Read crashes with `adb logcat`. Do this instead of asking DadCraft to copy files.
- Only DadCraft can judge the sound (pad level, chime density, walking feel). Make a change, tell him exactly what to listen for, wait for his report.
- Commit small, descriptive commits. Do not push or publish anything unless asked.

## Layout
- `app/src/main/java/io/github/dadcraftttv/ouralia/` — Java, plain Views (no Compose; do not rewrite).
  Param/Params/ParamStore/PresetManager/Modulation (slider registry, presets, movers), Worlds/WorldSelector (Auto Worlds),
  StepDetector (walking beat), AudioService/AudioDevices/AudioSettings (audio engine and devices), SceneSettings, MainActivity.
  **MainActivity's split** (Oct 22, ROADMAP 22, DadCraft's pick from the outside reviews; 3,753 -> 1,237 lines, no
  behaviour change, each step checked on the phone): one class per feature, each holding the activity (`a`) and
  using its shared pieces (`look`, `dp`, `addButton`, `chip`, `openSheet`/`closeSheet`, `sheetHeading`,
  `addListRows`, `segmentBar`, `toast`, `LookDialog`: package-private, not private). MainActivity keeps startup,
  the tabs, the bottom sheet's frame, Start/Stop, permissions, the walkthrough and tour, and the lifecycle.
  SettingRows (the cards and setting rows, movers, budget lines, faded rows, refreshRows), PlayerUi (the docked
  player, its sheet content, the status and Realm lines), ChoiceUi (Key/Scale tiles and sheets, segment buttons,
  choice sheets), RealmsUi (the Realms tab, Realm sheets, ingredients), DevicesUi (Audio Devices pickers, the mic
  hint, Diagnostics), AppearanceUi (theme, accent swatches, the colour ring), PresetUi (presets, Save, snapshot,
  export/import, Reset everything), KitUi (Your Kits card, kit import/delete), SceneUi (the Scenes tab and the
  shared pad), AboutUi (About, feedback, licences). A new screen part gets its own class the same way.
- `app/src/main/assets/patches/test_scene.pd` — the built-in effects patch. **Generated.** Edit `tools/patchgen/` (split from
  make_patch.py on Oct 22, the output byte for byte the same: constants.py, core.py, build.py and one module per area),
  then run `python3 tools/make_patch.py` (still the entry point; `--legacy-fx <path>` as before). Never hand-edit the .pd.
- `app/src/main/assets/scene_lib` — bundled rjlib. `docs/RJDJ-NOTES.md` — notes on the original format.
- `app/src/main/assets/builtin_scenes/` — the four official RjDj example scenes (GPLv3), copied to the scenes folder on first start by
  `SceneLibrary.installBuiltIns`. Their `rj/` folders keep only files that differ from `scene_lib/rj`. Reset everything restores deleted ones.
  `scene_lib/rj/s_fplaytable.pd` carries a fix for current Pd (`[list split]` output order); keep it when refreshing rjlib.
- Player-side helpers for scenes live in `scene_lib/`: `closebang`, `rj_time`, `rj_compass`, `rj_loc`, `rjdj` (from PdParty, BSD), our own `rj_text` and `scene_lib/rj/rj_image`,
  and stand-ins for externals some imported scenes use: `declip`, `avg~`, `aubiopitch~` (sigmund~, MIDI out) and a silent `keys`.
  **Scene state** (Oct 22, ROADMAP Compatibility "State saving"): rjlib's saving (`sssad`, `rjloader`, `u_loader`) needs
  the player: it sends "save" to RJ_SCENE_SAVE, keeps the lists the savers answer on RJ_SCENE ("rjsave-ex-one a 30"),
  and sends them to RJ_SCENE_LOAD when the scene opens again (rjlib's rjsave-ex-help). The RjDj app did; PdParty never
  did, nor did we until now (found: nothing remembered). AudioService saves at Stop (after the audio stops, before
  closebang; libpd on Android answers in the same call) and restores after the patch opens (after its loadbangs);
  `SceneState` is the text (one list a line, "s:"/"f:" atoms), kept per scene folder in SceneSettings' prefs, so Reset
  everything clears it; deleting a scene drops it; a restart for a new device keeps it. Scenes that use it:
  Strike, Echochamber (in the composer pack's rjdj_scenes). Tests: test_scene_helpers (rjsave-ex round trip), javatests S.
  **Info.plist in any capitals** (PdParty takes "info.plist" too; Android's file names care about case). The
  `index.rdf` fallback in early notes: SET ASIDE, never a thing (all 66 scenes in the composer pack use Info.plist, and
  neither the template nor PdParty mentions it).
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
- **pd-core is our own build** (Oct 16; 1.4.2-pdscenes.2 since Oct 22: `PdAudio.setInputPreset` and `inputChannels` in
  `audioRuntimeInfo`, for the room in stereo): `io.github.libpd.android:pd-core` from `third_party/maven` (committed;
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

## The design notes' parts (split on Oct 22, code health)

This file is the index. Each feature's notes live in one of these (`grep -rn <feature> docs/DESIGN-NOTES.md docs/design/`;
"DESIGN-NOTES \"Kits\"" and the like in code comments mean a heading or bold name in any of them):

- `docs/design/audio-io.md`: **Audio in and out**: the speaker rule, Bluetooth mics, the mic's shaping, Diagnostics and the health line, the phone's mics, Room in Stereo, calls and other apps' audio, the start path.
- `docs/design/sensing.md`: **What the phone senses**: Realm Hopper (Auto Worlds), pockets, pocket hush and rustle, handling noise, mic-down, Flux.
- `docs/design/rhythm.md`: **The Rhythm**: the walking beat, Tempo and the tempo clock (Tap Tempo), patterns, breakbeats, the found-sound kit, Clear Catches, the learned groove, glitch loops.
- `docs/design/music.md`: **The Music**: Mix, Slow Bloom, call and response, the found melody, and the Music section (kits, chords, Song Shape, The Band...).
- `docs/design/effects.md`: **Effects and CPU**: reverse reverb, echo and its scatter, dice morph, the glitch cloud, switchable DSP, the CPU work.
- `docs/design/app.md`: **The app**: Intensity, first run, the removed stems player, Send feedback, dropdowns, tabs, the screen, Reset everything, presets.
- `docs/design/dcs-objects.md`: **Our own Pd objects** (`dcs/`): each object, its numbers and tests.
- `docs/design/look.md`: **The look**: the Ouralia redesign, colours, cards, the player, icons.
- `docs/design/desktop.md`: **Desktop stream mode** (`desktop/`).
- `docs/design/status-log.md`: **Status log**: what happened when, newest last.

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

## Open / next
- **Flaky test, settled (DadCraft, Oct 21: the machine was busy both times):** test_patch "scatter New: cost" (the New echo scatter must cost
  under 12% more of the whole patch than Classic) fails on the full run: +12.6% and +12.9%, also with Oct 21's changes
  stashed, so it isn't new. A timing check (best of three, interleaved) on a busy machine, or New scatter's real cost
  sitting right at the line: measure it alone before deciding between a looser limit and a cheaper scatter.
  Both failing runs were while the build machine was busy with other programs; the next full run (machine quiet)
  passed. Likely load, but it sits close to the limit, so it can come back.
- **Verdicts and the rc5 plan (DadCraft, Oct 21):** Song Shape Evolving stays opt-in. Hooks That Grow, Living Grooves
  and Chord Colours: "all good". The Band Plays sounds good with Intensity at 50 (he is comparing it off with Intensity
  100). The renames, effect families and rounded icons: good. rc5 is to be the first public beta, renamed to Ouralia,
  posted under GitHub Releases but not advertised until GitHub purges the old commits (still found Oct 21). Before rc5:
  the rename and the found-sound rhythm section (ROADMAP 16); everything else after rc5's feedback.
- **What is open lives in one place: ROADMAP "Open now"** (Oct 22, after the ChatGPT / Grok / Kimi reviews kept
  suggesting things already built and showed the docs disagreeing: this section said the Oct 17 objects list was
  done, but dcs_listen~, dcs_dewind~, dcs_grain~ and the shimmer reverb were never built). The commit that builds,
  removes or sets aside something updates "Open now" and marks the ROADMAP item. This section keeps only notes.
- The Beta Tester Guide lives in this repo: `docs/TESTER-GUIDE.md` (tester-facing: "Dad"/"DadCraft", plain words; moved
  from DadCraft's Claude Docs on Oct 12, where an older Oct 10 copy remains). When behavior changes, update it in the
  same commit or tell DadCraft it needs an update.
