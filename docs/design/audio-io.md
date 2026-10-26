# Audio in and out (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

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
  (the mic and speaker are separate devices with separate ids; matching by id failed). LE Audio headsets can be better. The UI hints about it:
  under the Input tile for the one chosen, and (Oct 22, ROADMAP 22) in the Input list itself, before picking: each
  Bluetooth mic marked "(Bluetooth call mode: low quality)" or "(LE Audio)" (`AudioDevices.isBluetoothMic`/`isLeAudio`),
  and a warning under the sheet's heading when a call-mode one is listed (`mic_sheet_call`). The tile keeps the plain name.
  The test phone can't use LE Audio (checked Oct 26, with DadCraft's LE earbuds, LE switched on in their app): the
  Moto G Stylus 2025 runs no `LeAudioService` (`dumpsys bluetooth_manager`), `bluetooth.profile.bap.unicast.client.enabled`
  is unset, so the earbuds connect as classic A2DP + call-mode headset and no "(LE Audio)" input can appear. Not
  fixable without root. The LE path (`TYPE_BLE_HEADSET`) is still untested on a real device.
- **Soft limiter** at the output (`clip~` base plus cubic-eased excess, never above 1.0). Hard clipping sounded fuzzy.
- **Defaults:** input gain 1 (was 2.5 until Oct 17: DadCraft wants a tempered start, the user can turn it up; existing installs keep theirs), compressor -30 dB 4:1. Gain, gate, compressor, smoothing, nudge and music on/key/scale are `noPreset`.
- **Mic input shaping** (roadmap 15, DadCraft Oct 12; section "Gate and compressor shape", folded, noPreset/noRandom/
  `noMover()`): the gate has its own fast meter (`env~ 256 64`: 5 ms window every 1.3 ms; the shared 21 ms meter
  still drives the compressor, triggers and movers) so it opens on time without a lookahead delay (the room is heard
  live: Android already adds 20-40 ms). It opens at the threshold, starts closing only `GATE_HYST_DB` (4) below it,
  stays open `gate_hold` ms, opens over `gate_attack` (default 5) and fades over `gate_release` (80). The compressor's
  gain moves at `comp_attack` going down and `comp_release` going up (both 40 = before); an unchanged target keeps
  its last speed, and changes under ~0.1 dB don't count (the meter ripples). Measured: defaults open 3 ms after a
  sound, rise 3 ms, fade 64 ms; attack 30/hold 150/release 400 -> 23/196/320 ms; a level hovering at the threshold
  doesn't flutter; the fastest release doesn't chop harmony. +6% of the idle patch.
