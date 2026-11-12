package pro.tendercheck

import pro.tendercheck.model.ApplicationKind
import pro.tendercheck.model.Collateral
import pro.tendercheck.model.CreditRating
import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario

/**
 * Собирает тестовую заявку. По умолчанию это «чистая» заявка без красных флагов и предупреждений,
 * в тесте меняются только нужные поля.
 */
internal fun testScenario(
    id: String = "T-1",
    level: Difficulty = Difficulty.INTERN,
    kind: ApplicationKind = ApplicationKind.CREDIT,
    amountMln: Double = 30.0,
    currentRatio: Double = 2.0,
    debtToEbitda: Double? = 2.0,
    salesMarginPct: Double = 8.0,
    creditRating: CreditRating = CreditRating.A,
    collateral: Collateral = Collateral.PLEDGE,
    tenderContracts: Int = 5,
    correct: Decision = Decision.APPROVE,
    acceptable: List<Decision> = emptyList(),
): Scenario = Scenario(
    id = id,
    level = level,
    kind = kind,
    company = "ООО «Тест»",
    industry = "Тестовая отрасль",
    about = "Тестовая компания",
    amountMln = amountMln,
    termMonths = 12,
    purpose = "Тест",
    currentRatio = currentRatio,
    debtToEbitda = debtToEbitda,
    salesMarginPct = salesMarginPct,
    creditRating = creditRating,
    collateral = collateral,
    tenderContracts = tenderContracts,
    correct = correct,
    acceptable = acceptable,
    explanation = "Тестовый разбор",
)
