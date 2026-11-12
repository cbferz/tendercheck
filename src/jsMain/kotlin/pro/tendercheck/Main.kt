package pro.tendercheck

import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import pro.tendercheck.data.ScenarioRepository
import pro.tendercheck.storage.LocalStorageStore
import pro.tendercheck.storage.ProgressRepository
import pro.tendercheck.ui.App

/** Точка входа: находит корневой элемент страницы, загружает контент и запускает приложение. */
fun main() {
    val root = document.getElementById("app") as? HTMLElement ?: return

    // Контент встроен в бандл при сборке; ошибка здесь означает повреждённый data/scenarios.json
    val scenarios = try {
        ScenarioRepository.all
    } catch (e: Throwable) {
        root.textContent = "Не удалось загрузить заявки: ${e.message}"
        return
    }

    val store = LocalStorageStore()
    App(root, scenarios, ProgressRepository(store), store).start()
}
