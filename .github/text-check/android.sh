#!/usr/bin/env bash
# Runs inside the emulator step. Shoots every scene in English and Arabic with
# big text, once on a phone set to a bigger display size (as on many Samsung
# phones) and once with the largest font Android allows, both with the
# three-button navigation bar.
set -ex
sleep 30
PKG=com.njarrar.backthen
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/DCIM/Camera
for f in /tmp/photos/*.jpg; do adb push "$f" /sdcard/DCIM/Camera/ >/dev/null; done
adb shell content call --uri content://media/ --method scan_volume --arg external_primary || true
sleep 5
adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell cmd overlay enable com.android.internal.systemui.navbar.threebutton || true
SCENES=$(cat .github/text-check/scenes.txt)
shoot() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
  sleep 1
  adb exec-out screencap -p > "$1"
}
run() { # folder font_scale density
  OUT="shots/$1"; mkdir -p "$OUT"
  adb shell settings put system font_scale "$2"
  adb shell wm density "$3"
  sleep 3
  for l in en ar; do
    adb shell pm clear $PKG >/dev/null || true
    adb shell pm grant $PKG android.permission.READ_MEDIA_IMAGES
    adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
    timeout 30 adb shell am start -W -n $PKG/.MainActivity --es lang $l || true
    sleep 6
    shoot "$OUT/$l-welcome.png"
    for scene in $SCENES; do
      adb shell am force-stop $PKG
      timeout 30 adb shell am start -W -n $PKG/.MainActivity --es scene $scene --es lang $l || true
      sleep 8
      shoot "$OUT/$l-$scene.png"
    done
  done
}
# Makes a real reel on the emulator and keeps four of its frames. Fails the run if no video comes out.
reel_check() {
  OUT="shots/reel-check"; mkdir -p "$OUT"
  adb shell am force-stop $PKG
  adb logcat -c
  timeout 30 adb shell am start -W -n $PKG/.MainActivity --es scene reelexport --es lang en || true
  for i in $(seq 1 60); do adb logcat -d -s Flashback:I | grep -q "Reel check" && break; sleep 3; done
  adb logcat -d -s Flashback:I | grep "Reel check" | tee "$OUT/result.txt"
  for n in 0 1 2 3; do adb pull "/sdcard/Android/data/$PKG/files/reel-check-$n.png" "$OUT/frame-$n.png" || true; done
  grep -q "Reel check: ok" "$OUT/result.txt"
}
# Pixel 7 is 1080 x 2400 at 420 dpi. 480 dpi makes it 360 dp wide, like a
# Samsung phone with a bigger display size.
run big-display-1.3 1.3 480
reel_check
run huge-font-2.0 2.0 420
adb shell wm density reset
adb shell settings put system font_scale 1.0
