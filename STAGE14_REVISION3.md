# Stage 14 — Advanced Tactical Intelligence (revision 3)

Base: the Stage 17 source tree. All Stage 15-17 code is preserved unchanged.

## Fixed
- `AdvancedTacticalAnalyzerTest.findsVerifiedMateInOneFromLegalMoves` was **failing** in revision 2
  (the queen was placed on g7 instead of g6, giving an illegal position, so CI `testDebugUnitTest` would have failed).
  The position is corrected (queen on g6, white Kf6 vs black Kh8, Qg7#).

## Added (all verified against the real rules engine, no heuristics-as-fact)
New file `TacticalSearch.kt`:
- **Mate threat** — null-move check: if the opponent would have a mate in one were it their turn. Shown immediately (also inside `TacticalAnalyzer.analyze`).
- **Forced mate in two** — exhaustive: a move after which *every* legal reply allows a mate in one.
- **Poisoned capture** (trap warning) — a legal capture after which the opponent has a forced mate in <= 2 (e.g. Legal's trap ...Bxd1?).
- **Trap setup** — a legal move offering >= 3 points of material whose capture allows our forced mate in <= 2. Labelled as working only if the opponent takes the bait.
- Node budget (60,000 move generations) prevents a runaway search.
`ChessEngine.applyLegal` added (no re-validation; only for moves just taken from `legalMoves`) so search does not regenerate legal moves at every node.

## UI
- Deep search runs on `Dispatchers.Default`; the panel shows "Calculating deeper tactics…" meanwhile.
- A warning card above the board lists mate threats / mate-in-1 / mate-in-2 / poisoned captures. Squares are highlighted on the board as before.

## Tests
`TacticalSearchTest.kt` (11 tests): Fool's-mate-style mate in one, mate threat after 1.f3 e5 2.g4, no false threat in the start position or while in check, Legal's trap mate-in-two (Bxf7+ Ke7 Nd5#), poisoned capture ...Bxd1, trap setup Nxe5, no false positives on safe capture/start position, exhaustive re-verification of every reported mate in two, and a check that every finding's move is legal.

## Limits (be exact)
- Search is mate-only and at most mate in two; it does not evaluate material-winning combinations, long forced lines, or positional traps. That needs Stockfish (Stage 16).
- Fork/skewer/discovered-attack/under-attack findings remain heuristics.
- Stage 16 is still NOT runtime-complete: no Stockfish binary is bundled (needs the NDK build in CI).
