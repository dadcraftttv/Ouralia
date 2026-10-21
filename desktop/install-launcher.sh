#!/usr/bin/env bash
# Puts Ouralia in the app menu (Sound & Video), so it starts without a terminal. Run it again after moving the
# project folder. Remove with: rm ~/.local/share/applications/ouralia-desktop.desktop
set -e
H="$(cd "$(dirname "$0")" && pwd)"
D="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$D"
cat > "$D/ouralia-desktop.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Ouralia
GenericName=Reactive music
Comment=Turns your mic into music for your stream (plays to the Ouralia output for OBS)
Exec="$H/launch.sh"
Icon=$(dirname "$H")/docs/icon/ouralia-icon.svg
Terminal=false
Categories=AudioVideo;Audio;
StartupWMClass=io-github-dadcraftttv-ouralia-DesktopApp
DESKTOP
desktop-file-validate "$D/ouralia-desktop.desktop" 2>/dev/null || true
update-desktop-database "$D" 2>/dev/null || true
echo "Ouralia is in the app menu (Sound & Video)."
