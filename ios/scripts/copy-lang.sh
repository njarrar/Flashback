#!/bin/sh
# Xcode build step: copies lang/*.xml into the app, and makes a <code>.lproj folder for
# each language so iOS knows the app speaks it. That turns on right to left for Arabic and
# puts system sheets and the photo access prompts in the same language.
set -eu
src="$SRCROOT/../lang"
dst="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
mkdir -p "$dst/lang"
for f in "$src"/*.xml; do
  code=$(basename "$f" .xml)
  cp "$f" "$dst/lang/"
  mkdir -p "$dst/$code.lproj"
  out="$dst/$code.lproj/InfoPlist.strings"
  : > "$out"
  for pair in NSPhotoLibraryUsageDescription:ios_photos_permission NSPhotoLibraryAddUsageDescription:ios_photos_add_permission; do
    key=${pair%%:*}
    name=${pair#*:}
    text=$(xmllint --xpath "string(//string[@name='$name'])" "$f")
    if [ -n "$text" ]; then
      printf '"%s" = "%s";\n' "$key" "$(printf '%s' "$text" | sed 's/\\/\\\\/g; s/"/\\"/g')" >> "$out"
    fi
  done
done
