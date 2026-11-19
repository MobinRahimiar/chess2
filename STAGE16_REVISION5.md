# Stage 16 — Real Stockfish Integration (revision 5)

## Changed
- The whole UCI conversation moved out of the Android-only class into `UciSession.kt` (pure JVM, `UciTransport` interface).
  `StockfishUci` now only: checks ABI/binary, validates the FEN, starts the process, and feeds `UciSession`.
- Analysis timeout now sends `stop` and gives the engine a grace period to return a `bestmove`
  (before, it just waited out the clock and failed even though the engine had a result).
- Engine death (stream closed) during search is a clear error.
- `lowerbound`/`upperbound` info lines are no longer shown as exact evaluations.
- CI: added an ELF verification step (`tools/verify_stockfish_binary.sh`) before tests.

## Tests (`UciSessionTest`, 12 tests; 74 total, all passing on the JVM)
Scripted in-memory engine: exact command order and applied options; only advertised options set; MultiPV ranking; illegal PV dropped;
bound scores ignored; illegal / "(none)" bestmove rejected; `CRITICAL ERROR` handled; engine death; handshake timeout;
stop-on-timeout with late bestmove; timeout with no answer; invalid/terminal FEN never reaches the engine.

## Verified externally
- Stockfish 19 exists (stockfishchess.org blog, dated 2026-09-05) and its release is listed as `sf_19`.
  Stockfish 19 prints `info string CRITICAL ERROR` and exits on invalid commands — the session only sends validated commands.

## NOT done / NOT verified (be exact)
- **No Stockfish binary is bundled.** No NDK and no GitHub access in this environment, so `libstockfish.so` was not built.
- I could not read the sf_19 Makefile, so `make net` / `ARCH=armv8 COMP=ndk` / `make strip` are carried over from the previous revision and are
  UNVERIFIED against sf_19. The first GitHub Actions run is the real test; if it fails in "Build official Stockfish", send me the log.
- The real binary has never been run, so real Threads/Hash/MultiPV speed, memory use and NNUE loading on a device are untested.
- No APK built. Compose/MainActivity not compiled here.
