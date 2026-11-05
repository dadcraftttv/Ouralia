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

## Step 8: Underwater, both styles (Nov 2, Dev only; both kept while he keeps testing)

DadCraft (Nov 2), after reading the two options: "can we build both? just so i could test them side by side and let
you knwo which one works better?" So both are built behind one Music setting, **Underwater: Off / Gentle / Deeper**
(`underwater`, Off by default, Dev only, outside presets and dice; listening starts by itself like the other sound-kind
settings). The numbers are the options' (constants.py `UW_*`, index 1 gentle, 2 deeper):

| | Gentle | Deeper |
|---|---|---|
| two cascaded `lop~` | 1600 Hz | 900 Hz |
| wobbling delayed copy | 20% (light chorus) | 100% (pitch sway) |
| delay, +/- depth, rate | 10 ms, 1 ms, 0.20 Hz | 12 ms, 3 ms, 0.25 Hz |
| fade in / out | 4 s / 6 s | 5 s / 8 s |

- **App:** `SoundKindsRunner.uwTick()` sends `underwater` = the style while `SoundKinds.water()` holds (the same
  13/32-on, 4/32-off window as the trail, its own send), Music is on and the listener runs; 0 otherwise. Sent outside
  the runner's lock, like `water_trail`. Changing the style while water is heard switches at once.
- **Patch:** `add_underwater()` and `uw_path()` in build.py. Two paths, `music_rm` (before `filtered_mix`) and
  `beat_rm` (before `mix_out`): neither carries mic audio, so the dry mic and World in Stereo's side are untouched.
  Linear crossfade from the untouched path to the processed one; 0 keeps the last style for the fade out, a new
  value reverses from wherever the fade is. A style change glides the delay over 500 ms (no jump in its read
  position). Always running, not in a `[switch~]` block: 4 `lop~`, 2 `delread4~`, an `osc~` and some `*~`/`+~`.
  The Moto's cost isn't measured yet; the minute's "Health:" CPU line in events.log will show it.
