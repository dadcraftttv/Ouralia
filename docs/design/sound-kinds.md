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

## 4c progress (Oct 31, branch `sound-kinds`, Ouralia Dev only; not finished)

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
gate. Still open: DadCraft's call (accept on the estimate, or a screen-off rerun), then 4d (his walk).
