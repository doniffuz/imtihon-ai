package uz.imtihonai.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ExamCoreTest {
    private val min = 60_000L

    @Test fun case1_countdownStartsAtFullDuration() {
        val d = 60 * min
        assertEquals(60 * 60, ExamTimer.displaySeconds(ExamTimer.remainingMs(d, 1000, 5, 1000, 5)))
        assertEquals(59 * 60 + 59, ExamTimer.displaySeconds(ExamTimer.remainingMs(d, 1000, 5, 2000, 1005)))
    }

    @Test fun case2_closedApp_remainingIsReal() {
        // 10 daqiqa ishladi, 5 daqiqa yopiq turdi, qaytdi: jami 15 daqiqa o'tgan -> qolgan 45 daqiqa
        val d = 60 * min
        val rem = ExamTimer.remainingMs(d, 0, 100_000, 15 * min, 100_000 + 15 * min)
        assertEquals(45 * min, rem)
    }

    @Test fun case4_expiredIsZero() {
        assertEquals(0L, ExamTimer.remainingMs(60 * min, 0, 1000, 61 * min + 1000, 1000 + 61 * min))
    }

    @Test fun clockRolledBack_doesNotAddTime() {
        val d = 60 * min
        // soat 30 daqiqa orqaga surildi, lekin elapsedRealtime 20 daqiqa o'tganini ko'rsatadi
        val rem = ExamTimer.remainingMs(d, 5_000, 5_000, -25 * min, 5_000 + 20 * min)
        assertEquals(40 * min, rem)
    }

    @Test fun warnLevels() {
        assertEquals(0, ExamTimer.warnLevel(11 * min))
        assertEquals(10, ExamTimer.warnLevel(10 * min))
        assertEquals(5, ExamTimer.warnLevel(5 * min))
        assertEquals(1, ExamTimer.warnLevel(min))
        assertEquals(0, ExamTimer.warnLevel(0))
    }

    @Test fun validator() {
        val ok = listOf("1", "2", "3", "4")
        assertNull(QuestionRules.validate("q1", "math", "2+2?", ok, 3, 1))
        assertNotNull(QuestionRules.validate("q1", "math", "2+2?", listOf("1", "1", "3", "4"), 0, 1))
        assertNotNull(QuestionRules.validate("q1", "math", "2+2?", ok, 4, 1))
        assertNotNull(QuestionRules.validate("q1", "math", " ", ok, 0, 1))
        assertNotNull(QuestionRules.validate("q1", "math", "x", ok, 0, 9))
    }

    @Test fun hashIgnoresCaseSpacesAndOptionOrder() {
        val a = QuestionRules.hash("math", "2 + 2 ?", listOf("1", "2", "3", "4"))
        val b = QuestionRules.hash("math", "  2   +  2 ?", listOf("4", "3", "2", "1"))
        assertEquals(a, b)
        assertNotEquals(a, QuestionRules.hash("physics", "2 + 2 ?", listOf("1", "2", "3", "4")))
    }

    @Test fun shuffleIsPermutationAndRoundTrips() {
        val rnd = Random(1)
        repeat(200) {
            val p = Shuffle.permutation(rnd)
            assertEquals(setOf(0, 1, 2, 3), p.toSet())
            assertArrayEquals(p, Shuffle.decode(Shuffle.encode(p)))
        }
        assertArrayEquals(intArrayOf(0, 1, 2, 3), Shuffle.decode("garbage"))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), Shuffle.decode("0,0,1,2"))
    }

    @Test fun percent() {
        assertEquals("83.3%", percentText(percentOf(35, 42)))
        assertEquals("0.0%", percentText(percentOf(0, 0)))
    }
}
