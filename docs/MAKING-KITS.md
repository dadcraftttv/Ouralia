# Archived kit guide

Selectable kits were removed on `reactive` Oct 30, 2026 (DadCraft: "remove the kits";
"leave Classic the way it is, no change to accoustic"). This guide applies only to older releases.
The current app uses the existing Classic Pd voices and Found sounds; kit import and kit presets are removed.

# Making a kit, step by step

This guide starts from zero. If you want every setting in one place, the full reference is
[KIT-FORMAT.md](KIT-FORMAT.md).

## What a kit is

Think of the app as a band that makes up its music as it goes. It listens to the room and to how you move, then
decides which chord comes next, what the melody does, when the drums hit and how busy everything gets.

**The kit only decides which instruments the band plays.** It never changes *what* they play. The same melody can come
out as a plucked guitar, a bell, a game-console beep or a recording of your door chime, depending on the kit.

The band has four players. In a kit they are called **parts**:

| Part | Its name in the file | What it plays |
|---|---|---|
| Melody | `mel` | Single notes on top: chimes when you stand still, walking notes when you move |
| Bass | `bass` | One low note at a time, which changes when the chord changes |
| Pad | `pad` | The chords: up to four soft notes held together underneath everything |
| Drums | `drums` | The beat: a kick, a snare and hi-hats |

You don't have to fill in all four. A part you leave out plays the app's normal sound, called **Classic**.

## Your first kit, in five minutes

A kit is a plain text file named `kit.txt`, the same kind of file as a note in Notepad. Here's a complete one:

```
title: My First Kit
mel: engine modal, modal bell, level 6
```

That's all of it. It makes the melody play on bells and leaves the other three parts as Classic.

**To try it:**

1. In any text editor (on a computer, or a text-editor app on the phone), type those two lines.
2. Save the file as `kit.txt`.
3. Get it onto the phone if you made it on a computer (USB cable, Google Drive or email to yourself are all fine).
4. In the app, open the **Music** tab, scroll down to **Your kits**, tap **Import kit** and pick the file.
5. Press Start, with Music switched on. The melody now rings like bells.

"My First Kit" now appears in the **Kit** menu and in every part's "from" and layer menu.

## Reading a line

Every part line has the same shape:

```
mel: engine modal, modal bell, level 6
└┬┘  └─────┬────┘  └───┬────┘  └──┬──┘
part   setting      setting    setting
```

- **The part name**, then a colon (`:`).
- Then **settings, separated by commas**. Each setting is a word and a value with a space between them: `level 6`
  means "set the level to 6".
- **`engine` picks the instrument.** Everything else adjusts it. The engine can go anywhere on the line, but putting it
  first makes the line easier to read.
- Capitals don't matter. `Level 6` and `level 6` are the same.
- A line that starts with `#` is a note to yourself. The app ignores it.
- `title:` is the name shown in the menus.

If you make a typo, nothing breaks. The app skips the setting it doesn't understand and tells you which one when you
import the kit.

## Picking instruments (engines)

| Engine | Sounds like | Good for | A line to start from |
|---|---|---|---|
| `pluck` | A plucked string: harp, guitar | Melody | `mel: engine pluck, bright 0.45, level 4.3` |
| `bowed` | A bowed string: cello, a string section | Bass, pad | `pad: engine bowed, bright 0.3, attack 1200, release 2000, level 0.05` |
| `modal` | Something struck: `bell`, `marimba`, or `bowl` (a singing bowl) | Melody | `mel: engine modal, modal marimba, level 7` |
| `analog` | A synthesizer | Anything | `pad: engine analog, saws 5, detune 20, attack 1500, release 2500, level 0.5` |
| `sub` | A deep 808 bass that hits with the kick | Bass | `bass: engine sub, decay 2000, drive 0.4, level 2.5` |
| `drums` | A drum machine | Drums | `drums: engine drums, level 0.38` |
| `chip` | An old game console (`nes`, `gameboy`, `genesis`) | Anything | `mel: engine chip, chip nes, duty 0.25, level 6.5` |
| `chop` | Little slices of a note you sang or hummed | Melody | `mel: engine chop, chop 160, level 7` |
| `sample` | **Your own recordings** | Anything | See "Using your own recordings" below |
| `classic` | The app's normal sound | Anything | Same as leaving the line out |

