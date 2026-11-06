package uz.imtihonai.app

import java.security.MessageDigest
import java.util.Locale
import java.util.Random

/*
 * Android'ga bog'liq bo'lmagan sof mantiq: JVM unit testlarda tekshiriladi (src/test).
 */

/** Imtihon turlari. timeRequired=true bo'lsa vaqt majburiy. */
enum class ExamMode(val code: String, val timeRequired: Boolean) {
    PRACTICE("practice", false),
    EXAM("exam", true),
    TIMED_EXAM("timed", true),
    TOPIC_TEST("topic", false),
    RANDOM_TEST("random", false),
    FULL_EXAM("full", true);

    companion object {
        fun from(code: String?): ExamMode = values().firstOrNull { it.code == code } ?: PRACTICE
    }
}

/** Imtihon sozlamasi. subject=null — barcha fanlardan; durationMs=0 — vaqtsiz. */
data class ExamConfig(
    val subject: String?,
    val topicId: String?,
    val mode: ExamMode,
    val count: Int,
    val durationMs: Long
)

object ExamTimer {
    /**
     * Imtihonga sarflangan vaqt (ms). Ekrandagi "-1" hisoblagich emas: boshlanish vaqti va hozirgi vaqtdan hisoblanadi.
     * Ilova yopiq tursa ham vaqt o'tadi. Telefon soati orqaga surilsa, bir xil yoqilish (boot) ichida
     * elapsedRealtime ham hisobga olinadi va vaqt kamaymaydi.
     */
    fun usedMs(startWall: Long, startElapsed: Long, nowWall: Long, nowElapsed: Long): Long {
        val wall = (nowWall - startWall).coerceAtLeast(0L)
        val sameBoot = startElapsed > 0L && nowElapsed >= startElapsed
        return if (sameBoot) maxOf(wall, nowElapsed - startElapsed) else wall
    }

    fun remainingMs(durationMs: Long, startWall: Long, startElapsed: Long, nowWall: Long, nowElapsed: Long): Long =
        (durationMs - usedMs(startWall, startElapsed, nowWall, nowElapsed)).coerceAtLeast(0L)

    /** Ekranga chiqariladigan soniyalar: yuqoriga yaxlitlanadi, shuning uchun boshida 60:00, 1 s dan keyin 59:59. */
    fun displaySeconds(remainingMs: Long): Int = ((remainingMs + 999L) / 1000L).toInt()

    /** Ogohlantirish bosqichi: 10, 5, 1 (daqiqa) yoki 0 (ogohlantirish kerak emas). */
    fun warnLevel(remainingMs: Long): Int = when {
        remainingMs <= 0L -> 0
        remainingMs <= 60_000L -> 1
        remainingMs <= 300_000L -> 5
        remainingMs <= 600_000L -> 10
        else -> 0
    }
}

object QuestionRules {
    fun normalize(s: String): String = s.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    /** Xato bo'lsa sabab matni, to'g'ri bo'lsa null. */
    fun validate(
        id: String, subject: String, question: String,
        options: List<String>, answer: Int, difficulty: Int
    ): String? {
        if (id.isBlank()) return "id bo'sh"
        if (subject.isBlank()) return "fan bo'sh"
        if (question.isBlank()) return "savol matni bo'sh"
        if (question.length > 2000) return "savol juda uzun"
        if (options.size != 4) return "variantlar 4 ta emas"
        if (options.any { it.isBlank() }) return "bo'sh variant bor"
        if (options.map { normalize(it) }.toSet().size != 4) return "variantlar takrorlangan"
        if (answer !in 0..3) return "to'g'ri javob 0..3 oralig'ida emas"
        if (difficulty !in 1..5) return "qiyinlik 1..5 oralig'ida emas"
        return null
    }

    /** Dublikat aniqlash kaliti: fan + savol + variantlar (tartibdan qat'i nazar). */
    fun hash(subject: String, question: String, options: List<String>): String {
        val base = subject.trim() + "\n" + normalize(question) + "\n" +
            options.map { normalize(it) }.sorted().joinToString("\u0001")
        val d = MessageDigest.getInstance("SHA-256").digest(base.toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    fun topicId(subject: String, topic: String): String = subject.trim() + "/" + normalize(topic)
}

object Shuffle {
    /** Variantlar tartibi: ko'rsatilgan i-o'rinda asl p[i]-variant turadi. */
    fun permutation(rnd: Random): IntArray {
        val a = intArrayOf(0, 1, 2, 3)
        for (i in 3 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val t = a[i]; a[i] = a[j]; a[j] = t
        }
        return a
    }

    fun encode(p: IntArray): String = p.joinToString(",")

    /** Buzilgan/bo'sh qiymat bo'lsa asl tartib (0,1,2,3) qaytadi. */
    fun decode(s: String?): IntArray {
        val identity = intArrayOf(0, 1, 2, 3)
        if (s.isNullOrBlank()) return identity
        val p = s.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (p.size == 4 && p.toSet() == setOf(0, 1, 2, 3)) p.toIntArray() else identity
    }
}

fun percentOf(correct: Int, total: Int): Double = if (total > 0) correct * 100.0 / total else 0.0

fun percentText(p: Double): String = String.format(Locale.US, "%.1f%%", p)
