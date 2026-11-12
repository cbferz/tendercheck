package pro.tendercheck.ui

import kotlinx.html.*
import kotlinx.html.js.onClickFunction
import kotlinx.html.js.onInputFunction
import pro.tendercheck.data.Glossary
import pro.tendercheck.engine.ScoringConfig
import pro.tendercheck.model.Difficulty
import pro.tendercheck.util.formatDecimal
import pro.tendercheck.util.formatDuration
import pro.tendercheck.util.pluralRu

/** Множитель очков в виде «×1,25». */
internal fun formatMultiplier(multiplier: Double): String =
    "×" + formatDecimal(multiplier, 2).trimEnd('0').trimEnd(',')

/** Каркас страницы: шапка с вкладками, содержимое активной вкладки и подвал. */
internal fun FlowContent.shell(app: App) {
    header(classes = "topbar") {
        div(classes = "brand") {
            span(classes = "brand-mark") { +"TC" }
            div(classes = "brand-text") {
                span(classes = "brand-name") { +"TenderCheck" }
                span(classes = "brand-sub") { +"тренажёр банковского аналитика" }
            }
        }
        nav(classes = "tabs") {
            tabButton(app, Tab.PLAY, "🎮 Играть")
            tabButton(app, Tab.GLOSSARY, "📚 Глоссарий")
            tabButton(app, Tab.ABOUT, "ℹ️ О проекте")
        }
        button(classes = "icon-btn", type = ButtonType.button) {
            id = "theme-btn"
            title = "Переключить тему"
            onClickFunction = { app.toggleTheme() }
            +app.themeIcon()
        }
    }
    div(classes = "content") {
        when (app.tab) {
            Tab.PLAY -> playScreen(app)
            Tab.GLOSSARY -> glossaryScreen(app)
            Tab.ABOUT -> aboutScreen()
        }
    }
    footer(classes = "footer") {
        +"© TenderCheck · tendercheck.pro · учебный тренажёр для специальности «Банковское дело». "
        +"Все компании и цифры вымышлены."
    }
}

private fun FlowContent.tabButton(app: App, target: Tab, label: String) {
    button(classes = if (app.tab == target) "tab active" else "tab", type = ButtonType.button) {
        onClickFunction = { app.openTab(target) }
        +label
    }
}

/** Вкладка «Играть»: выбор уровня, вопрос, разбор или итоги — в зависимости от фазы. */
private fun FlowContent.playScreen(app: App) {
    when (app.phase) {
        Phase.MENU -> homeScreen(app)
        Phase.QUESTION -> questionScreen(app)
        Phase.FEEDBACK -> feedbackScreen(app)
        Phase.RESULT -> resultScreen(app)
    }
}

private fun FlowContent.stat(label: String, value: String) {
    div(classes = "stat") {
        span(classes = "stat-value") { +value }
        span(classes = "stat-label") { +label }
    }
}

private fun FlowContent.homeScreen(app: App) {
    section(classes = "hero") {
        h1 { +"Тренажёр банковского аналитика" }
        p(classes = "lead") {
            +("Вы — аналитик банка. Оценивайте кредитные и тендерные заявки компаний, находите красные флаги " +
                "в финансовых показателях и принимайте решение, пока не вышло время.")
        }
        div(classes = "stats") {
            stat("Партий сыграно", app.progress.gamesPlayed.toString())
            stat("Заявок рассмотрено", app.progress.applicationsReviewed.toString())
            stat("Уровней открыто", "${app.progress.unlocked.size} из ${Difficulty.entries.size}")
        }
    }

    h2 { +"Выберите уровень" }
    div(classes = "level-grid") {
        for (level in Difficulty.entries) levelCard(app, level)
    }

    section(classes = "card howto") {
        h3 { +"Как играть" }
        ol {
            li { +"Изучите карточку компании и шесть финансовых показателей." }
            li { +"Выберите решение: одобрить, отклонить или отправить на доработку." }
            li { +"По желанию кратко обоснуйте решение — назовите показатели, которые повлияли на выбор." }
            li { +"Получите разбор: какие показатели были красными флагами и почему верно именно такое решение." }
        }
    }

    div(classes = "reset-zone") {
        button(classes = "link-btn", type = ButtonType.button) {
            onClickFunction = { app.resetProgress() }
            +"Сбросить прогресс"
        }
    }
}

private fun FlowContent.levelCard(app: App, level: Difficulty) {
    val unlocked = app.progress.isUnlocked(level)
    val best = app.progress.best[level]
    article(classes = if (unlocked) "card level-card" else "card level-card locked") {
        div(classes = "level-head") {
            span(classes = "level-icon") { +level.icon }
            h3 { +level.title }
            if (!unlocked) span(classes = "badge") { +"🔒 закрыт" }
        }
        p(classes = "muted") { +level.description }
        ul(classes = "level-facts") {
            val count = app.scenarioCount(level)
            li { +"📄 $count ${pluralRu(count, "заявка", "заявки", "заявок")}" }
            li { +"⏱ ${level.timePerApplicationSec} сек на заявку" }
            li { +"⚠️ лимит ошибок: ${level.maxMistakes}" }
            li { +"✖️ очки ${formatMultiplier(level.scoreMultiplier)}" }
        }
        if (best != null) {
            div(classes = "best") {
                +"🏆 Лучший результат: ${best.score} очк. · точность ${best.accuracyPercent} % · ${formatDuration(best.timeSec)}"
            }
        } else {
            div(classes = "best muted") { +"Результата пока нет" }
        }
        button(classes = "btn btn-primary", type = ButtonType.button) {
            disabled = !unlocked
            onClickFunction = { app.startLevel(level) }
            +(if (unlocked) "Начать смену" else "Пройдите предыдущий уровень")
        }
    }
}

