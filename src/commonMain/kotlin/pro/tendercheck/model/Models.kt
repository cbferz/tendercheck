package pro.tendercheck.model

import kotlinx.serialization.Serializable

/** Решение аналитика по заявке. */
@Serializable
enum class Decision(val title: String, val icon: String, val hint: String) {
    APPROVE("Одобрить", "✅", "Заявка соответствует требованиям банка"),
    REVISE("На доработку", "📝", "Нужны дополнительные документы, обеспечение или иные условия"),
    REJECT("Отклонить", "⛔", "Риск для банка неприемлемо высок"),
}

/** Кредитный рейтинг заёмщика по внутренней шкале банка: A — лучший, D — худший. */
@Serializable
enum class CreditRating { A, B, C, D }

/** Вид обеспечения по заявке. */
@Serializable
enum class Collateral(val title: String) {
    PLEDGE("Залог"),
    GUARANTEE("Поручительство"),
    NONE("Без обеспечения"),
}

/** Вид заявки: обычный кредит или банковская гарантия для участия в тендере. */
@Serializable
enum class ApplicationKind(val title: String) {
    CREDIT("Кредитная заявка"),
    TENDER("Тендерная заявка (банковская гарантия)"),
}

/**
 * Уровень сложности.
 *
 * @property timePerApplicationSec время на одну заявку, секунд
 * @property maxMistakes число ошибок, при достижении которого игра завершается досрочно
 * @property scoreMultiplier множитель очков за заявку
 */
@Serializable
enum class Difficulty(
    val title: String,
    val icon: String,
    val description: String,
    val timePerApplicationSec: Int,
    val maxMistakes: Int,
    val scoreMultiplier: Double,
) {
    INTERN("Стажёр", "🎓", "Очевидные случаи: учимся читать показатели и находить красные флаги.", 90, 4, 1.0),
    ANALYST("Аналитик", "📊", "Пограничные значения и смешанные сигналы, где нужен баланс рисков.", 60, 3, 1.25),
    HEAD("Начальник отдела", "🏛️", "Ловушки и крупные суммы: сильные стороны маскируют слабые.", 45, 2, 1.5),
}

/**
 * Заявка компании. Красные флаги по ней считает [pro.tendercheck.engine.RiskAnalyzer],
 * а эталонное решение и разбор заданы автором сценария.
 *
 * @property amountMln сумма запроса, млн руб.
 * @property termMonths срок, месяцев
 * @property currentRatio коэффициент текущей ликвидности
 * @property debtToEbitda отношение долга к EBITDA; null — EBITDA отрицательна или равна нулю
 * @property salesMarginPct рентабельность продаж, %
 * @property tenderContracts число исполненных контрактов по тендерам
 * @property correct эталонное решение
 * @property acceptable «допустимые» решения: за них начисляется частичный балл и они не считаются ошибкой
 * @property explanation разбор: почему верно именно эталонное решение
 */
@Serializable
data class Scenario(
    val id: String,
    val level: Difficulty,
    val kind: ApplicationKind,
    val company: String,
    val industry: String,
    val about: String,
    val amountMln: Double,
    val termMonths: Int,
    val purpose: String,
    val currentRatio: Double,
    val debtToEbitda: Double? = null,
    val salesMarginPct: Double,
    val creditRating: CreditRating,
    val collateral: Collateral,
    val tenderContracts: Int,
    val correct: Decision,
    val acceptable: List<Decision> = emptyList(),
    val explanation: String,
)

/** Корневой объект файла data/scenarios.json. */
@Serializable
data class ScenarioFile(
    val version: Int,
    val scenarios: List<Scenario>,
)
