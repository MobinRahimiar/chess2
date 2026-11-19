# Stage 15 — Multi-Move Prediction Engine (revision 2)

## Defects found in revision 1 and fixed (`PredictionEngine.kt`)
1. Root moves after the first were searched with a narrowed alpha-beta window, so lines ranked 2-5 showed *bounds*, not real scores. Every root move now gets a full window.
2. The node budget was consumed by the first root moves; later moves were never searched. Budget is now shared per root move, with iterative deepening — the report uses the deepest fully completed iteration (and says so).
3. No quiescence: leaf positions in the middle of a capture sequence were mis-scored. Added a capture/evasion quiescence search.
4. `ChessEngine.apply` re-generated all legal moves for every child and `evaluate` generated them again. Search now uses `applyLegal` and reuses the legal-move count (large speed-up within the same budget).

## New
- Opponent's best reply inside each line (`opponentReply`, `opponentReplySan`).
- **Threat prediction**: null-move search of the opponent's strongest idea if you ignore them, reported only when it gains >= 1.5 pawns versus your best move or forces mate (e.g. 1.f3 e5 2.g4: threat Qh4#).
- **Arrows on the board** (Canvas overlay): green/blue/amber = your top 3 first moves, light red = opponent reply to line 1, dark red = opponent threat. Touches pass through to the squares.
- Top 3-5 lines, ranked, shown with SAN, score, opponent reply.

## Tests (`PredictionEngineTest`, 11 tests; 62 total in the project)
Sorted lines under a tiny budget with all five root moves searched; every PV replays legally; free queen ranked first; forced mate flagged (Ra8#); quiescence avoids a defended-pawn grab; opponent reply present; threat Qh4# found, and no threat in the start position.

## Limits
- Light evaluator (material, pawn advance, minor-piece centralisation, mobility), not Stockfish. Scores are for guidance.
- Lines are search variations; no probabilities are shown or implied. Public stats / local stats / engine score remain separate (Stage 17 panels).
- Repetition history is not part of the search tree.
- Arrows are straight lines with arrowheads (no curved knight arrows or animation).
- Compose code (arrows, panel) was syntax-checked only; it has not been compiled or run on a device here.