- **Measured in the patch** (test_patch 7z6): fade halves and ends on time for both styles; the mic alone comes out
  sample-identical with Deeper fully in; above 2.5 kHz the music drops from -45.7 dB (share of its energy) to
  -60.8 (gentle) and -69.3 (deeper), the beat from -9.6 to -31.4 and -41.6. Level: music -1.4 dB gentle, -0.1
  deeper; beat -3.1 dB gentle, -0.6 deeper (gentle's 20% copy partly cancels; no makeup gain added yet).

**His listen (side by side):** headphones, Underwater on Gentle, the music playing (pad/chimes, a found beat if
walking). Run the tap for ten seconds or so: the music should go muffled over about 4 s while the real water
stays clear; stop the tap and it should come back over about 6 s. Then Deeper, the same way: duller, with a slow
pitch sway. Hum along to hear whether the sway sounds out of tune. Switching between the two mid-water should not
click. Tell us which one, and whether it is too strong, too weak or too slow.

**His first listen (Nov 2):** "i can't tell which i like better. i'll keep testing with the two options." Both stay
in, no tuning; his pick (or a change) when he has one.

**Asleep while off (review performance item, Claude Code, Nov 4).** The four paths (music, music side, beat, beat
side) ran their filters and wobbling delay even with Underwater Off, in every build. Their wet part is now one switched
block (`switch~`, group `uw`): it wakes at once on `underwater` > 0 and sleeps 8.5 s after it returns to 0 (the longest
fade out, 8 s, plus the 40 ms buffer and a margin). Off, each path's output is its input exactly either way, and the
wake comes at amount 0, so nothing is heard (step at wake 0.0006). Test: asleep, awake at once, awake through the fade,
asleep after, working again. Beat through the paths sample-identical old vs new (the music can't be compared sample
for sample: it varies run to run even on one patch). **Desktop** (patch only, Music + pad + arp + beat, Underwater
off, 24 s, 5 runs each): median 0.524 s old, 0.448 s new, **14.4% less patch time**, about 0.3 points of a desktop
core, ranges apart. **Moto** (Ouralia Dev playing, Music + Music in Stereo on, Underwater Off; process CPU over three
60 s windows from /proc): before 57.4 / 62.0 / 62.7%, after 59.1 / 61.3 / 61.1% of a core: no difference the phone
can resolve (it swings about 3 points; `top` snapshots range 22-85%). 0 underruns both. Kept: it is free and saves
real work; the bigger costs are elsewhere (the classifier's resampling, finding 2's performance notes).

## Step 9 options: birds (Nov 2)

DadCraft: "hand birds to codex, options first." Write-up only; neither option is built. DadCraft cannot test
outdoors tonight: "i can't test it now as it's late and i'm inside. can't get out to test." No listen is needed
now. These are two different responses to weigh against the original high Glass idea, before any build.

**Evidence and limits:** birds passed 12/12 phone-level benchmark clips and held at the far level; the walk named
birds for 1% of its time. Dawn birdsong flickered with quiet between calls in the phone check, while the sunny-day
clip held. The bench's "fires on 7 of 91" belongs to the **non-ML fallback**, not YAMNet. YAMNet named four wind
clips as birds (three woodland clips likely contained birds); steps/handling also had one birds result. Squeaks
are a listen case, not a measured error rate. A birds kind identifies a sound scene, not individual calls or their
pitch. Neither proposal claims to copy a bird's tune or reliably separate birds from wind.

**Shared detection and switch proposal:** add a separate settled-birds window to `SoundKinds`, following
`water()`/`talking()`: last 32 settled-kind judgements (~8 s), on at 13 birds, off at 4 or fewer. From an empty
window, continuous birds need about 3.25 s of birds judgements after classification; a full window needs about
7 s without birds to release. Classification itself adds latency (the tested clips changed in ~1.5 s). Gaps of
a few seconds usually hold an established response, but sparse isolated calls may never enter. Do not retrigger
on every raw birds/quiet flip. These counts are starting proposals, not replayed performance claims.

Each option would have its **own Dev Music setting**, off by default, outside presets and dice, starting the
listener without Diagnostics. Build only DadCraft's pick initially; the two options are alternatives, not a pair
of stacked dips/voices. Extend `SoundKindsRunner.settingsTick()`, start/stop and judgement handling with an
independent birds send outside its lock. Music off, switch off or listener stop releases it; reset clears the
window. No dependency on Water Leaves a Trail or Underwater being enabled. No mic audio is saved.

1. **Birds Lift the Chimes — high Glass on earned notes.** After birds have settled, chimes already earned by
   sounds or walking gradually become lighter, higher Glass notes. Birds classification alone plays nothing.
   A distant chorus below Notes Only for Bigger Sounds may therefore make no chime: the classification is colour
   permission, not a new trigger. Once birds leave, later notes ease back to the selected Chime Voice and register;
   notes already ringing finish naturally. This can feel like the surroundings changing the instrument rather
   than a second bird trying to sing over them.

   **Starting sound:** keep the current key and scale; use octave moves to fold affected notes into MIDI 72-84
   (C5-C6, about 523-1047 Hz), never snap the bird's pitch to the scale. At full effect use Glass only, with note
   velocity multiplied by 0.71 (about -3 dB). Fade the colour/trim amount over 3 s in and 5 s out, sampled at each
   new note. For pitch, choose the ordinary or octave-folded register once per note, increasingly favouring the
   latter across the fade; never slide a ringing bell's pitch. **Tune the register first** if too shrill or too
   close to the real birds; start by lowering the range an octave rather than adding more notes. Birdsong's gaps
   are bridged by the shared window; no new tail or phrase is scheduled at a gap.

   **Where/cost:** Java window plus patch control in `tools/patchgen/music.py` (the chime voices live here, not
   directly in build.py). Add per-note effective frequency, voice mix and velocity before the four existing
   chime voices fire; reuse `GLASS_*` and the existing oscillators. The shared voices also serve walking, trailing
   notes and talk answers, so apply one consistent colour rule to them, with talk priority below. Don't write
   `chime_voice` or its stored value: take a per-note blend from the user's Classic/Both/Glass selection toward
   Glass. Ring length must use that effective voice too. Rough Moto budget: a small control addition with no new
   audio voices; Classic-only users will activate existing Glass oscillators, so incremental DSP is not guaranteed
   zero. Tentative target under 1 percentage point of a core beyond the already-running listener, unmeasured.

   **Stacking:** Mind the Gap keeps all holds, cancellations and answer timing; during settled talk, suspend the
   bird colour for new notes, including pause answers. Water Leaves a Trail and Trailing Melody keep their exact
   counts/timing; their notes use the effective bird colour when talk is not holding it. Make Room for Music's
   existing dip and bigger-sound gate still win (the bird trim adds about 3 dB of attenuation at full effect).
   Underwater processes these notes downstream as it does other music, so it can muffle the Glass cue. Chime Voice
   remains the underlying selection and returns after release; Glass-selected users mostly hear register/trim.

   **Risks:** persistent wind/squeaks could colour unrelated earned chimes; a single knock in birds is still a
   knock-triggered note, not proof a bird called. Quiet birds may produce no audible difference at all. High Glass
   partials may compete with real birds or sound sharp on small transducers; test phone-speaker harshness only in
   an offline render/other playback setup, since Ouralia itself remains headphones-only. No loudness boost or
   pitch following is proposed. Per-note transitions need level and voice-lifetime checks before a listen.

2. **Make Room for Birds — let the real birds lead.** Once birds have settled, generated music and beat ease
   down 3 dB; the real birds and their existing mic effects remain at their current level. After the last calls,
   the window holds through the gaps and the music slowly returns. No added notes, pitch change or Glass switch.
   **Starting numbers:** the same 13/32-on, 4/32-off window, 3 s down / 6 s back, unchanged pitch range and existing
   Chime Voice. **Tune the dip first** (3 dB initially); making detection faster would not solve a dip too subtle
   to hear. Long gaps eventually release it smoothly rather than chopping the level per call.

   **Where/cost:** Java birds window/send plus a control ramp in build.py feeding the existing Make Room gain
   stages for `music_rm` and `beat_rm`, before the mic joins. Combine requests by the stronger attenuation:
   birds alone 3 dB, music alone or both 6 dB, rather than adding to 9 dB. Keep the bigger-sound threshold tied to
   Make Room for Music alone. One gain envelope/control combination, no new oscillators or audio buffers; rough
   incremental Moto DSP target under 1 percentage point of a core, unmeasured, beyond the listener's cost.

   **Stacking:** Mind the Gap retains its timing/holds; answers are subject to the current dip. Water Leaves a
   Trail and Trailing Melody keep their notes and counts, now quieter. Underwater independently colours the
   generated paths; neither changes the other's detection. Chime Voice stays exactly the user's choice. Dry mic and
   its effects keep their current level. Singing Room is inside the musical sum and dips too, even though the
   mic supplies its material; this is not a master-volume dip.
   **Risks:** false birds can cause needless ducking, and frequent sparse calls may make a delayed swell feel
   unrelated. It gives birds space but may sound like Make Room for Music again rather than a distinct response.
   It adds no shrill voice or competing pitch; existing chimes may still sit close to the birds' pitch.

**Recommendation: 1, Birds Lift the Chimes, first.** It gives birds a distinct musical consequence using the
existing Glass voice, while keeping the world responsible for note starts. It is a limited experiment in colour
and register, not bird imitation. The deciding limitation is audibility with the current bigger-sound threshold:
if normal birds never earn notes and the change needs artificial knocks to be heard, judge that openly before
considering any new trigger. Pick 2 instead if making the actual birds easier to hear matters more than a new
musical colour. No classifier tuning or automatic bird melody is part of either proposal.

**First listen (after DadCraft picks and a build is reviewed):** indoors first, at any convenient time. Ouralia
output stays in headphones; play an existing birdsong recording from another device/speaker into the phone mic,
without recording the mic. Use a clip with short gaps and another with continuous calls if available; compare the
switch off/on at the same playback, Notes and Chimes level and bigger-sound threshold. Start with Music on and
Mind the Gap, Make Room for Music, Water Leaves a Trail and Underwater off; Trailing Melody Off. Diagnostics can
show the heard kind, but is not required to run the response.
- For option 1, listen to birds alone first: note whether they actually earn chimes. After roughly 10 s of birds,
  make one modest knock that already earns an ordinary chime in the off pass; it should reveal the high Glass
  colour without more notes or higher level. Short recording gaps should not reset the instrument. Stop playback,
  wait roughly 12-15 s, then make the same knock: ordinary colour should return. This knock checks colour only;
  it does not establish that birds alone make a convincing response.
- For option 2, have an already-responsive pad or other audible generated music: it should dip gently without the
  recording itself being turned down, then return after playback stops. No new notes should appear.
- Then enable the other settings one at a time, checking talk's priority and ordinary trail counts. Try a quiet
  non-bird passage or a squeak too; report any wrong colour/dip rather than raising the volume to force detection.

Outdoors later, repeat off/on while standing near real birds, then in a windy spot with no obvious calls. Check
whether sparse birds enter at all, whether the response survives normal gaps, and whether it leaves the real
birds clear. Indoor playback proves the route, not outdoor usefulness. Before DadCraft's listen, verify window
edges/reset, switch independence, no notes from classification alone, voice/level transitions (option 1) or
strongest-dip composition (option 2), mic isolation and incremental Moto CPU. No audio needs saving for any check.

## Step 9: Birds Lift the Chimes (Nov 2, Dev only; DadCraft's listen next)

DadCraft (Nov 2), after the options: "do birds lift the Chimes". Built as option 1, one Music setting **Birds Lift
the Chimes** (`birds_lift`, off by default, Dev only, outside presets and dice; listening starts by itself).

- **App:** `SoundKinds.birds()`, the same 13/32-on, 4/32-off window as water (~8 s; calls with quiet gaps hold it);
  `SoundKindsRunner.birdsTick()` sends `birds_lift` 1 while it holds, Music is on and the listener runs, else 0.
  Sent outside the runner's lock, like the water sends.
- **Patch** (music.py, the chime voices; constants.py `BIRDS_*`): `birds_lift` eases `bamt` to 1 over 3 s, back
  over 5 s. Each new note on the four shared chime voices takes it once (0 while Mind the Gap hears talk): its
  voice moves that share from the user's Chime Voice toward Glass, its velocity is trimmed by up to 0.29 (-3 dB),
  and it folds by octaves into MIDI 72-84 (523-1047 Hz) when a golden-ratio step is under the amount (so half-way
  through a fade about half the notes fold; no `[random]`, which would reshuffle the patch's other random
  sequences). Ringing notes keep what they took. Only the synth chime changes: found melody notes and the
  vocoder's note stay as they were. Birds alone start nothing. Chime Voice's Classic level is now set per note
  (it was a shared level that changed ringing notes too); with birds off the sound is the same.
- **Cost:** control objects only, no new oscillators; a Classic user's notes now start the Glass partials that were
  already there while birds are in. Moto cost unmeasured (events.log "Health:" CPU).
- **Measured in the patch** (test_patch 7z5b): a motif chime at 328 Hz plays at 660 Hz with birds in, -2.4 dB,
  the 2.76x Glass partial at -12 dB (off: -85); with talk held, and 5.5 s after birds leave, it is the plain
  328 Hz chime again (-0.1, -0.5 dB); a Glass user's chime folds up too; birds alone, no chimes.

**His listen (indoors, any time):** headphones, Music on, Birds Lift the Chimes on; Mind the Gap, Make Room for
Music, Water Leaves a Trail and Underwater off. Play birdsong from another device near the phone (no recording).
First birds alone: do they earn any chimes at the normal bigger-sound setting? Then, after about 10 s of birds,
knock once: the chime should be a higher, lighter glass note, no louder. Stop the birdsong, wait about 15 s, knock
again: the ordinary chime should be back. Tell us if it is too shrill (the first knob: an octave lower), too
subtle, or if birds alone never get a note. Outdoors later, near real birds and in wind.

**His first listen (Nov 2, indoors, birdsong from another device):** "knocking while bird sounds are playing
produced no chime. knocking after bird sounds stopped produced a normal chime. it sounds fine to me so far but i
wonder if there is a way to keep the birdsong from hitting the found sound rhythm grid." The event log: birds held
for about a minute, so the lift was on; the knock got no chime because Notes Only for Bigger Sounds (20.5 dB) is
measured above the room's floor, which the birdsong raised. In that minute the found beat caught 31 hats, all
bright with no lows (chirps). So he had not heard the high Glass yet. His call: "yes, tie both to birds lift".

**Built (Nov 2), both only while `birds_lift` is 1** (`blift`, at once, no fade):
- Notes Only for Bigger Sounds asks `BIRDS_NB_DB` = 8 dB less (never under 1 dB; off stays off), for the chimes and
  the room bells alike.
- The found beat catches no new hats: a slice that would file as a hat is dropped and reported as 5, "Skipped a
  chirp for the found-sound beat (birds heard)" in events.log. Kicks and snares are still caught; hats already
  caught keep playing. Real taps that read as hats are skipped too while birds are heard.
- Measured (test_patch 7z5b): thump, clap, tick, tick, thump are caught as kick, snare, hat, hat, kick; while birds
  lift, as kick, snare, 5, 5, kick. Knocks at amplitudes 0.05 / 0.1 / 0.2 / 0.5 with the threshold at 25 dB: chimes
  0 / 4 / 5 / 6 without birds, 5 / 5 / 6 / 7 with them.

**His next listen:** the same as before. Over the birdsong, a knock should now get a chime, and it should be the
high glass one. The found beat should stop gathering chirps (events.log shows "Skipped a chirp"). If chirps now
earn chimes by themselves too often, 8 dB is the knob.

**His re-listen (Nov 2, 2878343, indoors):** "knocking during birdsong now gives a high glass chime". The event
log for that run: 13 chirps skipped and none caught once birds had settled; 3 hats were caught in the first second
or so of birds, before the window turned on (it needs about 3 s of birds judgements). A few chirps were still
answered as sung phrases (not asked about). Outdoors later.

## Step 10 options: traffic (Nov 3)

DadCraft: "hand traffic to codex, options first, let's aim for 1 and two, but ask Codex for what it thinks would
work well in addition." Originally a write-up only; option 1 is now built (Step 10 below). His earlier idea is included in 1:
"could traffic send the glass/synth bells/chimes from one ear to the other smoothly?" Options 1 and 2 follow
Claude's list; 3 is Codex's addition. Names and numbers below are the original proposals; Step 10 below records the built option 1.

**Evidence and limits:** YAMNet named traffic on 12/12 phone-level bench clips, but only 1/12 at -50 dBFS. The
"fires on 8 of 91 others" figure is the non-ML fallback's result, not YAMNet's false-positive rate. The bench
found one wind clip and one water clip named traffic. Including YAMNet's plain Vehicle class fixed several
misses; Vehicle still does not mean a moving car. DadCraft's walk contained no classified traffic, so we have no
roadside verdict from him. None of these options claims to measure speed, count cars, recognise a red light,
separate an idling bus reliably, or locate the real direction of travel.

**Two speeds of control:** `SoundKinds.scores()[2]` already exposes the latest traffic group score (maximum of
its classes), every ~0.25 s, using 0.975 s of mono audio. There is no `traffic()` scene window yet. Scores are
available even when traffic loses to another kind; they are model scores, not calibrated probabilities. An
8 s settled window alone would be too slow to shape a short pass. Use the score to grant permission, and a
faster mic envelope to shape the rise/fall. The existing `mlev`/`mfloor` describe overall room level, not isolated
engines: another sound over traffic can move them. Prefer a dedicated short envelope on the same pre-effect mic
feed used for classification, so playback effects and generated music cannot drive it internally.

**Shared proposed detector:** in Java, permit traffic after its score is at least 0.20 and leads the other
non-quiet groups for 2 successive judgements; release after the score stays below 0.10, or another kind leads,
for 2 s. Keep a separate scene latch for option 2: 32 settled-kind judgements (~8 s), enter at 13 traffic,
leave at 4 or fewer. These are untested starting thresholds, with overlapping input windows, not independent
votes. Talk has priority: settled talk suspends new traffic passes/accents and returns option 2 toward unity.
Do not depend on Mind the Gap being enabled to obtain that priority.

In the patch, smooth mic level over about 150 ms; keep a slow baseline (~12 s, held during a candidate pass),
and look for a rise of **4 dB within 2 s**, held for 300 ms, while permission is valid. Save a 2 s history of
level values only: the score may arrive after the audible rise. A candidate expires if permission does not
arrive within 1.5 s; never play a delayed pass after it has ended. Follow its excess above baseline, mapping
0-10 dB to 0-1, with 250 ms attack / 1.5 s release. End when it falls within 2 dB of baseline for 1 s, with a
10 s maximum and a 2 s re-arm gap. Constant rumble may grant permission but supplies no new rise. This deliberately
misses some cars rather than inventing repeated passes on a motorway.

Wind Guard's existing windiness measurement is available from `dcs_dewind~`; a proposed high-wind veto (above
0.6 for 0.5 s, clear below 0.3 for 2 s) would freeze new candidates and release current ones. Wire that measurement
independently of the audible Wind Guard switch if needed, and verify its range before adopting these thresholds.
Handling/pocket-rustle indications also veto new candidates. This reduces mistakes; steady wind classified as
traffic and footsteps over a real road remain failure cases. Do not retune the classifier to force a response.

