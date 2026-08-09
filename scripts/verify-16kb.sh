#!/usr/bin/env bash
set -euo pipefail

apk_path=${1:?Usage: verify-16kb.sh path/to/app.apk}
if [[ ! -f "$apk_path" ]]; then
  echo "APK not found: $apk_path" >&2
  exit 1
fi

zipalign_path=$(find "${ANDROID_HOME:?ANDROID_HOME is required}/build-tools" -type f -name zipalign | sort -V | tail -n 1)
if [[ -z "$zipalign_path" ]]; then
  echo "zipalign was not found under ANDROID_HOME." >&2
  exit 1
fi
"$zipalign_path" -c -P 16 4 "$apk_path"
echo "Verified 16 KB APK zip alignment."

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
unzip -q "$apk_path" 'lib/*/*.so' -d "$work_dir"

readelf_path=""
for candidate in readelf llvm-readelf greadelf; do
  if command -v "$candidate" >/dev/null 2>&1; then
    readelf_path=$(command -v "$candidate")
    break
  fi
done
objdump_path=$(command -v objdump 2>/dev/null || true)
if [[ -z "$readelf_path" && -z "$objdump_path" ]]; then
  echo "Neither readelf nor an ELF-capable objdump is installed; native alignment cannot be verified." >&2
  exit 1
fi

checked=0
failed=0
inspection_file="$work_dir/program-headers.txt"
library_list="$work_dir/native-libraries.txt"
find "$work_dir/lib" -type f -name '*.so' -print0 > "$library_list"
while IFS= read -r -d '' library; do
  # Android's 16 KB devices use 64-bit ABIs. Google specifically requires ELF checks for
  # arm64-v8a and x86_64; the 32-bit libraries remain useful on older 4 KB devices.
  case "$library" in
    */arm64-v8a/*|*/x86_64/*) ;;
    *) continue ;;
  esac
  checked=$((checked + 1))
  load_segments=0
  if [[ -n "$readelf_path" ]]; then
    if ! "$readelf_path" -lW "$library" > "$inspection_file"; then
      echo "Could not inspect native library: $library" >&2
      failed=1
      continue
    fi
    while read -r alignment; do
      [[ -z "$alignment" ]] && continue
      load_segments=$((load_segments + 1))
      if (( alignment < 0x4000 )); then
        echo "Native LOAD segment is not 16 KB aligned: $library ($alignment)" >&2
        failed=1
      fi
    done < <(awk '$1 == "LOAD" { print $NF }' "$inspection_file")
  else
    if ! "$objdump_path" -p "$library" > "$inspection_file"; then
      echo "Could not inspect native library: $library" >&2
      failed=1
      continue
    fi
    while read -r exponent; do
      [[ -z "$exponent" ]] && continue
      load_segments=$((load_segments + 1))
      if (( exponent < 14 )); then
        echo "Native LOAD segment is not 16 KB aligned: $library (2**$exponent)" >&2
        failed=1
      fi
    done < <(
      awk '$1 == "LOAD" {
        for (i = 1; i < NF; i++) {
          if ($i == "align") {
            value = $(i + 1)
            sub(/^2\*\*/, "", value)
            print value
          }
        }
      }' "$inspection_file"
    )
  fi
  if (( load_segments == 0 )); then
    echo "No ELF LOAD segments could be read from: $library" >&2
    failed=1
  fi
done < "$library_list"

if (( checked == 0 )); then
  echo "No 64-bit native libraries were found; 16 KB compatibility could not be verified." >&2
  exit 1
fi
if (( failed != 0 )); then
  exit 1
fi
echo "Verified $checked 64-bit native libraries for 16 KB ELF LOAD alignment."
