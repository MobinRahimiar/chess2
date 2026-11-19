# Stage 16 — Real Stockfish integration (Revision 4)

## Integrated in the Android app
- The app invokes the official Stockfish UCI executable as a separate Android process.
- The app checks the device ABI and executable presence and reports unavailable instead of inventing engine scores.
- FEN is validated with the app chess rules engine before engine startup.
- UCI `uci` / `isready` handshakes have bounded timeouts.
- Threads, Hash, MultiPV, and depth are normalized before sending UCI options.
- Principal variations are replayed move-by-move against legal moves before they are shown.
- `bestmove` is validated against the current root position.
- Output is read asynchronously; stop/close terminate the worker/process.
- The game screen now exposes Threads, Hash MB, MultiPV, and depth controls. The AI opponent uses the selected Threads/Hash settings.

## Native engine source and packaging
- `tools/build_stockfish_android.sh` builds the official upstream `sf_19` tag for Android ARM64 using NDK r27c+.
- The script embeds the upstream NNUE network, builds and strips the ARM64 PIE executable, checks ELF class/machine/type, stages it at `app/src/main/jniLibs/arm64-v8a/libstockfish.so`, and records license, source commit, source tag, and SHA-256.
- The Android manifest requests extraction of native libraries and Gradle uses legacy JNI packaging so the executable is staged under the app's native library directory.
- `.github/workflows/android.yml` runs the build script before tests and APK assembly, and verifies the executable path in the APK.

## Important remaining limitation
This source archive does not contain the generated native executable. The current build environment did not provide Android NDK and could not retrieve upstream sources, so no genuine ARM64 executable could be built and included here. A placeholder binary has deliberately not been added. Consequently this is a fully wired source/build integration, but not a self-contained ready-to-install APK. The native engine becomes present after the build script runs successfully in an environment with the required NDK and upstream network access.

## Official build reference
The upstream Stockfish build guide documents Android NDK cross-compilation using `ARCH=armv8 COMP=ndk`, `make net`, `make build`, and `make strip`:
https://official-stockfish.github.io/docs/stockfish-wiki/Compiling-from-source.html

## Validation status
Per the current delivery request, no new runtime or Android build tests were run for this revision. This ZIP is source-only; APK generation and on-device execution are not claimed.
