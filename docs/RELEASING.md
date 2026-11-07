# Releasing Ouralia

The steps for a release, in order. Nothing is published until DadCraft says so.

## A GitHub release (an rc, or later a full version)

1. **Version.** In `app/build.gradle.kts`: `versionName` (`0.9.0-rc6`; a fix on an rc is `rcN.1`, DadCraft's call)
   and `versionCode`, always +1, even for a fix.
2. **Docs.** CLAUDE.md "Current state", DESIGN-NOTES "Status log", TESTER-GUIDE for anything that behaves
   differently, ROADMAP "Open now" true.
3. **Tests.** `tools/run_tests.sh` (the full tier) on a quiet machine, and the GitHub check green on the last push.
   The timing check "scatter New: cost" is known to fail while the machine is busy: rerun it then.
4. **Build.** Commit, then `tools/release_apk.sh`. It refuses with uncommitted changes, so the APK is exactly a
   commit, and won't overwrite a version that already exists (`--replace` does, for an APK swapped before anyone
   downloaded it). It writes to `~/Downloads`:
   - `Ouralia-<version>.apk`, the file to publish
   - `Ouralia-<version>.sha256`, its checksum, for the release notes
   - `Ouralia-<version>-mapping.txt`, R8's map from the shrunk names back to ours. Keep it, don't publish it: a
     crash report from that APK can only be read with it (`retrace` in the SDK's cmdline-tools).
5. **On the phone.** Install that APK (`adb install -r`), press Start, listen. DadCraft's ears decide.
6. **Publish** (when DadCraft asks): `gh release create v<version> --prerelease` with the APK, notes saying what
   changed, how to install, and the SHA-256 line. Then push the commits.

## Before the first Play release

**The release key.** Play needs one key for the app's whole life, so make it once and keep two backups somewhere
other than this computer:

```
keytool -genkeypair -v -keystore ~/ouralia-release.jks -alias ouralia -keyalg RSA -keysize 4096 -validity 10000
```

Then add to `~/.gradle/gradle.properties` (never the repo):

```
ouraliaKeystore=/home/dadcraft/ouralia-release.jks
ouraliaKeystorePassword=...
ouraliaKeyAlias=ouralia
ouraliaKeyPassword=...
```

From then on, release builds are signed with it (`tools/release_apk.sh` prints the signer). With **Play App
Signing** (Play's default), this key only uploads, and Google holds the key that signs what phones install, so a lost
upload key can be replaced. The test APKs so far (rc4-rc5.2, none on GitHub since the Nov 2
repo remake) are signed with the debug key: an APK signed with the new key can't install over them, so testers
uninstall once (exporting presets first).

**Play Console forms**, drafted from what the app does (check them against Play's current wording):

- **Foreground service types.** *microphone*: the app listens to the room and plays it, changed, into the
  headphones while the screen is off. Nothing plays without the mic. *mediaPlayback*: the music and effects keep
  playing with the screen off. Both start only when the user presses Start, and a Stop button sits in the
  notification. Play may ask for a short video of the app in use.
- **Data safety.** No data collected or shared: the app has no internet permission, so it can't send anything.
  The mic is processed live in memory and never stored. Send feedback opens the user's own mail app with the text
  filled in, and the user decides whether to send it. Session logs (off unless switched on) work the same way: a zip
  handed to the mail app the user picks, sent only if they send it. That is the user sharing, not the app
  collecting, but check Play's guidance on user-initiated email.
- **Privacy policy URL.** `PRIVACY.md` on GitHub:
  https://github.com/dadcraftttv/Ouralia/blob/main/PRIVACY.md
- **Content rating.** No user content, no ads, no purchases, no location.
- **Store listing.** Short description (80 characters): "Turn the world around you into music, live, in your
  headphones." Full description: below. Screenshots: `docs/screenshots/play/` (rc6.2 on the Moto, Nov 7, cropped
  to 1246x2492: Play refuses a long side more than twice the short one, and the phone's own 1220x2712, like the
  older 540x1143 ones in `docs/screenshots/`, is over that). Feature graphic (1024x500, required):
  `docs/icon/ouralia-feature-1024x500.png` (`tools/make_feature_graphic.py`). Icon: `docs/icon/ouralia-icon-512.png`. Category: Music & Audio.
- **Target audience:** 13 and over. An app aimed at children takes on Play's Families rules.

**Full description** (draft, Nov 7, about 2200 of Play's 4000 characters; DadCraft edits). Paste each paragraph and
bullet as one line: Play keeps the line breaks.

```
Put on headphones, press Start, and hear the room around you turned into music.

Ouralia listens through your phone's microphone and plays the world back to you, reshaped by effects and set to
music that it makes up as it goes. Nothing plays on its own: every note answers something you can hear.

WHAT IT DOES
• The world, reshaped: echo that flies around your head, reverb, glitches that bring back bits of what you just
  heard, pitch, a vocoder, filters, chorus, flanger, phaser and more, all on the live sound around you.
• Music that listens: chords, a melody, a bass line and a beat in a key you choose. Sounds in the room ring the
  current chord, a held hum gets an answering note, a sung phrase gets a reply.
• Found sounds: knocks, taps and clicks the mic catches become the drums.
• It knows what it hears: voices, music, traffic, birds, water and steps, told apart on the phone itself. The
  music makes room for music playing nearby, holds back while people talk, goes underwater by running water and
  turns to high glass when birds sing. Each response has its own switch.
• Walk and the beat follows your steps. Stay somewhere quiet and a gentle tune joins in.
• Realms change the whole sound to fit the moment: a quiet room, a loud street, a walk, after midnight, the phone
  in your pocket. Realm Hopper moves between them by itself.
• One dial, Intensity, turns everything calmer or busier.
• Presets save your sound for a walk, a kitchen or a park, to come back to or share.
• Plays classic reactive-music scenes (.rj) too.

YOURS TO SHAPE
Ouralia isn't one fixed piece of music. Switch the responses you like on and off, set the sounds and levels, and
make it your own.

PRIVATE BY DESIGN
• No internet permission: the app can't send anything anywhere.
• The microphone is never recorded or saved. What it hears lives in memory for a few seconds.
• No ads, no accounts, no tracking.

HEADPHONES ONLY
Ouralia plays only through headphones or earbuds, never the phone speaker (the mic would hear it and howl).

FREE AND OPEN SOURCE
Built on Pure Data (libpd). Source code, GPL-3.0: github.com/dadcraftttv/Ouralia
Made by DadCraft, a hobbyist musician.
```

**The .aab.** With the release key in `~/.gradle/gradle.properties`, `tools/release_apk.sh` also writes
`Ouralia-<version>.aab` (Play refuses a debug-signed bundle, so without the key it says so and makes none). Upload
the `-mapping.txt` beside it in Play Console (App bundle explorer, Downloads) so Play's crash reports are readable.
A Play versionCode can never be reused or go down, so the first Play upload takes the next free code.

## After any release

- The APK's SHA-256 in the notes, the mapping file kept beside the APK.
- Download counts: `gh release view v<version> -R dadcraftttv/Ouralia --json assets --jq '.assets[] | "\(.name): \(.downloadCount)"'`
