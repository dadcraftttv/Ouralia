# Making kits for Ouralia: the full reference

New to kits? Start with [MAKING-KITS.md](MAKING-KITS.md), a step-by-step guide. This page lists every setting.

A **kit** decides what the music sounds like. The app writes the music by itself: it picks the chords, melodies, bass
lines and drum patterns from what it hears around you. A kit says which instrument plays each part. You can make a kit
from the built-in synth engines, from your own recordings, or from both.

A kit is one text file, `kit.txt`. A kit that uses recordings is a folder (or a zip of one) holding `kit.txt` and the
sound files.

```
# Lines starting with # are notes to yourself.
title: Night Glass
mel: engine modal, modal bell, bright 0.6, level 6
bass: engine sub, decay 2000, drive 0.4, glide 80, level 2.5
pad: engine analog, saws 5, detune 20, cutoff 2, attack 1500, release 2500, level 0.5
drums: engine drums, ktune 45, kdecay 600, hchance 0.5, pump 0.4, pumprel 250, level 0.38
```

- `title:` is the name in the Kit menu. Without one, the folder's name is used.
- Each part gets one line: the part name, a colon, then settings separated by commas. Each setting is a name and a
  value. Put `engine` first so the line is easy to read; the order doesn't matter to the app.
- A part you leave out plays the app's own sound for it (**Classic**).
- Names are not case-sensitive. A setting the app doesn't know is skipped, and the import report names it.

## The parts

| Part | What it plays | How notes arrive |
|---|---|---|
| `mel` | The melody: chimes when you stand still, walking notes as you move, phrases | Single notes. Each one has a length, and the engine lets it ring |
| `bass` | The bass line | One held note that moves when the chord changes |
| `pad` | The chords | Up to four held notes. A new chord fades in as the old one fades out |
| `drums` | The beat: kick, snare and hats (open or closed) | Hits. Only the `drums`, `chip` and `sample` engines play them |

## Settings every engine understands

| Setting | What it does | Default |
|---|---|---|
| `level` | Volume. Starting points for each part are in the next table | 1 |
| `octave` | Moves every note this many octaves (−2 to 2) | 0 |
| `attack` | Fade-in time of a note, in ms (bowed, analog, chip, sample) | 300 |
| `release` | Fade-out time after a note ends, in ms | 800 |
| `chance` | How likely each melody note is to play, from 0 to 1. Lower values give a sparser part | 1 |
| `hchance` | The same, for hats | 1 |
| `bright` | Brightness from 0 to 1 (pluck, bowed, modal) | 0.4 |
| `decay` | How long a held note rings, in ms (pluck, sub, chip) | 3000 |

### How loud is `level 1`?

For the `sample` engine, `level 1` with a normalized recording is about as loud as the Classic part. The other engines
need different levels for each part. These are the levels the built-in kits use:

| Engine | `mel` | `bass` | `pad` | `drums` |
|---|---|---|---|---|
| `pluck` | 4.3 | | | |
| `bowed` | | 5 | 0.05 | |
| `modal` | 6–8 | | | |
| `analog` | 7 | 13 | 0.5 | |
| `sub` | | 2.5 | | |
| `drums` | | | | 0.38 |
| `chip` (NES) | 6.5 | 30 (the NES bass is very thin) | 1 | 2.5 |
| `chip` (Genesis) | 6.8 | 14 | 0.17 | 1 |
| `sample` | 1 | 1 | 1 | 1 |

## Engines

`engine classic` is the app's own sound for that part (the same as leaving the line out).

### `pluck`: a plucked string
A physically modeled string, from harp to guitar. Settings: `bright`, and `decay` for how long held notes ring.

### `bowed`: a bowed string
The same string, kept sounding by a bow, like a cello or a string section. Settings: `bright`, `attack`, `release`.

### `modal`: struck instruments
Tuned resonators struck by a mallet. Set `modal` to `bell`, `marimba` or `bowl` (a singing bowl), and adjust `bright`.

### `analog`: a synthesizer
Sawtooth oscillators through a resonant 4-pole filter.

| Setting | What it does | Default |
|---|---|---|
| `saws` | Number of saws, 1 to 7. Use 7 for a big supersaw | 2 |
| `detune` | Spread between the saws, in cents | 8 |
| `sub` | A square wave an octave down, 0 to 1 | 0.3 |
| `cutoff` | Filter cutoff, as a multiple of the note's pitch | 4 |
| `reso` | Filter resonance, 0 to 1 | 0.3 |
| `fenv` | How far the filter opens at the start of each note | 6 |
| `wobble`, `wrate` | Volume and filter pulsing in time with the beat: depth 0 to 1, then cycles per beat | 0, 2 |
| `bend`, `bendms` | Each note starts this many semitones off and glides to pitch over `bendms` ms | 0, 120 |

### `sub`: an 808 bass
A sine wave with drive. Settings: `decay` (ms), `drive` (0 to 1), and `glide` (ms to slide between notes). The app
retriggers the note on each kick, so the 808 plays in rhythm.

