#!/bin/sh
# Xcode build step: copies lang/*.xml into the app, and makes a <code>.lproj folder for
# each language so iOS knows the app speaks it. That turns on right to left for Arabic and
# puts system sheets and the photo access prompts in the same language. The app (not the
# widget) also gets the city list in places/ and font licenses from design/.
set -eu
src="$SRCROOT/../lang"
dst="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"

# Clean stale language XMLs and generated .lproj bundles from previous incremental builds.
rm -rf "$dst/lang"
mkdir -p "$dst/lang"
for existing in "$dst"/*.lproj; do
  [ -d "$existing" ] || continue
  if [ -f "$existing/InfoPlist.strings" ]; then
    rm -rf "$existing"
  fi
done

if [ "${WRAPPER_EXTENSION:-}" = "app" ]; then
  cp "$SRCROOT/../places/cities.tsv" "$dst/"
  for lic in NUNITO_OFL.txt TAJAWAL_OFL.txt; do
    if [ -f "$SRCROOT/../design/$lic" ]; then
      cp "$SRCROOT/../design/$lic" "$dst/"
    fi
  done
fi

pairs="NSPhotoLibraryUsageDescription:ios_photos_permission NSPhotoLibraryAddUsageDescription:ios_photos_add_permission"
if [ "${WRAPPER_EXTENSION:-}" = "app" ]; then
  pairs="$pairs NSLocationWhenInUseUsageDescription:ios_location_permission"
fi

write_lproj() {
  lproj_name="$1"
  xml_file="$2"
  mkdir -p "$dst/$lproj_name.lproj"
  out="$dst/$lproj_name.lproj/InfoPlist.strings"
  : > "$out"
  for pair in $pairs; do
    key=${pair%%:*}
    name=${pair#*:}
    text=$(xmllint --xpath "string(//string[@name='$name'])" "$xml_file")
    if [ -n "$text" ]; then
      escaped=$(printf '%s' "$text" | awk '
        BEGIN { ORS="" }
        {
          gsub(/\\/, "\\\\")
          gsub(/"/, "\\\"")
          gsub(/\r/, "")
          if (NR > 1) printf "\\n"
          printf "%s", $0
        }
      ')
      printf '"%s" = "%s";\n' "$key" "$escaped" >> "$out"
    fi
  done
}

for f in "$src"/*.xml; do
  [ -f "$f" ] || continue
  code=$(basename "$f" .xml)
  cp "$f" "$dst/lang/"
  if [ "$code" = "zh" ]; then
    # iOS names Chinese scripts zh-Hans and zh-Hant.
    write_lproj "zh-Hans" "$f"
    write_lproj "zh-Hant" "$f"
  else
    write_lproj "$code" "$f"
  fi
done
