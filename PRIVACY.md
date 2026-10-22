# Privacy

Ouralia turns the sound around you into music, so it needs your microphone. This page says exactly what it does
with that, and with everything else. Short version: nothing leaves your phone unless you send it yourself.

## The microphone

- The app listens only while it is playing (after you press Start), and Android shows its microphone notification
  the whole time.
- What the mic hears goes through the effects and out to your headphones live. **It is never recorded or saved.**
  The glitches and found sounds keep short pieces of it in memory to play with (the last few seconds, and a
  few caught hits like a door knock for the drums); that memory is overwritten as you go and never written anywhere.
- The app plays only through headphones, never the phone's speaker.

## No internet

The app has no internet permission, so it cannot send anything anywhere. No ads, no accounts, no analytics, no
tracking, no crash reporting. Android's automatic backup is turned off for it too.

## What it keeps on your phone

- **Your settings, presets, and any kits or scenes you import**, in the app's own storage. They stay on your phone
  and go only where you export them yourself.
- **An event log** (`events.log`, at most about 128 KB, oldest lines dropped first). It holds no sound and no words:
  only times, the room's loudness as numbers, the names of the headphones and mic in use, what the app did
  ("Phone went into a pocket", "Caught a snare"), and once a minute while playing the battery level, the phone's
  temperature and how hard the app works the processor. It is there so problems can be found after a walk.

Uninstalling the app deletes all of it.

## Sensors

Movement, light, the proximity sensor, the barometer and the compass shape the music while it plays. They are read
live and not stored, apart from what the event log notes (for example "in a pocket"). The app does not ask for your
location.

## When you send feedback

**Send feedback** (Setup, About) opens an email in your own mail app, to DadCraft. It is filled in with your phone
model, Android version, the app's version, the scene playing, and, if you leave the box ticked, the end of the
event log. You can read and change all of it before sending, and nothing is sent unless you send it.

## Permissions it asks for

| Permission | Why |
|---|---|
| Microphone | To hear the room (see above) |
| Notifications | The notification that shows while it plays, with its Stop button |
| Foreground service (microphone, media playback) | To keep playing with the screen off |
| Change audio settings | To use a Bluetooth headset's mic when you choose it as the input |

## Questions

Email dadcraftstudios@gmail.com or open an issue on the project's GitHub page.
The source code is public, so all of the above can be checked: https://github.com/dadcraftttv/Ouralia
