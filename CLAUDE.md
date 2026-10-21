# PdScenes (working name; chosen name: Ouralia) — notes for Claude Code

Open-source (GPLv3-or-later, bundles RjDj's rjlib) Android app: RjDj-style reactive music on libpd / Pure Data, plus a
Linux desktop "stream mode" (`desktop/`). Free on Google Play eventually.
Owner: **DadCraft** (GitHub dadcraftttv; testers also say "Dad"). **Always call him DadCraft**: in files, code comments,
commit messages, tests and replies. Never write his real name anywhere (the GitHub repo is public; his name was
erased from all of its history on Oct 19).
Test device: Moto G Stylus 2025, Android 16, no root. Build machine: Linux Mint, 12 cores.

**Full design notes and history: `docs/DESIGN-NOTES.md`** (every feature: how it works, numbers measured, why, what
DadCraft said). Don't read it whole: `grep -n` the feature you're touching and read that part. Update it in the same
commit when behaviour changes. Ideas queue: `docs/ROADMAP.md`. Tester guide: `docs/TESTER-GUIDE.md` (update it with
behaviour changes, or tell DadCraft it needs one).

## How we work
- Build and install yourself: `./gradlew assembleDebug`, then `~/Android/Sdk/platform-tools/adb -s ZD222T44PP install -r
  app/build/outputs/apk/debug/app-debug.apk`. Crashes: `adb logcat`. Event log: `adb shell run-as app.pdscenes cat files/events.log`.
- Only DadCraft judges sound. Make a change, tell him exactly what to listen for, wait for his report.
- New sound ideas go in behind a switch (old sound the default) so he can compare, until he says otherwise.
  A setting that uses our C objects gets `.dcs()` in Params.java (shown with " *").
- Commit small, descriptive commits. Do not push or publish anything unless asked.
- **Save tokens** (DadCraft asked): run the quick test tier while working, the full tier once before a push; print
  only failures and summaries; read files in parts (grep first); don't re-read what's already in context.

## Hard rules
- **The final name and applicationId must not contain "RjDj".** Chosen name: **Ouralia** (not renamed yet: a new
  applicationId is a new app for testers; DESIGN-NOTES "The name").
- **Output never goes to the phone speaker** (headphones only; see "Output never goes" in DESIGN-NOTES).
- **The mic never touches disk.** No recording audio to files.
- Nothing resets settings except the Reset everything button. Existing installs keep stored values when defaults change.
- Never switch the input to an earbud/headset mic by default (DadCraft: non-LE Bluetooth mics are call-mode, poor).
- Defaults: input gain 1, compressor -30 dB 4:1; a fresh install plays only the dry room.

## Layout
- `app/src/main/java/app/pdscenes/` — Java, plain Views (no Compose; do not rewrite). Params.java = every setting.
- `app/src/main/assets/patches/test_scene.pd` — the built-in patch. **Generated:** edit `tools/make_patch.py`, run
  `python3 tools/make_patch.py`. Never hand-edit the .pd.
- `app/src/main/assets/scene_lib` (rjlib + our scene helpers), `builtin_scenes/` (4 official RjDj scenes).
- `dcs/` — our own Pd objects in C (prefix `dcs_`, BSD-3, loaded by `#X declare -lib dcs;`). After changing
  `dcs/src`: `make -C dcs`, `tools/build_dcs_android.sh` (commits `app/src/main/jniLibs/*/libdcs.so`), `tools/test_dcs.py`.
- `app/src/main/assets/kits/*.txt` + `Kits.java` — kits (what each music part sounds like; `dcs_voice~` engines,
  Classic = the Pd voices). Details: DESIGN-NOTES "Kits".
- `desktop/` — Linux stream mode (Swing + JACK via pw-jack), compiles the phone's logic classes unchanged.
- `app/src/main/res/values/strings.xml` — escape apostrophes as `\'`.

## Dependencies (details: DESIGN-NOTES "pd-core is our own build")
- pd-core 1.4.2-pdscenes.1 from `third_party/maven` (committed), built by `tools/build_pdcore.sh` from our forks
  dadcraftttv/pd-for-android and dadcraftttv/libpd (`pdscenes` branches; Pd 0.55.2 + the Oboe race fix).
  Tests/desktop libpd: `tools/build_libpd.sh`, same libpd commit. Pd 0.56 breaks StreetBeatz: move all pins together.
- Fork rules: keep fork changes small, offer general fixes upstream; new objects go in `dcs/`, never change a
  standard Pd object.

## Tests
Setup: `export JAVA_HOME=~/.jdks/jbr-21.0.11 PATH=$JAVA_HOME/bin:$PATH`; libpd via `tools/build_libpd.sh`; numpy in
`tools/.venv`; `make -C dcs`.
- **`tools/run_tests.sh`** — everything in parallel (patch generation, Pd suites, Java, desktop), prints only
  failures and one summary line per suite. Before a push.
- **`tools/run_tests.sh quick <words>`** — while working: only the Pd test sections whose title matches (e.g.
  `quick glitch`, `quick pocket`), plus the Java tests. `tools/run_tests.sh list` shows the section titles.
- Android build: `./gradlew assembleDebug`. Real-device check is DadCraft's.

## Current state (keep this short: history goes in DESIGN-NOTES "Status log")
- **Release:** rc4 (0.9.0-rc4, versionCode 12) built Oct 20 (release APK, debug key like rc3) and handed to
  DadCraft to send; it carries the tester guide's "New in rc4" list. rc5 next: bump versionCode/versionName.
- **Defaults:** new sounds stay opt-in (the old sound the default) except Glitch voice (New; the old one is Legacy)
  and Distortion quality (Smooth). Back pocket, no setting but code kept: glitch kinds Ratchet and Spray
  (`GC_POCKET` in make_patch.py).
- **Waiting on DadCraft:** whether Music variety Evolving (`dcs_composer`, ROADMAP 21 stage 1, he likes it, opt-in for now)
  becomes the default; his ears on Melodies that develop (`mel_dev`, stage 2); stage 3 next; phone checks of user kits (with
  samples, and MP3/OGG/FLAC import), kit layers, kit drums in "Your steps", wired + Bluetooth together, the Pocket
  Realm; his desktop "<Part> from" report (likely a kit with no part of its own, now labelled "(Classic's drums)").
- **New look (Oct 19):** the Ouralia redesign is built (DESIGN-NOTES "The look"; mockups: claude.ai canvas
  https://claude.ai/artifact/3yEsxCthrK6Dornte1VG5R). Finished Oct 20 after a last review against the
  mockups (DadCraft: "looks and feels great"); only the Ouralia wordmark waits for the rename.
- **GitHub:** DadCraft asked Support to purge the old commits that held his name. When they answer, check that
  `gh api repos/dadcraftttv/PdScenes/commits/c193786cad2b75e94f01912ec5b3c7b4a1dd26e0` no longer finds it.
- Push only when DadCraft asks (`git log origin/main..HEAD` shows what is unpushed).

## Pd gotchas
- In message boxes write commas as space + `\,`. `[t b b f]` fires right to left; a `[t]` outlet feeding two objects
  has no defined order. A `[value]` feeding several destinations fires all on a bang: one reader per consumer.
- A `[declare]` object does nothing while a file loads: the `#X declare` line after `#N canvas` does.
- No `;` inside comments in Params.java (the test regex stops at `;`).
- Pd seeds every [random] from one counter: adding objects reshuffles random sequences in later tests.
- Desktop libpd queues messages: call `PdBase.pollPdMessageQueue()`.

## Test gotchas
- When a default changes, pin the old value where a test compares against the old sound (test_patch `fresh()`
  base, test_fx_port `REST`).
- A new check can pass while measuring nothing: make it fail once to see its numbers.
- libpd prints to stdout ("realloc called..."): a subprocess test hands its result back in a file.