/** Вкладка «Глоссарий»: поиск и статьи. */
private fun FlowContent.glossaryScreen(app: App) {
    h1 { +"Глоссарий" }
    p(classes = "lead") {
        +"${Glossary.terms.size} терминов, которые встречаются в заявках и разборах. Используйте поиск, чтобы быстро найти нужный."
    }
    input(type = InputType.search, classes = "search") {
        id = "glossary-search"
        value = app.glossaryQuery
        attributes["placeholder"] = "Поиск: ликвидность, залог, гарантия…"
        attributes["aria-label"] = "Поиск по глоссарию"
        onInputFunction = { app.onGlossaryQuery() }
    }
    div {
        id = "glossary-list"
        div(classes = "terms") { glossaryItems(app.glossaryQuery) }
    }
}

/** Список статей глоссария, подходящих под запрос. Пустой запрос показывает все статьи. */
internal fun FlowContent.glossaryItems(query: String) {
    val needle = query.trim().lowercase()
    val found = Glossary.terms.filter { term ->
        needle.isEmpty() ||
            term.term.lowercase().contains(needle) ||
            term.definition.lowercase().contains(needle) ||
            term.whyItMatters.lowercase().contains(needle) ||
            term.category.lowercase().contains(needle)
    }
    p(classes = "muted found") { +"Найдено статей: ${found.size} из ${Glossary.terms.size}" }
    if (found.isEmpty()) {
        p { +"Ничего не найдено. Попробуйте другое слово." }
    }
    for (term in found) {
        article(classes = "card term") {
            span(classes = "chip") { +term.category }
            h3 { +term.term }
            p { +term.definition }
            div(classes = "why") {
                strong { +"Для аналитика: " }
                +term.whyItMatters
            }
        }
    }
}

/** Вкладка «О проекте». */
private fun FlowContent.aboutScreen() {
    val scoring = ScoringConfig()
    h1 { +"О проекте" }
    section(classes = "card") {
        p {
            +"TenderCheck — образовательный тренажёр для специальности «Банковское дело». Он помогает студентам "
            +"колледжей и вузов отработать навык, с которого начинается работа кредитного и тендерного аналитика: "
            +"читать финансовые показатели компании, оценивать риски и обосновывать решение."
        }
        p {
            +"Игра рассчитана на студентов СПО (специальность 38.02.07 «Банковское дело») и вузовских "
            +"направлений, связанных с банковским делом и корпоративным кредитованием."
        }
    }

    section(classes = "card") {
        h3 { +"Как начисляются очки" }
        ul {
            li { +"✅ Верное решение — ${scoring.correctPoints} очков и до ${scoring.maxTimeBonus} очков за скорость." }
            li { +"🟡 Допустимое решение — ${scoring.partialPoints} очков, ошибкой не считается." }
            li { +"❌ Неверное решение или истёкшее время — 0 очков и +1 к счётчику ошибок." }
            li {
                +("✍️ Обоснование — до ${scoring.justificationBonus} очков, если в нём не меньше " +
                    "${scoring.minJustificationLength} символов и названы значимые для заявки показатели.")
            }
            li { +"✖️ Итог за заявку умножается на множитель уровня." }
        }
    }

    section(classes = "card") {
        h3 { +"Уровни сложности" }
        ul {
            for (level in Difficulty.entries) {
                li {
                    strong { +"${level.icon} ${level.title}: " }
                    +("${level.timePerApplicationSec} сек на заявку, лимит ошибок — ${level.maxMistakes}, " +
                        "очки ${formatMultiplier(level.scoreMultiplier)}.")
                }
            }
        }
        p(classes = "muted") {
            +"Следующий уровень открывается после успешного прохождения предыдущего: все заявки рассмотрены, лимит ошибок не исчерпан."
        }
    }

    section(classes = "card") {
        h3 { +"Управление" }
        ul {
            li { +"Клавиши 1, 2, 3 — выбрать «Одобрить», «На доработку», «Отклонить»." }
            li { +"Ctrl+Enter в поле обоснования или Enter вне поля — подтвердить решение." }
            li { +"Enter на экране разбора — перейти к следующей заявке." }
        }
    }

    section(classes = "card") {
        h3 { +"Важно" }
        p {
            +"Все компании, названия и цифры вымышлены, совпадения случайны. Пороговые значения показателей — "
            +"учебные ориентиры, а не методика какого-либо банка. Тренажёр не является финансовой или "
            +"юридической консультацией."
        }
        p(classes = "muted") {
            +"Игра работает полностью в браузере, без сервера и внешних запросов. Прогресс и лучшие результаты "
            +"хранятся только на вашем устройстве (localStorage)."
        }
    }
}
