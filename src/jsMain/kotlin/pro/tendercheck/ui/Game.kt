package pro.tendercheck.ui

import kotlinx.html.*
import kotlinx.html.js.onClickFunction
import pro.tendercheck.engine.AnswerResult
import pro.tendercheck.engine.GameSession
import pro.tendercheck.engine.RiskAnalyzer
import pro.tendercheck.engine.ScoringConfig
import pro.tendercheck.engine.Verdict
import pro.tendercheck.model.Decision
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario
import pro.tendercheck.model.Severity
import pro.tendercheck.util.formatDuration
import pro.tendercheck.util.formatMoneyMln

/** Параметры начисления очков — нужны, чтобы подсказки в разборе совпадали с реальными правилами. */
private val scoring = ScoringConfig()

// --- Общие элементы ------------------------------------------------------------------------

private fun FlowContent.hudItem(label: String, value: String) {
    div(classes = "hud-item") {
        span(classes = "hud-label") { +label }
        span(classes = "hud-value") { +value }
    }
}

/** Панель состояния партии: уровень, номер заявки, ошибки, очки, таймер. */
private fun FlowContent.hud(app: App, session: GameSession) {
    val number = if (app.phase == Phase.QUESTION) session.currentIndex + 1 else session.currentIndex
    val inQuestion = app.phase == Phase.QUESTION
    div(classes = "hud") {
        hudItem("Уровень", "${session.difficulty.icon} ${session.difficulty.title}")
        hudItem("Заявка", "$number / ${session.queue.size}")
        hudItem("Ошибки", "${session.mistakes} / ${session.difficulty.maxMistakes}")
        hudItem("Очки", session.score.toString())
        if (inQuestion) {
            div(classes = "hud-item timer") {
                id = "timer-box"
                span(classes = "hud-label") { +"Время" }
                span(classes = "hud-value") {
                    id = "timer-text"
                    +"⏱ ${formatDuration(session.difficulty.timePerApplicationSec)}"
                }
            }
        }
        button(classes = "link-btn", type = ButtonType.button) {
            onClickFunction = { app.exitGame() }
            +"✕ Выйти"
        }
    }
    if (inQuestion) {
        div(classes = "timer-bar") {
            // Ширина (100 % по умолчанию в CSS) меняется из таймера через CSSOM — это совместимо со строгим CSP
            div(classes = "timer-fill") {
                id = "timer-fill"
            }
        }
    }
}

/** Карточка компании: название, отрасль, сумма, срок, цель и краткое описание. */
private fun FlowContent.applicationCard(scenario: Scenario) {
    article(classes = "card app-card") {
        div(classes = "app-head") {
            div(classes = "app-title") {
                span(classes = "kind-badge") { +scenario.kind.title }
                h2 { +scenario.company }
                p(classes = "muted") { +scenario.industry }
            }
            div(classes = "app-amount") {
                div(classes = "amount") { +formatMoneyMln(scenario.amountMln) }
                div(classes = "muted") { +"срок: ${scenario.termMonths} мес." }
            }
        }
        p {
            strong { +"Цель: " }
            +scenario.purpose
        }
        p(classes = "about") { +scenario.about }
    }
}

// --- Экран заявки --------------------------------------------------------------------------

/** Экран заявки: карточка, показатели без оценок, выбор решения и поле обоснования. */
internal fun FlowContent.questionScreen(app: App) {
    val session = app.session ?: return
    val scenario = session.current ?: return

    hud(app, session)
    applicationCard(scenario)

    // Во время вопроса показываем только значения: оценку «норма / флаг» игрок даёт сам
    div(classes = "metrics") {
        for (assessment in RiskAnalyzer.analyze(scenario)) {
            div(classes = "metric") {
                div(classes = "metric-title") { +assessment.metric.title }
                div(classes = "metric-value") { +assessment.value }
            }
        }
    }

    h3 { +"Ваше решение" }
    div(classes = "decisions") {
        Decision.entries.forEachIndexed { index, decision ->
            button(classes = "decision-btn", type = ButtonType.button) {
                attributes["data-decision"] = decision.name
                attributes["aria-pressed"] = "false"
                onClickFunction = { app.select(decision) }
                span(classes = "decision-icon") { +decision.icon }
                span(classes = "decision-title") { +decision.title }
                span(classes = "decision-hint") { +decision.hint }
                span(classes = "decision-key") { +"${index + 1}" }
            }
        }
    }

    label(classes = "field-label") {
        attributes["for"] = "justification"
        +"Обоснование (по желанию, до +${scoring.justificationBonus} очков)"
    }
    textArea(rows = "3", classes = "justification") {
        id = "justification"
        attributes["maxlength"] = "500"
        attributes["placeholder"] = "Например: ликвидность ниже 1,0, а залога нет — риск невозврата высокий…"
    }

    div(classes = "actions") {
        button(classes = "btn btn-primary", type = ButtonType.button) {
            id = "confirm-btn"
            disabled = true
            onClickFunction = { app.confirm() }
            +"Принять решение"
        }
        span(classes = "muted hint") { +"Клавиши: 1 — одобрить, 2 — доработка, 3 — отклонить; Ctrl+Enter — подтвердить" }
    }
}

