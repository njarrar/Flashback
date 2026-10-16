#!/usr/bin/env bash
# Installs the shrunk release APK on the emulator, opens the app with photos in the
# library, and fails if it crashes.
set -ex
sleep 30
PKG=com.njarrar.backthen
adb install -r android/app/build/outputs/apk/release/app-release.apk
adb shell mkdir -p /sdcard/DCIM/Camera
for f in /tmp/photos/*.jpg; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
adb shell content call --uri content://media/ --method scan_volume --arg external_primary || true
sleep 5
adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb logcat -c
# First launch, then a second one so saved state is read back.
for run in 1 2; do
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity
  sleep 12
  # Tap around the middle and bottom of the screen to open a few views.
  adb shell input tap 540 1200; sleep 3
  adb shell input keyevent KEYCODE_BACK; sleep 2
  adb shell input tap 540 2250; sleep 3
  adb shell input tap 300 2250; sleep 3
  adb shell input tap 800 2250; sleep 3
done
adb logcat -d -b crash > crash.log || true
cat crash.log
if grep -q "$PKG" crash.log; then
  echo "The release build crashed."
  exit 1
fi
