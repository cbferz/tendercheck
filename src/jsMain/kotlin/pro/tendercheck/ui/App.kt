package pro.tendercheck.ui

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.div
import kotlinx.html.dom.append
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.KeyboardEvent
import pro.tendercheck.engine.AnswerResult
import pro.tendercheck.engine.GameSession
import pro.tendercheck.engine.ScoringEngine
import pro.tendercheck.engine.SessionSummary
import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario
import pro.tendercheck.storage.KeyValueStore
import pro.tendercheck.storage.Progress
import pro.tendercheck.storage.ProgressRepository
import pro.tendercheck.storage.RecordOutcome
import pro.tendercheck.util.formatDuration
import kotlin.js.Date
import kotlin.math.ceil

/** Вкладки верхнего меню. */
enum class Tab { PLAY, GLOSSARY, ABOUT }

/** Фазы вкладки «Играть». */
enum class Phase {
    /** Выбор уровня. */
    MENU,

    /** Идёт таймер, игрок выбирает решение. */
    QUESTION,

    /** Показан разбор последней заявки. */
    FEEDBACK,

    /** Итоги сессии. */
    RESULT,
}

/**
 * Состояние приложения и обработчики действий игрока.
 * Отрисовка экранов вынесена в функции-расширения (файлы Shell.kt и Game.kt) и читает состояние отсюда.
 */
