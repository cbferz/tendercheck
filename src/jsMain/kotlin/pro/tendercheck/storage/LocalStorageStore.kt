package pro.tendercheck.storage

import kotlinx.browser.localStorage

/**
 * Хранилище на основе localStorage браузера.
 *
 * localStorage может быть недоступен (приватный режим, запрет cookies, переполнение квоты),
 * поэтому все обращения защищены, а при сбое данные временно держатся в памяти вкладки —
 * игра продолжает работать, просто без сохранения между сеансами.
 */
class LocalStorageStore : KeyValueStore {
    private val fallback = InMemoryStore()

    override fun get(key: String): String? {
        val inMemory = fallback.get(key)
        if (inMemory != null) return inMemory
        return try {
            localStorage.getItem(key)
        } catch (e: Throwable) {
            null
        }
    }

    override fun put(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
            fallback.remove(key)
        } catch (e: Throwable) {
            fallback.put(key, value)
        }
    }

    override fun remove(key: String) {
        fallback.remove(key)
        try {
            localStorage.removeItem(key)
        } catch (e: Throwable) {
            // localStorage недоступен — удалять нечего
        }
    }
}
