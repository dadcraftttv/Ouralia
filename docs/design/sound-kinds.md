# Sound kinds: step 4 plan (Oct 31; started on DadCraft's yes, Oct 31)

**What DadCraft asked (Oct 30):** "we shouldn't be just measuring how loud a space is but what the various sounds are
within that space" (voices, traffic, birds, wind, music...). Step 4 of the reactive-first plan (ROADMAP "Open now";
Codex's second opinion: YAMNet first as the benchmark, licences checked; "don't trade genre chasing for classifier
chasing").

**What step 4 is:** finding out, with numbers, whether the phone can tell a handful of sound kinds apart while
Ouralia plays, cheaply and quickly enough to make music from. **Not** in step 4: any change to the music. If it
passes, the first musical response is picked with DadCraft afterwards, one kind at a time, A/B like steps 1-3.

## The kinds

Only the ones the music could answer differently. YAMNet knows 521 AudioSet classes; each group below is a list of
them, summed:

| Kind | Examples |
|---|---|
| Voices | speech, conversation, children, laughter |
| Singing / humming | singing, humming, whistling (Singing Room's own territory) |
| Music | music playing in the room, instruments |
| Traffic | cars, engines, buses, horns, tyres |
| Birds | bird song, chirps, crows, pigeons |
| Wind | wind, wind noise on the mic, rustling leaves |
| Water | rain, running water, waves |
| Steps and handling | footsteps, doors, knocks, typing |
| Quiet room | silence, room tone (nothing above) |

## The route

**4a. Licences and fit (no code).** Check and write down: YAMNet's code and weights licence (expected Apache 2.0,
which is GPLv3-compatible), the AudioSet class names' licence (expected CC BY), the on-device runtime (TensorFlow
Lite / LiteRT, expected Apache 2.0) and what it adds to the APK (expected a few MB: model ~4 MB, runtime ~1-3 MB).
Fallback if anything doesn't fit: MediaPipe's audio classifier (wraps YAMNet), or no ML at all (below).
*Gate:* every piece can ship in a GPLv3 app; APK grows by at most 8 MB.