**Shared setting/lifecycle:** each numbered option has its own Dev Music setting, off by default, outside presets
and dice, starting the listener without Diagnostics. Proposed IDs: `traffic_pass`, `traffic_flow`, `traffic_accent`.
Extend `SoundKindsRunner.settingsTick()`, start/stop and each judgement (including judgements with no kind change).
Read scores/state under its lock and send controls outside it, as the water/birds paths do. Add the scene latch
to `SoundKinds`; keep fast envelope/pass state in the patch, with tuning numbers in `constants.py`. Share one
pass detector between 1 and 3. Turning a switch or Music off, listener failure/stop, or input restart releases
its contribution and clears pending passes; re-enable starts neutral. No stored level/tone/voice/pan setting is
rewritten. All analysis is in memory; no mic audio is saved or requested for testing.

1. **Traffic Passes Through — the pad opens and an earned chime crosses.** A car's rising sound gently opens
   the pad's tone; its departure lets the pad settle. A synth bell/Glass chime earned during that pass fades in
   toward one ear, glides across and fades away at the other. Traffic classification alone starts no chime.
   There may therefore be a pad swell without a bell, or no audible change if the pad is muted and no note is
   earned. That limitation should be heard before deciding whether a car should earn a new note of its own.

   **Starting sound and one knob:** multiply the pad's current effective cutoff by up to 2 (one octave), capped
   at 6 kHz; at an already higher cutoff, leave it there. This preserves the user's tone as the resting sound.
   The actual pad cutoff is `mu_cut_pad` into `mu_pad_lp` in `music.py`, derived from Music Tone, rather than a
   new filter in build.py. Follow the shared pass envelope; no added gain initially, since opening the filter
   already changes perceived level. Tune **maximum opening** first (2x); do not compensate with master volume.
   Pad Only With a Steady Sound still decides whether the pad exists; this option does not force it on or add bass.

   Alternate the staged direction per accepted pass (first left-to-right, next right-to-left). The mono input
   provides no real direction, including when World in Stereo is selected: this proposal uses no stereo direction
   estimator. On the first earned synth chime in a pass, start a 3 s equal-power glide from pan -0.75 to +0.75
   (reverse next pass), with a 250 ms fade-in and a final 1 s fade-out. Multiply its ordinary ring envelope,
   never lengthen or amplify it; a short bell may die before reaching the far ear. Latch its direction and motion
   for that voice. Later notes in the same pass stay ordinary initially, avoiding a cluster of crossing bells.
   If talk starts, fade the traffic motion contribution away in 500 ms without scheduling a note.

   **Where/cost:** Java permission plus the patch pass detector and cutoff control in `music.py`. Chime voices
   currently sum to mono (`mu_v{k}_out`); a true isolated pan needs per-voice stereo gains and a stereo chime
   branch carried through the generated-music processing to the stereo output. Do not use build.py's global
   `space_pan`: that would pan the mic, pad and drums too. Apply Make Room/Underwater and relevant output effects
   consistently to the new branch, avoiding a second centre copy of the same bell. Found melody/Singing Room
   stay where they are; this moves the existing Synth bells/Glass mix without selecting a new Chime Voice.
   No new oscillators, but stereo routing/filtering can add DSP. Rough planning budget: under 2 percentage points
   of a Moto core beyond an already-running listener; unmeasured. The stereo path makes this the largest option.

   **Stacking/risks:** Mind the Gap owns chime holds and answers; a talk-pause answer is not captured for traffic
   motion. Water Leaves a Trail and Trailing Melody retain their counts/timing; at most one eligible earned note
   per pass moves. Birds Lift the Chimes still sets per-note register/voice/trim; traffic only supplies motion.
   Make Room for Music still attenuates; Underwater can hide the brightening and needs equivalent stereo treatment.
   Calm builds still supplies its own slow level/tone changes; apply the traffic multiplier to the resulting pad
   cutoff, not a stale saved value. The swell/pan may feel late on fast cars; a knock over a road may look like a
   pass. A constant motorway yields no repeated sweeps. Pocket filtering may remove both the rise and its identity.
   Global spatial processing can weaken the glide; a mono output cannot demonstrate it. Keep one setting for the
   first combined experiment, with pad-only and pan-only isolation available to the developer's checks.

