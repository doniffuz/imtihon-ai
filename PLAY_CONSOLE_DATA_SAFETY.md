# Data Safety — koddan tekshirilgan xulosa

## Tekshiruv natijasi (kod bo'yicha)
| Tekshirilgan narsa | Topilgan |
|---|---|
| `INTERNET` va boshqa permission | Yo'q (manifestda `uses-permission` yo'q) |
| Tarmoq kodi (HTTP, Retrofit, WebView, socket) | Yo'q |
| Uchinchi tomon SDK (Firebase, AdMob, analytics, crash) | Yo'q. Dependency'lar: core-ktx, appcompat, material |
| Personal data | Faqat ixtiyoriy ism — qurilmada (SharedPreferences) |
| Location / Contacts / Financial / Photos-files | Ishlatilmaydi |
| App activity | Test natijalari — qurilma ichidagi SQLite, serverga chiqmaydi |
| Device ID / Advertising ID | O'qilmaydi |
| Diagnostics / Crash logs | Yig'ilmaydi, `Log` chaqiruvlari yo'q |
| Backup | `allowBackup=false`, bulutga zaxira yo'q |

## Play Console'da tanlanadigan javoblar
- **Does your app collect or share any of the required user data types?** → **No**
- Barcha ma'lumot turlari (Personal info, Financial info, Health, Messages, Photos/videos, Audio, Files, Calendar, Contacts, **Location**, **App activity**, Web browsing, App info/performance, **Device or other IDs**) → belgilanmaydi.
- Security practices bo'limi: ma'lumot yig'ilmagani uchun shifrlash va o'chirish savollari ko'rsatilmasligi mumkin. Ko'rsatilsa: ma'lumotni o'chirish yo'li — ilova ichida *Profil → Barcha ma’lumotlarni o‘chirish*, yoki ilovani o‘chirish.
- Ads: **No**. Analytics: **None**.

## Sabab
Google ta'rifiga ko'ra ma'lumot qurilmadan chiqmasa va siz (yoki uchinchi tomon) unga kira olmasangiz, u "collected" hisoblanmaydi.

## Qachon qayta ko'rib chiqish kerak
Server, login, Firebase, AdMob, crash reporting yoki INTERNET permission qo'shilsa — bu fayl, `docs/privacy.html` va Play Console deklaratsiyasini yangilang.
