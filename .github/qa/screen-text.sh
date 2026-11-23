# Sourced by the emulator scripts. qa_check <label> reads every text on the current screen
# and notes any raw text key (a word that failed to load) or unfilled {slot}. qa_finish fails
# the run if any screen had a problem, or if any screen's UI hierarchy dump failed.
QA_DIR=${QA_DIR:-shots/qa}
mkdir -p "$QA_DIR"
: > "$QA_DIR/problems.txt"
QA_READ=0
QA_MISSED=0
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
  QA_MISSED=$((QA_MISSED + 1))
  echo "::error::Could not read the screen text for $label"
  echo "$label: uiautomator dump failed after 3 attempts" >> "$QA_DIR/problems.txt"
}
qa_finish() {
  if [ "$QA_READ" -eq 0 ]; then
    echo "::error::Could not read the text of any screen, so the raw key check did not run."
    exit 1
  fi
  if [ "$QA_MISSED" -gt 0 ]; then
    echo "::error::UI hierarchy dump failed on $QA_MISSED screen(s); see problems.txt."
    cat "$QA_DIR/problems.txt"
    exit 1
  fi
  if [ -s "$QA_DIR/problems.txt" ]; then
    echo "::error::Screens show raw text keys, unfilled slots, or other QA errors:"
    cat "$QA_DIR/problems.txt"
    exit 1
  fi
  echo "Raw key check: $QA_READ screens read, 0 missed, no raw keys."
}
