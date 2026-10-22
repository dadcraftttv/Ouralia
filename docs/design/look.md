# The look (Ouralia redesign, from Oct 19)

<!-- Part of the design notes: the index, how we work and the map of these files are in docs/DESIGN-NOTES.md. -->

DadCraft (Oct 19): "very plain"; wants "a solid, finished product": sleek, modern, clean but not bland, light on
battery, Light/Dark/AMOLED kept, and one day an iOS app. Mockups (claude.ai canvas "Ouralia look directions", page
"A + B mashup") chose: A's cards and controls (Material You), B's Intensity ring dial and pill tab bar, **Ouralia
violet** (#8B6CFF, his own pick before he saw it). Built tab by tab; he judges each on the phone.
- **`Look.java`** (plain Java, no Android: tests, desktop and a future iOS port share it): every theme colour from one
  accent (hue + colourfulness); each theme sets the lightness, then `fit()` steps it until the text reads at 4.5:1
  on what it sits on. Test V checks every text pair for every hue (3 degree steps), colourfulness 0-1 and theme.
  Shapes and type sizes live there too (cards 24 dp, tiles 16, pills 24).
- **Fonts** (assets/fonts, OFL, licence under About): Sora (headings, the title), Inter (text), JetBrains Mono (the
  dial's number). Variable fonts: one file each, the weight chosen in `Styler`.
- **`Styler.java`** puts the palette on plain Views: section cards (s1 surface, header row with a chevron),
  Material 3 switches (52x32 track; thumb a LayerDrawable, since an InsetDrawable's padding made the Switch shrink its
  track), tinted sliders, pick-one menus on a rounded tile with a chevron, tonal pill buttons, Reset everything is solid
  stop-sign red (`Look.DANGER` #C62828, white bold text, 5.6:1, the same in every theme; DadCraft Oct 20:
  "unmistakable, like a stop sign"). `Styler.fonts()` gives every other text Inter and the palette's colours (run on each section when it
  first fills).
- **The bottom** (DadCraft: in thumb reach while one-handed): the docked player (small dial, Start/Stop inside it,
  status on one line with warnings in the error colour, scene + Intensity, Save sound) above the pill tab bar. Tap
  the player: the player sheet slides up (300 ms) with the big dial (`DialView`: drag around the ring for
  Intensity; screen readers get it as a slider in steps of 5), Start/Stop in its middle, the full status, the realm
  line, Hold realm and Save sound. Back, the grab bar or the dimmed screen close it. Replaced the floating bottom
  card and its AMOLED accent glow. Motion only while something slides: nothing animates at rest.
- **Appearance** (Oct 19): Theme Phone/Light/Dark/AMOLED (`UiSettings.THEME_PHONE` = 3 resolves to Light or Dark by
  the phone's night mode; the activity is rebuilt when that changes). Accent (`UiSettings.accent`, default "violet",
  not in presets): `Look.ACCENTS` (violet, blue, teal, green, amber, coral, pink, graphite), "wall" (Android 12+:
  `system_accent1_500`'s hue and colourfulness), "custom" (hue + colourfulness, set in the colour ring sheet whose
  middle previews the Start button in this theme). A change calls `recreate()`; the sound keeps playing. Existing
  installs show violet (before the redesign the accent was the wallpaper's on Android 12+).
- **XY pad** (Oct 19): on a card; `ScenePad.drawXyPad` (only with no scene: a scene's pad keeps its look) draws a
  quarter grid, the point it holds (`Modulation.padPoint`, what the Pad X / Pad Y movers follow) as an accent dot
  with a halo and guide lines, "PAD X" / "PAD Y" in the corners, and an accent ring under a held finger.
- **The app icon** (Oct 20, DadCraft picked "A3" on the canvas's "App icon" page after six ideas, four dial +
  headphones mashups and a rebalance: "the dial because it's central in the app ... the headphones, because ... i like
  music"; A's cups "add extra weight to the bottom ... look off-center"): the dial's ring open at the bottom, its ends
  thickened into earcups (teal left, violet right), the lit arc teal to violet ending at the white knob, Start in the
  middle, on the dark background #0d0b13. `tools/make_icon.py` writes the adaptive icon (foreground, monochrome layer
  for themed icons, background colour; minSdk 28 so no PNG mipmaps) and `docs/icon/ouralia-icon.svg`;
  `desktop/.../AppIcon.java` draws the same in Java2D (window icons; `java AppIcon.java <png> <size>` wrote
  `docs/icon/ouralia-icon-512.png` for the store). The desktop menu entry uses the SVG. Android 12+ splash: the icon
  on the same dark (values-v31 `windowSplashScreenBackground`). Launchers with icon packs may keep the old icon until
  they refresh.
- Kit headings (Oct 20, DadCraft: the kit and layer menus "almost kind of run together"): the Music card has small
  accent headings with a line above (`addSubheading`): Kit, Mix and match, Layers, How it plays (from Melody rhythm on).
  Each layer and its blend share an outlined panel (`addLayerPanel`); the blend is at 40% while the layer is None
  (`updateFadedRows`, from refreshRows). Phone only (the desktop's Music tab is unchanged). Same headings in Mix
  (Drums, Pad and bass, Melody, Answers: ring, bells, Harmony), Glitch (cloud, kinds, Freeze) and Input & output
  (Microphone, Pocket and handling, Output): `SUBHEADINGS` (param id -> heading, hint) in MainActivity; no line above
  a card's first heading. The XY pad is a foldable section (`xy_pad`, open by default; DadCraft: hide it if unused).
- Player line and Realm names (Oct 20, DadCraft): the docked player's line said Built-in effects while a Realm played; it
  now says "Flux Realm · Intensity 50" (`mini_realm`, "(held)" when held; refreshed by the Worlds listener). Realm is
  a proper noun on screen: Hold Realm. Auto Realms renamed **Realm Hopper** and the cue **Warp sound** (DadCraft's names,
  Oct 20; ids unchanged: worlds_auto, cue_sound) (built-in preset names "Flux realm" unchanged: stored choices use them).
- **Sleep Timer** (Oct 22, ROADMAP 22, Kimi's review; DadCraft picked it): a button under Hold Realm / Save sound in the
  player's sheet while the sound plays, a list sheet of Off, 15, 30, 45, 60, 90 minutes (`SleepTimer.MINUTES`); the
  button counts down ("Sleep in 23 min", rounded up). `AudioService` keeps the end time (`elapsedRealtime`, static:
  it runs with the app closed); the movers' tick (every 500 ms, with the call check) follows the fade
  (`SleepTimer.gain`: full until the last 5 minutes, then evenly in dB down to -48, sent a quarter dB at a time) through
  the same dip as other apps' audio (`duck` in the patch, its 600 ms line~ smoothing the steps; a scene's `#volume`),
  so the dry room fades with the music, then shuts down as Stop does (event log: "Sleep timer: fading out",
  "stopped"). Stop or the notification's Stop ends the timer; a restart for a new device or scene keeps it. Not in
  presets or settings (a moment's choice). No patch change. Test: javatests Z.
- **Realms, Scenes, Setup** (Oct 19): each Realm is a block (name, when it plays, its preset on a tile opening a
  list sheet, "What it changes" folding open to switches); the audio Input/Output pickers are tiles with list
  sheets; the Scenes tab's scene and buttons sit on a card, the scene picker is a list sheet (press and hold to
  delete), RjDj timing is a switch. No Spinner is left in the app. A sheet opens scrolled to its chosen row.
- **Rows and card headers** (Oct 20, the mockup's remaining details): a setting's bracketed explanation moves to a
  quieter line under its name (`splitLabel`: "Calm builds" / "standing still, the music slowly grows"; " *" stays
  on the name); a slider shows its value on the right in the accent mono font (`valueText` is the value only), Lock
  is a padlock toggle; tapping a switch row's words flips it. Each effect card's header carries its **Movers pill**
  (Movers on/off) and its **on/off switch** (`onSwitch`: the section's "... enabled" toggle), so both work folded;
  they no longer appear inside the card. Sections without them (Sing in key, Mix...) keep a plain header. Card
  titles split the same way ("Mix" / "the level of each part"). A slider's Movement controls show only while its
  card's Movers are on (they were greyed copies under every slider: clutter).
- **Sound tab top** (Oct 20): Presets is one wide button (the list sheet) with Save, Export and Import as 48 dp icon
  buttons (+, up, down); Randomize, Nudge and Undo are outlined chips with icons (Undo greys out with nothing to
  undo). The Scenes card likewise: Choose scene as one wide button, Import zip / Import folder as chips.
- **Final mockup review** (Oct 20): the player sheet gained the mockup's dotted inner ring on the dial and the hint
  "Slide around the ring to set Intensity" (hidden for a scene). Deliberate differences from the mockups: no line under the title (the docked player shows the scene and
  Intensity); the Music card's on/off switch sits on its header; the colour ring previews the Start button in its
  middle rather than recolouring the whole app behind it while dragging; Both sits in the middle of button rows.
- **Headphone warning** (Oct 20, DadCraft: "does not match the mockup"): a small red "No headphones" chip (crossed-out
  headphones icon) on the title row instead of a full-width banner; tapping it shows the full warning, which is
  also its screen-reader text. Hidden while headphones are connected, as before.
- **Touch feedback** (Oct 20): a soft tick (KEYBOARD_TAP) when a button-row option, a sheet row or a Key note is
  picked; the Intensity dial ticks (CLOCK_TICK) every 5 steps while dragged. Android's own haptics: they follow the
  phone's touch-vibration setting.
- **Dialogs** (Oct 19): every AlertDialog is built with `LookDialog` (rounded on the sheet colour, the fonts,
  buttons in the accent; text fields via `boxed()`); Presets is a list sheet (tap loads, press and hold one of
  yours to delete); the walkthrough takes the look through `Walkthrough.applyLook`.
- **Key and Scale** (Oct 19; DadCraft found the mockup's 12-key strip easy to mis-tap): two tiles side by side in
  Music, each opening a sheet: Key = the 12 notes as big buttons (4 x 3), Scale = one row per scale with 12 dots
  (filled = the scale's notes above the key; `Params.SCALE_STEPS`, which test V checks against make_patch.py
  SCALES). One tap picks and closes. The sheet is one shared bottom sheet (`openSheet`), also used by the player.
  Closing it: back, a tap on the dimmed screen or the grab bar, or a drag down (`SheetLayout`: from anywhere a
  control isn't using the touch; past a quarter of its height or a flick closes, less springs back). DadCraft found
  the grab bar didn't drag (Oct 19), so this was added.
- **Every other pick-one setting** (Oct 19, `addChoiceRow`): two choices with short names up to 18 letters, or three
  up to 12, are side-by-side buttons (the short name is the words before " (", the chosen one's bracketed
  explanation shows under them: e.g. Pad register, Pad sounds, the Classic/New switches, Distortion type); longer
  lists are a tile opening a list sheet (kits, Crusher style, Melody rhythm, the movers' Movement). **Both in the
  middle** (DadCraft, Oct 20: "left side as the original, Both in the middle as a sort of unity, the other option on
  the right"): a three-way row ending in "Both" shows as 0, 2, 1 (`displayOrder`; Pad register reads Low | Both |
  Mid, his pick). Only the screen order: stored values and presets are unchanged. Long lists
  scroll in the sheet (up to 72% of the screen); a drag down closes it only once the list is at its top. The
  Realms menus and Setup's device pickers are still Spinners (their own code).

- **Names and families** (Oct 21, DadCraft: names should have character, "Chord Colours is great... Melodies that
  develop sounds dry"; and Title Case, "Input Gain"): he liked the music as **The Band** (a band in your pocket,
  *Making the Band*), so the Music tab became The Band (first the Music card was renamed: DadCraft meant the tab, "The
  Band kind of encompasses all of the musical things, including Rhythm"; the card is Music again), the kit "from" menus **Band Members** (Lead, Bassist, Keys,
  Drummer, "plays like" a kit), and: Song Shape (Music variety; Classic -> Steady), Hooks That Grow (Melodies that
  develop), Living Grooves (Patterns made by rules), Soft Steps, Wandering Home (Let the home chord drift), Sway with
  the World (Light, pocket and stairs...), Slow Bloom (Calm builds), Phrasing (Melody phrases), Ear for the Room (How
  much the music follows what it hears), Singing Room (Room notes), Stereo Space (Spatial). Standard effect names stay
  (musicians know them); Glitch keeps its name (it holds Freeze and Glitch cloud). Title Case on every setting name
  and card title (the part before " (": the bracketed explanation stays as it was; small words like and/the/of/with
  stay small); choice names, buttons and messages unchanged. Ids unchanged, so presets and stored values carry over.
  **Effect families** (his question: alphabetical, or another order?): grouped by what they do, each family under a
  small heading between the cards (`Section.group`, the in-card subheading's style): Voice (Sing in Key, Vocoder,
  Pitch Shifter), Grit (Bit Crusher, Distortion, Ring Modulator, Filter), Motion (Chorus, Flanger, Phaser, Tremolo),
  Time & Space (Echo, Reverse Echo, Glitch, Reverb, Stereo Space). Each effect keeps its own card (its switch and
  movers). Only the screen order: the sound's chain is unchanged.
- **Icons** (Oct 21): play and stop rounded (a round stroke on the triangle, 3-unit corners on the square), Start/Stop
  in JetBrains Mono (DadCraft: like the numbers); the padlock is a 44 dp InsetDrawable so the press glow centres on it
  (a CheckBox draws its button at the start edge, padding doesn't move it).