2. **Traffic Flow — the beat recedes when the road settles.** Once a traffic scene is established, louder
   renewed activity restores the existing beat; a sustained lull eases it down, leaving the pad and other music.
   The beat clock continues silently and returns in place, without a new fill, tempo change or automatic pad.
   This is a response to *audible activity*, not proof that vehicles stopped. Idling engines may still sound
   busy, and a smooth moving stream may sound steady: both are important tests, not solved classifications.

   **Driver/numbers and one knob:** only operate inside the shared settled-traffic latch. Establish an initial
   reference for 4 s without changing gain. Track a low recent level over ~20 s while the scene holds; activity
   is level at least **3 dB above that reference** for 1 s, or a valid pass. Require below 1 dB excess for 4 s to
   call a lull; the gap between thresholds prevents rapid flipping. Repeated passes keep flow active. A steady
   engine or motorway will eventually settle toward the lull state, an honest weakness of this simple detector.
   Start with a 12 dB beat dip over 4 s, restore over 2 s; this is a soft first trial of "thins to the pad", not
   a complete mute. **Tune lull depth first** (12 dB); try a full mute only if DadCraft wants a clearer stop.
   When traffic leaves, return to ordinary beat level over 8 s, so a distant missed car cannot leave the beat
   permanently down. That return may itself feel wrong at a quiet red light; note it in the first listen.

   **Where/cost:** Java scene/permission sends plus patch level history and a gain ramp on the generated beat
   path in build.py, next to `beat_rm` and before the beat's Underwater processing. Combine with existing Make
   Room attenuation by the stronger requested dip, rather than multiplying 12 dB and 6 dB into 18 dB. No audio
   buffer or oscillator, no beat-pattern rewriting. Rough incremental DSP budget under 1 percentage point of a
   Moto core beyond the listener, unmeasured; turning this on alone also incurs the listener's cost.

   **Stacking/risks:** Mind the Gap, Water Leaves a Trail, Birds Lift the Chimes and Trailing Melody retain their
   chime rules; this ducks drums only. Make Room still dips other generated music and keeps its bigger-sound
   threshold; Underwater colours the remaining beat downstream. Calm builds and Pad Only With a Steady Sound
   keep their normal decisions, so there may be neither an audible pad nor a "hold" in silence. With 1 on,
   the first departure can open the pad while restoring the beat; use the slower 2 s beat return so it does not
   leap over the car. Strong wind/handling veto or settled talk releases toward normal gain over 2 s, rather
   than interpreting them as stopped traffic. A bus idling, quiet electric traffic, overlapping engines and a
   phone pocket can all defeat the flow inference. If roadside A/B cannot distinguish lull/return convincingly,
   set this option aside rather than label rumble as vehicle motion.