The fastest way to learn the engines is to copy one line from a built-in kit, import it, and change one number at a
time. The built-in kits are listed under "Copy a built-in kit" at the end of this guide.

## The settings every instrument understands

| Setting | In plain words | Example |
|---|---|---|
| `level` | How loud the part is. See the next section, because this differs a lot between engines | `level 6` |
| `octave` | Moves every note up or down by whole octaves (-2 to 2). `octave -1` is deeper, `octave 1` is higher | `octave -1` |
| `attack` | How slowly each note fades in, in milliseconds (1000 = one second). Small = a sharp start, big = a soft swell | `attack 800` |
| `release` | How long a note takes to fade away after it ends, in milliseconds | `release 2000` |
| `bright` | 0 = dull and soft, 1 = bright and sharp (pluck, bowed, modal) | `bright 0.6` |
| `chance` | How many melody notes actually play, from 0 to 1. `chance 0.5` skips about half, so the melody gets sparser | `chance 0.5` |
| `hchance` | The same thing for the hi-hats | `hchance 0.5` |

## Getting the volume right

`level` isn't the same scale for every engine, which is the most confusing part of making a kit. These are the levels
the built-in kits use, so start there:

| Engine | Melody | Bass | Pad | Drums |
|---|---|---|---|---|
| `pluck` | 4.3 | | | |
| `bowed` | | 5 | 0.05 | |
| `modal` | 6 to 8 | | | |
| `analog` | 7 | 13 | 0.5 | |
| `sub` | | 2.5 | | |
| `drums` | | | | 0.38 |
| `chip` | 6.5 | 14 to 30 | 0.17 to 1 | 1 to 2.5 |
| `sample` | 1 | 1 | 1 | 1 |

**To check a part's volume**, compare it with Classic. On the Music tab, flip that part's "from" menu between your kit
and Classic while the music plays. If yours jumps out, lower its level. If it disappears, raise it. Doubling or halving
the number is a good-sized step.

## Using your own recordings

This is where kits get personal. Record a sound with your phone's voice recorder app (Ouralia never records anything
itself) and the band can play it.

### Where the sound files go

A kit with recordings is a **folder** holding `kit.txt` and the sound files:

```
My Kitchen Kit/
    kit.txt
    spoon on glass.wav
    door knock.m4a
    squeak.mp3
```

To install it, use **Import kit folder** and pick the folder, or zip the folder and use **Import kit**. WAV, MP3, OGG,
FLAC and M4A all work on the phone (the desktop app plays WAV only). Each recording can be up to 30 seconds long.

### Recordings as drums (the easiest way, and great for sounds with no note)

On the drums line, each recording plays **exactly as you recorded it**, with no pitch change:

```
drums: engine sample, kick door knock.m4a, snare squeak.mp3, hat keys jingle.wav
```

- `kick`, `snare` and `hat` name the file for each drum. File names can contain spaces.
- `ohat` is optional: an "open hat", used when the beat wants a longer hat.
- You don't have to replace all of them. A drum you don't name stays silent, so name all three for a full beat.

**This is the best home for a door squeak, a knock, a cough, a clap or a cupboard bang.** The rhythm comes from the
band, and the sound comes from your house.

### Recordings as melody, bass or pad (the band plays them at different notes)

On the other three lines, the band plays your recording **at different notes**: speeded up for higher notes and
slowed down for lower ones, like a tape. So the file needs one more piece of information: **which note it already
is**. That's the note you write after the file name:

```
mel: engine sample, sample spoon on glass.wav C5
```

**What "C5" means:** notes are named by a letter (C, D, E, F, G, A, B, with `#` for sharp and `b` for flat) and a
number for how high they are. Middle C on a piano is `C4`. `C5` is one octave above it, `C3` one octave below.

**How to find a sound's note:**

- Use a free tuner app (the kind for guitars), play the sound into it, and read the note it shows. Door chimes,
  glasses, bells and hums usually have a clear note.
- If you don't know, write `C4`, or leave the note out (it then counts as C4). The melody will still follow the music,
  just maybe a little off from the other parts. For a squeak or a sound with no real note, it doesn't matter much.

**Sounds with no note (a squeak, a swish, a creak) on the melody:** they work, and they become a funny "instrument":
the squeak plays the melody, higher and lower. Two tips:

