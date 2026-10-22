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
upload key can be replaced. The GitHub APKs so far are signed with the debug key: an APK signed with the new key
can't install over them, so testers uninstall once (exporting presets first).

**Play Console forms**, drafted from what the app does (check them against Play's current wording):

- **Foreground service types.** *microphone*: the app listens to the room and plays it, changed, into the
  headphones while the screen is off. Nothing plays without the mic. *mediaPlayback*: the music and effects keep
  playing with the screen off. Both start only when the user presses Start, and a Stop button sits in the
  notification. Play may ask for a short video of the app in use.
- **Data safety.** No data collected or shared: the app has no internet permission, so it can't send anything.
  The mic is processed live in memory and never stored. Send feedback opens the user's own mail app with the text
  filled in, and the user decides whether to send it. That is the user sharing, not the app collecting, but check
  Play's guidance on user-initiated email.
- **Privacy policy URL.** `PRIVACY.md` on GitHub:
  https://github.com/dadcraftttv/Ouralia/blob/main/PRIVACY.md
- **Content rating.** No user content, no ads, no purchases, no location.
- **Store listing.** Short description (80 characters): "Turn the world around you into music, live, in your
  headphones." Screenshots: `docs/screenshots/` are the right shape; Play wants at least two per form factor.
  Icon: `docs/icon/ouralia-icon-512.png`.
- **Target audience:** 13 and over. An app aimed at children takes on Play's Families rules.

## After any release

- The APK's SHA-256 in the notes, the mapping file kept beside the APK.
- Download counts: `gh release view v<version> -R dadcraftttv/Ouralia --json assets --jq '.assets[] | "\(.name): \(.downloadCount)"'`
