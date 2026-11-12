package pro.tendercheck.data

import pro.tendercheck.engine.RiskAnalyzer
import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario
import pro.tendercheck.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Проверки контента: объём, уникальность и согласованность эталонов с красными флагами. */
class ContentTest {
    private val all = ScenarioRepository.all

    private fun redCount(s: Scenario) = RiskAnalyzer.analyze(s).count { it.severity == Severity.RED }
    private fun warningCount(s: Scenario) = RiskAnalyzer.analyze(s).count { it.severity == Severity.WARNING }

    @Test
    fun hasEightApplicationsPerLevel() {
        assertTrue(all.size >= 24)
        for (level in Difficulty.entries) {
            assertEquals(8, ScenarioRepository.forLevel(level).size, "Уровень ${level.title}")
        }
    }

    @Test
    fun identifiersAreUniqueAndFieldsAreFilled() {
        assertEquals(all.size, all.map { it.id }.toSet().size)
        for (s in all) {
            assertTrue(s.company.isNotBlank() && s.industry.isNotBlank() && s.purpose.isNotBlank(), s.id)
            assertTrue(s.explanation.length > 50, "Разбор слишком короткий: ${s.id}")
            assertTrue(s.amountMln > 0 && s.termMonths > 0, s.id)
            assertFalse(s.correct in s.acceptable, "Эталон не должен входить в допустимые: ${s.id}")
        }
    }

    @Test
    fun contentIsDiverse() {
        assertTrue(all.map { it.industry }.toSet().size >= 10)
        for (decision in Decision.entries) {
            assertTrue(all.count { it.correct == decision } >= 6, "Мало заявок с эталоном ${decision.title}")
        }
        assertTrue(all.any { it.debtToEbitda == null })
    }

    @Test
    fun referenceDecisionsAreConsistentWithRedFlags() {
        for (s in all) {
            val red = redCount(s)
            val warn = warningCount(s)
            when (s.correct) {
                Decision.APPROVE -> assertTrue(red == 0 && warn <= 2, "${s.id}: одобрение при red=$red, warn=$warn")
                Decision.REJECT -> assertTrue(red >= 2, "${s.id}: отказ при red=$red")
                Decision.REVISE -> assertTrue(red <= 1 && red + warn >= 1, "${s.id}: доработка при red=$red, warn=$warn")
            }
        }
    }

    @Test
    fun glossaryHasAtLeastFifteenCompleteArticles() {
        val terms = Glossary.terms
        assertTrue(terms.size >= 15)
        assertEquals(terms.size, terms.map { it.term }.toSet().size)
        assertTrue(terms.all { it.definition.isNotBlank() && it.whyItMatters.isNotBlank() && it.category.isNotBlank() })
    }
}
