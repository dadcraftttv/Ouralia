# Contributing to Ouralia

Thanks for looking. Ouralia is a one-person project (DadCraft's), so the best first step for anything bigger than a
small fix is an issue saying what you'd like to change. Bug and sound reports are just as welcome as code: the
issue template asks for what helps.

## Building and testing

How to build, and how the code is laid out, is in the [README](README.md#building). Before sending a change:

- `tools/run_tests.sh quick` runs the fast tests (the Java logic, patch generation, the permission and name check).
  GitHub runs the same on every push and pull request.
- `tools/run_tests.sh` runs everything, including the Pure Data suites through a desktop libpd
  (`tools/build_libpd.sh` builds it; numpy in `tools/.venv`). Some of them time things, so run them on a quiet
  machine.

## Rules the app keeps

- **Headphones only.** Output never goes to the phone speaker.
- **The mic never touches the disk.** Nothing is recorded or saved; what the effects catch lives in memory.
- **No internet**, no analytics, no accounts. A new permission needs a very good reason, and an update to
  [PRIVACY.md](PRIVACY.md) and `tools/test_release.py`.
- **Nothing resets settings** except the Reset everything button. A changed default keeps people's stored values.
- **New sounds go in behind a switch**, with the old sound the default, until DadCraft has listened to both.
  Only ears decide sound changes, so say what to listen for.

## Where things go

- The built-in patch `app/src/main/assets/patches/test_scene.pd` is generated: edit `tools/make_patch.py` and run
  it. Never edit the `.pd` by hand.
- Our own Pd objects live in `dcs/` (C, prefix `dcs_`, BSD-3-Clause). After changing `dcs/src`: `make -C dcs`,
  `tools/build_dcs_android.sh`, `tools/test_dcs.py`. Don't change a standard Pd object; add a `dcs_` one.
- The Java is plain Android Views. Every setting is in `Params.java`; the screen is split into one class per part
  (`SettingRows`, `PlayerUi`, `PresetUi`...), with `MainActivity` coordinating.
- [docs/DESIGN-NOTES.md](docs/DESIGN-NOTES.md) says how each feature works and why: update it with any behaviour
  change. [docs/ROADMAP.md](docs/ROADMAP.md) starts with "Open now", the list of what isn't done.

## Licence

By contributing you agree your work is released under the project's licences: GPLv3 or later for the app,
BSD-3-Clause for `dcs/`. See [NOTICE.md](NOTICE.md) for the third-party parts.
