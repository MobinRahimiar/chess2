#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BIN="$ROOT/app/src/main/jniLibs/arm64-v8a/libstockfish.so"
if [[ ! -s "$BIN" ]]; then
  echo "Missing Stockfish executable: $BIN" >&2
  exit 2
fi
command -v file >/dev/null || { echo "file utility is required" >&2; exit 2; }
command -v readelf >/dev/null || { echo "readelf utility is required" >&2; exit 2; }
file "$BIN"
readelf -h "$BIN" | grep -E 'Class:|Machine:|Type:'
grep -q 'ELF 64-bit' < <(file "$BIN")
readelf -h "$BIN" | grep -q 'Machine:.*AArch64'
readelf -h "$BIN" | grep -q 'Type:.*DYN'
[[ -x "$BIN" ]] || chmod 0755 "$BIN"
sha256sum "$BIN"
echo "Stockfish ARM64 ELF verification passed."
