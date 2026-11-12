package pro.tendercheck.engine

import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario
import pro.tendercheck.testScenario
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Тесты игровой сессии: очередь заявок, лимит ошибок, итоги. */
class GameSessionTest {

    /** 8 заявок стажёра и 3 заявки аналитика; у всех эталон — «одобрить». */
    private fun pool(): List<Scenario> =
        (1..8).map { testScenario(id = "I-$it", level = Difficulty.INTERN) } +
            (1..3).map { testScenario(id = "A-$it", level = Difficulty.ANALYST) }

    private fun newSession(level: Difficulty = Difficulty.INTERN) =
        GameSession(level, pool(), ScoringEngine(), Random(42))

    @Test
    fun queueContainsOnlyScenariosOfSelectedLevel() {
        val session = newSession()

        assertEquals(8, session.queue.size)
        assertTrue(session.queue.all { it.level == Difficulty.INTERN })
    }

    @Test
    fun sessionEndsWhenMistakeLimitIsReached() {
        val session = newSession() // у стажёра лимит — 4 ошибки

        repeat(3) { session.submit(Decision.REJECT, "", 10) }
        assertFalse(session.isFinished)

        session.submit(Decision.REJECT, "", 10)
        assertTrue(session.failed)
        assertTrue(session.isFinished)
        assertNull(session.current)
        assertFalse(session.summary().passed)
    }

    @Test
    fun playingAllApplicationsWithinMistakeLimitPassesLevel() {
        val session = newSession()

        while (!session.isFinished) {
            session.submit(session.current!!.correct, "", 90)
        }
        val summary = session.summary()

        assertTrue(summary.passed)
        assertEquals(8, summary.answered)
        assertEquals(8, summary.correct)
        assertEquals(100, summary.accuracyPercent)
        assertEquals(800, summary.score)
        assertEquals(8 * 90, summary.totalTimeSec)
        assertEquals("Отлично", summary.grade)
    }

    @Test
    fun timeoutCountsAsMistake() {
        val session = newSession()
        session.submit(null, "", 90)

        assertEquals(1, session.mistakes)
        assertEquals(0, session.score)
    }

    @Test
    fun accuracyCountsPartialAnswersAsHalf() {
        val pool = listOf(
            testScenario(id = "P-1", correct = Decision.APPROVE),
            testScenario(id = "P-2", correct = Decision.APPROVE, acceptable = listOf(Decision.REVISE)),
        )
        val session = GameSession(Difficulty.INTERN, pool, ScoringEngine(), Random(1))

        // Один ответ верный, другой допустимый: (2 + 1) / (2 * 2) = 75 %
        while (!session.isFinished) {
            val scenario = session.current!!
            val decision = if (scenario.acceptable.isEmpty()) scenario.correct else Decision.REVISE
            session.submit(decision, "", 90)
        }

        assertEquals(75, session.summary().accuracyPercent)
        assertEquals(0, session.summary().mistakes)
    }

    @Test
    fun submittingAfterFinishThrows() {
        val session = newSession()
        while (!session.isFinished) session.submit(session.current!!.correct, "", 90)

        assertFailsWith<IllegalStateException> {
            session.submit(Decision.APPROVE, "", 1)
        }
    }
}
