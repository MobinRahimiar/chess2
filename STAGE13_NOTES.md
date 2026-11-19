# Stage 13 — PGN Game Knowledge Import

## Added
- Import `.pgn` files from Android's document picker (Storage Access Framework; no broad storage permission).
- A conservative PGN parser that reads standard headers and SAN mainline tokens, strips brace/semicolon comments, nested variations, move numbers and NAGs.
- Imported games are stored locally and contribute to exact-prefix next-move frequency in the existing opening intelligence layer.
- Imported records are clearly marked as PGN data and are not offered as restorable app games because this stage stores their move sequence rather than replayed FEN snapshots.
- Parser tests cover headers, multiple games, comments, variations and NAGs.

## Important limitations
- This is a SAN-token importer, not a full PGN legality/replay validator. Non-standard variants, malformed PGN and unusual SAN encodings may be skipped or produce incomplete move sequences.
- Frequencies represent only the files imported by the user on this device. They are not global popularity or win probabilities.
- Stockfish remains responsible for evaluating candidate moves. Imported frequency is only a repertoire prior/tie-breaker.
