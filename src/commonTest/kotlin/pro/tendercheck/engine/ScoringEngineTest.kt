package pro.tendercheck.engine

import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Metric
import pro.tendercheck.testScenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Тесты движка оценки: вердикты, очки, бонусы и множители уровней. */
class ScoringEngineTest {
    private val engine = ScoringEngine()

    @Test
    fun correctAnswerWithoutDelayGetsBasePointsAndFullTimeBonus() {
        val result = engine.evaluate(testScenario(), Decision.APPROVE, "", 0)

        assertEquals(Verdict.CORRECT, result.verdict)
        assertEquals(100, result.basePoints)
        assertEquals(30, result.timeBonus)
        assertEquals(130, result.totalPoints)
        assertFalse(result.isMistake)
    }

    @Test
    fun timeBonusShrinksProportionallyToElapsedTime() {
        // Лимит стажёра — 90 секунд, прошла половина: бонус 15 из 30
        val half = engine.evaluate(testScenario(), Decision.APPROVE, "", 45)
        assertEquals(15, half.timeBonus)
        assertEquals(115, half.totalPoints)

        // Ответ в последнюю секунду бонуса не даёт
        val last = engine.evaluate(testScenario(), Decision.APPROVE, "", 90)
        assertEquals(0, last.timeBonus)
        assertEquals(100, last.totalPoints)
    }

    @Test
    fun acceptableDecisionGivesPartialPointsAndIsNotMistake() {
        val scenario = testScenario(correct = Decision.APPROVE, acceptable = listOf(Decision.REVISE))
        val result = engine.evaluate(scenario, Decision.REVISE, "", 0)

        assertEquals(Verdict.PARTIAL, result.verdict)
        assertEquals(40, result.totalPoints)
        assertEquals(0, result.timeBonus)
        assertFalse(result.isMistake)
    }

    @Test
    fun wrongDecisionGivesNoPointsAndCountsAsMistake() {
        val result = engine.evaluate(testScenario(), Decision.REJECT, "", 10)

        assertEquals(Verdict.WRONG, result.verdict)
        assertEquals(0, result.totalPoints)
        assertTrue(result.isMistake)
    }

    @Test
    fun missingDecisionMeansTimeoutAndCountsAsMistake() {
        val result = engine.evaluate(testScenario(), null, "Не успел", 90)

        assertEquals(Verdict.TIMEOUT, result.verdict)
        assertEquals(0, result.totalPoints)
        assertTrue(result.isMistake)
    }

    @Test
    fun levelMultiplierIsAppliedToTotal() {
        // Время вышло ровно в лимит, поэтому бонуса за скорость нет и суммы «круглые»
        val analyst = engine.evaluate(testScenario(level = Difficulty.ANALYST), Decision.APPROVE, "", 60)
        assertEquals(125, analyst.totalPoints)

        val head = engine.evaluate(testScenario(level = Difficulty.HEAD), Decision.APPROVE, "", 45)
        assertEquals(150, head.totalPoints)
    }

    @Test
    fun elapsedTimeIsClampedToLevelLimit() {
        val result = engine.evaluate(testScenario(), Decision.APPROVE, "", 500)

        assertEquals(90, result.elapsedSec)
        assertEquals(0, result.timeBonus)
    }

    @Test
    fun justificationMentioningRelevantMetricEarnsBonus() {
        // Единственный проблемный показатель — ликвидность
        val scenario = testScenario(currentRatio = 0.8, correct = Decision.REVISE)
        val text = "Ликвидность ниже единицы, поэтому заявку нужно доработать"
        val result = engine.evaluate(scenario, Decision.REVISE, text, 90)

        assertEquals(20, result.justificationBonus)
        assertEquals(listOf(Metric.CURRENT_RATIO), result.mentionedMetrics)
        assertEquals(120, result.totalPoints)
    }

    @Test
    fun justificationAboutIrrelevantMetricOrTooShortEarnsNothing() {
        val scenario = testScenario(currentRatio = 0.8, correct = Decision.REVISE)

        // Рейтинг в этой заявке не проблема, а о ликвидности ни слова
        val irrelevant = engine.evaluate(scenario, Decision.REVISE, "Рейтинг компании хороший, значит можно доработать", 90)
        assertEquals(0, irrelevant.justificationBonus)

        // Слишком короткий текст
        val tooShort = engine.evaluate(scenario, Decision.REVISE, "Ликвидность", 90)
        assertEquals(0, tooShort.justificationBonus)
    }

    @Test
    fun justificationDoesNotHelpWrongDecision() {
        val scenario = testScenario(currentRatio = 0.8, correct = Decision.REVISE)
        val text = "Ликвидность ниже единицы, поэтому заявку нужно отклонить"
        val result = engine.evaluate(scenario, Decision.REJECT, text, 10)

        assertEquals(0, result.justificationBonus)
        assertEquals(0, result.totalPoints)
    }

    @Test
    fun cleanApplicationAcceptsAnyMetricInJustification() {
        // Проблемных показателей нет, значимыми считаются все
        val text = "Хороший рейтинг и достаточная ликвидность, одобряем"
        val result = engine.evaluate(testScenario(), Decision.APPROVE, text, 90)

        assertEquals(20, result.justificationBonus)
        assertTrue(Metric.CREDIT_HISTORY in result.mentionedMetrics)
        assertTrue(Metric.CURRENT_RATIO in result.mentionedMetrics)
    }
}