*4a result (Oct 31): licences pass; size passes on ARM only.*
- **Code:** tensorflow/models (YAMNet's code), Apache 2.0. **Weights:** Kaggle `google/yamnet`, every variation
  Apache 2.0. Apache 2.0 can go into a GPLv3 app; we ship its LICENSE/NOTICE text in About.
- **Class names:** the model embeds `yamnet_label_list.txt` (521 names from the AudioSet ontology). The ontology is
  **CC BY-SA 4.0**, not CC BY as expected. CC declared BY-SA 4.0 one-way compatible with GPLv3 (Oct 2015), so it
  ships under the app's GPLv3 with attribution. The AudioSet dataset (clips, labels) is CC BY 4.0; we don't ship it.
- **Model:** `classification-tflite` v1, 4,126,810 bytes (3.23 MB deflated). Input 15,600 float samples (0.975 s at
  16 kHz, the mel front end is inside the model), output 521 scores. Built-in ops only (RFFT2D, CONV_2D...; no
  Flex/select-TF ops), so the plain runtime runs it. Desktop: 2.4 ms per inference on one thread.
- **Runtime:** LiteRT `com.google.ai.edge.litert:litert` (Google Maven), Apache 2.0. Deflated in the APK (we use
  legacy packaging): 1.4.2 is arm64 1.82 MB, armeabi-v7a 1.36 MB, x86 2.53 MB, x86_64 2.47 MB (8.18 MB for all four);
  2.3.2 is larger on ARM (2.33 / 1.92 MB) and has no 32-bit x86. Take **1.4.2** (the classic Interpreter API).
- **Size:** today's Dev APK is 8.76 MB. With all four ABIs: +11.4 MB, **over the gate**. With ARM only
  (arm64 + armeabi-v7a): +6.4 MB, within it. x86/x86_64 are emulators and a few Chromebooks: there sound kinds
  would be off, with everything else unchanged. Per-ABI APKs (or Play's bundle split) would also be ~+5 MB each.
- Not needed: MediaPipe (YAMNet runs on the bare runtime).

**4b. Desktop benchmark (Python, `tools/`, never shipped).** Run YAMNet over short clips of each kind: Ouralia's
own test signals plus public clips with licences that allow it (CC0 / CC BY), kept out of the repo if their
licence asks. Measure each kind's hit rate and what it gets confused with, at phone-like levels and through the
Moto's mic shape (high-pass, compressor at the app's defaults).
*Gate:* each kind is named right on at least 8 of 10 clear clips; voices vs singing and wind vs traffic, the
likely confusions, are listed with numbers.

*4b result (Oct 31): six kinds pass, three don't; stopped for DadCraft's call.* `tools/sound_kinds/bench.py`
(clips.json: 103 clips with source and licence; groups.json: each kind = AudioSet ontology subtrees). Clips: 69
from Wikimedia Commons (CC0, PD, CC BY, CC BY-SA), 21 from ESC-50 (wind, traffic, steps, water, birds, laughter;
ESC-50 is CC BY-NC apart from its ESC-10 part: fine for a local test, never committed or shipped), 13 made here
(quiet rooms at -90..-45 dBFS, wind buffeting the mic, the app's buzz and clap). Each clip: up to 12 windows of
0.975 s, named by majority; a kind = the highest of its classes. Conditions: clean (-20 dBFS); **phone** (-35 dBFS,
100 Hz mic high-pass, the default compressor -30 dB 4:1) = the gate; far (phone 15 dB quieter).

| Kind (phone) | Right | Wrong as | No-ML fallback |
|---|---|---|---|
| Voices | 13/13 | | |
| Singing | **2/10** | music 5, voices 3 | |
| Music | 10/10 | | |
| Singing + music as one kind | 17/20 | voices 3 (folk singing heard as speech) | |
| Traffic | 12/12 | | 2/12, fires on 8 of 91 others |
| Birds | 12/12 | | 11/12, fires on 7 of 91 |
| Wind | **6/12** (synthetic mic wind 0/2) | birds 4, quiet 2 | 3/14, fires on 3 of 89 |
| Water | 9/10 | traffic 1 | |
| Steps and handling | **9/12** (75%) | music, voices, birds 1 each | 9/12, fires on 1 of 91 |
| Quiet | 8/10 (1 real + 7/9 made) | wind 2 (the noisiest rooms) | 6/10, never on others |

- **Confusions asked for:** voices named singing 0 of 13, singing named voices 3 of 10; wind named traffic 1 of 14,
  traffic named wind 0 of 12. YAMNet hears choirs and chants as Music, solo folk singing as Speech.
- **Wind:** its scores are low (0.05-0.3). Three Commons "Bourne woods windy" clips are heard as birds (0.3-0.5;
  birdsong in a wood, likely right); one ESC-50 wind as a train. The made mic buffeting reads as "heart sounds,
  rumble". Wind Guard's detector (`dcs_dewind~`, third outlet "how windy") already measures mic wind in the patch.
- **Far** (-50 dBFS): voices, birds, music, steps hold; traffic 1/12, water 3/10, wind 2/12 drop to quiet.
- **Speed:** 2.2 ms per window on the desktop (one thread). The app's buzz reads "Synthesizer", the clap "Chirp tone".
- **The one fix round used:** traffic's group missed YAMNet's plain "Vehicle" class (its top answer for traffic),
  wind's missed "Rustle", and the quiet floor 0.1 hid quiet traffic (now 0.05). Before: traffic 6/12, wind 3/12.
  Summing a kind's classes instead of taking the highest changes little (steps 10/12, traffic 11/12).
- **What ML buys over the fallback:** traffic, voices, music, water and quiet-vs-noise; birds and steps the
  fallback does nearly as well (birds with more false alarms).
- **Proposal for 4c (DadCraft's call):** seven kinds: voices, music (singing folded in; Harmony's pitch tracker,
  `harm_tracker`, already follows a pitched voice), traffic, birds, water and quiet from YAMNet; steps from onsets (the
  patch already finds them for found sounds); wind from Wind Guard's detector. Or stop here.

**4c. Phone spike (a branch off `reactive`, Dev only).** The patch writes the mic, downsampled to 16 kHz mono, into a
ring array in memory; the app reads it every 0.5 s (libpd's array read), runs YAMNet on the last ~1 s, and shows
the top kind and its confidence on a debug line. Group changes go to events.log as words ("Heard: birds 0.71").
**Nothing touches disk but those words** (hard rule: the mic never touches disk). Measure on the Moto: time per
inference, the app's CPU with it on and off, battery over 30 minutes on and off (events.log "Health:" lines),
APK size.
*Gate:* under 30 ms per inference; under 5 points of extra CPU; under 3% extra battery per hour; a new kind shows
within 2 s of it starting.

**4d. DadCraft's walk.** The debug line on screen, Ouralia playing as usual, a normal walk (street, park, home).
He notes when it's right and when it's wrong.
*Gate:* his verdict: "mostly right, quick enough" or not.

**Then a decision**, not more tuning: pass all four and we choose the first musical response together (a few
ideas, none decided: birds lift the chimes into Glass high notes; traffic brings the pad's bass under it; voices
hand the lead to Singing Room; wind makes the pad breathe). Fail a gate and we stop and look at the fallback,
with at most one round of fixes per gate.

## The fallback (no ML)

A few kinds can be told from features the patch already measures or could cheaply: wind is loud low-frequency
noise with no pitch, birds are short high pitched chirps, traffic is a steady low rumble, steps and knocks are
onsets. Cruder, but tiny, offline and fully ours. 4b measures YAMNet against this too, so we know what the ML
actually buys.

## Cost

4a and 4b: Claude/Codex time only, no phone. 4c: one Dev install and a 30-minute plugged-in session. 4d: one walk.
The music stays exactly as it is through all of step 4.

## 4c progress (Oct 31, branch `sound-kinds`, Ouralia Dev only; passed)

Built: the patch writes the mic (as it comes in) into `sk_ring`, three segments of 24,576 samples, only while the
app sends `sk_on 1` (test_patch "sound kinds"); `SoundKinds` (plain logic: 16 kHz resampler, seven kinds, two
judgements in a row to change; javatests J) and `SoundKindsRunner` (its own thread, events.log "Heard: ..." and a
"Sound kinds:" cost line each minute); `YamNet` in `src/dev` (LiteRT 1.4.2, ARM only). The switch is in Diagnostics
("Sound kinds (Dev test...)", Dev only); the Now line shows "YAMNet: <kind> <score>". The plan's patch-side 16 kHz
downsampling moved to Java (simpler; the patch only writes the ring). Full tier passed (279 s), Dev installed.
Measured so far on the Moto (plugged in, 48 kHz): **YAMNet median 9.3 ms, slowest 14.6 ms** per judgement (114 in a
minute, none skipped): the 30 ms gate passes. **APK: Dev 8.76 -> 15.89 MB (+7.1 MB)**: the 8 MB gate passes.
Indoors it flipped between voices and quiet every few seconds (low voice scores 0.08-0.33).
**CPU on/off (Oct 31, plugged in, playing, 5-minute windows, /proc utime+stime):** on 55.5% of a core (Sound
tab open), off 56.8% (Diagnostics open), on again 55.8% (Diagnostics open). On vs off is inside the app's own
swing; YAMNet's share is about 2% of a core (117 judgements a minute at ~10 ms). The 5-point gate passes.
**The 2 s check (Oct 31, clips from the 4b set played through headphones laid on the phone's bottom mic, start time
from the PC, "Heard:" lines corrected for the phone clock; the log has whole seconds, so each is +-0.5 s):** piano
concerto ~2.0 s, piano sonata ~2.0 s, birdsong (dawn) ~3.0 s and gone back to quiet 3 s later at score 0.26,
birdsong (sunny day) ~3.0 s with voices flips around it. Music is on the line, birds miss: **the gate fails for
birds.** The cause is mostly built in: a judgement every 0.5 s over the last ~1 s, and two in a row to change, so a
weak kind needs most of the window filled twice.
**Fix round (DadCraft, Oct 31: "yes, do the fix round"; the one allowed):** the ring is now five segments of 12,288
(0.256 s at 48 kHz) and the app reads the four whole ones (~1 s, same length as before), so a judgement comes every
~0.25 s; a lead of 0.5 or more changes the kind at once, weaker ones still need two in a row (now 0.25 s apart),
and quiet always needs two. Full tier passed (285 s), Dev installed. **Same four clips, same way: all ~1.5 s**
(+-0.5): dawn birdsong 1.5 s (then flickers with quiet, the recording's gaps between calls), piano concerto 1.5 s,
sunny-day birdsong 1.5 s and held for the whole clip, piano sonata 1.5 s. **The 2 s gate passes.**
**CPU again** (twice the judgements, Diagnostics open both times): on 55.5%, off 52.4% of a core: **+3.1 points**,
inside the 5-point gate (235 judgements a minute at a 9.4 ms median, slowest 16.3 ms, as expected).
**Battery on/off (Oct 31, unplugged 15:25-16:28, playing, Android's charge counter in batterystats; events.log had
rolled over at 15:43):** on 15:25-15:57: 3691 -> ~3543 mAh, **~5.6%/h**, screen on 7.7 min; off 15:57-16:28:
-> 3326 mAh, **~9.2%/h**, screen on 16.7 min (DadCraft used the phone more in the second half: launcher, social
apps). The "off" half drained more, so the screen time swamps the difference: **inconclusive as a measurement**.
Estimate from the CPU A/B: +3% of one core is ~10-15 mA, about 0.3%/h of the ~4,600 mAh battery, far under the 3%/h
gate. **DadCraft accepted the battery gate on the estimate** (Oct 31: "accept on the estimate."). **4c passes all
its gates.** Next: 4d (his walk). Before it, the event log rolls over at 1 MB instead of 64 KB (64 KB held ~45 min
with the room listener; DadCraft, Oct 31: "yes, raise the limit now").

## 4d (Nov 1, passed)

A short walk (Oct 31, 17 min, mostly in a pocket) and two home runs (Nov 1). Walk: voices 79%, quiet 15%, steps 4%, birds 1%, no traffic or wind; the voices were real (three people walking and talking). Home, 73 min (laundry, dishes, talking): quiet 78%, voices 13%, water 8% (the sink running: right). Music playing in the room, 14 min: music 67%, voices 25%. Judgements ~9.5 ms throughout. Dish clatter has no kind and reads as quiet or steps. **DadCraft (Nov 1): "mostly right and quick enough." 4d passes; step 4 passes all four gates.** Next: choose the first musical response together.

## Step 5: Make Room for Music (Nov 1, branch `sound-kinds`, Dev only; a Music setting)

DadCraft picked the first musical response (Nov 1: "let's go with 3, music in the room"): while music plays in the
room, Ouralia steps back. Built behind a second Dev switch in Diagnostics, "Make Room for Music" (off by default;
it only works with Sound kinds on).
- **When:** `SoundKinds.roomMusic()`: music was the kind for at least 40% of the last 80 judgements (~20 s), gone
  again under 15%. Replayed on his Nov 1 runs before building: never on the walk or the 73 minutes at home, on for
  86% of the 14 minutes of music played in the room with 3 changes. It takes ~8 s of steady music to step back and ~17 s
  after the music stops to come back (then the ramp). Java tests J.
- **What:** the app sends `room_music 1/0`; the patch dips the generated music (pad, chimes, room notes, harmony) and
  the beat by `RM_DB` 6 dB over 4 s, coming back over 8 s (the mic's dry room and its effects are untouched), and the
  chimes' and room bells' "bigger sounds" threshold rises `RM_BIG_DB` 6 dB (also when Notes Only for Bigger Sounds is
  off), so the room's music doesn't set off notes. Events log: "Room music: heard, the music steps back" / "gone, the
  music comes back" (logged with the switch off too). test_patch "make room for music": -6.0 dB, halfway -2.5,
  back 0.00; knocks at 0.08 get 5 chimes and 3 bells off, none on, loud ones (0.5) still get some.
- **A setting (DadCraft, Nov 1: "i'm fine with that level of pull back as long the user can turn it on or off as they
  like"):** the Diagnostics switch became the Music card's "Make Room for Music" (`room_make`, off by default, not in
  presets or the dice; his old switch value carried over once). Shown only where sound kinds can run (Ouralia Dev,
  `Param.soundKinds()`). On, it starts the listening itself: the Sound kinds switch in Diagnostics is no longer
  needed (it stays, for watching the kinds). AudioService's loop calls `SoundKindsRunner.roomTick()`, so a row, a
  reset or a preset switches it at once.


## Step 6 options: voices (Nov 1, he picked 2)

DadCraft (Nov 1: "yes, write up the voice options"). Voices were 79% of his walk and 13% at home, and the model
names them best (13/13). Each option sits behind its own switch, off by default, decided by the voices share of a
short window (~8 s, faster than Make Room's ~20 s, as talk starts and stops more often than a song).
1. **Hush for Talk:** while people talk, the music and beat step back (about 4 dB, quicker than Make Room), chimes
   need bigger sounds and Harmony stops answering the pitch of speech. Reuses step 5. Risk: on a walk with
   company the music would sit back most of the time.
2. **Answer in the Gaps:** while someone talks, the music holds still (no new chimes, the pad held); in the pause
   after a voice (0.5-2 s quieter, from the mic level, as the model judges only every 0.25 s) a short phrase of
   2-3 chimes answers in key, using Trailing Melody's code. The most musical, and the riskiest: notes during a
   conversation can annoy.
3. **Room for Words:** the level stays, the sound makes way: the generated music dips only in the speech band
   (about 300 Hz-3 kHz, 6 dB), so voices stay clear and the bass, beat and chime shimmer stay full. Comes back
   when the talk ends. Least intrusive, closest to how a mix makes room for a singer.
Recommended first: 3 (fits a talking walk without hiding the music); 2 after it if he wants the music to take part.

## Step 6: Mind the Gap (Nov 1, branch `sound-kinds`, Dev only; heard and liked Nov 2)

DadCraft (Nov 1: "go with 2"). A Music setting, "Mind the Gap" (originally Answer in the Gaps) (`voice_gaps`, off by default, not in presets
or the dice, only where sound kinds can run; on, it listens by itself, like Make Room for Music).
- **When:** `SoundKinds.talking()`: voices the kind for at least 40% of the last 32 judgements (~8 s), over under
  15%: on after ~3.5 s of talk, over ~7 s after it stops. Java tests J. Replayed on his runs: on 93% of the walk
  (19 changes), 14% at home (79 changes, about one a minute), 34% of the music run (sung words heard as voices).
  Events log: "Talk: heard, the chimes wait for the gaps" / "Talk: over, the chimes play again".
- **What:** the app sends `voice_talk 1/0`. While 1: the sudden-sound chimes, the still pattern and the room bells
  hold (their checks read `vtalk`), and the composer's bars wait (`cmp_talk_g`: the chord and section stay). The
  walking notes, the beat and the pad's sound go on. Every `TALK_TICK_MS` 50 ms the level above the room's quiet
  tells speech (>= `TALK_GAP_DB` 8 dB) from a pause; a pause of `TALK_GAP_MS` 600 ms after at least
  `TALK_SPEECH_MS` 800 ms of speech gets an answer into `mu_deg` (in key, the melody's own steps): 2 chimes, 3
  after `TALK_LONG_MS` 2 s of speech (no [random]: one would reshuffle every later one), the first at `TALK_VEL`
  0.45 x the pocket hush, each x0.75, `TALK_NOTE_MS` 260 ms apart then x1.3. Nothing with Music off.
  test_patch "answer in the gaps": four rounds of 1.5 s speech and a pause get 4 answers and 8 chimes (2.5 s of
  speech: 12); none without talk, in 0.3 s pauses or with Music off; knocks during talk 0 chimes and 0 bells
  (6 and 8 without).

## Step 7 options: water (Nov 2)

DadCraft: "Water, options first." Proposals only; his pick comes before a build. Water was right on 9/10
benchmark clips at phone level (one heard as traffic), but only 3/10 far away, the rest dropping to quiet.
At home it occupied 8% of the 73-minute run; DadCraft confirmed the sink was running. Rain and waves are in the
group, but untested on his phone: these are sink-first proposals, not evidence that all water behaves alike.

Either option would have its own Music setting, off by default, Dev only (`Param.soundKinds()`), outside presets
and the dice. Turning it on would start listening by itself, extending `SoundKindsRunner.settingsTick()` like
the existing Music settings; no Diagnostics switch needed. Music off means no musical response. The mic stays
in memory and never touches disk. The windows below count the settled `water` kind, like `roomMusic()` and
`talking()`, not raw class scores; timings are approximate and add the classifier's delay.

1. **Water Leaves a Trail:** while water is heard, a splash that already earns a sudden-sound chime gets at least
   two softer notes following it, each a little later. A smooth running tap gets no extra notes without a new
   qualifying sound; this does not start a repeating melody. **When:** water in at least 40% of the last 32
   judgements (~8 s), off at 15% or less: roughly 3.25 s to enter from empty and 7 s to leave a full window.
   That catches a tap running for seconds without letting one brief water judgement change the music; rain
   lasting minutes simply keeps the response available. On leaving, cancel pending water-added notes; already
   ringing notes decay. Reuses the sudden-sound gate and Trailing Melody's softer, slower `mu_deg` path. This
   setting supplies the two-note minimum even with Trailing Melody Off; a higher selected or moving count still
   wins, and its stored setting stays unchanged. Risk: taps may be too smooth to trigger chimes, especially with
   Notes Only for Bigger Sounds high; splashing may instead become busy, and distant water may lose its trail.
   With Make Room for Music, keep its 6 dB dip and raised trigger threshold. With Mind the Gap, talk takes
   priority: cancel water-added tails when `voice_talk` is on, including notes already queued, and add no water
   tail to the voice answer. That arbitration needs new wiring; the current trail scheduler does not check talk.

2. **Make Room for Water:** while the tap runs, generated music and beat ease down by about 4 dB, leaving the
   water and its existing effects more exposed; they return after it stops. No extra notes. **When:** water in
   at least 40% of the last 80 judgements (~20 s), off at 15% or less: roughly 8 s to enter and 17 s to leave a
   full window, followed by a 4 s dip / 8 s return ramp. Better for sustained washing or eventual rain than a
   quick splash; the slower window avoids repeated level changes between splashes. Reuses Make Room's gain
   stages and `RM_DOWN_MS` / `RM_UP_MS`, with an independent water receiver rather than sending fake
   `room_music` events. Keep dry mic and effects untouched. Risk: this may feel like ordinary ducking, with a
   late return after a short tap; far water can release it even while still running. If Make Room for Music is
   also active, take the stronger dip (6 dB), never add them into 10 dB; keep its bigger-sound threshold tied to
   music. Mind the Gap keeps its holds and pause answers, with those answers subject to the shared dip.

**Recommended first: 1, Water Leaves a Trail.** It gives the confirmed sink sounds a recognisable musical answer
using an existing voice, without a new clock-driven layer. First listen: tap running smoothly, then a few
splashes, then off; compare at the same chime level and threshold. Its value depends on splashes actually
earning chimes, so DadCraft's listen decides whether it adds enough beyond ordinary Trailing Melody. Option 2
is the quieter choice if he wants to hear more of the water itself.

**Replay:** no new on-time estimate claimed. The local `tools/.session/events*.log` copies have no `Heard:`
events; the documented 8% alone cannot reconstruct either window. With the original Oct 31 / Nov 1 event logs,
a small local replay can hold each logged kind until the next change, sample at 0.25 s, reset at listener/session
boundaries, and report each window's active share and changes as an estimate (judgements are not exactly 0.25 s
apart). That needs no audio. It estimates availability, not splash-triggered phrase counts or audibility; those
need a live listen, and rain/waves still need their own phone check.

## Step 7: Water Leaves a Trail (Nov 2, Dev only; heard and liked Nov 2)

DadCraft picked option 1: "Water Leaves a Trail, hand the build to Codex." The Music switch `water_trail` is
independent, off by default, outside presets and dice, and starts listening without the Diagnostics switch.
The settled water kind must fill 13 of the last 32 judgements to turn on (at least 40%); it turns off at 4 or
fewer (at most 15%). Music off, setting off, or stopping the listener sends water off, only when state changes.

While active, an existing sudden-sound chime starts at least two softer, increasingly spaced trailing notes.
A higher selected or mover count wins; the stored `chime_trail` value is untouched. The gate, threshold and
Make Room for Music behaviour stay as before. No sound earns a new trigger merely by being water.

Mind the Gap wins: talk starting removes the water-added notes still queued. Leaving water does the same.
The scheduler remembers the ordinary count taken at the chime and subtracts notes already played, so an ordinary
Trailing Melody tail survives either event. This preserves the sound DadCraft liked in step 6. Voice pause
answers use their own scheduler and acquire no water notes. Ringing notes decay naturally.

The rename to Mind the Gap rides with this build; `voice_gaps` is unchanged so saved settings survive.
Underwater is planned in ROADMAP, with no effect built yet. No audio is recorded or stored.

First listen after review: Music on, Water Leaves a Trail on, Trailing Melody Off, Notes and Chimes audible.
Let a tap run for a few seconds, splash enough to earn a chime, then stop the tap and wait about eight seconds.
Compare with the switch off at the same level and bigger-sound threshold. Smooth water need not trigger anything.
Then try Mind the Gap alongside it: speech should stop the water-added tail; pause answers stay as before.
Rain and waves remain untested on the phone.

His listen (Nov 2): "i just tested it and it sounds good." No tuning asked for.


## Step 8 options: Underwater (Nov 2)

DadCraft: "hand underwater to codex, options first." Write-up only; neither option is built. Water Leaves a
Trail was heard and liked. These choices change the colour of music already responding to the room; water
alone starts no notes. Sink evidence carries over from step 7; rain and waves still need a phone listen.

**Shared placement and control:** `add_music()` returns `mu_out` (pad/bass, chimes, room bells and harmony/tones).
In `build.py`, process `music_rm` after Make Room for Music's gain, before it joins `filtered_dry` at
`filtered_mix`. Separately process `beat_rm` before it joins `mix_pre` at `mix_out`. Those are two mono effect
paths with shared control, not a filter on the final output. Never feed `filtered_dry`, `voice`, `mix_out` or
the final stereo sum into Underwater: they contain mic audio. Leave `add_stereo_out()` and `stereo_plain`
alone too; World in Stereo's mic side stays as before. Found instruments and room resonators count as musical
voices even though the mic supplies their material. Existing downstream chorus/echo/reverb/spatial effects
still apply normally, including to the newly coloured music; old reverb tails need not darken instantly.

Both options propose an independent **Underwater** Music switch: off by default, Dev only, outside presets and
dice, listening by itself. Share `SoundKinds.water()`'s settled window (13/32 on, 4/32 off, ~8 s), but add a
separate Underwater receiver/state: today's `water_trail` send requires the trail switch, so reusing that send
would wrongly couple the settings. Extend the runner's start/stop/reset logic for either switch, sending Pd
messages outside its lock as step 7 now does. Music off, Underwater off or listener stop releases this effect;
turning the trail off does not. No audio is recorded or stored.

1. **Underwater, gentle:** after a tap has run for a few seconds, the pad and bells lose their bright edge,
   percussion softens, and sustained tones have a faint swimming shimmer. The real water stays clear. Stopping
   the tap gradually restores the usual sound. Start with two cascaded `lop~ 1600` filters per path, followed
   by a light chorus: 80% filtered signal plus 20% of its delayed copy, delay 10 ms +/- 1 ms at 0.20 Hz, no
   feedback. One shared `osc~`, two small `delwrite~` / `vd~` pairs (one per path), four `lop~`, and a handful
   of `*~` / `+~` plus smoothed control/crossfade objects. Crossfade the unchanged input to that whole processed
   branch over **4 s in / 6 s out**; keep the cutoff fixed initially. From an empty/full water window, entry is
   roughly 3.25 s and exit 7 s before the ramps, plus classifier settling delay. **Tune cutoff first** (try
   1.2-2.4 kHz if needed), leaving wobble and timings fixed for the first comparison.
   Expected added DSP cost on the Moto: low relative to the existing effect chain, with short delays and no
   FFT, new model or extra reverb; unmeasured, so no CPU percentage promised. A separate switch lets DadCraft
   hear this alone or with the trail; a trail style would hide that choice. Make Room for Music's 6 dB dip and
   trigger threshold still apply; Underwater adds colour, no deliberate second volume dip or makeup boost.
   Mind the Gap keeps its holds, chord wait and cancellation priority; its pause answers get the underwater
   colour if water remains active. Main risks: bells/beat lose definition on small headphones (and on a phone
   speaker if speaker output is ever supported; Ouralia currently uses headphones only), chorus comb filtering
   makes some notes hollow, or detuning clashes with an audible sung tone in the dry mic. Existing chorus can
   make the combined effect stronger. Lower the chorus share if that clash is heard.

2. **Underwater, deeper:** the same music seems farther below the surface, with a clearly audible slow pitch
   sway rather than a faint shimmer; beats soften more and sway with the tones. Use the same two insertion
   points and water window, but two `lop~ 900` filters per path and a fully delayed filtered branch, 12 ms
   +/- 3 ms at 0.25 Hz, no feedback or undelayed chorus share at full depth. Crossfade **5 s in / 8 s out**;
   water-window delays are the same as option 1. **Tune delay modulation depth first** (1-3 ms); leave cutoff
   at 900 Hz for that listen. Objects and expected Moto cost are similar to option 1 (four filters, two short
   variable delays, shared oscillator and fades); still unmeasured. Keep its own Underwater switch for the same
   independent comparison; making it a trail style would also colour pad and beat, an unclear fit for a chime
   setting. Make Room's dip stacks with this colour, without a new gain reduction; Mind the Gap still decides
   when notes may play, including its usual pause answers, which are coloured while water stays on. Risks:
   more pitch conflict against the dry voice, blurred found-drum attacks, loss of chime identity, and music
   becoming too dull beside Make Room's dip. The added 12 ms delay affects musical attacks, never mic latency.

**Switching for either option:** use complementary linear gain ramps for bypass/processed audio (no equal-power
boost on correlated signals), initialise bypass on, and never jump a delay read position or hard-switch live DSP.
If wet branches are suspended to save CPU when bypassed, start and prime their delay buffers silently before
fading them in; suspend only after the release reaches zero. Reversing a water decision mid-fade continues from
the current amount. Test this for clicks and stale-buffer bursts. Music off must retain its existing mute and
must not leak buffered notes. Filtering changes perceived loudness: measure it against bypass before adding any
compensation. Neither option guarantees that all existing effect tails sound uniformly submerged.

**Recommendation: option 1, gentle, with its own switch.** It gives water a recognisable colour while keeping
DadCraft's found percussion and the musical pitch relationship easier to hear. It can accompany the liked trail
without requiring it. Option 2 is a later choice if the gentle treatment feels too slight, after a listen rather
than building both now.

**First listen (after a separate build brief):** headphones, fixed levels/effects, Make Room for Music and Mind
the Gap off initially. With Underwater off, run the tap smoothly, make a few splashes, stop, and wait 15 seconds;
repeat with it on. Keep an already responsive pad and audible found beat/chimes so there is music to colour;
water should not create a pad or beat by itself. Listen for clearer real water, softer but recognisable musical
attacks, and a smooth return. Then compare the trail off/on alongside Underwater, hum against the coloured
music to judge pitch conflict, and try talk/pauses with Mind the Gap plus Make Room's dip when room music is
actually detected. Check both switches off during the effect and music off/on for clicks or delayed leftovers.
Before DadCraft's listen, a build must verify mic-only parity with World in Stereo on/off, both musical paths,
level/headroom, release/reversal/mute behaviour, and measured Moto DSP cost. No new audio recording is needed.
