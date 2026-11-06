# Release qo'llanmasi

## 1. Upload keystore yaratish (bir marta)
```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
`upload.jks` va parolni **repo'ga qo'ymang**. Zaxira nusxani 2 xavfsiz joyda saqlang.

## 2. Lokal build
`android/keystore.properties` (git ignor qilingan):
```
storeFile=/to'liq/yo'l/upload.jks
storePassword=PAROL
keyAlias=upload
keyPassword=PAROL
```
```
cd android
./gradlew bundleRelease   # → app/build/outputs/bundle/release/app-release.aab
./gradlew assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
```
Birinchi ishga tushirishda `gradlew` wrapper jar'ni o'zi yuklaydi (internet kerak). Java 17 va Android SDK 36 kerak (Android Studio bilan o'rnatiladi; `local.properties` ni u o'zi yaratadi).

## 3. GitHub Actions orqali build
Repo → Settings → Secrets and variables → Actions:
- `KEYSTORE_BASE64` = `base64 -w0 upload.jks`
- `KEYSTORE_PASSWORD`, `KEY_ALIAS` (=upload), `KEY_PASSWORD`

Actions → Build → Run workflow → artifact: `imtihon-ai-release-aab`, `imtihon-ai-debug-apk`.
Secret'siz AAB imzosiz chiqadi va Play'ga yuklanmaydi.

## 4. Privacy Policy
1. `scripts/set-contact-email.sh sizning@email.uz`
2. Repo → Settings → Pages → Branch `main`, folder `/docs`.
3. URL: `https://<username>.github.io/<repo>/privacy.html` — brauzerda ochilishini tekshiring, Play Console → App content → Privacy policy ga qo'ying.

## 5. Play Console
1. Create app → nom, til (uz), App, Free.
2. App content: Privacy policy, Ads=No, App access (cheklov yo'q), Content rating (`PLAY_CONSOLE_CONTENT_RATING.md`), Target audience, Data safety (`PLAY_CONSOLE_DATA_SAFETY.md`), Government app=No.
3. Store listing: `PLAY_CONSOLE_LISTING.md`, icon va feature graphic, screenshotlar.
4. Play App Signing — yoqilgan qoladi; AAB'ni Closed testing (yoki Production) ga yuklang.
5. Yangi personal developer account bo'lsa, production'dan oldin closed test sharti (tester soni/kun) mavjud bo'lishi mumkin — Dashboard'dagi amaldagi talabni tekshiring.

## 6. Keyingi update
`./gradlew bundleRelease -PversionCode=2 -PversionName=1.0.1` (versionCode har yuklashda oshishi shart).
