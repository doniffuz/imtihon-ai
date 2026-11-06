# IMTIHON AI

O'zbek tilida test yechish, natijani tahlil qilish va zaif mavzularni aniqlash uchun offline Android ilova.
Ilova rasmiy davlat ilovasi emas.

## Imkoniyatlar
Onboarding · 9 fan (144 savol) · test (belgilash, oldingi/keyingi, taymer) · natija va xatolar tahlili · statistika · zaif mavzular mashqi · tugallanmagan testni davom ettirish · dark mode · to'liq offline, permission yo'q.

## Texnologiya
Kotlin, XML Views + Material 3, SQLite (`SQLiteOpenHelper`), savollar `assets/questions.json` da. minSdk 26, targetSdk 36, AGP 8.9.1, Gradle 8.11.1.

## Savol qo'shish
- Kichik to'plam: `assets/questions.json` ga yozuv qo'shing va `Db.kt` dagi DB versiyasini oshiring.
- Katta baza (100 000 – 1 000 000+): savollarni **JSONL** paket sifatida bering (`assets/packs/*.jsonl` yoki keyinchalik serverdan yuklangan fayl → `Db.importPack(stream)`).
  Paket qator-ma'qator o'qiladi, 1000 tadan tranzaksiyada yoziladi, har savol validatsiyadan o'tadi, dublikat (`hash`) va eski versiya rad etiladi.
  Butun baza APK ichiga joylanmaydi va hech qachon RAMga yuklanmaydi.
- Yangi fan: `res/values/arrays.xml` ga slug va nom qo'shing, savollarda shu slug'ni ishlating.

## Imtihon tizimi
Fan → tur (vaqtli imtihon / mashq / mavzu bo'yicha / aralash) → savollar soni → vaqt. Vaqtli imtihonda taymer teskari (countdown) va
`boshlanish vaqti + davomiylik` dan hisoblanadi (fon/yopilish ta'sir qilmaydi); 00:00 da imtihon avtomatik yakunlanib qulflanadi.
Batafsil holat: `STATUS.md`. SQL tekshiruvi: `python3 scripts/verify_db.py`. Unit testlar: `cd android && ./gradlew test`.

## Build
```
cd android
./gradlew assembleDebug  # debug APK
./gradlew bundleRelease  # release AAB
```
`./gradlew` kerakli wrapper jar faylini birinchi ishga tushirishda o‘zi yuklaydi.

## Release
Qadamlar, Data safety, store listing: `RELEASE_GUIDE.md`, `PLAY_CONSOLE_LISTING.md`.

## Privacy
`docs/privacy.html` (GitHub Pages: Source = `/docs`). Ilova ma'lumot yig'maydi.

## License
Litsenziyani o'zingiz tanlang (masalan, All rights reserved yoki MIT) va `LICENSE` fayl qo'shing.
