package pro.tendercheck.engine

import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Итоги игровой сессии (одного прохождения уровня).
 *
 * @property total число заявок на уровне
 * @property answered сколько заявок рассмотрено
 * @property accuracyPercent точность: верный ответ — 1 балл, допустимый — 0,5, от рассмотренных
 * @property passed уровень пройден: все заявки рассмотрены и лимит ошибок не исчерпан
 */
data class SessionSummary(
    val difficulty: Difficulty,
    val score: Int,
    val total: Int,
    val answered: Int,
    val correct: Int,
    val partial: Int,
    val mistakes: Int,
    val accuracyPercent: Int,
    val totalTimeSec: Int,
    val passed: Boolean,
    val results: List<AnswerResult>,
) {
    /** Словесная оценка прохождения. */
    val grade: String
        get() = when {
            !passed -> "Нужно повторить"
            accuracyPercent >= 90 -> "Отлично"
            accuracyPercent >= 75 -> "Хорошо"
            else -> "Удовлетворительно"
        }
}

/**
 * Игровая сессия: очередь заявок выбранного уровня, накопленные результаты и условия завершения.
 *
 * Сессия завершается, когда рассмотрены все заявки либо число ошибок достигло
 * [Difficulty.maxMistakes]. Порядок заявок перемешивается при создании.
 */
class GameSession(
    val difficulty: Difficulty,
    scenarios: List<Scenario>,
    private val engine: ScoringEngine,
    random: Random = Random.Default,
) {
    /** Очередь заявок этого уровня. */
    val queue: List<Scenario> = scenarios.filter { it.level == difficulty }.shuffled(random)

    private val answers = mutableListOf<AnswerResult>()

    /** Результаты по уже рассмотренным заявкам. */
    val results: List<AnswerResult> get() = answers

    /** Номер текущей заявки (с нуля). */
    val currentIndex: Int get() = answers.size

    /** Число ошибок (неверные решения и истёкшее время). */
    val mistakes: Int get() = answers.count { it.isMistake }

    /** Накопленные очки. */
    val score: Int get() = answers.sumOf { it.totalPoints }

    /** Лимит ошибок исчерпан. */
    val failed: Boolean get() = mistakes >= difficulty.maxMistakes

    /** Сессия завершена: заявки кончились или исчерпан лимит ошибок. */
    val isFinished: Boolean get() = failed || currentIndex >= queue.size

    /** Текущая заявка или null, если сессия завершена. */
    val current: Scenario? get() = if (isFinished) null else queue[currentIndex]

    /**
     * Принимает решение по текущей заявке.
     *
     * @param decision решение или null, если время вышло
     * @throws IllegalStateException если сессия уже завершена
     */
    fun submit(decision: Decision?, justification: String, elapsedSec: Int): AnswerResult {
        val scenario = checkNotNull(current) { "Сессия уже завершена" }
        val result = engine.evaluate(scenario, decision, justification, elapsedSec)
        answers.add(result)
        return result
    }

    /** Сводка по сессии на текущий момент. */
    fun summary(): SessionSummary {
        val correct = answers.count { it.verdict == Verdict.CORRECT }
        val partial = answers.count { it.verdict == Verdict.PARTIAL }
        val accuracy = if (answers.isEmpty()) {
            0
        } else {
            ((correct * 2 + partial) * 100.0 / (answers.size * 2)).roundToInt()
        }
        return SessionSummary(
            difficulty = difficulty,
            score = score,
            total = queue.size,
            answered = answers.size,
            correct = correct,
            partial = partial,
            mistakes = mistakes,
            accuracyPercent = accuracy,
            totalTimeSec = answers.sumOf { it.elapsedSec },
            passed = isFinished && !failed,
            results = answers.toList(),
        )
    }
}
