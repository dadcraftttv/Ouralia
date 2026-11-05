# Status log (moved out of CLAUDE.md on Oct 19)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

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
- **Notes and Chimes Level** (`music_arp`) moved from Music to Mix (Oct 21, DadCraft: "it directly controls the
  Lead's volume"), heading the Melody group above its Chimes and Walking Notes trims, as Pad Level did before. A Realm
  now sets it through its Mix ingredient (on by default, like Music).
- **Card on/off switches** (fixed Oct 21, DadCraft: "the switches for the effects in Sounds are gone"): a card's
  header switch is the section's toggle whose label ends in " enabled"; the Title Case renames made it "Enabled", so
  no card found its switch (rc5, rc5.1), and Music Enabled sat in the card's body. `Params.onSwitch` now matches in any
  case (test M: all 17 cards find theirs).
- **How the band plays in presets** (Oct 21, DadCraft: a preset made in PdScenes didn't bring them over; a preset is
  "the sum of all the things they did to shape the sound", so it loads exactly as meant): `Params.BAND`, 27 settings
  (Music on, Key, Scale, Song Shape, Chord Colours, Hooks That Grow, Living Grooves, Soft Steps, The Band Plays,
  Wandering Home, Sway, Slow Bloom, Pad Register, the Sounds choices, Melody rhythm, Phrasing, Found note ring, Pitch
  tracker, and the Beat's Follows, Tempo, Set tempo, Plays and Sounds) are saved in presets. None is resetWhenAbsent (a
  preset made before keeps what you have); a Realm never changes them, even one playing a preset of yours (like the
  kit; test W); built-in presets leave them out (`PresetManager.defaults`), so picking Robot doesn't stop the band. The
  kit and Band Members were always in presets (since Oct 21 the built-in ones leave the kit, its parts, layers and blends alone too, DadCraft: "yes"): loading one on the Sound tab left The
  Band tab's menus showing the old kit (rows on a hidden tab skip refreshRows), so `showTab` now refreshes.
  Also saved since Oct 21 (DadCraft: "yes, those should go into presets"), left out of built-in presets
  (`Params.builtInsLeave`): the effects' voices `Params.STYLES` (Glitch voice, Pitch shifter, Crusher style,
  Distortion quality, Scatter style; none resetWhenAbsent) and Intensity (Realms leave it too, `Params.realmsLeave`).
  Kept out by DadCraft's choice: the phone and listening setup (input gain, gate, compressor and their shape, pocket
  and handling, the sudden-sound detector) and Smoothing, Nudge strength and the Warp sound ("a personal choice").
- **The rename** (Oct 21, DadCraft: rc5 is to be the first public beta, as Ouralia): applicationId and Java package
  `io.github.dadcraftttv.ouralia` (tied to the GitHub account he owns; his pick over com.dadcraftstudios.ouralia), app
  name "Ouralia", the in-app wordmark lower case "ouralia" as in the mockups, prefs stores `ouralia_*`, themes
  `Theme.Ouralia.*`, presets written as format "ouralia-preset" (old "pdscenes-preset" files import as before: the
  format isn't checked). A new app beside the PdScenes one (testers export presets there and import them). Desktop:
  its ~/.config/pdscenes and ~/.local/share/pdscenes move to .../ouralia on the first start, stores renamed
  (`Context.moveFromWorkingName`); its output is now "Ouralia" (pick it again in OBS); re-run install-launcher.sh.
  Kept: the libpd forks' `pdscenes` branches and the `1.4.2-pdscenes.1` pd-core version (they name branches in other
  public repos), and the repo folder name until DadCraft moves it.
