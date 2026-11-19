# Stage 17 — Opening Database & Statistics

## Added
- Expanded the offline curated opening book to 50+ named lines with ECO identifiers.
- Opening suggestions now display ECO codes and retain the existing legal-move validation.
- Existing PGN import and local-device frequency counts remain available and are clearly labelled as local data.
- Added an optional Lichess Masters Opening Explorer lookup for the current position.
- Converts the current move history into legal UCI coordinate notation before querying the explorer.
- Displays total master-game counts, white/draw/black result percentages, and candidate continuation statistics.
- Added HTTPS Internet permission. The app remains playable and its curated book remains usable offline.
- The user-provided Lichess read token is kept only in Compose memory for the current screen; it is not persisted in SharedPreferences or files.
- Handles rejected tokens, rate limiting, HTTP errors, and empty candidate lists with visible messages.

## Data honesty
- Offline repertoire weights are curated ordering hints, not global popularity or win-rate data.
- Imported PGNs contribute only to this device's local frequency counts.
- The online result panel is explicitly sourced from Lichess Masters data and is not presented as a prediction for the current game.

## API
Uses the Lichess Opening Explorer Masters endpoint on `explorer.lichess.org`, with a bearer token and one request at a time. The current Lichess API documentation describes the Opening Explorer endpoints and their host; API rate-limit guidance says to serialize requests and wait after HTTP 429 responses.

## Verification status
- Added unit tests for ECO metadata, local-prefix counting, and history-to-UCI conversion.
- Full Android Gradle build and device/network test were not run in this environment (no Gradle executable or Android SDK is available here).
- No APK is claimed.
