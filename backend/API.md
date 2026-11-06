# Backend API shartnomasi (hali amalga oshirilmagan — ilova keyinchalik shunga ulanadi)

Ilova hozir to'liq lokal. Server qo'shilganda `RemoteQuestionSource` (ExamCore/QuestionImporter.kt) realizatsiyasi yoziladi;
yuklangan savollar `QuestionImporter` orqali (validatsiya + dublikat + versiya) lokal SQLite ga tushadi.

## Pagination (keyset, 1M+ uchun)
`GET /v1/questions?subject=math&topic=...&after=<lastId>&limit=50` → `{items:[...], nextAfter:"..."}`
- `limit` ≤ 200. OFFSET ishlatilmaydi (katta sahifalarda sekin); `page=1&limit=50` ko'rinishi ham qo'llab-quvvatlanishi mumkin.
- Inkremental: `GET /v1/questions?since=<updated_at>&after=<id>` (indeks: `idx_q_sync`).
- Pack: `GET /v1/packs/{subject}/{version}.jsonl.gz` — oldindan yuklash (offline) uchun, oqim bilan import qilinadi.

## correctAnswer xavfsizligi
Offline rejim tabiatan javoblarni qurilmada saqlaydi (aks holda internetsiz ishlamaydi). Shuning uchun ikki rejim:
1. **Offline mashq/imtihon** — pack ichida `answer` bor; pack faqat autentifikatsiyadan keyin, foydalanuvchi bo'yicha rate-limit bilan beriladi.
   Bu rejim natijalari "rasmiy/reytingli" hisoblanmaydi.
2. **Onlayn reytingli imtihon** — `POST /v1/exams` server savollarni tanlaydi va **`answer`siz** qaytaradi;
   `POST /v1/exams/{id}/submit` javoblarni yuboradi, ball serverda hisoblanadi. Oddiy `GET /v1/questions` javobida `answer` hech qachon bo'lmaydi.

## Autentifikatsiya va kalitlar
- API kalit APK ichida saqlanmaydi. Qurilma `POST /v1/auth/guest|login` orqali qisqa muddatli JWT + refresh token oladi.
- Server tomonda: input validatsiya, parametrlangan SQL, rate limit, vaqt tekshiruvi (`started_at + duration`) — mijoz soatiga ishonilmaydi.
- Attemptlar `client_uuid` bilan yuboriladi (idempotent), `backend/schema.sql` → `attempts.UNIQUE(user_id, client_uuid)`.
