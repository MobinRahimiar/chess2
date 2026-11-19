# Stage 12 — Opening Intelligence & Game Knowledge

## Added
- Offline curated opening repertoire covering common first moves and representative continuations (Italian, Ruy Lopez, Scotch, Sicilian, French, Caro-Kann, Queen's Gambit, Slav, King's Indian, Nimzo-Indian, London, Catalan and others).
- Opening suggestions are generated only when the SAN prefix matches the current game history.
- Every suggested SAN is resolved against the current position's legal moves before it is shown as playable.
- Saved games on the device contribute a position-prefix-specific occurrence count. These are explicitly labeled as local saved-game occurrences, not worldwide statistics.
- Opening recommendation card added to the game screen. The first opening suggestion is used as a board highlight when no Stockfish best move is available; an actual Stockfish best move takes precedence.
- Unit tests cover legal first moves, an Italian Game continuation, and notation normalization.

## Important limitations
- This is a curated offline repertoire, not a downloaded live opening database.
- The repertoire priority is an authored ranking, not an engine score, win rate, or probability.
- Local occurrence counts only reflect games saved on this device.
- General middlegame prediction still requires a working Stockfish Android binary. This stage does not claim exhaustive future-move prediction.