- Melody notes sit fairly high, so a squeak can turn into a chipmunk. Add `octave -1` to bring it down.
- `oneshot 1` (the default) lets each recording play to its end. That's right for a door chime, a bell or a squeak.

**Several recordings make it sound more natural.** If you record the same instrument at different notes, list them
all and each note uses the closest one:

```
mel: engine sample, sample chime low.wav C4, sample chime high.wav C5
```

**Long, steady sounds as a pad** (a fridge hum, a fan, a held "ooh"): the pad holds notes for a long time, longer
than most recordings. `loop 1` makes the recording repeat its steady middle part for as long as the note lasts:

```
pad: engine sample, sample fridge hum.wav A2, loop 1, loopstart 500, attack 800, release 1500
```

`loopstart 500` means "repeat from half a second in", which skips the start of the recording. Pick a point where the
sound has settled.

### Recording tips

- **Trim the silence at the start.** If the sound starts half a second into the file, every note comes half a second
  late. Most voice recorder apps can trim, and so can free audio editors like Audacity on a computer.
- **Record somewhere quiet** and close to the sound.
- **Keep it short.** A drum hit needs well under a second, and a melody note one or two seconds.
- **Make it loud.** "Normalize" in an audio editor turns the recording up as far as it goes without distorting. Then
  `level 1` is about as loud as Classic.

## A whole kit, explained line by line

```
# My Kitchen Kit: the house plays the music.
title: My Kitchen Kit

# The melody: a spoon tapped on a glass. A tuner said it rings at about E5.
mel: engine sample, sample spoon on glass.wav E5, octave -1, chance 0.7

# The bass: the built-in 808, so the low end stays solid.
bass: engine sub, decay 2000, drive 0.4, level 2.5

# The chords: a soft synth, slow to fade in and out.
pad: engine analog, saws 5, detune 20, attack 1500, release 2500, level 0.5

# The beat: a knock for the kick, the cupboard door's squeak for the snare, keys for the hats.
drums: engine sample, kick door knock.wav, snare squeak.wav, hat keys jingle.wav
```

## Mixing kits without writing anything

You can also build a sound from kits that already exist, with no files at all. On the Music tab:

- **Mix and match:** "Melody from", "Bass from" and so on take one part from another kit. For example, the EDM drums
  under the Acoustic strings.
- **Layers:** play a second kit on the same part, with a blend slider between the two. For example, the NES pad and
  the Synth pad together.

## Sharing a kit

- **Send the file (or the zipped folder).** Your friend imports it the same way.
- **Save a preset.** A preset carries your kit's text, so a friend who loads it hears your kit. Recordings don't fit
  in a preset, though: the parts that use them play Classic until your friend also imports the kit's folder or zip.

## When something's wrong

| What happens | Why, and what to do |
|---|---|
| A part sounds exactly like Classic | The line has a mistake in its part name or engine, or a recording it names isn't there (the file name must match exactly, spaces included). Check the import report, and that the line starts with `mel:`, `bass:`, `pad:` or `drums:` |
| A part is silent, or one drum never plays | Its `level` is too low for that engine (see the volume table). With recorded drums, a drum you didn't name (`kick`, `snare`, `hat`) stays silent |
| A part drowns everything | Its `level` is too high. Halve it |
| Recorded notes are late | The recording has silence at its start. Trim it |
| A recording sounds like a chipmunk | It's being played much higher than its note. Add `octave -1`, or check the note you wrote after the file name |
| The import report names a setting | It's a typo or a setting that engine doesn't use. That setting is skipped and everything else still works |

## Making kits on the computer (the fast way)

The desktop app is the best kit-making bench. Kits live in `~/.local/share/ouralia/kits/`, one folder each. Open a
kit's `kit.txt` in a text editor while the music plays, change a number and save. **The part you changed plays the
new sound within a second.** When it sounds right, copy the folder to the phone and import it.

## Copy a built-in kit

The best starting point is a built-in kit. Their files are in the app's source code, in
`app/src/main/assets/kits/`: `acoustic.txt`, `synth.txt`, `edm.txt`, `futurebass.txt`, `lowend.txt`,
`chipnes.txt`, `chipgb.txt` and `chipgenesis.txt`. Copy one, change its `title:`, import it, and change it a little
at a time.

Every setting for every engine (filter, wobble, chip, drum tuning, loops) is in [KIT-FORMAT.md](KIT-FORMAT.md).
