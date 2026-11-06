# Holat hisoboti (haqiqiy)

Bu muhitda Kotlin kompilyatori, Android SDK va internet yo'q edi, shuning uchun **ilova kompilyatsiya qilinmadi va qurilmada ishga tushirilmadi**.
Quyida nima haqiqatan tekshirilgani va nima tekshirilmagani ajratib ko'rsatilgan.

## TEKSHIRILDI (skript bilan ishga tushirildi: `python3 scripts/verify_db.py` — 28/28)
Db.kt dan SQL matni olinib, SQLite'da bajarildi (Kotlin mantig'ining Python nusxasi bilan):
- 144 ta mavjud savol to'liq saqlanadi, invalid/dublikat yo'q; qayta import idempotent.
- Dublikat (boshqa id, bir xil matn, variantlar tartibi boshqa) va invalid savollar (takror variant, answer=7) rad etiladi; eski versiya yangisini bosmaydi.
- v3 → v4 migratsiya: eski urinishlar saqlanadi, yangi ustunlar default bilan to'ladi.
- 1 000 000 qatorli bazada: COUNT/probe/MIN-MAX/topics so'rovlari indeks ishlatadi; 50/100/200 savol 15–17 ms da, hammasi unique; o'qilgan qator soni ≤ savollar soni (millionlik ro'yxat RAMga yuklanmaydi); pagination 50 qator.
- Qulf SQL'i: yakunlangandan keyin javob o'zgarmaydi; yakunlash idempotent; savollar/variantlar tartibi qayta o'qilganda bir xil; bir imtihonda bir savol ikki marta bo'lmaydi (PK).
- Natija hisobi: 50 savol / 42 javob / 8 javobsiz / 35 to'g'ri / 7 noto'g'ri → 83.3%.
- `backend/schema.sql` xatosiz bajariladi. Barcha XML fayllar to'g'ri, kodda ishlatilgan barcha `R.string.*` mavjud.

## YOZILDI, LEKIN ISHGA TUSHIRILMAGAN (qurilmada/Gradle'da tekshirish kerak)
Barcha Kotlin kodi (qavslar balansi tekshirilgan, xolos). Jumladan: countdown timer UI, 00:00 da avto-yakun, ilova qayta ochilganda davom ettirish,
10/5/1 daqiqa ogohlantirishi (Snackbar), ExamSetupActivity, natija ekrani, Timed Exam statistikasi, Zaif mavzular (Fan → Mavzu).
JVM testlari yozilgan (`ExamCoreTest.kt`: CASE 1, 2, 4, soat orqaga surilishi, hash, validator, shuffle) — **ishga tushirilmagan**: `cd android && ./gradlew test`.

Test scenariylari holati:
- CASE 1, 2, 4, 5 — kod va unit-testlar bor, qurilmada tekshirilmagan.
- CASE 3 (internetsiz) — ilovada INTERNET ruxsati ham, tarmoq kodi ham yo'q, taymer faqat qurilma soatiga bog'liq; qurilmada tekshirilmagan.
- CASE 6 (100 savol unique) — SQL darajasida tekshirildi (C6, D7), ilova darajasida emas.
- CASE 7 (1M savol RAMga yuklanmasin) — SQL/so'rov darajasida tekshirildi; qurilmada xotira o'lchanmagan.

## QILINMAGAN / CHEKLOVLAR
- Backend va tarmoq klienti yo'q (`RemoteQuestionSource` — faqat interfeys, `backend/API.md` — shartnoma).
- Ekranda 4 tur chiqadi (Vaqtli imtihon, Mashq, Mavzu bo'yicha, Aralash); `EXAM` va `FULL_EXAM` enum'da tayyor, UI'da yo'q.
- "Skipped" alohida saqlanmaydi: `selected = -1` bo'lsa javobsiz hisoblanadi (natijada shunday sanaladi).
- Mavzu bo'yicha tasodifiy tanlash juda kam uchraydigan mavzuda (zichlik past) sekinlashishi mumkin (probe indeksi fan bo'yicha).
- Tasodifiy tanlash rowid bo'shliqlari (o'chirilgan qatorlar) bo'lsa mukammal bir tekis emas.
- "Ball" = to'g'ri javoblar / jami savollar; "foiz" = to'g'ri / javob berilganlar (spetsifikatsiyadagi 83.3% misoliga mos). Boshqacha formula kerak bo'lsa ayting.
- Vaqt telefon soatiga tayanadi; soat orqaga surilishidan `elapsedRealtime` himoya qiladi, lekin qayta yoqilgandan keyin soatni oldinga/orqaga surish to'liq oldini olinmaydi (to'liq himoya — serverda).
