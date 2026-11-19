# ساخت APK همراه موتور واقعی Stockfish

این پروژه در GitHub Actions تست‌های واحد را اجرا می‌کند، نسخهٔ رسمی Stockfish 19 را از سورس اصلی برای Android ARM64 می‌سازد و سپس APK حاوی موتور را بسته‌بندی می‌کند.

## اجرای خودکار

1. محتویات پوشهٔ `ChessMasterAI_Android` را در ریشهٔ مخزن GitHub قرار دهید؛ پوشهٔ مخفی `.github` را حذف نکنید.
2. تغییرات را به شاخهٔ `main` یا `master` Push کنید، یا از بخش Actions اجرای دستی Workflow را شروع کنید.
3. Workflow با نام **Android CI, Stockfish ARM64 and APK** این کارها را انجام می‌دهد:
   - نصب JDK 17، Android SDK و NDK r27c
   - دریافت سورس رسمی Stockfish با Tag ثابت `sf_19`
   - دریافت NNUE رسمی با دستور `make net`
   - کامپایل ARM64 با Android NDK و Strip کردن خروجی
   - ثبت Commit، نوع ELF و SHA-256 باینری
   - اجرای تست‌های JVM و ساخت APK
   - بررسی وجود `lib/arm64-v8a/libstockfish.so` داخل APK
4. APK و اطلاعات منشأ باینری در بخش **Actions → اجرای موفق → Artifacts** قرار می‌گیرند.

## ساخت محلی موتور

پیش‌نیازها: Linux x86_64 یا macOS سازگار، Git، Make، ابزارهای پایهٔ ساخت و Android NDK r27c یا جدیدتر.

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk
bash tools/build_stockfish_android.sh
gradle testDebugUnitTest
gradle assembleDebug
```

فایل اجرایی در `app/src/main/jniLibs/arm64-v8a/libstockfish.so` قرار می‌گیرد. پسوند `.so` برای آن است که Android آن را در `nativeLibraryDir` استخراج کند؛ فایل از نظر ماهیت یک executable نوع PIE است، نه shared library معمولی.

## محدودیت ABI

فعلاً فقط `arm64-v8a` در `abiFilters` فعال است. گوشی‌های ARM64 پشتیبانی می‌شوند؛ برای دستگاه‌های ۳۲ بیتی یا شبیه‌سازهای x86 باید باینری و فیلتر ABI جداگانه اضافه شود.

## انتشار نسخه‌دار

برای انتشار، یک Tag مثل `v0.3.0` بسازید و Push کنید:

```bash
git tag v0.3.0
git push origin v0.3.0
```

Workflow پس از تست و ساخت، APK را به GitHub Release پیوست می‌کند.

## مجوز و توزیع

Stockfish تحت GPL-3.0-or-later منتشر می‌شود. هنگام توزیع APK حاوی موتور، متن مجوز و کد منبع متناظر/تعهدات GPL را رعایت کنید. Workflow فایل `STOCKFISH_LICENSE.txt`، Commit دقیق سورس، مشخصات باینری و SHA-256 را به‌عنوان Artifact نگه می‌دارد.

## وضعیت تأیید

ساخت موفق در GitHub Actions هنوز باید واقعاً اجرا شود. این محیط Android SDK و NDK ندارد؛ بنابراین APK و باینری ARM64 در اینجا ساخته یا روی دستگاه اجرا نشده‌اند. پس از اولین اجرای سبز CI، نصب و اجرای موتور روی گوشی واقعی نیز باید آزمایش شود.


## تنظیم منابع موتور در برنامه

در صفحه بازی و بخش Stockfish Analysis می‌توان مقدارهای زیر را تغییر داد:
- Depth: عمق جست‌وجو
- Threads: تعداد رشته‌ها، محدود به تعداد پردازنده‌های منطقی دستگاه
- Hash: حافظه جدول هش بر حسب MB
- MultiPV: تعداد خطوط برتر خروجی (۱ تا ۵)

این مقادیر به‌صورت UCI option به موتور ارسال می‌شوند؛ موتورهایی که گزینه‌ای را اعلام نکنند، آن گزینه را دریافت نمی‌کنند.
