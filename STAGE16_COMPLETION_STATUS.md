# Stage 16 — Delivery status (Revision 4)

**Source integration: implemented. Native binary: not bundled. APK: not generated.**

The Android application now has UCI process integration, legal-position/PV/bestmove validation, configurable Threads/Hash/MultiPV/depth, and a pinned upstream build script for official Stockfish 19 ARM64.

The build script requires Android NDK r27c or newer, Git, Make, and access to the official Stockfish source and NNUE download. It produces `app/src/main/jniLibs/arm64-v8a/libstockfish.so` and provenance files.

This archive intentionally contains no fake engine binary. Since this environment lacks the Android NDK and cannot fetch upstream sources, the native executable and APK could not be generated here. The user requested that testing be deferred, so no additional tests were run for Revision 4.

Do not describe Stage 16 as runtime-complete until the official binary has been built and packaged. Device execution remains a separate verification step.
