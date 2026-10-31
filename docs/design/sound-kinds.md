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
