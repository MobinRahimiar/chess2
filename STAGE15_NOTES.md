# Stage 15 — Multi-Move Prediction Engine

## Implemented
- Added `PredictionEngine`, a local negamax/minimax search with alpha-beta pruning.
- Every candidate and continuation is generated through `ChessEngine.legalMoves` and applied through `ChessEngine.apply`; illegal moves are not emitted.
- Searches 1–5 plies, ranks up to five candidate root lines, and returns SAN continuations, centipawn-style heuristic scores, node count, and a bounded-search notice.
- Uses a mobile-oriented node budget (45,000 nodes by default) and a small move-ordering table to keep the local search bounded.
- Added a Compose panel to run prediction off the UI thread, show ranked lines, and highlight the first move of each candidate on the board.
- Added unit tests for legal root candidates, line limits, terminal positions, and depth validation.

## Important limits
- This is a lightweight local search with a material/mobility/development heuristic. It is not Stockfish and its score is not an engine-grade evaluation.
- Candidate lines are search variations, not probabilities or guaranteed predictions of what an opponent will play.
- Search is bounded by a node budget; a report explicitly marks when that budget was reached.
- The board highlights candidate first-move squares; full graphical curved/animated arrows are not implemented in this stage.
- Repetition history is not passed into the search tree, so threefold repetition is not adjudicated inside prediction search. Standard legal move generation and checkmate/stalemate detection are used.

## Validation performed in the build environment
- Compiled `ChessEngine.kt` and `PredictionEngine.kt` with the installed Kotlin compiler.
- Ran a JVM smoke test on the initial position: five candidate lines were returned and their moves were legal.
- Ran a terminal-position smoke test: no candidate lines were returned.
- Android Gradle tests and APK assembly were not run because Gradle/Android SDK are unavailable in this environment.
