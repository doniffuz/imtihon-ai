package uz.imtihonai.app

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

data class ImportReport(
    var inserted: Int = 0,
    var updated: Int = 0,
    var duplicates: Int = 0,
    var invalid: Int = 0,
    var older: Int = 0
)

/**
 * Savollarni bazaga yozadi: validatsiya + dublikat tekshiruvi (hash) + versiya.
 * JSONL paketi qator-ma'qator o'qiladi va 1000 tadan tranzaksiyada yoziladi — butun fayl hech qachon RAMga yuklanmaydi.
 *
 * JSONL qatori: {"id","subject","topic","difficulty","question","options":[4],"answer":0..3,
 *                "explanation","source","version","topicId"(ixtiyoriy)}
 */
object QuestionImporter {
    private const val BATCH = 1000

    fun importArray(db: SQLiteDatabase, arr: JSONArray): ImportReport {
        val r = ImportReport()
        db.beginTransaction()
        try {
            for (i in 0 until arr.length()) importOne(db, arr.optJSONObject(i), r)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return r
    }

    fun importJsonl(db: SQLiteDatabase, input: InputStream): ImportReport {
        val r = ImportReport()
        input.bufferedReader(Charsets.UTF_8).use { br ->
            var n = 0
            db.beginTransaction()
            try {
                while (true) {
                    val line = br.readLine() ?: break
                    if (line.isBlank()) continue
                    val obj = try { JSONObject(line) } catch (e: Exception) { null }
                    importOne(db, obj, r)
                    n++
                    if (n % BATCH == 0) {
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        db.beginTransaction()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        return r
    }

    private fun importOne(db: SQLiteDatabase, o: JSONObject?, r: ImportReport) {
        if (o == null) { r.invalid++; return }
        try {
            val opts = o.getJSONArray("options")
            val options = List(opts.length()) { opts.getString(it) }
            val id = o.getString("id")
            val subject = o.getString("subject")
            val topic = o.optString("topic").ifBlank { "Umumiy" }
            val question = o.getString("question")
            val answer = o.getInt("answer")
            val difficulty = o.optInt("difficulty", 1)
            val version = o.optInt("version", 1)
            if (QuestionRules.validate(id, subject, question, options, answer, difficulty) != null) {
                r.invalid++; return
            }
            val cv = ContentValues().apply {
                put("id", id)
                put("subject", subject)
                put("topic", topic)
                put("topic_id", o.optString("topicId").ifBlank { QuestionRules.topicId(subject, topic) })
                put("difficulty", difficulty)
                put("question", question)
                put("a", options[0]); put("b", options[1]); put("c", options[2]); put("d", options[3])
                put("answer", answer)
                put("explanation", o.optString("explanation"))
                put("source", o.optString("source"))
                put("version", version)
                put("hash", QuestionRules.hash(subject, question, options))
            }
            val existing: Int? = db.rawQuery("SELECT version FROM questions WHERE id=?", arrayOf(id)).use {
                if (it.moveToFirst()) it.getInt(0) else null
            }
            if (existing == null) {
                try { db.insertOrThrow("questions", null, cv); r.inserted++ }
                catch (e: SQLiteConstraintException) { r.duplicates++ }
            } else if (version < existing) {
                r.older++
            } else {
                try { db.update("questions", cv, "id=?", arrayOf(id)); r.updated++ }
                catch (e: SQLiteConstraintException) { r.duplicates++ }
            }
        } catch (e: Exception) {
            r.invalid++ // bitta buzuq yozuv butun importni to'xtatmaydi
        }
    }
}

/**
 * Backend uchun ulanish nuqtasi (hozircha amalga oshirilmagan — ilovada INTERNET ruxsati yo'q).
 * Keyin: sahifalab (page/limit) savollarni olib, QuestionImporter orqali lokal bazaga yozadigan realizatsiya qo'shiladi.
 * Server javobida to'g'ri javob bo'lmaydigan "onlayn imtihon" rejimi uchun backend/API.md ga qarang.
 */
interface RemoteQuestionSource {
    /** Bitta sahifa savollar JSONL qatorlari ko'rinishida. Bo'sh ro'yxat — tugadi. */
    fun fetchPage(subject: String, page: Int, limit: Int): List<String>
}
