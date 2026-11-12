package pro.tendercheck.engine

import pro.tendercheck.model.ApplicationKind
import pro.tendercheck.model.Collateral
import pro.tendercheck.model.CreditRating
import pro.tendercheck.model.Metric
import pro.tendercheck.model.MetricAssessment
import pro.tendercheck.model.Scenario
import pro.tendercheck.model.Severity
import pro.tendercheck.util.formatDecimal
import pro.tendercheck.util.pluralRu

/** Пороговые значения, по которым показатели получают оценку. Учебные ориентиры, а не методика конкретного банка. */
object RiskThresholds {
    /** Ликвидность ниже — красный флаг. */
    const val CURRENT_RATIO_RED = 1.0

    /** Ликвидность от этого значения — норма. */
    const val CURRENT_RATIO_OK = 1.5

    /** Долг/EBITDA выше — красный флаг. */
    const val DEBT_EBITDA_RED = 4.5

    /** Долг/EBITDA до этого значения включительно — норма. */
    const val DEBT_EBITDA_OK = 3.0

    /** Рентабельность продаж от этого значения (%) — норма; ниже нуля — убыточность. */
    const val SALES_MARGIN_OK = 5.0

    /** Сумма (млн руб.), начиная с которой отсутствие обеспечения — красный флаг. */
    const val UNSECURED_RED_FROM_MLN = 20.0

    /** Число исполненных контрактов, от которого опыт считается достаточным. */
    const val TENDER_EXPERIENCE_OK = 3
}

/**
 * Разбирает заявку по шести показателям и присваивает каждому оценку
 * ([Severity.OK], [Severity.WARNING] или [Severity.RED]) с пояснением.
 * Объект не хранит состояния и потому легко тестируется.
 */
object RiskAnalyzer {

    /** Оценивает все показатели заявки в фиксированном порядке (как на экране заявки). */
    fun analyze(s: Scenario): List<MetricAssessment> = listOf(
        currentRatio(s),
        debtToEbitda(s),
        salesMargin(s),
        creditHistory(s),
        collateral(s),
        tenderExperience(s),
    )

    private fun currentRatio(s: Scenario): MetricAssessment {
        val value = formatDecimal(s.currentRatio, 2)
        return when {
            s.currentRatio < RiskThresholds.CURRENT_RATIO_RED -> MetricAssessment(
                Metric.CURRENT_RATIO, value, Severity.RED,
                "Оборотных активов не хватает, чтобы покрыть краткосрочные обязательства (меньше 1,0).",
            )
            s.currentRatio < RiskThresholds.CURRENT_RATIO_OK -> MetricAssessment(
                Metric.CURRENT_RATIO, value, Severity.WARNING,
                "Ниже комфортного уровня 1,5: запас прочности по ликвидности небольшой.",
            )
            else -> MetricAssessment(
                Metric.CURRENT_RATIO, value, Severity.OK,
                "Ликвидность в норме (1,5 и выше).",
            )
        }
    }

    private fun debtToEbitda(s: Scenario): MetricAssessment {
        val ratio = s.debtToEbitda
        if (ratio == null) {
            return MetricAssessment(
                Metric.DEBT_EBITDA, "н/м", Severity.RED,
                "EBITDA отрицательна или равна нулю: операционный бизнес не генерирует денег на обслуживание долга.",
            )
        }
        val value = formatDecimal(ratio, 1) + "×"
        return when {
            ratio > RiskThresholds.DEBT_EBITDA_RED -> MetricAssessment(
                Metric.DEBT_EBITDA, value, Severity.RED,
                "Долговая нагрузка выше 4,5 EBITDA: на погашение долга уйдёт больше 4,5 лет операционной прибыли.",
            )
            ratio > RiskThresholds.DEBT_EBITDA_OK -> MetricAssessment(
                Metric.DEBT_EBITDA, value, Severity.WARNING,
                "Нагрузка выше комфортных 3,0: рост долга оставляет мало запаса.",
            )
            else -> MetricAssessment(
                Metric.DEBT_EBITDA, value, Severity.OK,
                "Долговая нагрузка умеренная (до 3,0).",
            )
        }
    }

