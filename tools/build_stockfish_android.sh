#!/usr/bin/env bash
# Build the official Stockfish 19 Android ARM64 PIE executable and stage it for APK packaging.
# Requires Android NDK r27c or newer, git, make, and the NDK LLVM tools.
# Upstream instructions: https://official-stockfish.github.io/docs/stockfish-wiki/Compiling-from-source.html
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
if [[ -z "$NDK" || ! -d "$NDK/toolchains/llvm/prebuilt" ]]; then
  echo "ERROR: Set ANDROID_NDK_HOME to an Android NDK r27c+ installation." >&2
  exit 2
fi

case "$(uname -s):$(uname -m)" in
  Linux:x86_64) HOST_TAG=linux-x86_64 ;;
  Darwin:arm64) HOST_TAG=darwin-arm64 ;;
  Darwin:x86_64) HOST_TAG=darwin-x86_64 ;;
  *) echo "ERROR: Unsupported build host $(uname -s)/$(uname -m)." >&2; exit 2 ;;
esac

TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST_TAG/bin"
CXX="$TOOLCHAIN/aarch64-linux-android29-clang++"
LLVM_STRIP="$TOOLCHAIN/llvm-strip"
LLVM_READELF="$TOOLCHAIN/llvm-readelf"
for tool in "$CXX" "$LLVM_STRIP" "$LLVM_READELF"; do
  [[ -x "$tool" ]] || { echo "ERROR: Required NDK tool missing: $tool" >&2; exit 2; }
done
export PATH="$TOOLCHAIN:$PATH"

WORK="${STOCKFISH_WORK_DIR:-$ROOT/.stockfish-build}"
REPO="$WORK/Stockfish"
TAG="${STOCKFISH_TAG:-sf_19}"
JOBS="${STOCKFISH_JOBS:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 2)}"
mkdir -p "$WORK"

if [[ ! -d "$REPO/.git" ]]; then
  git clone --filter=blob:none --depth 1 --branch "$TAG" https://github.com/official-stockfish/Stockfish.git "$REPO"
else
  git -C "$REPO" fetch --depth 1 origin "refs/tags/$TAG:refs/tags/$TAG"
  git -C "$REPO" checkout --detach "$TAG"
fi

SRC="$REPO/src"
make -C "$SRC" net
make -C "$SRC" -j"$JOBS" build ARCH=armv8 COMP=ndk
make -C "$SRC" strip ARCH=armv8 COMP=ndk

OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$OUT"
install -m 0755 "$SRC/stockfish" "$OUT/libstockfish.so"
chmod 0755 "$OUT/libstockfish.so"

cp "$REPO/Copying.txt" "$ROOT/STOCKFISH_LICENSE.txt"
git -C "$REPO" rev-parse HEAD > "$ROOT/STOCKFISH_COMMIT.txt"
printf '%s\n' "$TAG" > "$ROOT/STOCKFISH_SOURCE_TAG.txt"

INFO="$ROOT/STOCKFISH_BINARY_INFO.txt"
"$LLVM_READELF" -h "$OUT/libstockfish.so" | grep -E 'Class:|Machine:|Type:' > "$INFO"
grep -q 'Class:.*ELF64' "$INFO"
grep -q 'Machine:.*AArch64' "$INFO"
grep -q 'Type:.*DYN' "$INFO"
"$LLVM_STRIP" --version | head -n 1 >> "$INFO"
sha256sum "$OUT/libstockfish.so" | tee "$ROOT/STOCKFISH_BINARY_SHA256.txt"

echo "Stockfish $TAG ARM64 PIE staged at: $OUT/libstockfish.so"
echo "Now run: gradle :app:assembleDebug"