3. **Road Accents — a pass changes the found beat's emphasis (Codex's addition).** The found-sound rhythm grid
   DadCraft loves stays in place. As a car approaches, its existing low hits gain prominence because the high
   hits soften; as it recedes, the normal balance returns. No new drum hit, recording, note, tempo or pattern is
   created. A passing car shapes sounds already gathered from the surroundings instead of becoming another synth.

   **Driver/numbers and one knob:** use the same accepted-pass envelope as 1, with the same traffic permission,
   wind/handling veto, talk priority and re-arm. At its peak attenuate found hats by 3 dB, snares by 1.5 dB and
   kicks by 0 dB, smoothing over 300 ms in / 1.5 s out. Tune **hat trim** first (3 dB); snare trim stays half that.
   This is deliberately a darkening of the percussion against option 1's opening pad, without boosting bass.
   Stationary rumble starts no accents; on a busy road merged passes may produce only one long change.

   **Where/cost:** gains on existing found kick/snare/hat playback lanes in `rhythm.py`, before the beat sum;
   leave capture, assignment and playback timing intact. Classic drums keep their current sound. It adds three
   smoothed gains, no new voices/buffers; rough incremental Moto DSP budget under 1 percentage point of a core
   beyond the shared listener/detector, unmeasured. With no found hats/snare playing, it may be inaudible.

   **Stacking/risks:** 1 and 3 share a car arc but change different parts; 2 applies its final beat gain after
   these trims and can hide them in a lull. Make Room/Underwater further attenuate or muffle the result. Mind
   the Gap, Water Leaves a Trail and Trailing Melody keep chime counts and timing; Calm builds stays independent.
   Birds Lift the Chimes can prevent new hats being caught while old ones still play; this option neither
   reinstates those catches nor overwrites them. It could sound merely dull, or be imperceptible under effects.
   Judge it after 1 alone, at the normal found-beat mix, before spending time on more elaborate car responses.

**Recommendation:** build **1 and 2 as two independent settings**, one at a time, starting with 1. Share the
analysis, not the on/off decision: a clear pass-by swell and a convincing stop-and-go response may have different
verdicts. Option 1 includes DadCraft's pan on earned chimes; validate the isolated stereo route before listening.
If ordinary cars never earn chimes, explicitly bring that result back to DadCraft before adding a pass-triggered
bell. Option 2 is the weaker inference and should earn its place in a real roadside A/B. Keep 3 as an optional
next experiment after 1 is convincing; it complements the found rhythm without adding more autonomous music,
but should not delay hearing DadCraft's idea. No new user-facing tuning sliders are needed for the first builds.

