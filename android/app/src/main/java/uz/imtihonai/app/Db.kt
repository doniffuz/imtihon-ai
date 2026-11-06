package uz.imtihonai.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
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

data class Item(
    val pos: Int, val question: Question, val selected: Int, val marked: Boolean,
    /** Ko'rsatilgan i-o'rinda asl order[i]-variant turadi. */
    val order: IntArray = intArrayOf(0, 1, 2, 3)
)

data class Attempt(
    val id: Long,
    val subject: String,
    val started: Long,
    val finished: Long,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val timeSec: Int,
    val mode: String = "practice",
    val durationMs: Long = 0L,
    val reason: String = "manual"
) {
    val timedOut get() = reason == "timeout"
}

data class AttemptMeta(
    val id: Long,
    val mode: ExamMode,
    val durationMs: Long,
    val startWall: Long,
    val startElapsed: Long,
    val finished: Long,
    val locked: Boolean,
    val curQ: Int,
    val reason: String
) {
    val timed get() = durationMs > 0L
    val isOpen get() = finished == 0L && !locked
    fun remainingMs(
        nowWall: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime()
    ): Long = ExamTimer.remainingMs(durationMs, startWall, startElapsed, nowWall, nowElapsed)
}

/** Lokal baza: savollar assets/questions.json dan o'qiladi, urinishlar har javobdan keyin darhol saqlanadi. */
class Db private constructor(private val ctx: Context) :
    SQLiteOpenHelper(ctx, "imtihon.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE questions(id TEXT PRIMARY KEY, subject TEXT NOT NULL, topic TEXT NOT NULL, " +
                "difficulty INTEGER NOT NULL, question TEXT NOT NULL, a TEXT NOT NULL, b TEXT NOT NULL, " +
                "c TEXT NOT NULL, d TEXT NOT NULL, answer INTEGER NOT NULL, explanation TEXT, source TEXT, " +
                "topic_id TEXT, version INTEGER NOT NULL DEFAULT 1, hash TEXT)"
        )
        db.execSQL(
            "CREATE TABLE attempts(id INTEGER PRIMARY KEY AUTOINCREMENT, subject TEXT NOT NULL, " +
                "started INTEGER NOT NULL, finished INTEGER NOT NULL DEFAULT 0, total INTEGER NOT NULL, " +
                "correct INTEGER NOT NULL DEFAULT 0, wrong INTEGER NOT NULL DEFAULT 0, " +
                "skipped INTEGER NOT NULL DEFAULT 0, time_sec INTEGER NOT NULL DEFAULT 0, day TEXT NOT NULL, " +
                "elapsed INTEGER NOT NULL DEFAULT 0, mode TEXT NOT NULL DEFAULT 'practice', " +
                "duration_ms INTEGER NOT NULL DEFAULT 0, start_elapsed INTEGER NOT NULL DEFAULT 0, " +
                "cur_q INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0, " +
                "reason TEXT NOT NULL DEFAULT 'manual')"
        )
        db.execSQL(
            "CREATE TABLE answers(attempt_id INTEGER NOT NULL, pos INTEGER NOT NULL, question_id TEXT NOT NULL, " +
                "selected INTEGER NOT NULL DEFAULT -1, marked INTEGER NOT NULL DEFAULT 0, opt_order TEXT, " +
                "PRIMARY KEY(attempt_id, question_id))"
        )
        createIndexes(db)
        seed(db)
    }

    /**
     * Savollar bazasi yangilanganda (versiya oshganda) bundled savollar qayta import qilinadi (upsert),
     * urinishlar va yuklab olingan paketlardagi savollar saqlanib qoladi.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE attempts ADD COLUMN elapsed INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE questions ADD COLUMN topic_id TEXT")
            db.execSQL("ALTER TABLE questions ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE questions ADD COLUMN hash TEXT")
            db.execSQL("ALTER TABLE attempts ADD COLUMN mode TEXT NOT NULL DEFAULT 'practice'")
            db.execSQL("ALTER TABLE attempts ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE attempts ADD COLUMN start_elapsed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE attempts ADD COLUMN cur_q INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE attempts ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE attempts ADD COLUMN reason TEXT NOT NULL DEFAULT 'manual'")
            db.execSQL("ALTER TABLE answers ADD COLUMN opt_order TEXT")
            createIndexes(db)
        }
        seed(db)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_q_subject ON questions(subject)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_q_subject_topic ON questions(subject, topic_id, topic)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_q_topic ON questions(topic)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_q_difficulty ON questions(difficulty)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_q_hash ON questions(hash)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_a_attempt ON answers(attempt_id, pos)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_att_open ON attempts(finished, id)")
    }

    /** Bundled savollar: questions.json (kichik boshlang'ich to'plam) va assets/packs papkasidagi .jsonl fayllar (oqim bilan). */
    private fun seed(db: SQLiteDatabase) {
        try {
            val json = ctx.assets.open("questions.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            QuestionImporter.importArray(db, JSONArray(json))
        } catch (e: Exception) {
            // fayl o'qilmasa ilova yiqilmaydi, faqat savollar bo'sh bo'ladi
        }
        try {
            for (name in ctx.assets.list("packs").orEmpty()) {
                if (!name.endsWith(".jsonl")) continue
                ctx.assets.open("packs/$name").use { QuestionImporter.importJsonl(db, it) }
            }
        } catch (e: Exception) {
        }
    }

    /** Yuklab olingan savollar paketini (JSONL) bazaga import qiladi. Fayl oqim bilan o'qiladi. */
    fun importPack(input: java.io.InputStream): ImportReport = QuestionImporter.importJsonl(writableDatabase, input)

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun questionCount(subject: String): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM questions WHERE subject=?", arrayOf(subject)).use {
            it.moveToFirst(); it.getInt(0)
        }

    /** Fan bo'yicha tasodifiy savollar bilan oddiy (vaqtsiz) urinish. Savol bo'lmasa -1. */
    fun createAttempt(subject: String, maxCount: Int): Long =
        createExam(ExamConfig(subject, null, ExamMode.PRACTICE, maxCount, 0L))

    /** Sozlama bo'yicha imtihon yaratadi: savollar va variantlar tartibi shu yerda belgilanib saqlanadi. Savol bo'lmasa -1. */
    fun createExam(cfg: ExamConfig): Long {
        val count = cfg.count.coerceIn(1, MAX_EXAM_QUESTIONS)
        val subj = cfg.subject
        val topic = cfg.topicId
        val cond: String
        val args: Array<String>
        if (subj == null) { cond = "1=1"; args = emptyArray() }
        else if (topic != null) { cond = "subject=? AND topic_id=?"; args = arrayOf(subj, topic) }
        else { cond = "subject=?"; args = arrayOf(subj) }
        val ids = pickIds(cond, args, count)
        return insertAttempt(cfg.subject ?: "all", ids, cfg)
    }

    /** Savollar soni (shart bo'yicha). Indeks orqali sanaladi. */
    fun countWhere(cond: String, args: Array<String>): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM questions WHERE $cond", args).use {
            it.moveToFirst(); it.getInt(0)
        }

    fun availableFor(subject: String?, topicId: String?): Int {
        if (subject == null) return countWhere("1=1", emptyArray())
        if (topicId != null) return countWhere("subject=? AND topic_id=?", arrayOf(subject, topicId))
        return questionCount(subject)
    }

    /** Fan mavzulari: (topicId, nomi, savollar soni). Qoplovchi indeks ishlatiladi. */
    fun topicsOf(subject: String): List<Triple<String, String, Int>> {
        val list = ArrayList<Triple<String, String, Int>>()
        readableDatabase.rawQuery(
            "SELECT topic_id, topic, COUNT(*) FROM questions WHERE subject=? AND topic_id IS NOT NULL " +
                "GROUP BY topic_id, topic ORDER BY topic", arrayOf(subject)
        ).use { while (it.moveToNext()) list.add(Triple(it.getString(0), it.getString(1), it.getInt(2))) }
        return list
    }

    /**
     * Takrorlanmas tasodifiy savol id'lari. Butun jadval hech qachon RAMga yuklanmaydi va ORDER BY RANDOM() ishlatilmaydi:
     * - mos savollar kam (<= SMALL_SET) bo'lsa faqat id'lar o'qilib aralashtiriladi;
     * - ko'p bo'lsa tasodifiy rowid nuqtalari indeks bo'yicha tekshiriladi (har biri O(log n)).
     */
    fun pickIds(cond: String, args: Array<String>, count: Int): List<String> {
        val db = readableDatabase
        val n = countWhere(cond, args)
        if (n == 0) return emptyList()
        val want = minOf(count, n)
        if (n <= SMALL_SET) {
            val all = ArrayList<String>(n)
            db.rawQuery("SELECT id FROM questions WHERE $cond", args).use { while (it.moveToNext()) all.add(it.getString(0)) }
            all.shuffle()
            return all.take(want)
        }
        var lo = 0L
        var hi = 0L
        db.rawQuery("SELECT MIN(rowid), MAX(rowid) FROM questions WHERE $cond", args).use {
            if (it.moveToFirst()) { lo = it.getLong(0); hi = it.getLong(1) }
        }
        val rnd = java.util.Random()
        val seen = HashSet<Long>()
        val ids = ArrayList<String>(want)
        var tries = 0
        while (ids.size < want && tries < want * 30) {
            tries++
            val start = lo + (rnd.nextDouble() * (hi - lo + 1)).toLong()
            db.rawQuery(
                "SELECT rowid, id FROM questions WHERE ($cond) AND rowid>=? ORDER BY rowid LIMIT 1",
                args + start.toString()
            ).use { if (it.moveToFirst() && seen.add(it.getLong(0))) ids.add(it.getString(1)) }
        }
        if (ids.size < want) { // kamdan-kam: yetishmasa ketma-ket to'ldiriladi
            val have = ids.toHashSet()
            db.rawQuery("SELECT id FROM questions WHERE $cond ORDER BY rowid LIMIT ?", args + (want * 2).toString()).use {
                while (ids.size < want && it.moveToNext()) { val id = it.getString(0); if (have.add(id)) ids.add(id) }
            }
        }
        ids.shuffle()
        return ids
    }

    /** Sahifalab o'qish (page 1 dan boshlanadi). Hech qachon hammasini yuklamaydi: limit 1..200. */
    fun page(subject: String, page: Int, limit: Int = 50): List<Question> {
        val lim = limit.coerceIn(1, 200)
        val off = (page.coerceAtLeast(1) - 1).toLong() * lim
        val list = ArrayList<Question>(lim)
        readableDatabase.rawQuery(
            "SELECT id, subject, topic, difficulty, question, a, b, c, d, answer, explanation FROM questions " +
                "WHERE subject=? ORDER BY rowid LIMIT ? OFFSET ?", arrayOf(subject, lim.toString(), off.toString())
        ).use {
            while (it.moveToNext()) list.add(
                Question(
                    it.getString(0), it.getString(1), it.getString(2), it.getInt(3), it.getString(4),
                    listOf(it.getString(5), it.getString(6), it.getString(7), it.getString(8)),
                    it.getInt(9), it.getString(10) ?: ""
                )
            )
        }
        return list
    }

    /** Zaif mavzular (aniqlik 70% dan past) bo'yicha mashq. Zaif mavzu bo'lmasa -1. */
    fun createWeakAttempt(maxCount: Int): Long {
        val topics = weakTopics().map { it.first }
        if (topics.isEmpty()) return -1
        val per = (maxCount + topics.size - 1) / topics.size
        val ids = LinkedHashSet<String>()
        for (t in topics) ids.addAll(pickIds("topic=?", arrayOf(t), per))
        val list = ids.toMutableList().apply { shuffle() }.take(maxCount)
        return insertAttempt("weak", list, ExamConfig("weak", null, ExamMode.PRACTICE, maxCount, 0L))
    }

    private fun insertAttempt(subject: String, ids: List<String>, cfg: ExamConfig): Long {
        if (ids.isEmpty()) return -1
        val db = writableDatabase
        val rnd = java.util.Random()
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                put("subject", subject)
                put("started", System.currentTimeMillis())
                put("start_elapsed", SystemClock.elapsedRealtime())
                put("total", ids.size)
                put("day", today())
                put("mode", cfg.mode.code)
                put("duration_ms", cfg.durationMs)
            }
            val attemptId = db.insert("attempts", null, cv)
            ids.forEachIndexed { pos, qid ->
                val a = ContentValues().apply {
                    put("attempt_id", attemptId); put("pos", pos); put("question_id", qid)
                    put("opt_order", Shuffle.encode(Shuffle.permutation(rnd)))
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
        c.getInt(4), c.getInt(5), c.getInt(6), c.getInt(7), c.getInt(8),
        c.getString(9), c.getLong(10), c.getString(11)
    )

    private val attemptCols =
        "id, subject, started, finished, total, correct, wrong, skipped, time_sec, mode, duration_ms, reason"

    fun attempt(id: Long): Attempt? =
        readableDatabase.rawQuery("SELECT $attemptCols FROM attempts WHERE id=?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) readAttempt(it) else null
        }

    fun attemptMeta(id: Long): AttemptMeta? =
        readableDatabase.rawQuery(
            "SELECT mode, duration_ms, started, start_elapsed, finished, locked, cur_q, reason " +
                "FROM attempts WHERE id=?", arrayOf(id.toString())
        ).use {
            if (!it.moveToFirst()) null
            else AttemptMeta(
                id, ExamMode.from(it.getString(0)), it.getLong(1), it.getLong(2), it.getLong(3),
                it.getLong(4), it.getInt(5) == 1, it.getInt(6), it.getString(7)
            )
        }

    /** Vaqti o'tib ketgan, lekin hali yakunlanmagan imtihonlarni avtomatik yakunlaydi. */
    fun finalizeExpired() {
        val ids = ArrayList<Long>()
        readableDatabase.rawQuery("SELECT id FROM attempts WHERE finished=0 AND duration_ms>0", null)
            .use { while (it.moveToNext()) ids.add(it.getLong(0)) }
        for (id in ids) {
            val m = attemptMeta(id) ?: continue
            if (m.isOpen && m.remainingMs() <= 0L) finishAttempt(id, "timeout")
        }
    }

    /** Oxirgi tugallanmagan imtihon (muddati o'tganlari avval yakunlanadi). */
    fun unfinished(): Attempt? {
        finalizeExpired()
        return readableDatabase.rawQuery(
            "SELECT $attemptCols FROM attempts WHERE finished=0 ORDER BY id DESC LIMIT 1", null
        ).use { if (it.moveToFirst()) readAttempt(it) else null }
    }

    fun finishedAttempts(): List<Attempt> {
        finalizeExpired()
        val list = ArrayList<Attempt>()
        readableDatabase.rawQuery(
            "SELECT $attemptCols FROM attempts WHERE finished>0 ORDER BY finished DESC", null
        ).use { while (it.moveToNext()) list.add(readAttempt(it)) }
        return list
    }

    /** Faqat vaqtli imtihonlar natijalari (alohida statistika uchun). */
    fun timedAttempts(): List<Attempt> {
        finalizeExpired()
        val list = ArrayList<Attempt>()
        readableDatabase.rawQuery(
            "SELECT $attemptCols FROM attempts WHERE finished>0 AND duration_ms>0 ORDER BY finished DESC", null
        ).use { while (it.moveToNext()) list.add(readAttempt(it)) }
        return list
    }

    fun setCurrentQuestion(attemptId: Long, index: Int) {
        val cv = ContentValues().apply { put("cur_q", index) }
        writableDatabase.update("attempts", cv, "id=? AND finished=0", arrayOf(attemptId.toString()))
    }

    fun items(attemptId: Long): List<Item> {
        val list = ArrayList<Item>()
        readableDatabase.rawQuery(
            "SELECT a.pos, a.selected, a.marked, q.id, q.subject, q.topic, q.difficulty, q.question, " +
                "q.a, q.b, q.c, q.d, q.answer, q.explanation, a.opt_order " +
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
                list.add(Item(it.getInt(0), q, it.getInt(1), it.getInt(2) == 1, Shuffle.decode(it.getString(14))))
            }
        }
        return list
    }

    private val openGuard = "EXISTS(SELECT 1 FROM attempts WHERE id=? AND locked=0 AND finished=0)"

    /**
     * Javobni saqlaydi. Imtihon yakunlangan/qulflangan bo'lsa yoki vaqti tugagan bo'lsa false qaytaradi
     * (vaqti tugagan bo'lsa imtihon shu yerning o'zida yakunlanadi) — tugagandan keyin javobni o'zgartirib bo'lmaydi.
     */
    fun setAnswer(attemptId: Long, questionId: String, selected: Int): Boolean {
        val m = attemptMeta(attemptId) ?: return false
        if (!m.isOpen) return false
        if (m.timed && m.remainingMs() <= 0L) { finishAttempt(attemptId, "timeout"); return false }
        val cv = ContentValues().apply { put("selected", selected) }
        return writableDatabase.update(
            "answers", cv, "attempt_id=? AND question_id=? AND $openGuard",
            arrayOf(attemptId.toString(), questionId, attemptId.toString())
        ) > 0
    }

    fun setMarked(attemptId: Long, questionId: String, marked: Boolean): Boolean {
        val cv = ContentValues().apply { put("marked", if (marked) 1 else 0) }
        return writableDatabase.update(
            "answers", cv, "attempt_id=? AND question_id=? AND $openGuard",
            arrayOf(attemptId.toString(), questionId, attemptId.toString())
        ) > 0
    }

    /** Test ustida haqiqatda o'tgan faol vaqt (sekund). Ilova yopiq turgan vaqt hisoblanmaydi. */
    fun elapsed(attemptId: Long): Int =
        readableDatabase.rawQuery("SELECT elapsed FROM attempts WHERE id=?", arrayOf(attemptId.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun setElapsed(attemptId: Long, sec: Int) {
        val cv = ContentValues().apply { put("elapsed", sec) }
        writableDatabase.update("attempts", cv, "id=?", arrayOf(attemptId.toString()))
    }

    /** Barcha test tarixi va javoblarni o'chiradi (savollar bazasi saqlanadi). */
    fun clearProgress() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("answers", null, null)
            db.delete("attempts", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Natijani hisoblab, urinishni yakunlaydi va qulflaydi (idempotent, bitta tranzaksiyada).
     * reason: "manual" yoki "timeout". Javob berilmaganlar (selected=-1) "skipped" hisoblanadi.
     */
    fun finishAttempt(attemptId: Long, reason: String = "manual") {
        val m = attemptMeta(attemptId) ?: return
        if (m.finished > 0L) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            var correct = 0; var skipped = 0; var total = 0
            db.rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN a.selected=q.answer THEN 1 ELSE 0 END),0), " +
                    "COALESCE(SUM(CASE WHEN a.selected<0 THEN 1 ELSE 0 END),0), COUNT(*) " +
                    "FROM answers a JOIN questions q ON q.id=a.question_id WHERE a.attempt_id=?",
                arrayOf(attemptId.toString())
            ).use { if (it.moveToFirst()) { correct = it.getInt(0); skipped = it.getInt(1); total = it.getInt(2) } }
            val wrong = total - correct - skipped
            val now = System.currentTimeMillis()
            // Vaqt allaqachon tugagan bo'lsa, "manual" so'rovi ham timeout sifatida yoziladi.
            val timeout = m.timed && (reason == "timeout" || m.remainingMs(now, SystemClock.elapsedRealtime()) <= 0L)
            val finishedAt = if (timeout) m.startWall + m.durationMs else now
            val usedSec = when {
                timeout -> (m.durationMs / 1000L).toInt()
                m.timed -> (ExamTimer.usedMs(m.startWall, m.startElapsed, now, SystemClock.elapsedRealtime())
                    .coerceAtMost(m.durationMs) / 1000L).toInt()
                else -> elapsed(attemptId)
            }
            val cv = ContentValues().apply {
                put("finished", finishedAt)
                put("locked", 1)
                put("reason", if (timeout) "timeout" else "manual")
                put("correct", correct)
                put("wrong", wrong)
                put("skipped", skipped)
                put("time_sec", usedSec)
                put("day", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(finishedAt)))
            }
            db.update("attempts", cv, "id=? AND finished=0", arrayOf(attemptId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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

    /** Umumiy statistika: [testlar soni, to'g'ri javoblar, jami savollar, sekundlar, javob berilgan savollar]. */
    fun totals(): IntArray =
        readableDatabase.rawQuery(
            "SELECT COUNT(*), COALESCE(SUM(correct),0), COALESCE(SUM(total),0), COALESCE(SUM(time_sec),0), " +
                "COALESCE(SUM(correct+wrong),0) FROM attempts WHERE finished>0", null
        ).use { it.moveToFirst(); intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4)) }

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

    /** Zaif mavzular: (fan, mavzu, foiz) — aniqlik 70% dan past, eng zaifi birinchi. */
    fun weakTopicsDetailed(): List<Triple<String, String, Int>> {
        val list = ArrayList<Triple<String, String, Int>>()
        readableDatabase.rawQuery(
            "SELECT q.subject, q.topic, SUM(CASE WHEN a.selected=q.answer THEN 1 ELSE 0 END), COUNT(*) " +
                "FROM answers a JOIN questions q ON q.id=a.question_id " +
                "JOIN attempts t ON t.id=a.attempt_id WHERE t.finished>0 AND a.selected>=0 " +
                "GROUP BY q.subject, q.topic", null
        ).use {
            while (it.moveToNext()) {
                val pct = it.getInt(2) * 100 / it.getInt(3)
                if (pct < 70) list.add(Triple(it.getString(0), it.getString(1), pct))
            }
        }
        return list.sortedBy { it.third }
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
        const val MAX_EXAM_QUESTIONS = 200
        private const val SMALL_SET = 5000
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
        return slugs.indices.associate { slugs[it] to names[it] } + ("weak" to ctx.getString(R.string.weak_practice_title)) + ("all" to ctx.getString(R.string.random_all_title))
    }
}

fun formatTime(sec: Int): String = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
