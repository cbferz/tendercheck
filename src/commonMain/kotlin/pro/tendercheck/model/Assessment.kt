package pro.tendercheck.model

/**
 * Показатель заявки, по которому аналитик принимает решение.
 *
 * @property keywords подстроки (в нижнем регистре), по которым в обосновании игрока
 * распознаётся упоминание показателя
 */
enum class Metric(val title: String, val keywords: List<String>) {
    CURRENT_RATIO("Текущая ликвидность", listOf("ликвидн")),
    DEBT_EBITDA("Долг/EBITDA", listOf("ebitda", "долг", "закредит", "долговая")),
    SALES_MARGIN("Рентабельность продаж", listOf("рентабельн", "маржа", "маржин")),
    CREDIT_HISTORY("Кредитная история", listOf("кредитная история", "рейтинг", "просрочк")),
    COLLATERAL("Обеспечение", listOf("залог", "поручител", "обеспечен")),
    TENDER_EXPERIENCE("Опыт в тендерах", listOf("тендер", "опыт", "контракт")),
}

/** Оценка значения показателя: норма, предупреждение или красный флаг. */
enum class Severity(val icon: String, val label: String) {
    OK("🟢", "Норма"),
    WARNING("🟡", "Внимание"),
    RED("🔴", "Красный флаг"),
}

/**
 * Результат оценки одного показателя.
 *
 * @property value значение для показа игроку (уже отформатированное)
 * @property comment пояснение, почему показатель получил такую оценку
 */
data class MetricAssessment(
    val metric: Metric,
    val value: String,
    val severity: Severity,
    val comment: String,
)
