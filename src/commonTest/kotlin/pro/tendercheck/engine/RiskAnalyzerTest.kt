package pro.tendercheck.engine

import pro.tendercheck.model.ApplicationKind
import pro.tendercheck.model.Collateral
import pro.tendercheck.model.CreditRating
import pro.tendercheck.model.Metric
import pro.tendercheck.model.Scenario
import pro.tendercheck.model.Severity
import pro.tendercheck.testScenario
import kotlin.test.Test
import kotlin.test.assertEquals

/** Тесты пороговых значений и красных флагов. */
class RiskAnalyzerTest {

    /** Оценка одного показателя заявки. */
    private fun severityOf(scenario: Scenario, metric: Metric): Severity =
        RiskAnalyzer.analyze(scenario).first { it.metric == metric }.severity

    @Test
    fun cleanApplicationHasNoFlags() {
        val flags = RiskAnalyzer.analyze(testScenario())
        assertEquals(6, flags.size)
        assertEquals(emptyList(), flags.filter { it.severity != Severity.OK })
    }

    @Test
    fun badCompanyGetsAllRedFlags() {
        val scenario = testScenario(
            amountMln = 60.0, currentRatio = 0.6, debtToEbitda = 7.2, salesMarginPct = -2.0,
            creditRating = CreditRating.D, collateral = Collateral.NONE, tenderContracts = 0,
        )
        val red = RiskAnalyzer.analyze(scenario).filter { it.severity == Severity.RED }.map { it.metric }

        assertEquals(
            listOf(Metric.CURRENT_RATIO, Metric.DEBT_EBITDA, Metric.SALES_MARGIN, Metric.CREDIT_HISTORY, Metric.COLLATERAL),
            red,
        )
        // Для обычного кредита отсутствие тендерного опыта — лишь предупреждение
        assertEquals(Severity.WARNING, severityOf(scenario, Metric.TENDER_EXPERIENCE))
    }

    @Test
    fun currentRatioBoundaries() {
        assertEquals(Severity.OK, severityOf(testScenario(currentRatio = 1.5), Metric.CURRENT_RATIO))
        assertEquals(Severity.WARNING, severityOf(testScenario(currentRatio = 1.49), Metric.CURRENT_RATIO))
        assertEquals(Severity.WARNING, severityOf(testScenario(currentRatio = 1.0), Metric.CURRENT_RATIO))
        assertEquals(Severity.RED, severityOf(testScenario(currentRatio = 0.99), Metric.CURRENT_RATIO))
    }

    @Test
    fun debtToEbitdaBoundariesAndMissingEbitda() {
        assertEquals(Severity.OK, severityOf(testScenario(debtToEbitda = 3.0), Metric.DEBT_EBITDA))
        assertEquals(Severity.WARNING, severityOf(testScenario(debtToEbitda = 3.01), Metric.DEBT_EBITDA))
        assertEquals(Severity.WARNING, severityOf(testScenario(debtToEbitda = 4.5), Metric.DEBT_EBITDA))
        assertEquals(Severity.RED, severityOf(testScenario(debtToEbitda = 4.51), Metric.DEBT_EBITDA))
        // Отрицательная EBITDA: показатель не рассчитывается, это красный флаг
        assertEquals(Severity.RED, severityOf(testScenario(debtToEbitda = null), Metric.DEBT_EBITDA))
    }

    @Test
    fun salesMarginBoundaries() {
        assertEquals(Severity.OK, severityOf(testScenario(salesMarginPct = 5.0), Metric.SALES_MARGIN))
        assertEquals(Severity.WARNING, severityOf(testScenario(salesMarginPct = 4.99), Metric.SALES_MARGIN))
        assertEquals(Severity.WARNING, severityOf(testScenario(salesMarginPct = 0.0), Metric.SALES_MARGIN))
        assertEquals(Severity.RED, severityOf(testScenario(salesMarginPct = -0.1), Metric.SALES_MARGIN))
    }

    @Test
    fun missingCollateralIsRedOnlyForLargeAmounts() {
        val small = testScenario(collateral = Collateral.NONE, amountMln = 19.9)
        val large = testScenario(collateral = Collateral.NONE, amountMln = 20.0)

        assertEquals(Severity.WARNING, severityOf(small, Metric.COLLATERAL))
        assertEquals(Severity.RED, severityOf(large, Metric.COLLATERAL))
    }

    @Test
    fun tenderExperienceDependsOnApplicationKind() {
        val tenderNoExperience = testScenario(kind = ApplicationKind.TENDER, tenderContracts = 0)
        val creditNoExperience = testScenario(kind = ApplicationKind.CREDIT, tenderContracts = 0)

        assertEquals(Severity.RED, severityOf(tenderNoExperience, Metric.TENDER_EXPERIENCE))
        assertEquals(Severity.WARNING, severityOf(creditNoExperience, Metric.TENDER_EXPERIENCE))
        assertEquals(Severity.WARNING, severityOf(testScenario(tenderContracts = 2), Metric.TENDER_EXPERIENCE))
        assertEquals(Severity.OK, severityOf(testScenario(tenderContracts = 3), Metric.TENDER_EXPERIENCE))
    }

    @Test
    fun valuesAreFormattedForDisplay() {
        val flags = RiskAnalyzer.analyze(testScenario(currentRatio = 2.1, debtToEbitda = 3.4, salesMarginPct = -2.0))
        val byMetric = flags.associateBy { it.metric }

        assertEquals("2,10", byMetric.getValue(Metric.CURRENT_RATIO).value)
        assertEquals("3,4×", byMetric.getValue(Metric.DEBT_EBITDA).value)
        assertEquals("−2,0 %", byMetric.getValue(Metric.SALES_MARGIN).value)
        assertEquals("5 исполненных контрактов", byMetric.getValue(Metric.TENDER_EXPERIENCE).value)
    }
}
