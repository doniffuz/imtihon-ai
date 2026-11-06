package uz.imtihonai.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Question(
    val id: String,
    val subject: String,
    val topic: String,
    val difficulty: Int,
    val text: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

data class Item(val pos: Int, val question: Question, val selected: Int, val marked: Boolean)

data class Attempt(
    val id: Long,
    val subject: String,
    val started: Long,
    val finished: Long,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val timeSec: Int
)

/** Lokal baza: savollar assets/questions.json dan o'qiladi, urinishlar har javobdan keyin darhol saqlanadi. */
class Db private constructor(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "imtihon.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE questions(id TEXT PRIMARY KEY, subject TEXT NOT NULL, topic TEXT NOT NULL, " +
                "difficulty INTEGER NOT NULL, question TEXT NOT NULL, a TEXT NOT NULL, b TEXT NOT NULL, " +
                "c TEXT NOT NULL, d TEXT NOT NULL, answer INTEGER NOT NULL, explanation TEXT, source TEXT)"
        )
        db.execSQL("CREATE INDEX idx_q_subject ON questions(subject)")
        db.execSQL(
            "CREATE TABLE attempts(id INTEGER PRIMARY KEY AUTOINCREMENT, subject TEXT NOT NULL, " +
                "started INTEGER NOT NULL, finished INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, " +
                "correct INTEGER NOT NULL DEFAULT 0, wrong INTEGER NOT NULL DEFAULT 0, " +
                "skipped INTEGER NOT NULL DEFAULT 0, time_sec INTEGER NOT NULL DEFAULT 0, day TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE answers(attempt_id INTEGER NOT NULL, pos INTEGER NOT NULL, question_id TEXT NOT NULL, " +
                "selected INTEGER NOT NULL DEFAULT -1, marked INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(attempt_id, question_id))"
        )
        seed(db)
    }

    /** Savollar bazasi yangilanganda (versiya oshganda) savollar qayta yoziladi, urinishlar saqlanib qoladi. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        seed(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val json = ctx.assets.open("questions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("options")
            val cv = ContentValues().apply {
                put("id", o.getString("id"))
                put("subject", o.getString("subject"))
                put("topic", o.getString("topic"))
                put("difficulty", o.getInt("difficulty"))
                put("question", o.getString("question"))
                put("a", opts.getString(0)); put("b", opts.getString(1))
                put("c", opts.getString(2)); put("d", opts.getString(3))
                put("answer", o.getInt("answer"))
                put("explanation", o.optString("explanation"))
                put("source", o.optString("source"))
            }
            db.insertWithOnConflict("questions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun questionCount(subject: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM questions WHERE subject=?", arrayOf(subject)).use {
            it.moveToFirst(); it.getInt(0)
        }

    /** Fan bo'yicha tasodifiy savollar bilan yangi urinish yaratadi. Savol bo'lmasa -1. */
    fun createAttempt(subject: String, maxCount: Int): Long {
        val db = writableDatabase
        val ids = ArrayList<String>()
        db.rawQuery(
            "SELECT id FROM questions WHERE subject=? ORDER BY RANDOM() LIMIT ?",
            arrayOf(subject, maxCount.toString())
        ).use { while (it.moveToNext()) ids.add(it.getString(0)) }
        return insertAttempt(subject, ids)
    }

    /** Zaif mavzular (aniqlik 70% dan past) bo'yicha mashq. Zaif mavzu bo'lmasa -1. */
    fun createWeakAttempt(maxCount: Int): Long {
        val topics = weakTopics().map { it.first }
        if (topics.isEmpty()) return -1
        val marks = topics.joinToString(",") { "?" }
        val args = (topics + maxCount.toString()).toTypedArray()
        val ids = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT id FROM questions WHERE topic IN ($marks) ORDER BY RANDOM() LIMIT ?", args
        ).use { while (it.moveToNext()) ids.add(it.getString(0)) }
        return insertAttempt("weak", ids)
    }

    private fun insertAttempt(subject: String, ids: List<String>): Long {
        if (ids.isEmpty()) return -1
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("subject", subject)
                put("started", System.currentTimeMillis())
                put("total", ids.size)
                put("day", today())
            }
            val attemptId = db.insert("attempts", null, cv)
            ids.forEachIndexed { pos, qid ->
                val a = ContentValues().apply {
                    put("attempt_id", attemptId); put("pos", pos); put("question_id", qid)
                }
                db.insert("answers", null, a)
            }
            db.setTransactionSuccessful()
            return attemptId
        } finally {
            db.endTransaction()
        }
    }

    private fun readAttempt(c: android.database.Cursor) = Attempt(
        c.getLong(0), c.getString(1), c.getLong(2), c.getLong(3),
        c.getInt(4), c.getInt(5), c.getInt(6), c.getInt(7), c.getInt(8)
    )

    private val attemptCols = "id, subject, started, finished, total, correct, wrong, skipped, time_sec"

    fun attempt(id: Long): Attempt? =
        readableDatabase.rawQuery("SELECT $attemptCols FROM attempts WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) readAttempt(it) else null
        }

    fun unfinished(): Attempt? =
        readableDatabase.rawQuery(
            "SELECT $attemptCols FROM attempts WHERE finished=0 ORDER BY id DESC LIMIT 1", null
        ).use { if (it.moveToFirst()) readAttempt(it) else null }

    fun finishedAttempts(): List<Attempt> {
        val list = ArrayList<Attempt>()
        readableDatabase.rawQuery(
            "SELECT $attemptCols FROM attempts WHERE finished>0 ORDER BY finished DESC", null
        ).use { while (it.moveToNext()) list.add(readAttempt(it)) }
        return list
    }

    fun items(attemptId: Long): List<Item> {
        val list = ArrayList<Item>()
        readableDatabase.rawQuery(
            "SELECT a.pos, a.selected, a.marked, q.id, q.subject, q.topic, q.difficulty, q.question, " +
                "q.a, q.b, q.c, q.d, q.answer, q.explanation " +
                "FROM answers a JOIN questions q ON q.id = a.question_id " +
                "WHERE a.attempt_id=? ORDER BY a.pos",
            arrayOf(attemptId.toString())
        ).use {
            while (it.moveToNext()) {
                val q = Question(
                    it.getString(3), it.getString(4), it.getString(5), it.getInt(6), it.getString(7),
                    listOf(it.getString(8), it.getString(9), it.getString(10), it.getString(11)),
                    it.getInt(12), it.getString(13) ?: ""
                )
                list.add(Item(it.getInt(0), q, it.getInt(1), it.getInt(2) == 1))
            }
        }
        return list
    }

    fun setAnswer(attemptId: Long, questionId: String, selected: Int) {
        val cv = ContentValues().apply { put("selected", selected) }
        writableDatabase.update("answers", cv, "attempt_id=? AND question_id=?", arrayOf(attemptId.toString(), questionId))
    }

    fun setMarked(attemptId: Long, questionId: String, marked: Boolean) {
        val cv = ContentValues().apply { put("marked", if (marked) 1 else 0) }
        writableDatabase.update("answers", cv, "attempt_id=? AND question_id=?", arrayOf(attemptId.toString(), questionId))
    }

    /** Natijani hisoblab, urinishni yakunlaydi. */
    fun finishAttempt(attemptId: Long) {
        val att = attempt(attemptId) ?: return
        if (att.finished > 0) return
        val items = items(attemptId)
        val correct = items.count { it.selected == it.question.answer }
        val skipped = items.count { it.selected < 0 }
        val wrong = items.size - correct - skipped
        val now = System.currentTimeMillis()
        val cv = ContentValues().apply {
            put("finished", now)
            put("correct", correct)
            put("wrong", wrong)
            put("skipped", skipped)
            put("time_sec", ((now - att.started) / 1000).toInt())
            put("day", today())
        }
        writableDatabase.update("attempts", cv, "id=?", arrayOf(attemptId.toString()))
    }

    /** Mavzu bo'yicha (mavzu, to'g'ri, jami) — bitta urinish uchun. */
    fun topicBreakdown(attemptId: Long): List<Triple<String, Int, Int>> {
        val map = LinkedHashMap<String, IntArray>()
        for (it in items(attemptId)) {
            val arr = map.getOrPut(it.question.topic) { IntArray(2) }
            if (it.selected == it.question.answer) arr[0]++
            arr[1]++
        }
        return map.map { Triple(it.key, it.value[0], it.value[1]) }
    }

    /** Bugun javob berilgan savollar soni (yakunlangan testlar bo'yicha). */
    fun solvedToday(): Int =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(correct+wrong),0) FROM attempts WHERE finished>0 AND day=?", arrayOf(today())
        ).use { it.moveToFirst(); it.getInt(0) }

    /** Ketma-ket necha kun test yakunlangan. Bugun hali yechilmagan bo'lsa, kechagidan sanaydi. */
    fun streak(): Int {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val days = HashSet<String>()
        readableDatabase.rawQuery("SELECT DISTINCT day FROM attempts WHERE finished>0", null)
            .use { while (it.moveToNext()) days.add(it.getString(0)) }
        val cal = Calendar.getInstance()
        if (fmt.format(cal.time) !in days) cal.add(Calendar.DAY_OF_YEAR, -1)
        var n = 0
        while (fmt.format(cal.time) in days) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }

    /** Eng past aniqlikdagi mavzu: (mavzu, foiz). Ma'lumot bo'lmasa null. */
    fun weakestTopic(): Pair<String, Int>? = allTopicAccuracy().minByOrNull { it.second }

    /** Aniqligi 70% dan past mavzular (mavzu, foiz). */
    fun weakTopics(): List<Pair<String, Int>> = allTopicAccuracy().filter { it.second < 70 }

    /** Umumiy statistika: (testlar soni, to'g'ri javoblar, jami savollar, sekundlar). */
    fun totals(): IntArray =
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(correct),0), COALESCE(SUM(total),0), COALESCE(SUM(time_sec),0) " +
                "FROM attempts WHERE finished>0", null
        ).use { it.moveToFirst(); intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3)) }

    /** Fan bo'yicha (fan, to'g'ri, jami). Zaif mavzu mashqlari ham hisobga olinadi. */
    fun subjectStats(): List<Triple<String, Int, Int>> {
        val list = ArrayList<Triple<String, Int, Int>>()
        readableDatabase.rawQuery(
            "SELECT q.subject, SUM(CASE WHEN a.selected=q.answer THEN 1 ELSE 0 END), COUNT(*) " +
                "FROM answers a JOIN questions q ON q.id=a.question_id " +
                "JOIN attempts t ON t.id=a.attempt_id WHERE t.finished>0 GROUP BY q.subject", null
        ).use { while (it.moveToNext()) list.add(Triple(it.getString(0), it.getInt(1), it.getInt(2))) }
        return list
    }

    private fun allTopicAccuracy(): List<Pair<String, Int>> {
        val list = ArrayList<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT q.topic, SUM(CASE WHEN a.selected=q.answer THEN 1 ELSE 0 END), COUNT(*) " +
                "FROM answers a JOIN questions q ON q.id=a.question_id " +
                "JOIN attempts t ON t.id=a.attempt_id " +
                "WHERE t.finished>0 AND a.selected>=0 GROUP BY q.topic", null
        ).use {
            while (it.moveToNext()) {
                list.add(Pair(it.getString(0), it.getInt(1) * 100 / it.getInt(2)))
            }
        }
        return list
    }

    companion object {
        @Volatile private var instance: Db? = null
        fun get(ctx: Context): Db =
            instance ?: synchronized(this) {
                instance ?: Db(ctx.applicationContext).also { instance = it }
            }
    }
}

object Subjects {
    fun names(ctx: Context): Map<String, String> {
        val slugs = ctx.resources.getStringArray(R.array.subject_slugs)
        val names = ctx.resources.getStringArray(R.array.subject_names)
        return slugs.indices.associate { slugs[it] to names[it] } + ("weak" to ctx.getString(R.string.weak_practice_title))
    }
}

fun formatTime(sec: Int): String = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
