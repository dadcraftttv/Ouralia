# Desktop stream mode (`desktop/`, Oct 16)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- DadCraft wants the app on Linux as background music for his stream (no sensors, Set tempo, Realms by room level).
  Step 1 done: `desktop/run.sh` runs the built-in patch in libpd for Java (`make javalib` in tools/.libpd, via
  `desktop/fetch_libs.sh`, with JNAJack 1.4.0 / JNA 5.14.0 / org.json 20240303 from Maven Central, SHA-1 checked,
  not in git) through PipeWire's JACK layer (`pw-jack`). Mic (+ optional game sound = a sink's monitor) mixed to the
  patch's one input; output to its own null sink "ouralia" (description "Ouralia"; "pdscenes" before the rename, made with pactl, removed on
  exit), which OBS records with Audio Output Capture. The JACK client is "Ouralia Engine" (JACK port names are
  device *descriptions*, so the sink's "Ouralia" would clash). Routes (output, mic, game, --listen) are re-checked
  every 2 s and reconnected; devices match by name or part of name/description. Never hears itself: the sink is
  never an input, --listen refused/skipped while the game sound comes from that sink. Typed lines change settings.
- DadCraft's machine: PreSonus AudioBox Go (its USB link drops now and then; nodes come back renamed ".3"/".4"),
  EasyEffects on the mic (OBS reads Easy Effects Source). Through the patch the EasyEffects source (peaks at 0 dBFS)
  sounded overdriven; the AudioBox directly with the compressor off "sounds amazing" -> `DESKTOP_DEFAULTS`
  comp_thresh 0. Pd 0.55.2's expr prints "realloc called with N bytes" to stdout (filtered in run.sh).
- **Window** (Oct 16; `DesktopApp` tick + Pd receiver, `DesktopWindow` Swing, `DesktopEngine` routes): compiles the
  phone's 15 logic classes unchanged (`run.sh` SHARED list) with `desktop/shim/` stand-ins: android.content.Context
  (files dir ~/.local/share/ouralia, prefs ~/.config/ouralia/<store>.json via JsonPrefs) and AudioService
  (isRunning). Tabs Sound/Music/Realms/Presets/Setup built from Params (sections as on the phone; mover params,
  section mover switches and `PHONE_ONLY` hidden), rows refreshed 4x a second from ParamStore. The tick is the
  phone's mover tick minus sensors: `PdBase.pollPdMessageQueue()` first (desktop libpd queues messages; without it
  mic_level/tempo never arrived), morph, Modulation, realm_cue, Worlds.tick(walking/pocket false, no shake),
  CalmBuild + calm_chimes, call and response, motif. `DESKTOP_DEFAULTS` (first start and Reset): comp_thresh 0,
  tempo_mode Set. First start connects no mic (DadCraft's laptop mic reads 0 dBFS nonstop) and opens Setup. Device
  menus / `mic_device`, `game_device`, `listen` commands call `engine.apply`. A loop the engine can't see: OBS
  monitoring Ouralia onto the output the game sound is taken from (UI and README say: give the game its own
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
  round 1: window size/place, last tab and open sections kept (ouralia_desktop), wider label column, the glitch
  budget line, "Mic in"/"Out" meters. DadCraft (Oct 16): stem packs and scenes are not part of the desktop app; it is
  for showing the engine on stream.
- App menu launcher (Oct 16): `desktop/install-launcher.sh` writes ~/.local/share/applications/ouralia-desktop.desktop
  (no app icon yet: the theme's audio-x-generic), `desktop/launch.sh` = flock on ~/.local/state/ouralia/running.lock
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
  when the game restarts. `DesktopEngine.apps()` leaves out Ouralia, anything with "obs" (its monitoring carries
  our music), EasyEffects' ee_ filters, speech-dispatcher, MIDI. A whole output's monitor can still be picked
  (loop guard as before). Tested with a pw-cat tone into a throwaway sink: listed, linked, lost on quit, back.
  DadCraft's Minecraft (Prism Launcher Flatpak) plays as a stream named just "java": the menu labels apps from
  `pactl --format=json list sink-inputs` (portal app id -> "java (PrismLauncher)"; pw-dump's JSON repeats keys and
  org.json refuses it). Picking an entry ticks "Also hear a game" (he picked it, the box stayed off, nothing came).
  Device names match without PipeWire's ".N" suffix (`DesktopEngine.base`), and menus store the plain name.
- No walking stand-in on the desktop (DadCraft, Oct 19: the Rhythm's BPM setting is enough there).