- **Diagnostics** (roadmap item 2, Oct 13; `add_clip_watch`, `ClipWatch.java`, test D): once a second the patch
  sends `diag_clip_in`, `diag_clip_gain`, `diag_clip_out` (out last) = how many `CLIP_WINDOW` (512-sample, ~12 ms)
  stretches held a sample at/over full scale: the adc~ as it arrives (`CLIP_IN` 0.99), after Input gain (1.0; float,
  the compressor soaks it up, so it is informational) and the output limiter's input (1.0). abs~, `-~ thr`, `*~ 1e9`,
  `clip~ 0 1`, one env~ each. ClipWatch: a warning starts at 3 stretches within 5 s and ends after 5 s without any;
  "episodes" = runs of seconds with any in the last minute. Setup tab "Diagnostics" (folded): the mic really in use
  (`AudioDevices.inputInUse`, from this app's own `getActiveRecordingConfigurations`), call-mode note, output label +
  rate, mic level (after gain) with a bar and the gate, episode counts. The status line says "Mic overloading" while
  the input warning is on (only the mic's own overload: Input gain can't cause real clipping, it is applied in float).
- **The health line** (Oct 22, from DadCraft's long session: `tools/session_log.sh` dies when the cable comes out, so
  that session was rebuilt from batterystats): while the sound plays, AudioService writes to events.log at Start and
  then every minute (`Health.EVERY_MS`, from the movers' tick) "Health: battery 73% unplugged, 31.2 C, thermal none,
  app CPU 48% of a core, dropouts 0": `BatteryManager` capacity, the sticky ACTION_BATTERY_CHANGED's plugged and
  temperature (the battery's), `PowerManager.getCurrentThermalStatus` (Android 10+), the app's whole process CPU
  over the minute (`Process.getElapsedCpuTime`, 100 = one core; the Start line has none) and dropouts since Start.
  No permission needed; PRIVACY.md says so. ~90 bytes a minute. Testers' Send feedback carries it. A cost per
  feature: compare the CPU lines with a feature on and off. Test: javatests K.
  Event log: "Mic overloading", "Mic overloaded for a moment" (at most every 10 s), "Output limiter working hard".
  Dropouts (Oct 22, for the long session): every 5 s `AudioService.checkXruns` reads Oboe's count
  (`audioRuntimeInfo("xRuns")`), counted from Start; new ones go in the event log ("Audio dropouts: +N (T since
  Start)"), and Diagnostics shows the total once there is one. `tools/session_log.sh` logs a session over USB
  (battery, temperature, thermal status, the app's CPU every 30 s, and its log) and reports it. Plugged in only:
  unplugging ends it (Android ends adb's processes). Unplugged sessions: `dumpsys batterystats --history` (level
  and temperature by time) and the app's events.log (ROADMAP: the app logging battery and CPU itself).
  Scenes send no counts (the section says so).
- **The phone's mics by position** (Oct 22, DadCraft saw "Phone microphone" twice): Android lists each built-in
  mic as TYPE_BUILTIN_MIC with an address (the Moto G Stylus: "bottom" and "back"; the audio policy also has them
  as AUDIO_DEVICE_IN_BUILTIN_MIC and _BACK_MIC). `AudioDevices.label` adds the position (bottom, back, top in words,
  else the address) to the menu and to Diagnostics' mic in use. Stored choices are keyed type:address, unchanged.
  Both at once: the built-in mic also offers stereo (channel mask 0x000c); on most phones that records two mics as
  left and right. The app opens one mono input (`initAudio(rate, 1, 2...)`, `adc~ 1`): ROADMAP "Open now".
  **Measured (Oct 22, `StereoProbe`, debug builds: `am start ... --ez stereo_probe true`, logcat StereoProbe;
  DadCraft tapping by each mic in turn, 20-30 s per source, nothing recorded):** VOICE_RECOGNITION (Oboe's default,
  what our pd-core opens: OboeEngine sets no input preset) and UNPROCESSED give one mic on both sides (correlation
  1.000, 0 dB apart), at -56 and -49 dB. MIC gives two mics but in partly opposite phase (correlation -0.16, sides
  -7 to +9 dB), CAMCORDER two mics naturally (+0.38, -10 to +9 dB); both about 25 dB hotter (-31 dB: gain or AGC
  in the phone's processing). So the room in stereo needs the Camcorder preset, which pd-core can't set yet.
- **World in Stereo** (named Room in Stereo until later on Oct 22, DadCraft's rename; ids unchanged: `stereo_room`, StereoRoom; Oct 22, DadCraft's idea; Setup, Audio Devices; off by default, `AudioSettings`, not in presets:
  it depends on the phone). DadCraft: "the experience should be universal", so nothing is fixed to his phone:
  which way of opening the mic gives two mics, and how loud it is, is up to the phone maker (Android's rules pin
  down only VoiceRecognition and Unprocessed). `StereoCalibration` measures each phone the first time the switch
  goes on (and on Check Again): 2 s of the room per preset, mono VoiceRecognition first as the reference level,
  then Unprocessed, Camcorder, Mic in stereo; levels and how alike the sides are, nothing stored.
  `StereoRoom.choose` takes the first that opened in stereo, isn't one mic on both sides (identical samples) and
  isn't partly opposite (correlation >= 0), trimmed to the reference's level (-40..+6 dB); none: "mono only" and
  the switch goes off. On the Moto (a quiet room): Camcorder, -10.6 dB (the 25 dB from the taps was rough).
  **The background fooled it** (Oct 22): Check Again in a quieter room gave +6.0 dB (Camcorder quieter than
  VoiceRecognition there): the Moto's Camcorder mode suppresses a quiet room's steady background, and the levels
  compared were each mode's median window, mostly background. Now 3 s per mode while the user talks or claps
  (the measuring line asks), each mode's level its loud tenth (`LOUD_SHARE` 0.9: the talk and claps, which the
  phone's processing leaves mostly alone). And **Stereo Level** (+-12 dB by ear, `AudioSettings.stereoAdjust`,
  live while it plays, back to 0 on a new measurement), since no measurement suits every phone and room.
  Measured so on the Moto (DadCraft talking and clapping): Camcorder -15.0 dB (reference -29.0, Camcorder -14.0;
  Mic correlation -0.79, rightly skipped). DadCraft: "sounds about the same", Stereo Level left at 0.
  `AudioService.openAudio` opens 2 inputs with that preset (pd-core 1.4.2-pdscenes.2's `PdAudio.setInputPreset`)
  only for the built-in effects and the phone's own mic (`AudioDevices.isPhoneMic`: a headset or USB mic stays
  mono); if `audioRuntimeInfo("inputChannels")` isn't 2 once the sound has started it reopens in mono (event log).
  (Oct 22, found in DadCraft's long session on the R8 build: the check first ran before the start, but Oboe opens
  the streams on start, and the count was an unset int, 2 by chance on the debug build, 0 on release: stereo
  fell back to mono. Now asked after the start, and the fork's count starts at 0.) The patch
  (`add_stereo_in`/`add_stereo_out`) works in mid and side: the mid (L+R)/2 x trim feeds everything as the one
  mic did; the side (L-R)/2 x trim takes the same gain, gate, compressor, pocket hush and Dry, times
  `stereo_plain` (the app's `StereoRoom.plain`: the product of 1 - amount over Sing in Key, vocoder, ring, pitch,
  crusher, distortion, filter, so a reshaped room narrows to mono with them), and joins the ears after the
  spatial stage: left + side, right - side. Delayed like the mid (1.5 ms, the spatial stage; a delay line read
  before its write can't go under one block, so it clamps rather than adds; Smooth distortion turned up adds 16
  samples: `st_extra`). Off: st_r 0, st_mid 1, st_side 0, so the sound is the left channel as before, sample for
  sample. Tests: test_patch "stereo room" (off unchanged by a second mic; one mic on both sides = off; a click
  only the left mic hears is in the left ear alone, right < 0.5e-3; the right mic's in the right; plain 0 = both
  ears alike; trim; the gate shuts the side; Smooth's 16 samples), Java Y (the choice, the patch values, plain,
  the delay). Diagnostics says "Room in stereo: the phone's two mics" while it plays.
  **DadCraft's verdict (Oct 22, by ear):** "i can definately hear the back mic on the left and the bottom mic on the
  right"; effects kicking in feel more centred; the room sounds as before, just in stereo; "i like it, and it
  should be an option for phones that support it, not default. that way the experience can remain as close to
  universal as possible." Stays off by default.
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