// --- Экран разбора -------------------------------------------------------------------------

private fun verdictClass(verdict: Verdict): String = when (verdict) {
    Verdict.CORRECT -> "verdict-correct"
    Verdict.PARTIAL -> "verdict-partial"
    Verdict.WRONG, Verdict.TIMEOUT -> "verdict-wrong"
}

private fun verdictIcon(verdict: Verdict): String = when (verdict) {
    Verdict.CORRECT -> "✅"
    Verdict.PARTIAL -> "🟡"
    Verdict.WRONG -> "❌"
    Verdict.TIMEOUT -> "⏰"
}

private fun verdictTitle(verdict: Verdict): String = when (verdict) {
    Verdict.CORRECT -> "Верное решение!"
    Verdict.PARTIAL -> "Допустимо, но не лучший вариант"
    Verdict.WRONG -> "Решение не совпало с эталоном"
    Verdict.TIMEOUT -> "Время вышло"
}

private fun severityClass(severity: Severity): String = when (severity) {
    Severity.OK -> "sev-ok"
    Severity.WARNING -> "sev-warn"
    Severity.RED -> "sev-red"
}

private fun decisionText(decision: Decision?): String =
    if (decision == null) "⏰ Не принято (время вышло)" else "${decision.icon} ${decision.title}"

/** Расшифровка начисленных очков одной строкой. */
private fun pointsLine(result: AnswerResult): String =
    "Базовые: ${result.basePoints} · скорость: +${result.timeBonus} · обоснование: +${result.justificationBonus} · " +
        "множитель уровня ${formatMultiplier(result.multiplier)} · время ответа ${formatDuration(result.elapsedSec)}"

/** Комментарий к обоснованию игрока. */
private fun justificationNote(result: AnswerResult): String {
    val significant = result.assessments.filter { it.severity != Severity.OK }.map { it.metric.title }
    val keyList = if (significant.isEmpty()) "любой из показателей" else significant.joinToString(", ")
    return when {
        result.justification.isBlank() ->
            "Обоснование не заполнено. Если назвать значимые показатели ($keyList), при верном решении " +
                "можно получить до +${scoring.justificationBonus} очков."
        result.justificationBonus > 0 ->
            "Обоснование засчитано: вы упомянули — " + result.mentionedMetrics.joinToString(", ") { it.title } + "."
        result.verdict == Verdict.WRONG || result.verdict == Verdict.TIMEOUT ->
            "Обоснование не учитывается, пока решение неверное."
        else ->
            "Обоснование не засчитано: нужно не меньше ${scoring.minJustificationLength} символов " +
                "и упоминание значимых показателей ($keyList)."
    }
}

/** Экран разбора: вердикт, очки, красные флаги, эталонное решение и объяснение. */
internal fun FlowContent.feedbackScreen(app: App) {
    val session = app.session ?: return
    val result = app.lastResult ?: return
    val scenario = result.scenario

    hud(app, session)

    div(classes = "verdict " + verdictClass(result.verdict)) {
        div(classes = "verdict-main") {
            span(classes = "verdict-icon") { +verdictIcon(result.verdict) }
            div {
                h2 { +verdictTitle(result.verdict) }
                p(classes = "muted") { +pointsLine(result) }
            }
        }
        div(classes = "verdict-points") { +"+${result.totalPoints} очк." }
    }

    article(classes = "card compare") {
        div(classes = "compare-row") {
            span(classes = "muted") { +"Ваше решение" }
            strong { +decisionText(result.decision) }
        }
        div(classes = "compare-row") {
            span(classes = "muted") { +"Верное решение" }
            strong { +decisionText(scenario.correct) }
        }
        if (scenario.acceptable.isNotEmpty()) {
            div(classes = "compare-row") {
                span(classes = "muted") { +"Допустимо (частичный балл)" }
                span { +scenario.acceptable.joinToString(", ") { it.title } }
            }
        }
    }

    val redFlags = result.redFlags
    h3 { +(if (redFlags.isEmpty()) "Красных флагов нет" else "Красных флагов: ${redFlags.size}") }
    div(classes = "metrics") {
        for (assessment in result.assessments) {
            div(classes = "metric " + severityClass(assessment.severity)) {
                div(classes = "metric-title") { +"${assessment.severity.icon} ${assessment.metric.title}" }
                div(classes = "metric-value") { +assessment.value }
                div(classes = "metric-comment") { +assessment.comment }
            }
        }
    }

    article(classes = "card explain") {
        h3 { +"Разбор" }
        p { +scenario.explanation }
        p(classes = "muted") { +justificationNote(result) }
    }

    div(classes = "actions") {
        button(classes = "btn btn-primary", type = ButtonType.button) {
            onClickFunction = { app.next() }
            +(if (session.isFinished) "Показать итоги" else "Следующая заявка →")
        }
        span(classes = "muted hint") { +"Enter — дальше" }
    }
}

