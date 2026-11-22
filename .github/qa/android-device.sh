#!/usr/bin/env bash
# Release QA on the emulator: the on-phone tests (src/androidTest: every language file
# loads with Android's own XML reader, reminder text in every language, the real reminder
# job posts real words), then the shrunk release build with the raw key check on screen.
set -ex
sleep 20
(cd android && ./gradlew connectedDebugAndroidTest --stacktrace)
bash .github/release-smoke.sh
