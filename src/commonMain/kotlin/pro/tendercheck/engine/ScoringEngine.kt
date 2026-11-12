package pro.tendercheck.engine

import pro.tendercheck.model.Decision
import pro.tendercheck.model.Metric
import pro.tendercheck.model.MetricAssessment
import pro.tendercheck.model.Scenario
import pro.tendercheck.model.Severity
import kotlin.math.roundToInt

/** Итог проверки одного ответа. */
enum class Verdict(val title: String) {
    /** Совпало с эталоном. */
    CORRECT("Верно"),

    /** Не эталон, но решение из списка допустимых: частичный балл, ошибкой не считается. */
    PARTIAL("Допустимо"),

    /** Решение не совпало с эталоном. */
    WRONG("Ошибка"),

    /** Время вышло, решение не принято. */
    TIMEOUT("Время вышло"),
}

/**
 * Параметры начисления очков.
 *
 * @property correctPoints очки за решение, совпавшее с эталоном
 * @property partialPoints очки за допустимое (но не эталонное) решение
 * @property maxTimeBonus максимальный бонус за скорость (только при верном решении)
 * @property justificationBonus бонус за содержательное обоснование
 * @property minJustificationLength минимальная длина обоснования (символов), чтобы оно учитывалось
 */
data class ScoringConfig(
    val correctPoints: Int = 100,
    val partialPoints: Int = 40,
    val maxTimeBonus: Int = 30,
    val justificationBonus: Int = 20,
    val minJustificationLength: Int = 25,
)

/**
 * Разбор ответа на одну заявку.
 *
 * @property totalPoints итоговые очки с учётом множителя уровня
 * @property mentionedMetrics показатели, которые игрок упомянул в обосновании
 */
data class AnswerResult(
    val scenario: Scenario,
    val decision: Decision?,
    val verdict: Verdict,
    val basePoints: Int,
    val timeBonus: Int,
    val justificationBonus: Int,
    val multiplier: Double,
    val totalPoints: Int,
    val elapsedSec: Int,
    val assessments: List<MetricAssessment>,
    val mentionedMetrics: List<Metric>,
    val justification: String,
) {
    /** Показатели с красным флагом. */
    val redFlags: List<MetricAssessment> get() = assessments.filter { it.severity == Severity.RED }

    /** Считается ли ответ ошибкой (идёт в лимит ошибок). */
    val isMistake: Boolean get() = verdict == Verdict.WRONG || verdict == Verdict.TIMEOUT
}

/**
 * Движок оценки решений: сверяет ответ игрока с эталоном сценария и начисляет очки.
 *
 * Правила:
 *  - совпало с эталоном — [ScoringConfig.correctPoints] плюс бонус за скорость
 *    (пропорционален оставшемуся времени);
 *  - допустимое решение — [ScoringConfig.partialPoints], бонуса за скорость нет;
 *  - иное решение или истёкшее время — 0 очков, ответ считается ошибкой;
 *  - содержательное обоснование (достаточная длина и упоминание значимых показателей)
 *    даёт [ScoringConfig.justificationBonus], если решение верное или допустимое;
 *  - сумма умножается на множитель уровня сложности.
 *
 * Класс не зависит от UI и времени: затраченные секунды передаются параметром.
 */
class ScoringEngine(private val config: ScoringConfig = ScoringConfig()) {

    /**
     * Оценивает ответ игрока.
     *
     * @param decision выбранное решение или null, если время вышло
     * @param justification текст обоснования (может быть пустым)
     * @param elapsedSec затрачено секунд (значение ограничивается лимитом уровня)
     */
    fun evaluate(
        scenario: Scenario,
        decision: Decision?,
        justification: String,
        elapsedSec: Int,
    ): AnswerResult {
        val assessments = RiskAnalyzer.analyze(scenario)
        val limit = scenario.level.timePerApplicationSec
        val elapsed = elapsedSec.coerceIn(0, limit)

        val verdict = when {
            decision == null -> Verdict.TIMEOUT
            decision == scenario.correct -> Verdict.CORRECT
            scenario.acceptable.contains(decision) -> Verdict.PARTIAL
            else -> Verdict.WRONG
        }

        val base = when (verdict) {
            Verdict.CORRECT -> config.correctPoints
            Verdict.PARTIAL -> config.partialPoints
            Verdict.WRONG, Verdict.TIMEOUT -> 0
        }

        val timeBonus = if (verdict == Verdict.CORRECT) {
            (config.maxTimeBonus * (limit - elapsed).toDouble() / limit).roundToInt()
        } else {
            0
        }

        val mentioned = findMentionedMetrics(justification, assessments)
        val earnsJustification = (verdict == Verdict.CORRECT || verdict == Verdict.PARTIAL) &&
            justification.trim().length >= config.minJustificationLength &&
            mentioned.isNotEmpty()
        val justificationBonus = if (earnsJustification) config.justificationBonus else 0

        val multiplier = scenario.level.scoreMultiplier
        val total = ((base + timeBonus + justificationBonus) * multiplier).roundToInt()

        return AnswerResult(
            scenario = scenario,
            decision = decision,
            verdict = verdict,
            basePoints = base,
            timeBonus = timeBonus,
            justificationBonus = justificationBonus,
            multiplier = multiplier,
            totalPoints = total,
            elapsedSec = elapsed,
            assessments = assessments,
            mentionedMetrics = mentioned,
            justification = justification,
        )
    }

    /**
     * Находит в тексте обоснования упоминания значимых показателей.
     * Значимые — те, что получили предупреждение или красный флаг; если таких нет
     * (чистая заявка), значимыми считаются все показатели.
     */
    fun findMentionedMetrics(justification: String, assessments: List<MetricAssessment>): List<Metric> {
        val text = justification.lowercase()
        val relevant = assessments
            .filter { it.severity != Severity.OK }
            .map { it.metric }
            .ifEmpty { Metric.entries }
        return relevant.filter { metric -> metric.keywords.any { keyword -> text.contains(keyword) } }
    }
}
