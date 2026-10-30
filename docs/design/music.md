# The Music (design notes)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

- **Mix** (DadCraft, Oct 13: "every musical and rhythmic element should either have a volume slider or on/off
  switch"): section `mix` on the Music tab, in presets (DadCraft: shared with a preset), a Realm ingredient (`Worlds.GROUPS`
  "mix"). It holds the existing beat_snare, beat_hats, beat_glitch, music_pad, music_harm (moved from Rhythm/Music,
  ids unchanged) and new trims 0-1.5 (default 1 = as before, noRandom, NOT resetWhenAbsent so older presets and the
  built-in Realms leave them alone): `mix_kick` (synth kick and found kick; the found one is `fs_ks_kick`, named into
  the fsound group: outside it the trim made a DSP loop between the toplevel and that group), `mix_bass` (synth +
  found bass, `ba_sm`), `mix_chimes` / `mix_walk` (the chimes and walking notes share the 4 voices, so the trims scale
  each note's velocity: chimes at `mu_deg` and motif replays, the arpeggio writes its own trimmed velocity on each
  note from `apvel` (`mu_arp_t` outlet 3; before, an arpeggio note after a chime played at the chime's velocity) and
  the groove note), `mix_ring` / `mix_bells` (`rn_out` / `rnf_out`). At 0 a part is silent but still runs (no extra
  switch~ factors). Turning Auto realms on asks first ("Let Realms change your sound?": they set the sliders from
  their own preset, the mix too; "What it changes" keeps a part).
- **Calm builds** (roadmap item 7, Oct 14; `CalmBuild.java`, test B; `calm_build` toggle in Music, off by default,
  noPd; in presets since Oct 21, see "How the band plays in presets"): calm = switch on, music on, not walking, room not
  loud-and-lively. After `WAIT_S` 30 s of calm, the level rises over 180 s, holds 60, falls over 120, then rests 180
  before another; not calm -> it fades within `DROP_S` 5 s and waits for 30 s of calm again. Layers join along the
  rise (pad from 0, room notes from 0.25, standing-still chimes from 0.5, harmony and reverb from 0.75) and each is
  lifted up to +40% at the top in `Modulation.output` after Intensity (only what is on, never past the slider's top;
  resent on a change > 0.005). The chimes layer goes to Pd as `calm_chimes`: an extra still-pattern chance of
  `CALM_CHIME_P` (40 in 1000 per 0.25 s at the top, about one soft chime every ~6 s in a quiet room). Event log
  "Calm build starts/ends"; the Now line says "building".
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
  **Found pad / bass** (DadCraft, Oct 12; `add_found_pad`; "Pad sounds" `pad_src` Synth / Found (default) / Both,
  "Bass sound" `bass_src` Synth (default) / Found, Music, noPreset): when the Harmony tracker hears a steady pitch
  (its note-on), a `FP_TAKE_S` (0.6 s) take from `FP_PREROLL_MS` (180) earlier is recorded from `hv_buf` (group
  `fprec`) and kept if the note is still held at its end, with its pitch (`fpad_f0`), a peak gain (`fpad_g`), in
  `FP_SLOTS` (2) round-robin tables `fpad_<n>` (`fpad_n`: count, newest); never in a pocket. Each pad set
  (`fpad<k>` groups, 4 voices `fp<k><i>_*`) takes the newest take when it takes its chord; each voice is two
  `FP_GRAIN_MS` (120) grains, a new one every half grain, starting a whole number of the take's periods from where
  the playing grain is (so a steady note stays steady), wandering in `FP_LOOP_S`, rate = chord note / take pitch
  folded within a tritone (`FP_GAIN` 0.075: as loud as the synth pad). Each set's sound (synth or found, `fpw_m`)
  is latched at its chord change (a new take joins at the next one; a fading set is never cut; a Pad sounds change
  re-latches at once); the switches (`fpw_*`: active x register != Low x mode) run only what is heard, so with Pad
  register Low no pad set runs. The found bass (`fb_*`, group `fbass`) is one such voice on the bass note, not
  folded, `FB_GAIN` 2.8; it starts on the current note when switched on; the synth bass gives way. The tracker and
  detection now run while Harmony is up OR a found pad/bass wants takes (`harm_run`; the voice harmony still only
  while Harmony > 0). The event log says "Kept a held note for the found pad (A3)". The found bass has faint grain
  sidebands (~16 dB down) when pulled far down; untested on the phone.
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
  **Chimes Only Answer** (`chime_answer`, Music, default off, saved in presets; Oct 30, reactive-first step 2, branch
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
  Classic's degree_to_hz now arrives through a spigot) and the bass into ba_fold; so kits, the found pad, the
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