    private fun salesMargin(s: Scenario): MetricAssessment {
        val value = formatDecimal(s.salesMarginPct, 1) + " %"
        return when {
            s.salesMarginPct < 0.0 -> MetricAssessment(
                Metric.SALES_MARGIN, value, Severity.RED,
                "Компания убыточна на уровне продаж: каждый рубль выручки приносит убыток.",
            )
            s.salesMarginPct < RiskThresholds.SALES_MARGIN_OK -> MetricAssessment(
                Metric.SALES_MARGIN, value, Severity.WARNING,
                "Рентабельность ниже 5 %: любое удорожание затрат способно «съесть» прибыль " +
                    "(для оптовой торговли и ритейла такой уровень бывает типичным).",
            )
            else -> MetricAssessment(
                Metric.SALES_MARGIN, value, Severity.OK,
                "Рентабельность достаточна, чтобы переживать колебания затрат.",
            )
        }
    }

    private fun creditHistory(s: Scenario): MetricAssessment {
        val value = "Рейтинг ${s.creditRating.name}"
        return when (s.creditRating) {
            CreditRating.D -> MetricAssessment(
                Metric.CREDIT_HISTORY, value, Severity.RED,
                "Рейтинг D: в истории есть существенные просрочки или реструктуризации.",
            )
            CreditRating.C -> MetricAssessment(
                Metric.CREDIT_HISTORY, value, Severity.WARNING,
                "Рейтинг C: встречались просрочки, платёжная дисциплина требует внимания.",
            )
            CreditRating.A, CreditRating.B -> MetricAssessment(
                Metric.CREDIT_HISTORY, value, Severity.OK,
                "Платёжная дисциплина хорошая.",
            )
        }
    }

    private fun collateral(s: Scenario): MetricAssessment {
        val value = s.collateral.title
        return when (s.collateral) {
            Collateral.NONE ->
                if (s.amountMln >= RiskThresholds.UNSECURED_RED_FROM_MLN) {
                    MetricAssessment(
                        Metric.COLLATERAL, value, Severity.RED,
                        "Крупная сумма (от 20 млн ₽) без обеспечения: при дефолте банку нечем компенсировать потери.",
                    )
                } else {
                    MetricAssessment(
                        Metric.COLLATERAL, value, Severity.WARNING,
                        "Обеспечения нет; при небольшой сумме это допустимо, но повышает риск потерь.",
                    )
                }
            Collateral.PLEDGE -> MetricAssessment(
                Metric.COLLATERAL, value, Severity.OK,
                "Есть залог — вторичный источник возврата. Оценивайте его ликвидность и дисконт.",
            )
            Collateral.GUARANTEE -> MetricAssessment(
                Metric.COLLATERAL, value, Severity.OK,
                "Есть поручительство — оно надёжно ровно настолько, насколько финансово устойчив поручитель.",
            )
        }
    }

    private fun tenderExperience(s: Scenario): MetricAssessment {
        val n = s.tenderContracts
        val value = "$n " + pluralRu(n, "исполненный контракт", "исполненных контракта", "исполненных контрактов")
        return when {
            n == 0 && s.kind == ApplicationKind.TENDER -> MetricAssessment(
                Metric.TENDER_EXPERIENCE, value, Severity.RED,
                "Для тендерной заявки нет подтверждённого опыта: неясно, справится ли компания с контрактом.",
            )
            n < RiskThresholds.TENDER_EXPERIENCE_OK -> MetricAssessment(
                Metric.TENDER_EXPERIENCE, value, Severity.WARNING,
                "Опыта пока мало: для обычного кредита не критично, для гарантии — повод запросить детали.",
            )
            else -> MetricAssessment(
                Metric.TENDER_EXPERIENCE, value, Severity.OK,
                "Есть подтверждённый опыт исполнения контрактов.",
            )
        }
    }
}