**First listen (after a pick/build/review):** stationary, safely back from a road, with headphones and normal mic
input; do not walk into traffic to test. No recording. Begin with the normal found-beat sounds/levels already
established, Music on, an audible pad, and the other sound-kind responses, Trailing Melody and Calm builds off.
Leave the normal bigger-sound threshold in place. Diagnostics may show kind/score; it does not have to be enabled
for the response. Use a short off/on comparison of each chosen option separately, then together.
- For 1: several isolated cars, gaps, then steady traffic. Does the pad arc belong to the car despite classifier
  delay? Do cars earn any chime, and if so does one bell fade across without moving the room? A modest knock
  during an established pass can check the pan route, but cannot prove cars themselves earn notes. After the
  road quiets, the normal tone/position should return. Constant rumble must not schedule periodic crossings.
- For 2: a street with real stop-and-go if convenient. Compare pull-away, a lull with engines still idling, and
  moving steady traffic. Does the beat thin/return at useful times, or just react to loudness? Wait through a
  long quiet stop to reveal the 8 s scene-release return. Note false "stops" instead of calling them successful.
- For 3, if picked later: use an already audible found hat/snare/kick pattern, comparing the same three trims off
  and on. Does the car reshape its emphasis recognisably without sounding like a volume fault?
- Then add Mind the Gap, the two water responses, Birds Lift the Chimes, Make Room for Music, Trailing Melody and
  Calm builds individually. Check talk priority, unchanged trail counts and a swell not lost under Underwater.
  Compare exposed phone, ordinary pocket use, wind without cars, and a steady fan indoors. Report missed passes
  and wrong ones; no need to lower thresholds simply to make the feature always respond.

An indoor traffic video/recording played from another device is an honest **routing/timing stand-in** if it has
isolated passes and gaps, no music/narration, and is played at a fixed volume with enough low end for detection.
No Ouralia output goes to the speaker. Stereo direction in the video does not supply direction to the mono
classifier; the proposed alternation remains staged. A video with authentic idling/pull-away can expose obvious
option-2 mistakes, but cannot establish outdoor reliability, wind rejection or distance sensitivity. Do not
record DadCraft's mic to obtain test material. Before his listen, check lifecycle/reset, stale permission and
late-score expiry, no new notes/hits, off-path equivalence and mic isolation, stereo levels and no duplicate
chime, gain composition, then incremental Moto CPU. The budgets above are estimates, not measurements; the
listener's earlier +3.1 core-percentage-point A/B is context, not a new traffic benchmark.

## Step 10: Traffic Passes Through (Codex, Nov 3)

DadCraft picked "1 and 2, build 1 first", after Music in Stereo sounded good. Built option 1 only, Dev Music
switch **Traffic Passes Through** (`traffic_pass`), off by default, outside presets/dice. It starts the listener
without Diagnostics. Traffic Flow (option 2) followed the same day: see "Step 11: Traffic Flow" below. The options above are the original
proposal; the implementation below supersedes its stereo range, fade and routing suggestions.

**Permission and talk:** `SoundKinds` uses traffic's group score on each judgement, including unchanged kinds:
enter after two successive leads over every other non-quiet group at >=0.20; leave after <0.10 or another group
leading continuously for 2000 monotonic ms. Scores between 0.10 and 0.20 hold an existing permission. Settled talk
(the existing independent talk window) vetoes passes even with Mind the Gap off. `SoundKindsRunner` sends one
`traffic_state`: 0 off/reset, 1 listening without permission, 2 permitted, 3 settled talk. State is read under its
lock and sent outside it; Music off, switch off, stop/detach and startup failure clear it. Stop clears the model's
logic immediately; queued old startup/judgement work is discarded and old cleanup closes only its own model.

**Pass detector (`tools/patchgen/traffic.py`, tuning in `constants.py`):** raw `st_mid`, the same pre-effect feed
as classification, through `env~ 1024 512` then a 150 ms envelope at 50 ms ticks. On enabling, a **500 ms warm-up**
seeds the baseline from the current room; this prevents a constant sound from looking like a pass while the meter
fills. The baseline follows with a 12 s time constant and freezes during a pending candidate or accepted pass.
Forty scalar levels keep 2 s of history (no mic recording). A >=4 dB rise against both baseline and history
starts a candidate; it must remain >=4 dB for 300 ms. Permission can arrive up to 1.5 s after that rise; an expired
candidate cannot be revived. A fall within 2 dB cancels a pending candidate. A pass ends after 1 s within 2 dB,
a veto/permission loss, or 10 s maximum, followed by a 2 s re-arm gap. Flat rumble supplies no new rising edge.
The 0–10 dB excess maps to 0–1 with 250 ms attack / 1.5 s release; the multiplier interpolates between ticks.

**Vetoes:** reuse `dcs_dewind~`'s third signal outlet. Inspection of `dcs/src/dcs_dewind_tilde.c` confirms it is
0–1 independently of `amount`/Wind Guard: >0.6 for 500 ms latches the veto, <0.3 for 2 s clears it. Handling above
0.15, an active jolt, or pocket state veto candidates and release passes too. This is conservative: the existing
wind detector can also call deep noisy traffic rumble windy, so some real cars may be missed. No classifier or
Wind Guard tuning changed.

**What changes in the music:** apply `1 + envelope` to the **live** `mu_cut_pad` after Music Tone/Calm builds,
up to 2x and capped at 6 kHz (an already higher resting cutoff stays unchanged). No saved setting, pad level,
chord, bass, or gain changes. The pad's existing steady-sound condition still decides whether it sounds at all.

