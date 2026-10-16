#!/usr/bin/env bash
# Runs inside the emulator step: loads sample photos, then captures each scene.
set -ex
# Let the launcher settle so it does not show an "isn't responding" dialog.
sleep 30
PKG=com.njarrar.backthen
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/DCIM/Camera
for f in /tmp/photos/*.jpg; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
adb shell content call --uri content://media/ --method scan_volume --arg external_primary || true
for f in /tmp/photos/*.jpg; do
  adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/DCIM/Camera/$(basename "$f")" >/dev/null || true
done
sleep 5
adb shell content query --uri content://media/external/images/media --projection _display_name:datetaken | head -5 || true
adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
# Clean status bar.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
mkdir -p shots/android
shoot() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
  sleep 1
  adb exec-out screencap -p > "shots/android/$1.png"
}
# Languages to shoot. English files have no prefix, others start with the code (ar-today).
# The Play Store workflow sets LANGS="en fr es ar". Debug builds take the language as a
# launch extra. Welcome screens go first, before a demo scene fills the app.
LANGS=${LANGS:-en ar}
pre() { if [ $1 = en ]; then echo ""; else echo "$1-"; fi; }
for l in $LANGS; do
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity --es lang $l
  sleep 6
  shoot "$(pre $l)1-welcome"
done
for l in $LANGS; do
  for scene in today calendar viewer favorites album; do
    adb shell am force-stop $PKG
    adb shell am start -W -n $PKG/.MainActivity --es scene $scene --es lang $l
    sleep 8
    shoot "$(pre $l)$scene"
  done
done