### `chop`: vocal chops (melody only)
Plays short slices of a note you sang or hummed, pitched to the melody. Set the slice length with `chop` in ms. Until
you've sung something, it plays the `analog` sound instead, using any analog settings on the line.

### `drums`: a drum machine
| Setting | What it does | Default |
|---|---|---|
| `ktune`, `kdecay`, `kdrive` | Kick: pitch in Hz, length in ms, drive 0 to 1 | 50, 350, 0.3 |
| `stone`, `sdecay` | Snare: tone in Hz, length in ms | 185, 180 |
| `hdecay` | Closed hat length, in ms | 45 |
| `pump`, `pumprel` | Sidechain pump: how far the rest of the music ducks at each kick (0 to 1), and how fast it comes back (ms). Set it on the `drums` line | 0, 250 |

### `chip`: a game console's sound chip
| Setting | What it does | Default |
|---|---|---|
| `chip` | `nes`, `gameboy` or `genesis` | nes |
| `wave` | `pulse`, `triangle` (NES), `wave` (Game Boy's 4-bit channel) or `fm` (Genesis) | pulse |
| `duty` | Pulse width: 0.125 is thin, 0.25 is nasal, 0.5 is hollow | 0.5 |
| `sus` | The level each note falls to over `decay` | 0.6 |
| `vib` | Vibrato depth in cents, starting 150 ms into a note | 0 |
| `arp` | For chords: ms per note as the chord's notes take turns on one channel, the way chip composers wrote chords. 0 plays them together | 0 |
| `fmratio`, `fmindex`, `fmfb` | Genesis FM: modulator pitch ratio, brightness, grit | 1, 2, 0 |

Like the real hardware, each part plays one note at a time, and very high notes come out slightly out of tune. On the
`drums` line, `chip` plays the console's noise channel.

### `sample`: your own recordings
Put the sound files in the kit's folder and name them in `kit.txt`:

```
title: My Keys
mel: engine sample, sample keys c4.wav C4, sample keys c5.wav C5, attack 2
bass: engine sample, sample bass.wav C2, loop 1, loopstart 200, release 200
pad: engine sample, sample strings.wav A3, loop 1, loopstart 500, attack 400, release 800
drums: engine sample, kick kick.wav, snare snare.wav, hat hat.wav, ohat open hat.wav
```

- `sample <file> <note>`: the file and the note it plays, as a name (`C4`, `F#3`, `Bb2`) or a MIDI number (C4 = 60).
  If you leave the note out, it's C4. File names can contain spaces. A part can have up to 16 recordings, and each
  note plays the one closest to it, sped up or slowed down to fit. More recordings sound more natural.
- `oneshot`: 1 lets a recording ring to its end (the default; good for plucks, pianos and bells). 0 stops it when the
  note ends, then fades out over `release`.
- `loop 1`, `loopstart`, `loopend` (ms): held notes (`bass`, `pad`) can last longer than the recording. The loop
  repeats from `loopstart` to `loopend` with a crossfade. A `loopend` of 0 means 50 ms before the end of the file.
  Pick a steady stretch with no attack in it.
- Drums: `kick`, `snare`, `hat`, and optionally `ohat` (an open hat, used when the pattern asks for a long hat).
- Formats: WAV (8, 16, 24 or 32-bit, or float) plays directly. On a phone, MP3, OGG, FLAC and M4A are converted when
  the kit is imported. The desktop app plays WAV only. Stereo files are mixed to mono.
- Limits: 30 seconds per file, and about 2 minutes of sound per part.
- Normalize your recordings (peaks near full scale), and `level 1` will be about as loud as the Classic part.

## Installing and sharing a kit

- **On the phone:** go to the Music tab → **Your kits** → **Import kit**. Pick a `kit.txt`, a zip, or (with Import kit
  folder) a folder. `kit.txt` can be at the top of the zip or one folder down. The kit plays straight away, and the
  report lists anything it couldn't use.
- **Mix and match:** each part can come from a different kit (Music tab → Melody from, Bass from, and so on).
- **Layers:** each part can also play a second kit at the same time (Music tab → Melody layer, Pad layer, and so on),
  with a blend slider between the two. A kit that's quiet on its own may need a higher `level` to hold its own in a
  blend. The sidechain pump comes from the first kit's `drums` line.
- **Presets:** a preset saved with your kit carries the kit's text, so a friend who loads it hears your kit. Recordings
  don't travel inside presets. Until your friend imports the kit's zip, the parts that use recordings play Classic.
  Once they import it, those parts fill in.
- **Desktop app (a kit-making bench):** kits live in `~/.local/share/ouralia/kits/<number> <title>/kit.txt`. Edit
  the file while the music plays. Each time you save, the parts you changed play again within a second.

## Tips

- Start from a built-in kit. Their files are in the app's source, under `app/src/main/assets/kits/`.
- Change one part at a time and compare it with Classic using that part's "from" menu.
- The app plays music around you, often quietly and for a long time. Soft attacks and gentle tones last better than
  sharp, bright ones.
