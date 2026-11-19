# ChessMaster AI — Stage 16 (Real Stockfish integration)

پروژهٔ اندرویدی شطرنج با رابط فارسی، تحلیل‌گر UCI/Stockfish، مربی هوشمند و بخش آکادمی.

## محتوای این مرحله

- GitHub Actions برای اجرای تست‌های واحد و ساخت خودکار APK
- ذخیرهٔ APK در بخش Artifacts هر اجرای موفق
- ساخت موتور رسمی Stockfish 19 برای ARM64 با Android NDK در هر اجرای CI
- ایجاد GitHub Release و پیوست APK هنگام Push کردن Tag نسخه‌ای مثل `v0.3.0`
- راهنمای قدم‌به‌قدم GitHub در فایل `CI_BUILD.md`

## شروع سریع

1. این پوشه را در یک GitHub Repository قرار دهید؛ پوشهٔ مخفی `.github/workflows` را حذف نکنید.
2. به زبانهٔ **Actions** مخزن بروید.
3. بعد از اجرای موفق **Android CI and APK**، فایل APK را از بخش **Artifacts** دانلود کنید.
4. Workflow موتور Stockfish 19 را می‌سازد، داخل APK قرار می‌دهد و SHA-256 و Commit سورس را نیز ذخیره می‌کند.

راهنمای کامل فارسی: [`CI_BUILD.md`](CI_BUILD.md)

## وضعیت تحویل

این بسته سورس کامل و مسیر ساخت موتور را دارد، اما باینری واقعی Stockfish و APK از پیش ساخته‌شده داخل ZIP نیستند. برای ساخت محلی، Android NDK r27c+ را نصب کنید و دستورهای `CI_BUILD.md` را اجرا کنید. هیچ موتور نمایشی یا فایل اجرایی جعلی در پروژه قرار داده نشده است.

## پیش‌نیاز ساخت

- JDK 17
- Gradle 8.11.1
- Android SDK Platform 36 / Build Tools 36.0.0
- برای موتور Stockfish: Android NDK r27c یا جدیدتر

## مجوز Stockfish

در صورت بسته‌بندی موتور Stockfish، شرایط GPL-3.0-or-later باید رعایت شود. جزئیات در `STAGE7_NOTES.md` و `CI_BUILD.md` آمده است.

## Stage 12 — Opening Intelligence
See `STAGE12_NOTES.md` for the offline opening repertoire, local saved-game frequency support, legal-move validation, and current limitations.

## Stage 13 — PGN game knowledge import
Use **Import PGN database** in the game screen to select a `.pgn` file from your device. Imported mainline moves are saved locally and used as exact-prefix frequency hints for opening suggestions. These counts describe only your imported files; Stockfish remains the source for position evaluation. See `STAGE13_NOTES.md` for parser limitations.

## Stage 14 — Advanced Tactical Intelligence

Adds corrected slider motif geometry, legal mate-in-one verification, conservative sacrifice-candidate warnings, tactical board-square highlighting, and regression tests. Multi-move traps and forced mating sequences remain Stage 15 / engine-search work. See `STAGE14_NOTES.md` for scope and limitations.

## Stage 15 — Multi-Move Prediction

See `STAGE15_NOTES.md` for implementation details and limits. The local predictor searches legal continuations with alpha-beta pruning and a bounded heuristic. It is separate from Stockfish; its candidate lines are not opponent-move probabilities.

## Stage 16 — Real Stockfish Integration

See `STAGE16_NOTES.md`. The app now exposes engine resource settings and uses the official pinned Stockfish 19 ARM64 NDK build script. The generated binary is not included in this source ZIP; run the build script in an NDK-equipped environment before assembling an APK.
\n\n## Stage 17 — Opening database\n\nThe app includes an offline ECO-tagged opening book, device-local PGN frequency counts, and an optional Lichess Masters explorer lookup. Online statistics require a user-supplied Lichess read token; it is not saved by the app. Offline play and opening suggestions do not require a network connection. See `STAGE17_NOTES.md`.\n