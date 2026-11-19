# Stage 17 — Opening Database & Global Statistics (revision 2)

## Gaps found in revision 1 and fixed
1. **No offline storage of statistics** (a stated Stage 17 requirement). Added `ExplorerCache`: bounded (200 positions, oldest dropped), persisted through SharedPreferences (`PrefsExplorerStore`), keyed by position (board/side/castling/en-passant, move counters ignored so transpositions share an entry). Stores only public aggregates, never the token. A corrupt cache is ignored, not fatal.
2. **Online/offline combination**: `ExplorerCache.lookup` — fetch succeeds -> saved; fetch fails (network, rate limit) -> saved copy with an explicit warning; no token -> saved copy only (fully offline); nothing saved and no token -> clear message. The UI shows "Fresh from Lichess…" or "Offline copy saved N min/h ago".
3. **JSON parsing was untestable**: `org.json` is a stub in Gradle JVM unit tests. Replaced by `MiniJson` (strict pure-Kotlin parser); `OpeningExplorerClient.parseResponse` is now a pure, tested function. `org.json` is no longer used.
4. **Query by FEN** (`?fen=...`) instead of replaying moves from the start, so transpositions and games restored from a FEN query the right position. `historyToUci` kept (tested) and no longer re-validates every candidate move.
5. Hard-coded `User-Agent` version removed.

## Data separation (unchanged, verified in UI)
Master/public statistics (Lichess, with source label and cache age), local saved-game frequency (book panel), and engine/search scores (Stockfish and Prediction panels) are separate panels; none is presented as a prediction for the current game.

## Tests: `ExplorerCacheTest` (12): response parsing incl. blank-SAN rows, sorting, escapes; empty and malformed responses; FEN URL; persistence round trip; key semantics; corrupt cache; bound; online->offline use; fallback warning; error cases; MiniJson edge cases. Project total: 86 passing JVM tests.

## NOT verified
- No real request to Lichess was made (no network from this sandbox). The `/masters` `fen` parameter and response shape (white/draws/black, moves[].san/uci/white/draws/black, opening.eco/name) are from my knowledge of the public API; the Lichess API reference page could not be fetched here. Authentication requirement and host `explorer.lichess.org` carried over from revision 1. Please test once with a real token on a device.
- Compose changes (button, notes) syntax-checked only; no Gradle/Android build, no APK.
