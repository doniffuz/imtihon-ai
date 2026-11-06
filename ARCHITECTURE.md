# IMTIHON AI — Arxitektura (1-bosqich)

Slogan: "Bilimingni sinab ko'r. Natijangni oshir."
Eslatma: ilova rasmiy DTM/UZBMB ilovasi emas, shunday deb ko'rsatilmaydi.

## 1. Texnologiya
- Android: Kotlin, XML Views + Material 3, Room (offline), Retrofit/OkHttp, WorkManager (sync), ML Kit Text Recognition (OCR, qurilmada), CameraX.
- Backend: Node.js + Fastify, SQLite (better-sqlite3), JWT access + refresh token, argon2/bcrypt, rate limit.
- Admin panel: backend xizmat qiladigan oddiy web sahifa (HTML/JS), admin 2FA (TOTP) keyingi bosqichda.
- AI: faqat backend orqali (API kalit serverda .env da, APK ichida emas).
- Muhit: development / staging / production alohida .env fayllar.

## 2. Android modullari (feature-based)
app/
  core/ (network, database, di, localization, theme, util)
  feature/
    onboarding, home, subjects, tests (engine), results, ai_tutor,
    scanner (camera+ocr), mistakes, progress, ranking, profile, premium
  data/ (repository, remote, local, sync)
  domain/ (model, usecase)
Har feature: ui -> domain -> data. Matnlar faqat strings.xml (values-uz, keyinroq values-ru, values-en).

## 3. Offline va sync
- Savollar va test paketlari Room ga keshlanadi.
- Attempt (javoblar) lokal saqlanadi, har javobdan keyin yoziladi: internet uzilsa progress yo'qolmaydi.
- Sync: WorkManager, attemptlar `client_uuid` bilan yuboriladi (idempotent).
- Konflikt: server `updated_at` bo'yicha; attemptlar o'zgarmas (append-only), profil sozlamalari last-write-wins.

## 4. Real imtihon konfiguratsiyasi
Imtihon formati kodda emas, `exam_configs` jadvalida (JSON): fanlar, savollar soni, vaqt, ball, qiyinlik, bloklar. Ilova uni serverdan oladi va keshlaydi, app update shart emas.

## 5. AI xavfsizligi
- Foydalanuvchi bo'yicha rate limit (kunlik kvota: free/premium).
- Javob "rasmiy javob" deb ko'rsatilmaydi, ishonchsiz holatda: "Bu savolni tekshirish kerak".
- AI ishlamasa test tizimi ishlashda davom etadi (AI alohida modul).
- AI yaratgan savol faqat `draft` holatida tushadi.

## 6. Savol sifat nazorati
Workflow: draft -> review -> approved -> published -> archived.
Publish oldidan tekshiruvlar: duplicate, answer, explanation, options, difficulty, language. Ambiguous savollar `flagged`.

## 7. Xavfsizlik va maxfiylik
- Parollar hash, sirlar faqat serverda, HTTPS, input validation, parametrlangan SQL.
- Har so'rovda server-side authorization (user faqat o'z ma'lumotini ko'radi).
- Permission: faqat CAMERA (OCR uchun, kerak bo'lganda so'raladi).
- Account o'chirish: ilova ichida va serverda.
- Leaderboard: faqat nickname, privacy sozlamasi bilan.

## 8. Bosqichlar
1 Arxitektura -> 2 DB sxema -> 3 Backend -> 4 UI tizimi -> 5 Android skelet -> 6 Test engine -> 7 Natijalar/tahlil -> 8 AI -> 9 OCR -> 10 Admin -> 11 Premium -> 12 Testlar -> 13 Release.
Har bosqichdan keyin tekshiruv; critical xato bo'lsa keyingisiga o'tilmaydi.

## 9. Imtihon tizimi (joriy lokal realizatsiya)
- `ExamCore.kt` — Android'siz sof mantiq: `ExamMode`, `ExamTimer`, `QuestionRules` (validatsiya, hash), `Shuffle`.
- `Db.kt` v4 — `questions(+topic_id, version, hash)`, `attempts(+mode, duration_ms, start_elapsed, cur_q, locked, reason)`, `answers(+opt_order)`.
  Indekslar: `subject`, `(subject, topic_id, topic)`, `topic`, `difficulty`, `hash UNIQUE`, `id` (PK).
- Savol tanlash: `ORDER BY RANDOM()` yo'q; kichik to'plamda id'lar aralashtiriladi, katta to'plamda tasodifiy rowid nuqtalari indeks orqali tekshiriladi.
  Savollar va variantlar tartibi imtihon yaratilganda saqlanadi — qayta ochilganda o'zgarmaydi.
- Vaqt: `ExamTimer.remainingMs` = davomiylik − (hozir − boshlanish); soat orqaga surilsa `elapsedRealtime` bilan himoyalangan.
- Qulf: `setAnswer/setMarked` faqat ochiq (finished=0, locked=0) imtihonda ishlaydi; vaqt tugagan bo'lsa imtihon avtomatik yakunlanadi.
- Backend: `backend/API.md`, `backend/schema.sql`. Ilovada INTERNET ruxsati hali yo'q.
