# FINAL RELEASE CHECKLIST

Ushbu muhitda Android SDK va internet yo'q edi: **hech qanday Gradle build ishga tushirilmadi**, ilova qurilmada sinalmadi.

- [x] Application ID — **DONE** (`uz.imtihonai.app`, manifest/namespace/build.gradle'da tekshirildi)
- [x] Version — **DONE** (1.0.0 / versionCode 1, `-P` orqali oshiriladi)
- [x] Target SDK — **DONE** (36; Google Play 2026-08-31 dan talab qiladi, compileSdk 36)
- [x] Release configuration — **DONE** (R8 + resource shrink, debuggable=false, signing env/properties orqali)
- [x] Gradle — **DONE** (AGP 8.9.1, Gradle 8.11.1, Kotlin 1.9.25) — compile qilib tekshirilmagan
- [x] Gradle Wrapper — **DONE** (`gradlew`, `gradlew.bat`, properties). Jar birinchi ishga tushirishda yuklanadi
- [ ] AAB — **TODO**: `cd android && ./gradlew bundleRelease` (yoki GitHub Actions). Hali build qilinmagan
- [ ] APK — **TODO**: `./gradlew assembleDebug`. Hali build qilinmagan
- [ ] Privacy Policy — **TODO**: email kiritildi (donyorabdullayev892@gmail.com), matn koddagi haqiqatga mos; faqat HTTPS hostingga (GitHub Pages) joylash qoldi
- [x] Data Safety — **DONE** (`PLAY_CONSOLE_DATA_SAFETY.md`; Play Console'da o'zingiz kiritasiz)
- [x] Content Rating — **DONE** (javoblar tayyor; anketani Console'da o'zingiz yuborasiz)
- [ ] Store Listing — **TODO**: matnlar tayyor; email, privacy URL va screenshotlar kerak
- [x] App Icon — **DONE** (adaptive + monochrome, 512×512 PNG)
- [x] Feature Graphic — **DONE** (1024×500 PNG)
- [ ] Screenshots — **TODO**: emulyator/telefondan 2–8 ta olish (reja `PLAY_CONSOLE_LISTING.md`da)
- [x] README — **DONE**
- [x] GitHub security — **DONE** (.gitignore: keystore, properties, build, .env, aab/apk; kodda secret/API key topilmadi). Push'dan oldin `git status` bilan tekshiring
- [ ] Crash check — **TODO**: kod ko'rigi bajarildi (bo'sh data, indeks chegaralari, ikki marta bosish, aylantirish, taymer), lekin qurilmada oqim sinalmagan
- [x] Offline check — **DONE** (kod bo'yicha: INTERNET permission va tarmoq kodi yo'q, savollar assets'da)
- [ ] Release signing — **TODO**: konfiguratsiya tayyor; `upload.jks` ni siz yaratasiz (`RELEASE_GUIDE.md`)