class App(
    private val root: HTMLElement,
    private val scenarios: List<Scenario>,
    private val progressRepository: ProgressRepository,
    private val settings: KeyValueStore,
) {
    private val engine = ScoringEngine()

    var tab: Tab = Tab.PLAY
        private set
    var phase: Phase = Phase.MENU
        private set
    var progress: Progress = progressRepository.load()
        private set
    var session: GameSession? = null
        private set
    var lastResult: AnswerResult? = null
        private set
    var summary: SessionSummary? = null
        private set
    var outcome: RecordOutcome? = null
        private set
    var theme: String = settings.get(THEME_KEY) ?: "dark"
        private set
    var glossaryQuery: String = ""
        private set

    private var selected: Decision? = null
    private var roundStartMs = 0.0
    private var timerId: Int? = null

    /** Сколько заявок в контенте на этом уровне. */
    fun scenarioCount(level: Difficulty): Int = scenarios.count { it.level == level }

    /** Запускает приложение: применяет тему, подписывается на клавиши и рисует первый экран. */
    fun start() {
        applyTheme()
        document.addEventListener("keydown", { event -> onKey(event as KeyboardEvent) })
        render()
    }

    // --- Навигация ---------------------------------------------------------------------

    /** Переключает вкладку. Во время партии уход с вкладки «Играть» требует подтверждения. */
    fun openTab(target: Tab) {
        val playing = phase == Phase.QUESTION || phase == Phase.FEEDBACK
        if (target == Tab.PLAY) {
            if (playing) return
            tab = Tab.PLAY
            if (phase == Phase.RESULT) phase = Phase.MENU
        } else {
            if (playing && !window.confirm("Прервать игру? Результаты этой партии не сохранятся.")) return
            if (playing) abortSession()
            tab = target
        }
        render()
    }

    /** Возврат к выбору уровня (с экрана итогов). */
    fun toMenu() {
        abortSession()
        render()
    }

    /** Выход из партии с подтверждением. */
    fun exitGame() {
        if (!window.confirm("Прервать игру? Результаты этой партии не сохранятся.")) return
        abortSession()
        render()
    }

    private fun abortSession() {
        stopTimer()
        session = null
        lastResult = null
        selected = null
        phase = Phase.MENU
    }

    // --- Игровой цикл ------------------------------------------------------------------

    /** Начинает партию на выбранном уровне, если он открыт. */
    fun startLevel(level: Difficulty) {
        if (!progress.isUnlocked(level)) return
        session = GameSession(level, scenarios, engine)
        summary = null
        outcome = null
        beginRound()
    }

    private fun beginRound() {
        selected = null
        lastResult = null
        roundStartMs = nowMs()
        phase = Phase.QUESTION
        render()
    }

    /** Выбор решения (кнопка или клавиши 1–3). Страница не перерисовывается, чтобы не потерять текст обоснования. */
    fun select(decision: Decision) {
        if (phase != Phase.QUESTION) return
        selected = decision
        val buttons = document.querySelectorAll(".decision-btn")
        for (i in 0 until buttons.length) {
            val element = buttons.item(i) as? HTMLElement ?: continue
            val active = element.getAttribute("data-decision") == decision.name
            if (active) element.classList.add("selected") else element.classList.remove("selected")
            element.setAttribute("aria-pressed", if (active) "true" else "false")
        }
        (document.getElementById("confirm-btn") as? HTMLButtonElement)?.disabled = false
    }

    /** Подтверждает выбранное решение. */
    fun confirm() {
        val decision = selected ?: return
        submit(decision)
    }

    private fun submit(decision: Decision?) {
        val current = session ?: return
        if (phase != Phase.QUESTION) return
        stopTimer()
        val justification = (document.getElementById("justification") as? HTMLTextAreaElement)?.value ?: ""
        val elapsedSec = ((nowMs() - roundStartMs) / 1000.0).toInt()
        lastResult = current.submit(decision, justification, elapsedSec)
        phase = Phase.FEEDBACK
        render()
    }

    /** Переход после разбора: следующая заявка или итоги. */
    fun next() {
        val current = session ?: return
        if (phase != Phase.FEEDBACK) return
        if (current.isFinished) finishSession() else beginRound()
    }

    private fun finishSession() {
        val current = session ?: return
        val result = current.summary()
        val recorded = progressRepository.recordSession(result)
        summary = result
        outcome = recorded
        progress = recorded.progress
        phase = Phase.RESULT
        render()
    }

    // --- Таймер ------------------------------------------------------------------------

    private fun startTimer() {
        val current = session ?: return
        updateTimerView(current.difficulty.timePerApplicationSec * 1000.0, current.difficulty.timePerApplicationSec * 1000.0)
        timerId = window.setInterval({ tick() }, 250)
    }

    private fun stopTimer() {
        timerId?.let { window.clearInterval(it) }
        timerId = null
    }

    private fun tick() {
        val current = session ?: return
        val limitMs = current.difficulty.timePerApplicationSec * 1000.0
        val leftMs = limitMs - (nowMs() - roundStartMs)
        if (leftMs <= 0.0) {
            submit(null) // время вышло — заявка засчитывается как ошибка
        } else {
            updateTimerView(leftMs, limitMs)
        }
    }

    /** Обновляет только цифры и полосу таймера, не трогая остальную страницу. */
    private fun updateTimerView(leftMs: Double, limitMs: Double) {
        val seconds = ceil(leftMs / 1000.0).toInt()
        document.getElementById("timer-text")?.textContent = "⏱ " + formatDuration(seconds)
        val percent = (leftMs / limitMs * 100.0).coerceIn(0.0, 100.0)
        (document.getElementById("timer-fill") as? HTMLElement)?.style?.width = "$percent%"
        if (seconds <= 10) document.getElementById("timer-box")?.classList?.add("danger")
    }

    private fun nowMs(): Double = Date().getTime()

    // --- Клавиатура --------------------------------------------------------------------

    private fun onKey(event: KeyboardEvent) {
        if (tab != Tab.PLAY) return
        val tag = (event.target as? HTMLElement)?.tagName?.lowercase() ?: ""
        val typing = tag == "textarea" || tag == "input"
        when (phase) {
            Phase.QUESTION -> {
                if (typing) {
                    if (event.ctrlKey && event.key == "Enter") confirm()
                    return
                }
                when (event.key) {
                    "1" -> select(Decision.APPROVE)
                    "2" -> select(Decision.REVISE)
                    "3" -> select(Decision.REJECT)
                    "Enter" -> if (tag != "button") confirm()
                }
            }
            Phase.FEEDBACK -> if (event.key == "Enter" && !typing && tag != "button") next()
            else -> {}
        }
    }

    // --- Прочее ------------------------------------------------------------------------

    /** Поиск по глоссарию: перерисовывается только список статей. */
    fun onGlossaryQuery() {
        val input = document.getElementById("glossary-search") as? HTMLInputElement ?: return
        glossaryQuery = input.value
        val list = document.getElementById("glossary-list") as? HTMLElement ?: return
        list.innerHTML = ""
        val query = glossaryQuery
        list.append { div(classes = "terms") { glossaryItems(query) } }
    }

    /** Переключает тёмную и светлую тему и запоминает выбор. */
    fun toggleTheme() {
        theme = if (theme == "dark") "light" else "dark"
        settings.put(THEME_KEY, theme)
        applyTheme()
        document.getElementById("theme-btn")?.textContent = themeIcon()
    }

    /** Значок кнопки темы: показывает, на какую тему переключит нажатие. */
    fun themeIcon(): String = if (theme == "dark") "☀️" else "🌙"

    private fun applyTheme() {
        document.documentElement?.setAttribute("data-theme", theme)
    }

    /** Сброс прогресса после подтверждения. */
    fun resetProgress() {
        if (!window.confirm("Сбросить открытые уровни и лучшие результаты?")) return
        progressRepository.reset()
        progress = progressRepository.load()
        render()
    }

    private fun render() {
        stopTimer()
        root.innerHTML = ""
        root.append { div(classes = "shell") { shell(this@App) } }
        window.scrollTo(0.0, 0.0)
        if (tab == Tab.PLAY && phase == Phase.QUESTION) startTimer()
    }

    private companion object {
        const val THEME_KEY = "tendercheck.theme"
    }
}