On the first earned Synth/Glass note of an accepted pass, use the existing `music_pan0..3` hook: ±0.6, a 3 s
control glide, first left to right then alternating per accepted pass. The ordinary per-note place lands first;
the traffic override follows it. Found-only notes with an available catch leave this opportunity pending for an audible Synth/Glass note. Later notes retain their normal places. Voice reuse stops its old glide before
placing the new note. Talk/pass/switch/listener/Music stop returns a moving voice to its latched ordinary place
in 500 ms. No extra voice fade: the ordinary ring remains, and a short bell may die before reaching the far ear.
No stereo branch, oscillator or Pd `[random]` was added. The hook's existing equal-power gains and 5 ms smoothing
remain. **Music in Stereo must be on for the crossing**; off, only the pad opening is audible. Stereo off clears
pending motion and returns a moving voice. Re-enabling begins neutral; no old candidate/pass is replayed.

Make Room, Underwater, Birds Lift the Chimes, Trailing Melody and found sounds retain their existing paths and
settings. Direction is staged, never inferred from the mono input. Classification alone generates no note;
a passing car can yield a pad opening without a bell, and muted pad plus no earned chime yields no audible change.

**Listening/checks:** compare the switch Off/On at fixed settings with Music in Stereo on, first steady traffic
then separated passes, followed by talk/handling/wind. Listen for the pad opening on the rise and at most one earned
bell crossing; check that a continuous engine does not keep retriggering. Claude Code reviewed it (Nov 3, 74dc295).
Moto CPU A/B (Nov 3, Ouralia Dev playing, Music + Music in Stereo + Birds Lift the Chimes on so the listener runs;
`top` 2 s samples, a flaky USB link kept 14 and 20 of 30): Traffic off median 62.0% of a core, on 59.25%, ranges
54-67% both: no measurable cost (target <2 extra points), 0 underruns in logcat. DadCraft's roadside verdict remains open. Desktop checks
and measurements are recorded in the handoff and below once complete; these do not establish roadside accuracy.

Rough desktop patch-only CPU A/B (three repetitions, median process time, 24 s synthetic audio each): old
0.778 s, traffic off 0.807 s, on 0.823 s. About 0.19 percentage points of a desktop core beyond the old path,
0.07 beyond Off; this excludes YAMNet and is not a phone estimate. Actual pre-change 421f9bf versus the new patch
with traffic off: fixed-seed synthetic pad/chime passage sample-identical in mono and stereo, peak difference 0.

**Verification:** full tier passed, 185 Pd sections plus all Java, desktop, release and eight supporting Pd suites
(445 s); Dev build and Java runner lifecycle tests passed. The first full run exposed a test-layout issue:
adjacent top-level comments were splitting sequential traffic checks into separate workers. Kept the checks in
one section, and measured toggle release with 5 ms channel projection (maximum ratio step 0.060), rather than
pointwise division near waveform zero crossings. The passing traffic checks include startup neutrality,
rise/permission combinations, late/expired candidates, all vetoes, max duration/re-arm, live cutoff and 6 kHz cap,
~3 s alternating glide within ±0.6 and equal power within 1 dB, later/Found-only notes, voice reuse, talk's 500 ms
return, off/stop/re-enable, mono with Music in Stereo off, finite audio and sample-identical off bypass. Runner tests
cover independent listener start, unchanged-kind judgements, talk with Mind the Gap off, Music/switch stop,
queued startup/cleanup/judgements across restart, and startup failure. No install; Claude review/Moto/listen next.

## Step 11: Traffic Flow (Claude Code, Nov 3)