- **Oct 22: the outside reviews and a long session.** ChatGPT, Grok and Kimi reviewed the public repo (ROADMAP 22:
  what was accepted, built, dropped). ROADMAP got "Open now", the one list of what isn't done (the reviews kept
  suggesting built things). Built: PRIVACY.md and About's source link; MainActivity split into ten classes;
  motif notes dropped after a restart; test_release.py; CI, CONTRIBUTING, an issue template; fresh screenshots; the
  notification icon; R8 on release builds; docs/RELEASING.md and tools/release_apk.sh; the phone's mics named by
  position; Room in Stereo (pd-core 1.4.2-pdscenes.2, StereoCalibration, an option, never the default); dropouts
  counted. DadCraft's long session (12:18-12:59, release R8 build, walking with the phone in a pocket and Pokemon
  Go, then the office): no dropouts, no crash, battery 79 -> 73% in 38 min unplugged (the whole phone, Pokemon Go
  included), 31 C at most, the app about half a core. Found: the found-sound kit "grainy, crackling" (faint catches
  levelled up: Clear Catches, his setting 10.4 dB, verdict pending); the walking notes not with his pace (his Tempo
  was Set, 99.8 BPM: everything on one grid, but not his); a muted Pokemon Go still ducked the app (6.5 min);
  Room in Stereo had played mono (the channel check ran before the streams opened, fixed); and with stereo on the
  room's background reads ~20 dB low, which misleads fixed level thresholds (ROADMAP Open now).
  `tools/session_log.sh` died when the cable came out (Android ends adb's processes on unplug, setsid or not): the
  session was rebuilt from `dumpsys batterystats --history` and the app's events.log (the debug build installed
  over the release keeps its data and allows run-as).
  Later the same day (DadCraft's extra time): the found kit judges catches against the room's background in stereo,
  Sleep Timer, the health line in events.log, scene state saving (rjlib's sssad) and Info.plist in any capitals,
  Tap Tempo, the Bluetooth marks in the Input list; then code health: make_patch.py split into tools/patchgen/ (the
  patch byte for byte the same) and these notes into docs/design/ (every line kept, checked by script).
- **Oct 23-30: Shimmer, Wind Guard, the Room listener, Session Log, New Pieces; rc5.2.** Reverb Shimmer and its
  Style, Wind Guard, the Room listener, Session Log (Oct 29) and New Pieces (Oct 29, off by default) came in behind
  their switches. Oct 30, DadCraft: logging goes in the release, "only sendable if the user wants to email the logs":
  Session logs in About (email them as one zip or delete them all), the cap down from 200 to 50 MB. Released as
  0.9.0-rc5.2 (versionCode 15), his call over rc6.

- **Oct 30: kits removed on reactive (Codex; review/listen next).** DadCraft: "remove the kits" and
  "leave Classic the way it is, no change to accoustic". All selectable kits (Acoustic included), user kit
  import/delete, layers/blends/pump and 13 settings removed. Classic synth voices, Found sources and source
  choices/defaults unchanged; three reactive controls kept. The vocoder's note messages remain after removing
  their kit plumbing; expanded parity caught and corrected an initial frequency-read regression. No C/native
  changes, Harmony Organ and Chip crusher retained, composer frozen. Old saved state/presets ignore kit keys,
  keep surviving values/locks and save without kit data (5 Java checks). Fixed-seed reference 0515f8d: 55/55
  16-second synthetic renders sample-identical (4 parts × 3 sources × 4 control cases, 3 composer cases,
  4 vocoder carriers), peak difference 0; baseline Pd diagnostics identical too. Dev assemble and desktop
  tests/application compile passed. Maestro unchanged: 371 passed, 1 skipped. Full tier: 166/167 Pd sections and all 11 supporting suites passed (294 s).
  The glitch pitch test then drew an octave up (Pd's random seeds shift with the removed objects) and exposed an
  existing flaw: a jump up read past the live sound and played it unshifted (1001 Hz). Claude's review fixed the
  pitch jump (dcs-objects "More glitch kinds"); full tier then 167/167 sections and all suites passed (265 s).
  The legacy kit guide is archived. Claude review/install and DadCraft's listen remain; main untouched.
- **Nov 5: rc6, the first public beta.** CLAUDE.md's "Current state" as it stood before rc6:
  - **Release:** rc5.2 (0.9.0-rc5.2, versionCode 15, Oct 30; DadCraft chose rc5.2) = rc5.1 + everything since Oct 22
    (Shimmer, Wind Guard, World in Stereo, Clear Catches, Room listener, Sleep Timer, Tap Tempo, Session Log, New
    Pieces) and Session logs in About (email them as one zip or delete them, cap 50 MB). ~/Downloads/Ouralia-0.9.0-rc5.2.apk. Its GitHub
    pre-release is gone with the Nov 2 repo remake (see GitHub below).
  - **rc5.1** (0.9.0-rc5.1, versionCode 14, Oct 21; DadCraft: a hotfix of rc5, not rc6) = rc5 + presets that save
    The Band, Intensity and the effects' voices, the Kit menus refreshing after a preset loads, and the cards'
    on/off switches back, Notes and Chimes Level in Mix (its APK was replaced twice on the release for those, before
    any download, DadCraft's choice). Release APK, debug
    key, ~/Downloads/Ouralia-0.9.0-rc5.1.apk; its GitHub pre-releases (and rc5's) are gone with the Nov 2 repo
    remake. A fix on an rc is rcN.1 (versionCode always +1). Next: rc6 / versionCode 16.
  - **Plan (DadCraft, Oct 21):** rc5 = the first public beta, renamed to Ouralia, posted under GitHub Releases. The rc5
    releases were never advertised and were dropped on Nov 2 (see GitHub). The rename is done (Oct 21); the found-sound rhythm section (ROADMAP 16) was
    already built (DadCraft will walk-test it). Everything else waits for wider testers' feedback.
  - **DadCraft's verdicts (Oct 21):** Evolving stays opt-in (not the default). Hooks That Grow (`mel_dev`), Living
    Grooves (`pat_gen`), Chord Colours (`chord_color`): "all good". The Band Plays good with Intensity 50 (he is
    comparing it off with Intensity 100). Renames, effect families, rounded icons: good.
  - **Oct 22:** the reviews' points built (ROADMAP 22), MainActivity split, World in Stereo, pd-core 1.4.2-pdscenes.2,
    a long session (DESIGN-NOTES Status log). All pushed, forks included.
  - **Open:** see ROADMAP "Open now". Next, from the long session: Clear Catches' verdict, Tempo Set following steps?
    (Tap Tempo built), ducking for a muted game. DadCraft (Oct 22): found sounds work with World in Stereo (the name
    since Oct 22, was Room in Stereo), the Bluetooth notice shows in Audio Devices.
  Since rc5.2 (Oct 30-Nov 5, all on `sound-kinds`, merged into `main` Nov 5): kits removed (Codex, Oct 30); the
  reactive-first steps made fixed behaviour (chimes only for a heard cause, the pad only under a steady sound, Notes
  Only for Bigger Sounds at 20.5 dB); Evolving, Chord Colours, New Pieces, The Band Plays and Wandering Home always
  on (Nov 1); Chime Voice Glass/Both, Pad Voice Glass Bowl, Bass Voice, Trailing Melody (with a mover), Calm Builds
  rebuilt as the quiet tune, Music in Stereo, Found Palette; sound kinds (YAMNet) with Make Room for Music, Mind the
  Gap, Water Leaves a Trail, Underwater (one style), Traffic Passes Through, Traffic Flow, Birds Lift the Chimes, all
  under Mixed Surroundings (one switch, Nov 5), in presets (Nov 5), and in every phone build on ARM (Nov 5: "bring it
  in"); Codex's Nov 3 review findings fixed. DadCraft (Nov 5) on the RjDj question: "a user driven type of RjDj
  experience". Then: "lets make and post rc6 and make sure all of our documentation is completely up to date. i'd
  like to open this up to a public beta today." Docs brought up to date for it: tester guide (rc6 section, the new
  features moved into The Band tab, Mixed Surroundings' own part, privacy, what to test), README (public beta,
  shaped by you, sound kinds on ARM), NOTICE, PRIVACY, ROADMAP "Open now" summary, the Diagnostics switch's label.
- **Nov 5, later: rc6 replaced before any download** (DadCraft: "replace the rc6, do not make rc6.1"). The
  release build's classifier confirmed on the Moto (screen recording: the Now line naming voices 0.99, music, birds
  while he talked and whistled); a README GIF made from it. The recording's log showed Pd printing "random: no method
  for 'float'" over and over: the found pad's two `[random]`s got the grain number (design/music.md "found pad"),
  fixed, with a patch-wide check. Whistling read as music and birds: ROADMAP "Open now" (singing and Make Room).