// --- Экран итогов --------------------------------------------------------------------------

private fun FlowContent.resultStat(label: String, value: String) {
    div(classes = "stat") {
        span(classes = "stat-value") { +value }
        span(classes = "stat-label") { +label }
    }
}

/** Карточка разбора одной ошибки (или допустимого, но неидеального решения) на экране итогов. */
private fun FlowContent.mistakeCard(result: AnswerResult) {
    val scenario = result.scenario
    article(classes = "card mistake") {
        div(classes = "mistake-head") {
            span(classes = "verdict-chip " + verdictClass(result.verdict)) {
                +"${verdictIcon(result.verdict)} ${result.verdict.title}"
            }
            h3 { +scenario.company }
        }
        p {
            strong { +"Вы: " }
            +decisionText(result.decision)
            +" → "
            strong { +"верно: " }
            +decisionText(scenario.correct)
        }
        val flags = result.redFlags
        if (flags.isEmpty()) {
            p(classes = "muted") { +"Красных флагов в заявке не было." }
        } else {
            ul(classes = "flag-list") {
                for (flag in flags) {
                    li { +"🔴 ${flag.metric.title}: ${flag.value} — ${flag.comment}" }
                }
            }
        }
        p { +scenario.explanation }
    }
}

/** Экран итогов партии: очки, точность, время, таблица ответов и разбор ошибок. */
internal fun FlowContent.resultScreen(app: App) {
    val summary = app.summary ?: return
    val recorded = app.outcome
    val unlockedNext = recorded?.unlockedNext
    val passed = summary.passed

    section(classes = if (passed) "hero result-hero passed" else "hero result-hero failed") {
        div(classes = "result-icon") { +(if (passed) "🏆" else "📉") }
        h1 { +(if (passed) "Уровень пройден" else "Лимит ошибок исчерпан") }
        p(classes = "lead") { +"${summary.difficulty.icon} ${summary.difficulty.title} · оценка: ${summary.grade}" }
        div(classes = "badges") {
            if (recorded?.isNewBest == true) {
                span(classes = "badge badge-gold") { +"🏅 Новый личный рекорд" }
            }
            if (unlockedNext != null) {
                span(classes = "badge") { +"🔓 Открыт уровень: ${unlockedNext.title}" }
            }
        }
        div(classes = "stats") {
            resultStat("Очки", summary.score.toString())
            resultStat("Точность", "${summary.accuracyPercent} %")
            resultStat("Время", formatDuration(summary.totalTimeSec))
            resultStat("Ошибки", "${summary.mistakes} из ${summary.difficulty.maxMistakes}")
        }
    }

    section(classes = "card") {
        h3 { +"Ход смены" }
        div(classes = "table-wrap") {
            table(classes = "results-table") {
                thead {
                    tr {
                        th { +"№" }
                        th { +"Компания" }
                        th { +"Ваше решение" }
                        th { +"Эталон" }
                        th { +"Итог" }
                        th { +"Очки" }
                    }
                }
                tbody {
                    summary.results.forEachIndexed { index, result ->
                        tr {
                            td { +"${index + 1}" }
                            td { +result.scenario.company }
                            td { +decisionText(result.decision) }
                            td { +decisionText(result.scenario.correct) }
                            td(classes = verdictClass(result.verdict)) {
                                +"${verdictIcon(result.verdict)} ${result.verdict.title}"
                            }
                            td { +result.totalPoints.toString() }
                        }
                    }
                }
            }
        }
    }

    h2 { +"Разбор ошибок" }
    val review = summary.results.filter { it.verdict != Verdict.CORRECT }
    if (review.isEmpty()) {
        p(classes = "lead") { +"Все решения верные — безупречная работа!" }
    } else {
        for (result in review) mistakeCard(result)
    }

    div(classes = "actions") {
        button(classes = "btn btn-primary", type = ButtonType.button) {
            onClickFunction = { app.startLevel(summary.difficulty) }
            +"🔁 Играть снова"
        }
        val nextLevel = Difficulty.entries.getOrNull(summary.difficulty.ordinal + 1)
        if (passed && nextLevel != null && app.progress.isUnlocked(nextLevel)) {
            button(classes = "btn", type = ButtonType.button) {
                onClickFunction = { app.startLevel(nextLevel) }
                +"Следующий уровень: ${nextLevel.title} →"
            }
        }
        button(classes = "btn", type = ButtonType.button) {
            onClickFunction = { app.toMenu() }
            +"🏠 К выбору уровня"
        }
    }
}
