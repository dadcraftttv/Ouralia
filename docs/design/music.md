# The Music (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- **Mix** (DadCraft, Oct 13: "every musical and rhythmic element should either have a volume slider or on/off
  switch"): section `mix` on the Music tab, in presets (DadCraft: shared with a preset), a Realm ingredient (`Worlds.GROUPS`
  "mix"). It holds the existing beat_snare, beat_hats, beat_glitch, music_pad, music_harm (moved from Rhythm/Music,
  ids unchanged) and new trims 0-1.5 (default 1 = as before, noRandom, NOT resetWhenAbsent so older presets and the
  built-in Realms leave them alone): `mix_kick` (synth kick and found kick; the found one is `fs_ks_kick`, named into
  the fsound group: outside it the trim made a DSP loop between the toplevel and that group), `mix_bass` (the held
  bass, either voice, `ba_sm`, and Calm Builds' bass), `mix_chimes` / `mix_walk` (the chimes and walking notes share the 4 voices, so the trims scale
  each note's velocity: chimes at `mu_deg` and motif replays, the arpeggio writes its own trimmed velocity on each
  note from `apvel` (`mu_arp_t` outlet 3; before, an arpeggio note after a chime played at the chime's velocity) and
  the groove note), `mix_ring` / `mix_bells` (`rn_out` / `rnf_out`). At 0 a part is silent but still runs (no extra
  switch~ factors). Turning Auto realms on asks first ("Let Realms change your sound?": they set the sliders from
  their own preset, the mix too; "What it changes" keeps a part).
- **Calm builds, the quiet tune** (roadmap item 7, Oct 14; reworked Nov 3; `CalmBuild.java`, test B; `calm.py`, test_patch
  "7z10"; `calm_build` toggle in Music, "Calm Builds", off by default, noPd, in presets since Oct 21; DadCraft, Nov 4: "Calm Builds is working well now"). DadCraft (Nov 3):
  "I'd like the chimes, bells, bass, etc to play along with the drums when a room has been quiet for ... 30 seconds to
  a minute. it should be a slow build up ... can trail off over 15 seconds once a consistent amount of noise has been
  detected. ... I don't want to bring back the kits." He chose to replace the Oct 14 layer lift (pad/room/harmony/
  reverb +40% and the `calm_chimes` still-pattern chance) rather than keep both; Codex's review in Conductor
  `.handoff/to-claude-quiet.md`.
  - **Quiet** is judged in Java from the pre-effect mic level (`Modulation.micLevelDb`, dBFS) against a reference
    that follows the room's low level while waiting (falls over ~3 s, rises with a 60 s time constant). Noisy:
    `NOISE_DB` 8 above it, or over `GUARD_DB` -40 at all, or walking. Consistent noise: noisy for 3 of the last 5 s.
    A knock or cough changes nothing (and the found drums still catch it: nothing here gates catching). A steady fan
    under the guard becomes the reference, so it counts as quiet; a louder room never does.
  - **Dropouts (fixed Nov 4).** DadCraft: "it was quiet in my house for several minutes but Calm Builds did nothing.
    the only time i heard anything was when i started making the slightest amount of noise." His session logs: a
    steady -56 dBFS room, never walking, with the Moto's level reading about -100 for a moment every 10-20 s. Each
    dropout pulled the reference (then falling within 1 s) to about -95, the room then read 40 dB over it, i.e. noise,
    so the wait restarted; the reference needs ~100 s to climb back, and the next dropout came first. Calm stayed
    "waiting" for 7 minutes. Now a level under `DROPOUT_DB` -80 dBFS counts as quiet and never moves the reference,
    and the reference falls over 3 s, not 1. Replaying his two sessions' logged levels: old code never starts, new
    starts at 29 s in both (and trails off in the second when he made noise). Test B has both cases. Where the
    dropouts come from (the Moto's input processing?) is not known.
  - **Lifecycle:** a quiet wait (30-60 s, new each time; noise restarts it) -> a fresh phrase, rising over
    `RISE_S` 120 s -> plays in full while quiet, indefinitely -> consistent noise starts a `FADE_S` 15 s trail-off
    that always finishes (quiet mid-fade doesn't revive it), then a fresh full wait. The reference is held while it
    plays and fades, so the noise that ended it cannot become the floor and revive it. Switch or Music off: gone at
    once. Event log "Calm tune starts/ends"; the Now line still says "building".
  - **The tune (patch, `calm.py`):** six chime steps 7..14 and `calm_tune` 0..1. Every build is its own
    (Nov 4; DadCraft asked if it always starts the same way with the same pattern, then: "do all three. the point
    is that it should never sound the same"): the notes start on any step 7..13, move a step or two (a leap of
    three one time in five), the fourth note a chord tone half the time (sometimes its octave, which uses the
    scale's length 5/6/7, up then down within 7..14, else the chord tone kept: no clamp onto 14), and the end comes
    home half the time, else near home or on a chord tone. The rhythm is picked per build: the six notes split
    3+3, 2+4 or 4+2 over the two bars, each bar's places from 16ths 0, 2, 3, 4, 6, 8, 10, 11, 12, 14, at least two
    16ths apart (also into the next pass). The rise's joining order is a fresh shuffle of ranks 0..5 (until Nov 4,
    downbeats first). The app sends `calm_phrase` as the two-bar grid: 32 slots of a step or -1, then 32 ranks;
    a slot plays when the level passes rank/6 (`tick16` slot = bar*16 + 16th). Velocity 0.2 -> 0.45. Test B
    "variety": 60 seeded builds all differ (7 starts, 60 rhythms, 56 joining orders); test_patch "7z10e". They go through `motif` (a chime at a scale step: key, register,
    chime voice, Mix level, Music in Stereo places all apply; Chimes Only When Something Happens doesn't block it).
    No [random] in the patch. Only while the drums are sounding: the beat's own output (`beat_out`) above -60 dBFS
    within the last 3 s, so it never starts the drums and stops with them. No new voice for the chimes.
  - **Development (Nov 4).** DadCraft: "calm builds sounds great, but it's getting stuck repeating the same phrase
    over and over ... it doesn't have to adhere to a particular style, but it shouldn't get stuck either."
    He chose Codex's plan: keep the sound; let the tune move. During the two-minute rise, the opening motif stays
    intact so notes still join in their original order. In full, eight-bar sections have four two-bar passes:
    motif, exact repeat, one or two changed answer notes, then another answer with an optional breath (two rests).
    Each next section develops the previous full motif: starts a step from its last note, changes inner notes,
    returns to its new opening half the time (Nov 4). The rhythm moves too: each new section keeps it, re-picks
    one bar's places or picks a new rhythm (one in three each). Reject the last 16 applied pitch sequences (rests ignored) and the last four
    section contours; a bounded random search has an exhaustive fallback. A spacious fourth pass is followed by
    a full six-note motif. No increasing sparsity, onset moves, voice/level/effect changes, or harmony/bass changes.
  - **Phrase boundary:** `calm_phrase` now fills `ct_pending_phrase`; a sounding two-bar boundary copies it into
    `ct_phrase` before its first note, clears the pending flag and emits `calm_pass <epoch>`. Phone and desktop
    acknowledge that epoch in `CalmBuild`, then prepare the next pass on their service tick. No pending phrase:
    repeat the active one. Drums stopped: do not consume pending work. Java never estimates phrase length from
    tempo or wall time. `calm_reset <epoch>` empties active/pending tables and invalidates old callbacks on off,
    reset, a new build and the completed fade. Live scale-size changes prepare a fresh motif with a new epoch
    without restarting the rise/fade clock. The patch's existing bar counter (also used by the bass) is unchanged.
    Test B: 48 seeded 128-bar runs across scale lengths 5/6/7, max three equal consecutive pitch sequences,
    four distinct per rolling 32 bars (rests ignored), contour change by 16 bars, range/count/fullness and lifecycle.
    Test_patch "7z10": exact mid-pass update ordering, missing pending work, reset, rests, rank gate in both
    directions, tempo changes and drums stop/restart. The five-minute render uses the real Java/Pd handshake;
    `~/Downloads/calm-development-clips/` (README: settings/timeline). It includes quiet wait, full build,
    sustained playing and the fade, no normalization or microphone recording. Peak 0.3956, no Pd errors;
    full-level applied phrases: longest equal pitch run 2, minimum 12 distinct per rolling 32 bars, ignoring rests.
    Full tier passed (201 patch sections + all 12 other suites, 576 s); Dev build passed. DadCraft's listen
    decides whether the motion feels free enough. Not installed yet.
  - **The bass (Nov 4, DadCraft: "lets start on the bass for Calm Builds"; `add_calm_bass` in `calm.py`).** Built
    before his first Calm listen, at his call. In a quiet room the held bass is silent (Pad Only With a Steady Sound,
    on by default, keeps it out), so the tune has its own: a plucked low note (the held bass's tone: 2nd and 3rd
    harmonics for earbuds, 65-131 Hz, lop~ 700; 10 ms up, down over 1.4 s, squared) on the chord's root at each
    bar's downbeat, and its fifth (4 steps of 7, 3 of a pentatonic) on 16th 10 of the second bar. It joins once
    `calm_tune` passes `CALM_BASS_FROM` 0.4 (48 s into the rise), downbeat first, all three notes by 1.0; velocity
    0.5 -> 1. In the 15 s fade it leaves first (by 9 s in). It reads the grid 5 ms late, since the chord changes on
    the same tick16 0. Mix bass trim and Pad level apply, like the held bass. While it plays the held bass makes way
    (x 1 - its level, only while the drums sound), so two basses never stack when Pad Only With a Steady Sound is
    off. Level: `CALM_BASS_GAIN` 0.16 puts it within 0.1 dB of the held bass (40-300 Hz power over 9.6 s, drums
    subtracted). Test_patch "7z10": joins downbeats first, fifth as the pickup, stops with the drums, the held bass
    makes way and comes back, heard in the low band, silent at Pad 0. Not in the composer path (Evolving's own bass
    notes go straight to the held bass; the Calm bass follows the chord root). Clips: `~/Downloads/calm-bass-clips/`.
  - **Moto CPU (Nov 3, Ouralia Dev playing, Music + Music in Stereo on, `top` 30 x 2 s):** Calm Builds on median
    56.25% of a core, off 58.5%: no measurable cost.
  - The patch's `calm_chimes` receiver stays (the tests use it to make standing-still chimes come); the app no
    longer sends it.
- **Call and response** (roadmap 12, Oct 14; `CallResponse.java`, test L; `music_answer` Music 0..1 default 0.5, in
  presets): the Harmony tracker (now also run for it: `fpc_run` includes music_answer) sends `heard_deg <step>` at each
  note-on and `heard_off` at note-off; a phrase = notes with pauses < 1.5 s (max 6); after `ANSWER_AFTER_S` 1.6 s of
  quiet (DadCraft: 0.7 was "answering too soon") it is answered once (cooldown 3 s) through the motif path (`motif
  <step> <vel>`, velocity 0.55 x level): same rhythm, +2 steps (-2 if it reached step 14), 40% the last note on the key
  note's octave. The "Hearing:" harmony count only counts while Harmony > 0. Tests set music_answer 0 unless testing it.
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
  **Soft Steps** (`walk_ease`, Oct 21, first called "Walking notes ease in"; the default since Oct 21, DadCraft: "just be
  the default", still "quite loud when they reach their full volume", so ~3 dB softer again, then "just about right"; DadCraft: chimes become "extremely loud on walking", glide
  to where they should be): the footstep note's loudness was (0.4 + 0.6 pace) x (0.8 + 0.4 strength), up to 1.2, at
  full from the first step (the pace is one step's interval), where the standing chimes reach ~0.6-0.8. Eased:
  (0.18 + 0.21 pace) x (0.9 + 0.2 strength) (first 0.25 + 0.3 pace), the pace it follows moving 30% of the way to each new step's, and a walk
  starting at 35%, full after 8 steps (both reset when the walk ends). Measured (test "walking notes ease in"): brisk
  0.063 vs 0.156 (-8 dB, first -5 dB), the first 2 s 0.017 vs 0.156, a stroll 0.013. Patterns and rests still use the raw pace.
  **The Band Plays** (`band_plays`, opt-in, Oct 21; DadCraft: the band could "dynamically get busier within measures
  and scale back down, like a natural build and flow of a song"; he heard that on a walk, but "there could be more
  activity while in a calm room"): 8-bar phrases on the rhythm's bar count. `rh_band` moves the drive (the one number
  behind the drum picks, extra kicks, ghost snares, hat length, Melody rhythm cells) from -0.15 at a phrase's first
  bar to +0.25 at its last (power 1.6), plus a per-phrase lift of -0.06/0/+0.06; the last bar ends in the snare fill
  (`rh_bfill`, when the drive > 0.2); standing still the drive's floor is 0.3 (was 0.1 in a quiet room). The
  standing-still chimes get `bandch` x BAND_CHIME_P (30 in 1000 per 0.25 s, x0.5 at a phrase's start to x1.5 at its
  end). Levels untouched: busier, not louder. Two catches, found by the test: patterns are held 2 bars and picked at
  odd bar counts, so a phrase starts on a picking bar (else bar 1 kept bar 8's busy pattern); and the pick reads the
  drive in the same message as the bar change, so the new `rh_band` re-runs `rh_dg_i` at once (a `[value]` set by a
  float outputs nothing: the bang comes from a trigger). Measured (test "The Band Plays", quiet room): 4.3 hits a
  second vs 3.4; hits per phrase bar 58 51 45 43 56 61 99 104; 10 chimes in 120 s vs 1 in 60 s.
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
  **Found pad** (DadCraft, Oct 12; `add_found_pad`; "Pad sounds" `pad_src` Synth / Found (default) / Both, Music,
  noPreset; the found bass, "Bass sound" `bass_src`, was REMOVED Nov 4, see "Bass Voice"): when the Harmony tracker hears a steady pitch
  (its note-on), a `FP_TAKE_S` (0.6 s) take from `FP_PREROLL_MS` (180) earlier is recorded from `hv_buf` (group
  `fprec`) and kept if the note is still held at its end, with its pitch (`fpad_f0`), a peak gain (`fpad_g`), in
  `FP_SLOTS` (2) round-robin tables `fpad_<n>` (`fpad_n`: count, newest); never in a pocket. Each pad set
  (`fpad<k>` groups, 4 voices `fp<k><i>_*`) takes the newest take when it takes its chord; each voice is two
  `FP_GRAIN_MS` (120) grains, a new one every half grain, starting a whole number of the take's periods from where
  the playing grain is (so a steady note stays steady), wandering in `FP_LOOP_S`, rate = chord note / take pitch
  folded within a tritone (`FP_GAIN` 0.075: as loud as the synth pad). Each set's sound (synth or found, `fpw_m`)
  is latched at its chord change (a new take joins at the next one; a fading set is never cut; a Pad sounds change
  re-latches at once); the switches (`fpw_*`: active x register != Low x mode) run only what is heard, so with Pad
  register Low no pad set runs. The tracker and detection now run while Harmony is up OR the found pad wants takes
  (`harm_run`; the voice harmony still only while Harmony > 0). The event log says "Kept a held note for the found
  pad (A3)".
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
  **Chimes Only When Something Happens** (`chime_answer`, was "Chimes Only Answer", Music, default off, saved in presets; Oct 30, reactive-first step 2, branch
  `reactive`). DadCraft's listening baseline (Singing Room alone, then chimes, then the pad): Singing Room "doesn't
  play by itself without something happening", but with the chimes "a chime played in a quiet space with nothing
  causing it". Two uncaused sources: the still pattern (path 2, `mu_p`: its chance follows the room's loudness, plus
  calm build and The Band, so a room a little above its floor still rolls one now and then) and motif memory (made to
  come back after 8 s without chimes). On: `mu_still_ans` closes the still pattern after `mu_still_on`, so every way
  it rolls (free, the 8ths, Melody rhythm) stays silent, and the app (phone and desktop) drops motif comebacks.
  Sudden-sound chimes, Call and Response answers and walking notes are unchanged. Pd test "Chimes Only Answer"
  (steady noise after a faint room, calm chimes up: 161 still chimes off, 0 on, 0 synced; a burst still chimes).
  Levels at the same three knocks (dry off): Singing Room Ring rms -45.4 dBFS, Found -46.2; chimes at Notes and Chimes
  0.4 -39.7 (about 6 dB louder, DadCraft: "they seem quite loud"), at 0.2 -45.0. Default not changed; his verdict.
  DadCraft's A/B in Ouralia Dev (Oct 30): on, every chime had a cause ("Yes"); at 0.2 "when it's quiet they sound
  fine"; "we can keep it, but it should be clear what it does": shown as "Chimes Only When Something Happens" since.
  **Pad Only With a Steady Sound** (`pad_room`, Music, default off, saved in presets; Oct 30, reactive-first, branch
  `reactive`). DadCraft's baseline: with the pad, "it's a constant musical sound/tone" (the pad's room factor
  `mu_pad_fac` is 0.5 + 0.5 x loudness: never below half). On: the pad (`mu_pad_mid`: synth and found voices) is multiplied by a hold `mphold` that heads, ten times a second, toward the room's loudness above
  its quiet (`mloud01`, before Ear for the Room; PAD_ROOM_MIN 0.03, x PAD_ROOM_GAIN 2.5, so ~13 dB above the quiet is
  full), in straight lines: silence to full over PAD_ROOM_UP_S 1.5 s, full to silence over PAD_ROOM_DOWN_S 4 s
  (the first try was exponential and still whispered at -56 dB ten seconds after a hum). Chords and their changes
  are unchanged. The room's quiet (the floor) still rises 0.3 dB/s under a long steady sound, so a hum that lasts
  minutes slowly becomes the room and the pad fades: to be heard. Pd test "Pad Only With a Steady Sound": quiet
  -140 dB (off -37), three knocks -65, a held 150 Hz hum -34 (off -33), 5 s after it -140.
  DadCraft's A/B (Oct 30), first through Ouralia Dev's leftover Future bass kit ("the pulsing pad sound"); on Kit
  Classic: "it feels like it's answering my tones better now when i ahhhhhh or hum", "the bass still comes in and
  just kind of feels like it always has", fades "fine". He chose "Pad without bass": while on, the bass (`ba_out`)
  stays out (`mu_pr_blp`); the pad alone follows the hold. Test: under the hum, Pad register Low (bass only) -140.
  **Notes Only for Bigger Sounds** (`note_big`, Music, 0-30 dB, 0 = off by default, saved in presets; Oct 30). DadCraft:
  typing "just sounds like it's random notes responding with no musical semblance at all"; he chose: small clicks
  become rhythm, only bigger sounds get a note. A sudden sound gets a chime (`mu_nb_chk`) or a Singing Room found bell
  (`rnf_chk`) only if the level `mlev` (mic_level, ~30 a second, now stored before the onset reads it) is this many dB
  above the room's quiet (`mfloor`, held within 46-58). The found bells' detector hears 5 ms readings, ahead of
  `mlev`: with note_big on it checks 40 ms later (first try read the level before the knock and dropped knocks'
  bells). Small sounds still get caught for the found drums and the learned groove, so with the beat on they turn
  into rhythm. Measured (8 taps in a faint room): clicks get notes up to ~20 dB and none at 25, knocks and claps keep
  theirs at 30, so it is a slider to set by ear. Pd test "Notes Only for Bigger Sounds" (at 25 dB: clicks 0 chimes 0
  bells, knocks 5 and 5; off, clicks 5 and 8). `s rnf_note` reports each found bell for the tests.
  DadCraft's A/B (Oct 30): at 0 typing chimes; "not hearing any chimes when the slider is at 20.55. keep it. knocks
  and louder noises still get a chime." Default stays 0 until he says.
  **Chime Voice** (`chime_voice`, Music, Classic by default, saved in presets; Oct 31, branch `reactive`). DadCraft
  (Oct 30) on Classic: "more chime or bell like, but they also sound like what i'd call a musical bubble pop". Read:
  Classic is harmonic sines (1x, 2x, 3x), 4 ms up, one straight fade for all. Clips (numpy, level-matched on each
  note's first 300 ms): classic, classic with exponential fades, glass bar, small bell; he leaned to the glass bar
  "with a slightly softer attack", then of three softer ones (6 ms rise, softer mallet, both): "both sounds best".
  Glass (GLASS_* in constants.py): partials 1, 2.76, 5.40, 8.93 (a free bar's), levels 1, 0.24+0.6b, 0.072+0.18b,
  0.017 (b = the chime's random brightness), each fading to -60 dB over 1, 0.35, 0.15, 0.07 of the ring
  (GLASS_RING_X 1.2 x Classic's), a 6 ms rise, x1.612 to match Classic. Built from plain objects (no expr~): one
  vline~ fade in dB below full, scaled per partial, +100, dbtorms~; a second vline~ is the rise and level. The
  fundamental is Classic's own oscillator. With Classic the glass lines never start and Classic's sum is x1:
  check_classic_parity against 1f938ec 55/55 sample-identical. Its signal objects join the voice's switch~ group
  (left outside, they made a DSP loop: silence). Walking notes share the voices, so they change too; found notes
  don't. Pd test "Chime Voice Glass" (E4 328 Hz: strike +0.2 dB against Classic, 2.76x partial -12 dB vs -83,
  -35 dB 0.4 s on). DadCraft's A/B (Oct 31): "Glass sounds great. can we keep both options and have a Both option
  between them?" Now Classic / Both / Glass (0/1/2; Dev's stored Glass, 1, reads as Both): Classic x (1 - v/2),
  Glass x v/2, so Both is half each on the shared fundamental.
  **Bass Voice** (`bass_voice`, Music, Synth by default, saved in presets; Nov 4, `sound-kinds`). DadCraft: "can we
  keep the normal synth bass but add those earlier low glass bowls as another bass option?" (both basses, his pick)
  "i forgot to mention that we should remove Found Sounds from Bass options. I don't think Found Sounds work well in
  that area." So Bass Voice replaces Bass Sound (`bass_src`; the found bass's `fb_*` voice, its `fbass` group,
  `FB_GAIN` and its test are gone; stored `bass_src` values are simply ignored). Glass Bowl is the Pad Voice's bowl
  (a pure tone, a twin `BASS_BOWL_BEAT_HZ` 0.31 Hz above at `PAD_BOWL_TWIN_LEVEL`, the octave at `PAD_BOWL_OCTAVE`)
  at `BASS_BOWL_UP` 2 x the bass note, 131-262 Hz: the register of the first, lower Glass Bowl pad (0c8bedd, before
  the octave up), and a pure tone at 65 Hz would be lost in earbuds. The held bass: each new note eases in over
  `PAD_BOWL_SWELL_MS` 4 s (on top of the bass's own dip and rise). Calm Builds' bass: each note eases in over
  `CALM_BOWL_RISE_MS` 300 and rings down over `CALM_BOWL_RING_MS` 2.6 s into the next, no pluck. The tones
  (`ba_bw_*`, `ba_cbw_*`) are their own switch group `bbowl` (Music on x Bass Voice), so they take no signal from
  the music's group; switching crossfades over 300 ms. Levels by RMS: `BASS_BOWL_LEVEL` 0.79 matches the synth held
  bass over a minute (pad register Low), `CALM_BOWL_LEVEL` 0.46 matches the Calm pluck's 30-600 Hz power (drums
  near silent). Test "Bass Voice (Nov 4)": the bowl an octave up, nothing an octave below, about as loud. Clips:
  `~/Downloads/bass-voice-clips/`. DadCraft (Nov 4): "Bass voice is also working well."
  Test fallout, worth knowing: removing the found bass dropped a [random], so `fp_seedkeep` holds its place (every
  later [random] keeps its sequence; without it birds-lift and Chime character failed). The bowl's new DSP objects
  re-sort the graph, which moves some one-block delays and so event timing; three tests that had leaned on a lucky
  random path were made robust: `fresh(_listen=...)` hears a chord picked during the settle (patterns made by rules),
  the arrangement run is 72 bars, not 36, and the Calm bass check plays the drums barely audible.
  **Pad Voice** (`pad_voice`, Music, Synth by default, saved in presets; Nov 4, branch `sound-kinds`; DadCraft, Nov 4: "Pad Voice can stay on Synth as default"). DadCraft: "can
  we make glass bowl option for the pads? i saw something on tv last night and heard a person on that program using
  glass bowls in a sort of "sound healing" kind of setting ... it might even compliment the glass bells sounds we're
  already using." Glass Bowl only changes the Synth pad (Pad Sounds Synth or Both).
  **Third version, shaped on recordings** (DadCraft: "there really isn't a stike, just a soft attack/easeing inkind of
  start ... are there samples you could find to help inform what they should sound like?"). Measured (preview MP3s
  from Freesound, `bowl_measure.py` in the session scratchpad: rise, partials, beat of the fundamental's band):
  juskiddink 129219 (14" frosted quartz, rubbed with a leather mallet, CC BY 4.0): rise 5->90% 54 s, nothing above -45
  dB (2x -56, 2.54x -53, 4.63x -59), beat 0.38 Hz 86% deep; easy_thunder 264181 (12" C# drone, CC BY): rise 47 s, 2x
  -38, 3x -57, beat 0.56 Hz full depth; Asuriya 530848 (12" pure tone, CC0): nothing above -44 dB, beat 0.15 Hz full
  depth. (caiogracco's G4/A4, CC0, 7-8 s: too short and noisy to read.) So a rubbed crystal bowl is almost a pure tone,
  a very deep slow swell and dip, and a slow rise; no strike, and no 2.71x mode (that is a metal bowl's, or the glass
  bar's). Now each note: Synth's first tone (shared), a twin `PAD_BOWL_BEATS_HZ` above it (0.23, 0.38, 0.29, 0.53 Hz:
  each bowl wobbles at its own pace) at 0.9 (dips ~25 dB), the octave at 0.012 (-38 dB); each note swells in from
  silence over 4 s when its set takes a chord, 0.7 s after the one before (the recordings' 50 s rise is one held note;
  6 s plus 1.5 s apart left the moving pad 10-15 dB under Synth, and a squared swell left a gap at each chord change).
  Measured the same way, ours: beat 0.19 Hz full depth, octave -37 dB, nothing at 2.71x. Level x1.15: Synth's
  loudness over a minute (C, F, A). **Earlier versions:** first, a beating hum with a 2.71x mode, measured nearly the
  same as Synth, heard through wrong clips (Pad and Melody Sounds left on Found, voices as the mic: "like maybe
  children or babies"); second, a mallet strike on each chord change with 2.71x and 5.15x modes ("that's not what i
  hear when i've heard glass bowls").
  Switching crossfades over 300 ms. The bowl's oscillators are their own switch~ groups (`bowl0/1`), on only while
  Glass Bowl is chosen and that set sounds (`pad_voice` x `sw_pads<k>`), so Synth costs nothing new; each set ramps
  its gains and swells inside its own group (one shared ramp in the music's group made a DSP loop: silence, the Chime
  Voice lesson again). Synth sample-identical to before over the first 5 s (after that the music varies run to run,
  the old patch against itself too). Pd test 10u: tones in key, none at 2.71x, eases in (0.14 of Synth at 0.4-0.9 s,
  1.05 at 5-6.5 s), asleep with Synth, awake with Glass Bowl, switching smooth. Clips `~/Downloads/glass-bowl-clips/`
  (40 s, all sources Synth, a quiet room with soft knocks as the mic, one gain for all): 1 Synth, 2 Glass Bowl, 3
  Synth + Glass chimes, 4 Glass Bowl + Glass chimes. DadCraft: "its deeper than i expected but i guess it's ok";
  asked whether lower notes or a deeper wobble, "higher notes, try it an octave up". So the bowls now sound
  `PAD_BOWL_UP` (2) x the pad's notes (C4-C5 for a C3-C4 chord), with their own tone oscillator (o5, no longer Synth's
  o1); the twin keeps its Hz offset (as the recordings: the wobble rate is the bowl's, not a ratio). Level kept at
  x1.15 (0.7 dB under Synth by RMS, louder to the ear an octave up). Clips re-rendered. Waiting on his listen.
  **Motif memory removed (Oct 31):** with Chimes Only When Something Happens fixed its comebacks never play, so
  MotifMemory.java and its tests are gone; its Note type moved to CallResponse. The patch's `chime_note` report stays
  (the tests count chimes with it).
  **Trailing Melody** (`chime_trail`, Music, Off / 1-5 notes, Off by default, saved in presets; Oct 31, branch
  `reactive`). DadCraft: "the chimes/bells we just worked on should continue for a bit after they've stopped
  receiving stimuli, so there's a little trailing melody." A sudden-sound chime standing still (`mu_q_now`; walking
  has its own arpeggio) starts a tail (`tr_*`): that many notes through `mu_deg`, so each walks a step or two on like
  any chime; TRAIL_GAP_MS 450 to the first, each gap x TRAIL_SLOW 1.3, each note x TRAIL_VEL 0.6. The next sudden
  sound stops the tail and starts its own. No new [random], so with it Off every test and Classic is unchanged
  (check_classic_parity 55/55). Pd test "Trailing Melody" (3: notes at 0.02, 0.46, 1.04, 1.80 s).
  **Left to the user, with a mover (DadCraft, Nov 2):** "i can't make up my mind between 3 or 5, which tells me
  something: that it should be left as is and users can determine which amount they like"; Off stays the default.
  Then: "that should have a Movers, to allow for random slides up and down the amount scale, just to add a touch of
  flux to it." Dropdowns had no movers; `Param.movingChoice()` gives one its mover (Java only, `Modulation.moved`):
  it swings within `rand(1, 5)` notes, Pd's `int($f1+0.5)` takes the count when each chime starts, and Off stays
  off (the mover never switches the tail on). Java test M "Trailing Melody has a mover".
  **Fixed, no switch (DadCraft, Oct 31):** "rather than make them switches on by default, lets make those the default
  behavior period, no switch." Then for the notes: "we should set the 20 db threshold on the slider, but users should
  have the ability to move it as they need or want, since each manufacturer could set mic values differently." So
  `chime_answer` and `pad_room` left Params.java (old saves and presets ignore their keys) and are 1 in the patch's
  own defaults, kept there only so the tests can pin the old sound (0, in test_patch `fresh()`); `note_big` stays a
  slider, default 20.5 dB (his setting; clicks still got notes at ~20), patch default the same. Existing installs
  keep a stored value. Motif comebacks are gone from the app (phone and
  desktop no longer listen to `chime_note`); MotifMemory remains for its Note type, shared with Call and Response.
  DadCraft (Oct 30), on the way: the found sound on the rhythm grid as the drums, with his effects, he has "absolutely
  loved from the day we got it worked out": keep it as it is.
  **Key drift** (`music_drift`, Music, default on, noPreset; roadmap 12): every 3-6 min while the music plays the
  home chord moves to a related one on the same notes (`value mdrift` scale steps added to each pad pick, mod n), 60%
  of the time back home when away. Allowed homes per scale (`drift_options`): every pool chord moved there must not be
  diminished (C major: C, D, G, A minor feel; minor: 0, 2, 3, 6). No contrast chord while drifted; the bass's home
  note follows. A scale change resets it; switching it off brings home back at once. `drift_now` (tests) drifts now.
  Pattern picker fix: a repeated pattern now moves toward the pace's own pattern (it used to bump upward, so a stroll
  sometimes got a mid-level pattern).
  **Music variety** (`music_var`, Music, Classic (default) / Evolving, noPreset, `.dcs()`; Oct 21; `dcs_composer`,
  `add_composer`; Pd tests "music variety", test_dcs "dcs_composer"). DadCraft, after a long time with E minor
  pentatonic and a kit he likes: "the band keeps playing much of the same things ... it shouldn't feel stagnant",
  and anyone who settles on a key and scale will hit it. Why, from the code: Classic's chord is brightness x (pool-1)
  + jitter every 7 s, memoryless, so a steady room circles 2-3 neighbouring chords; pentatonic "chords" (every other
  pentatonic note) are nearly all open sus/quartal shapes; the chimes walk within CHIME_SPAN of the brightness note;
  the arpeggio, melody cells and drums pick from short tables by pace or room; nothing changes over minutes except
  the drift. Stage 1 (this): [dcs_composer] (C, control only) plays progressions: per mode (the scale's own, or for
  the pentatonics the full scale they come from: major / natural minor) a weighted move table over every chord but
  the diminished one, each mode's colour chord favoured (Dorian IV, Mixolydian bVII, Lydian II); whole tone has its
  two augmented chords. Four-chord phrases (first chord, two moves, the ending; the third chord weighted by how well
  it leads to the ending). Sections: Home (I .. I, 6.5 s a chord, bass on the root 65%), Lift (the mode's lifting
  chord .. an open ending, 4.5 s, melody +2 steps, bass on roots), Calm (vi-ish .. home, 9 s, melody -2, bass on
  the home note, spread voicing), Turn (away .. home or vi, 5.5 s, +1). Two phrases a section (three for a third of
  the others); the second is often the first again, else one chord changed; a section that comes back brings its
  phrase with a chord changed. Home comes back after another (70%), else another away section; which one leans on
  Intensity (Lift) and quiet (Calm). Chords change on the bar (tick16 0, chord bars = the section's seconds in
  whole bars 1-4, a cadence held a bar longer 35%); a watchdog keeps bar time from the tempo when no bar comes. The
  same chord again is held, not re-struck. Voicing: the triad's inversion and octave closest to the last (bottom
  note -2..5 from the key note, top <= 19), the root doubled an octave up. Out: anchor (the scale step whose stacked
  triad shares the most with the chord, its root first: for mchord, room notes, harmony, phrases, arpeggio), bass
  (0-11), four pad notes (semitones from the key note). The patch: the anchor goes into mu_pad_root_v (slot flip,
  mchord, pad_chord), whose outlets then bang the held notes through mtof into each voice's `mu_pad<i>_hz` (where
  Classic's degree_to_hz now arrives through a spigot) and the bass into ba_fold; so the found pad, the
  vocoder's chord and the bass all follow. Classic's picker (`mu_chord_go`, now a spigot before `mu_chord_go_t`)
  is closed while Evolving plays; switching back strikes Classic's chord at once. The section's register goes to
  `mu_sreg_add` (after the sensors' register; 0 in Classic). Random seed: time at creation (`comp_seed` for tests).
  The app logs "Music section: <name>". Measured (test_dcs, 400 bars each scale): 112-135 chords, 5-6 different (2
  in whole tone), a voice moves 1.2-1.6 semitones a chord on average; E minor pentatonic borrows F# or C in 59%
  of chords; through the patch the pad stays in E natural minor. Stage 2 (melodies that develop) and 3 (rules-made
  rhythms): ROADMAP 21. DadCraft, Oct 21: "it feels familiar without being something I can expect to play in a
  certain way. I like it." He kept it opt-in for now.
  **Melodies that develop** (`mel_dev`, Music toggle, default off, noPreset, `.dcs()`; Oct 21, ROADMAP 21 stage 2;
  `dcs_composer` `note`; test_dcs "melody", Pd test "melodies that develop"). Every chime (sudden sound, standing
  still, rhythm cell) took its pitch in `mu_pick`: a random +-1/2 step from the last, near the brightness note. With
  the switch on, `mu_pick`'s output is shut (`mu_dev_cl`) and the same bang asks the composer `note <middle> <mchord>
  <scale_n>` (middle = mu_pick's own centre, int(mbright*2n+0.5)+n); its third outlet goes into `mu_pick_ph`, so
  Melody phrases (if on) still win, and the sensors' and the section's registers still apply after. Works with
  either Music variety (the sections run unheard in Classic; mchord is then Classic's chord). In C: each kind of
  section has a motif (3-6 notes, mostly steps, a skip, now and then a repeat or a leap of 3, within +-4, direction
  turning after two steps or by 35% chance). Notes play it in statements: round one = the motif, again or answered
  (its last note on the chord's root), developed, the motif; later rounds = the motif, developed, developed,
  answered. Developed = moved +-1/2 steps (30%), upside down (20%), backwards (10%: steps reversed and turned over),
  cut short (20%), carried one step further (20%). A statement starts on the chord tone nearest middle + drift; moves
  change the drift (within ROAM 4), the theme's return halves it (round one: back to 0). A pause of GAP_MS 2.2 s ends
  a statement; notes past it fold by octaves to within middle +-8. A section that returns brings its motif back
  with one note moved a step (60%). Reports: `comp_form` (form, first note), `comp_note`. Measured (test_dcs, 40
  bursts): the motif came back 13 times identical, all 8 forms used, statements started on 5 different notes; 60
  bursts across sections: 10 different motifs. The walking arpeggio is untouched (stage 3).
  **Patterns made by rules** (`pat_gen`, Music toggle, default off, noPreset, `.dcs()`; Oct 21, ROADMAP 21 stage 3;
  `dcs_composer` `drums`/`arp`; test_dcs "patterns", Pd test "patterns made by rules"). Before: the drums pick per
  lane from KICK/SNARE/HAT_PATS (5 each, + 5 breakbeat) every RH_HOLD_BARS by the drive; the walking arpeggio from
  5 ARP_PATTERNS each bar by the pace. Now each table has one more row (rh_kpat/rh_spat row 10, rh_hpat row 5,
  arp_pat row 5, starting as a middle pattern) and `value rgen` points the players at it (`rh_kp_bb`/`rh_sp_bb`/
  `rh_hp_g`, `mu_pat_idx`); fills, ghosts, drop-outs, chops, the learned groove, breaks and the arpeggio's rests
  still layer on top. Drums: at each bar's tick 15 (`rh_bar_sel`) the composer gets `drums <rh_drive> <rh_bbv>` and
  sends three "drum <lane> <16 velocities>" lists, written in with `list prepend` into the tables. Its bar: kicks
  2 + 3d evenly spread (k in 16, rotation 0: the downbeat; 1.0 / beat 3 0.85 / others 0.65), a breakbeat leaning
  the middle kick to step 10 (half the time when rh_bbv > 0.3); snare on 12 (d < 0.2) or 4 and 12, 0..3 ghosts
  (0.3) on 7/9/10/14/15; hats 4 + 8d evenly spread (four of them sometimes on the off-beats), accents by beat, an
  open hat on 14 (d > 0.5, half the time). d = drive + the section's lean (Lift +0.15, Calm -0.25, Turn +0.05). The
  section's base bar is made once (again if d moves by 0.15); each bar 70% one change (a weak kick moves 1-2 steps,
  a ghost comes or goes, an off-beat hat comes or goes or opens), every 4th bar the base again. Walking: at each
  walking bar's first step (`mu_pat_bar`) `arp <pace> <scale_n>` -> "arp <8 steps>" into arp_pat row 5: chord tones
  0..3+2pace up (tone t = (t%3)*2 + (t/3)*n steps above the chord root) as rise-and-fall, rising, falling, broken
  or around the root, a passing note (+1) on a weak step 25%; each bar 60% one note (not the first) moves to a
  neighbouring chord tone; every 4th bar the base, bar 7 of 8 the base backwards (and bar 8 varies that). A section
  that comes back brings its base patterns back; a new pace (by 0.3) or scale makes a new walking base.
  Measured (test_dcs, 24 bars at drive 0.5): every bar kept the downbeat and backbeat, bars changed 0-2 hits from
  the last; drive 0.05 -> 0.95: kicks 2 -> 5, hats 4 -> 12; pace 0 -> 1: the figure's top 5 -> 9 steps.
  With Melody rhythm on, its bar too (`cell <rh_drive> <mrhy>` at the bar's tick 0, `mr_bar0`, before the first
  onset is read; "cell <16 lengths> <count> <last onset>" into mel_cells / mel_cn / mel_lo row 28 = 2 x (straight +
  dotted) rows; `mr_pick` takes it while rgen, `mr_pick2`'s never-twice skipped). Straight: 1, 2, 4, 6 or 8 notes
  by drive spread on the 8ths (3 on the 8ths would be 3+3+2, a dotted feel); dotted: 3, 5 or 7 spread on the 16ths
  (6+5+5, 4+3+3+3+3 ..), a quarter of the time starting an 8th late; mixed: one of the two per section. The
  section's lean on the drive as for the drums; each bar 60% one change (a note but the first moves a grid step, a
  long note splits, two merge), every 4th bar the base. Melody phrases' A' still takes "A's cell": the same row,
  now as rewritten (DadCraft, Oct 21, with Melody phrases on: "it all sounds good"). Measured (24 bars): drive 0.1/0.5/0.9 -> straight 1/4/8 notes,
  dotted 3/5/7; 6-12 of 23 bars changed.
  **New pieces** (`new_pieces`, Music toggle, default off, in The Band's presets, `.dcs()`; Oct 29; composer
  `renew`; test_dcs "new pieces"). Evolving only. Why: DadCraft's first Session Log sessions were one piece for
  18 minutes (a new piece only came with a scale change; `comp_seed` has no sender), and "the melody, progression,
  flatness didn't really feel like it changed all that much". He asked for a blend, "kind of like how a DJ would
  overlap", not a cold cut. After `PIECE_BARS` (64) bars, then 64-96 per piece, the next section's last phrase is
  a lead-in: it ends on the mode's open chord, and in that chord's bars the drums drop to the hats (kick and snare
  silent). Then the new piece: its own sections (Home first, all phrases new) under the old piece's groove (drums,
  walking notes, melody rhythm keep their section) and motif. A phrase later the groove is made new, a phrase after
  that the motifs. With it on, Home comes between half the sections (7 in 10 without). The first piece is a fixed
  64 bars so a seed's random numbers stay as before. Measured: 4 drops in 500 bars at 100 bpm (about one every
  5 minutes), none with it off. DadCraft's first listen (Oct 29, 17.7 min with it on): "it's sounding better".
  Not yet in the Session Log: an event at each handover (so a mark can be lined up with it).
  **Chord colours** (`chord_color`, Music toggle, default off, noPreset, `.dcs()`; Oct 21; composer `color`; test_dcs
  "chord colours"). Evolving only. After voicing, per chord by section (percent seventh / added ninth / sus; Home
  25/10/10, Lift 20/10/30, Calm 30/40/0, Turn 35/10/15): the seventh (degree + 6 of the chords' mode) replaces the
  doubled root, just below it; the ninth (degree + 1) goes an octave and a step above the root; sus4 (if the fourth
  is perfect, else sus2; sus2 a third of the time when both work) moves the third and resolves at the next bar
  (`bar`: the third back, the chord sent again; a one-bar chord moves on unresolved). Whole tone: none. Measured
  (400 bars): major 46/159 chords with a 7th or 9th and 26 sus (all resolved), minor pentatonic 47/146 and 17,
  Dorian 51/151 and 19; every note in the chords' scale.
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

## Kit removal (Oct 30, reactive)

DadCraft: "remove the kits"; "leave Classic the way it is, no change to accoustic". The built-in Pd chimes,
pad and bass keep their existing voices; Acoustic and every other kit are removed. Synth / Found / Both keep
their labels and defaults. The Classic and Found sums now feed the same filters and levels directly, with no
kit shares, layers or pump. Pad Only With a Steady Sound still gates the complete pad sum and keeps bass out;
Chimes Only When Something Happens and Notes Only for Bigger Sounds are unchanged. The frozen composer
features use the same Classic path and no kit settings. The vocoder still receives the melody's frequency and
all four chord notes. See dcs-objects "Kits: REMOVED Oct 30" for removal scope and status-log for checks.

## Removed and restored, Nov 1

Song Shape, Chord Colours, New Pieces, The Band Plays and Wandering Home were removed (Ouralia 1277104; DadCraft: "if
the room or environment doesn't drive it, then lets take them out"), then restored the same day, all five (DadCraft:
"no no no, i want that stuff in the drums. can we add that code back in?"; asked which, "Everything removed").
Then (Nov 1) made the fixed behaviour, switches removed: DadCraft, "can we make those being on the default and remove
the switches? i feel like what they bring makes the sound we're currently getting a bit more alive." The patch's own
defaults turn all five on (patchgen DEFAULTS); the app no longer has the settings, so stored values and presets that
name them are ignored. test_patch pins the old values in `fresh()` so the existing checks keep comparing like for
like, and checks the five defaults are on. Their entries above describe what each does.

