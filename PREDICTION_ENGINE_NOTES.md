# Stage 11 — Multi-line prediction and board guidance

## Included in this stage
- Stockfish MultiPV analysis is requested with up to five candidate lines from the analysis button.
- Candidate principal variations remain visible in the analysis panel, allowing the player to inspect several continuations rather than only one move.
- The engine's current best move is decoded against the current legal position and highlighted directly on the board: gold for the origin square and green for the destination square.
- Stockfish hash allocation is increased to 96 MB; thread count remains conservative at 2 for mobile devices.
- Java and Kotlin compilation targets are aligned to JVM 17.
- GitHub Actions Android SDK setup uses setup-android v4 and installs SDK packages in separate sdkmanager calls.

## Important limits
- The repository still does not contain `app/src/main/jniLibs/arm64-v8a/libstockfish.so`. The app can build without it, but engine-powered analysis and AI replies require that executable.
- MultiPV lines are engine-calculated candidate variations, not certainty about an opponent's future choice.
- Popularity estimates require a sourced opening/game database. No crowd probabilities are fabricated in this stage.
- The tactical scanner remains a heuristic motif detector; it is not a claim of exhaustive tactical coverage. A verified pattern corpus and deeper search integration are separate work items.

## Build
Run the repository's GitHub Actions workflow manually. Enable `build_stockfish` only when you want the ARM64 Stockfish build step and have reviewed the engine's GPL-3.0 licensing obligations.