DadCraft: "lets get the other traffic done" (roadside listen for option 1 deferred). Built option 2 as its own
Dev Music switch **Traffic Flow** (`traffic_flow`, `.dcs()`), off by default, outside presets/dice, starting the
listener without Diagnostics. Turning it on alone does not turn on option 1.
- **Scene latch (Java):** `SoundKinds.trafficScene()`: traffic the settled kind for 40% of the last 80 judgements
  (~20 s, Make Room's window, so a red-light lull of ~15 s keeps it), gone at 15%. `SoundKindsRunner` sends
  `traffic_flow_state` (0 off, 1 no scene, 2 scene, 3 settled talk) through the same tick as `traffic_state`
  (one code, pass + 4 x flow), so every start/stop/reset/failure path Codex guarded covers it too.
- **Lull/activity (patch, `traffic.py`):** either switch runs the shared 50 ms level detector; passes still need
  `traffic_state` 2, so Flow alone uses level activity only. Reference: follows the level for the first 4 s of a
  scene (no dip), then the low recent level (falls at once, rises with a 20 s time constant). Activity: 3 dB above
  it for 1 s, or an active pass. Lull: within 1 dB for 4 s. Wind/handling/pocket vetoes, talk, Music off and the
  scene ending clear the lull.
- **Beat dip (`build.py`):** a lull ramps the beat 12 dB down over 4 s (`TRAFFIC_FLOW_DIP_DB`, tune first);
  activity, talk or a veto bring it back over 2 s, the scene ending over 8 s. Combined with Make Room as the
  stronger dip (`max~` of the two dips), never both; off is exactly the old `rm_gain`. Beat clock untouched.
- **Checks:** test_patch "7z9. traffic flow" (settle 1.000, lull 0.251, activity back to 1.000; scene leaving
  0.626 at 4 s, 1.000 at 8.5 s; with Make Room 0.251; wind, off and Music off never dip). J2: Flow alone starts the
  listener, latches after the window fills, keeps pass state 0, holds through a 15 s lull, leaves, stops.
- **Moto CPU (Nov 3, Ouralia Dev playing, Music + Music in Stereo on, `top` 30 x 2 s):** Birds Lift the Chimes on
  (listener running) median 57.75% of a core, plus Traffic Flow 57.0%: no measurable cost; 0 underruns.
- **Known weakness (as proposed):** a steady engine or motorway reads as a lull after 4 s and thins the beat;
  idling engines may read as activity. Judge it at a real stop-and-go street; set it aside if lull/return
  doesn't track the road convincingly.


## The runner's Pd sends, one queue (review finding 2, Claude Code, Nov 4)

The project review (`docs/PROJECT-REVIEW-2026-11-03.md`, finding 2) found that `SoundKindsRunner` read its state under
its lock and sent to Pd after letting go. A stop on another thread could send its neutral state in that gap, and the
older startup (`sk_on=1`) or judgement (water/birds/traffic, room music, talk) then landed after it: the ring back on
with nothing reading it, or a stale effect left on. Now every Pd send the runner makes is queued, while the lock is
still held, on one `SoundKindsPd` thread: sends reach Pd in the order their state was read, so a stop's neutral state
always comes last. Sends still run outside the lock (Pd's thread calls `ready()` holding Pd's lock, so sending under
ours could deadlock). No behaviour change otherwise; sends are now a moment later, on that thread. Java test J2 pins it:
nothing is sent from the caller's thread, and stop during inference, stop during loading and an immediate restart all
end in the right state. The race itself needs real threads, which the single-threaded test stubs can't stage.

## The classifier's own cost (review performance item, Claude Code, Nov 4)

DadCraft: "move ahead on the classifier." Measured first: the Moto's minute lines put YAMNet at a median 9.5 ms per
judgement, ~235 a minute, i.e. under 4% of a core in an app at ~60%; the patch's DSP is the bulk, not the classifier.
Around YAMNet each judgement copied ~1 s of ring into a new array, rebuilt the 65-tap low-pass and resampled into
another new array (~1 MB a second allocated), computing two filtered points per output sample. Now the low-pass is
made once per sample rate, each worker thread reuses its two buffers (per thread: an old worker can still be finishing
after a restart), and the second point is skipped when its weight is exactly 0, which at 48 kHz (step exactly 3) is
always. Output sample-identical to the old resampler at 48 and 44.1 kHz (test J keeps the old one as reference).
Desktop: 1.94 -> 0.81 ms per judgement. The minute line now also gives the whole judgement (ring copy + resampling +
YAMNet) and the ring copy. On the Moto (3 minutes): YAMNet median 9.5 ms, whole judgement 17-19 ms, ring copy
0.1-0.2 ms (now at most two reads, i.e. two takes of Pd's lock, not four): the rest is resampling, wall time
including the audio thread pre-empting the worker. So the resampler keeps what it computed: consecutive judgements
read the ring one segment on, and at 48 kHz a segment (12288 samples) is exactly 4096 outputs, so the older 11504 are
the last output moved along, sample for sample, and only 4096 are computed (`SoundKinds.Resampler`; a skipped
judgement, a restart, another rate or the first time computes all 15600). Test J: identical to the full computation
through a wrapping, skipping sequence. Left as it was: the listener keeps running while Music is off
(test J2 checks it on purpose, the sound-kind context stays warm); stopping it there is DadCraft's call.

Moto reading of the kept output (Nov 4, Music + Music in Stereo, 3 minutes): whole judgement median 16.7, 21.1,
21.4 ms, but YAMNet alone ran slower this time (12.0, 15.0, 15.3 ms, against 9.5), so the totals hide the change.
What sits around YAMNet (whole minus YAMNet medians, approximate) fell from ~8-9 ms to 4.7-6.1 ms, about a third less;
ring copy 0.2-0.3 ms. The remainder is mostly wall time: the worker pre-empted by the audio thread, which no
resampler change removes. Why YAMNet itself varies (9.5 to 17 ms across today's runs) is open: the phone's load or
heat, not this code.

**Music off stops the listener (DadCraft, Nov 4: "yes, let's have it stop listening while those things that don't need
it aren't on. it shouldn't take much for it to get up to speed once it turns on, right?").** Every sound-kind setting
acts on the music, so they keep the listener running only while Music is on; the Diagnostics switch still listens
always. Music off stops YAMNet, its thread and the patch's ring writer (`sk_on` 0) and logs "Sound kinds: off (Music
off)"; Music on starts it again. Getting up to speed: the model loads, the first judgement needs ~1 s of sound, then
the windows fill from silence: about 3 s for talk, water and birds (13 of 32 judgements at ~4 a second) and about 8 s
for room music and the traffic scene (32 of 80). Until then the music simply plays as if nothing was heard. Test J2:
Music off stops it and its ring, on starts it, starting to play with Music off doesn't listen.

## Step 12: Mixed Surroundings (review item 3, Claude Code, Nov 4; Dev only, off by default; DadCraft's listen next)

DadCraft (Nov 4), choosing from the review's reactive proposals: 2 first, "item 3 would be my second choice", then
"go ahead and start on mixed surroundings". The problem: every scene (Make Room for Music, Mind the Gap, water,
birds, the traffic scene) counted a judgement only while its kind *won* it, so in a real mix (talk by a fountain,
birds by a road) each got part of its window and maybe neither came on; nothing scoring (an unfamiliar sound) read
as "quiet" (the Moto's events log, Oct 31-Nov 4: "Heard: quiet" 2734 times, the most of any).
`sk_mixed` ("Mixed Surroundings", Music, toggle, noPd, noPreset, soundKinds) sets `SoundKinds.mixed`. On:
- A kind counts in its scene's window while it is clearly there: its score at least `PRESENT_MIN` (0.2) and at least
  `PRESENT_REL` (half) of the strongest kind's, unless YAMNet's top class is silence. The windows, their lengths and
  their on/off shares are unchanged, as is the Now line's one kind (`kind()`) and Traffic Passes Through's fast permission.
- The two decorative gestures don't stack: while water and birds are both settled, the one with the louder ~8 s
  average score (`AVG_N` 32 judgements) leads (`waterLeads()` for Water Leaves a Trail, `birdsLead()` for Birds Lift
  the Chimes); the other takes over only past `LEAD_RATIO` (1.25x). Underwater still follows `water()`.
- No kind above QUIET_FLOOR while YAMNet isn't hearing silence is "unsure" (`unsure()`, "Heard: unsure"), not quiet.
  Calm Builds judges quiet from the level, so nothing there changes.
Off: exactly as before (test J "mixed off"). Talk keeps its existing priority (the traffic responses give way to it);
whether water and birds should also hold back for talk is DadCraft's call.
Data: each judgement's seven scores now go into the session log (`"ev":"kinds"`, NAMES order; ~4 a second, ~1 MB an
hour, under the 50 MB cap) through `SoundKindsRunner.scoresSink`, so real mixes can be replayed to tune PRESENT_*
(the first numbers are judgement, not measured: there were no stored scores). Tests: J "mixed" (talk by a fountain
and birds by a road, both heard on, only the winner off; the gestures lead and don't flap on an even mix; unsure vs
YAMNet's silence), each made to fail once to see its numbers.
