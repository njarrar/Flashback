# Sourced by the emulator scripts. qa_check <label> reads every text on the current screen
# and notes any raw text key (a word that failed to load). qa_finish fails the run if any
# screen had one, or if no screen could be read at all.
QA_DIR=${QA_DIR:-shots/qa}
mkdir -p "$QA_DIR"
: > "$QA_DIR/problems.txt"
QA_READ=0
qa_check() {
  local label=$1 out="$QA_DIR/$(echo "$1" | tr '/ ' '__').xml"
  for try in 1 2 3; do
    if adb shell uiautomator dump /sdcard/qa.xml >/dev/null 2>&1 && adb pull /sdcard/qa.xml "$out" >/dev/null 2>&1; then
      python3 .github/qa/raw_keys.py lang/en.xml "$out" "$label" "$QA_DIR/problems.txt"
      QA_READ=$((QA_READ + 1))
      return 0
    fi
    sleep 2
  done
  echo "::warning::Could not read the screen text for $label"
}
qa_finish() {
  if [ "$QA_READ" -eq 0 ]; then
    echo "::error::Could not read the text of any screen, so the raw key check did not run."
    exit 1
  fi
  if [ -s "$QA_DIR/problems.txt" ]; then
    echo "::error::Screens show raw text keys in place of words:"
    cat "$QA_DIR/problems.txt"
    exit 1
  fi
  echo "Raw key check: $QA_READ screens read, no raw keys."
}
