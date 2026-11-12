package pro.tendercheck.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pro.tendercheck.engine.SessionSummary
import pro.tendercheck.model.Difficulty

/** Минимальное хранилище «ключ — строка». В браузере это localStorage, в тестах — память. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Хранилище в памяти: используется в тестах и как запасной вариант, если localStorage недоступен. */
class InMemoryStore : KeyValueStore {
    private val data = mutableMapOf<String, String>()

    override fun get(key: String): String? = data[key]
    override fun put(key: String, value: String) {
        data[key] = value
    }

    override fun remove(key: String) {
        data.remove(key)
    }
}

/** Лучший результат на уровне. */
@Serializable
data class BestResult(
    val score: Int,
    val accuracyPercent: Int,
    val timeSec: Int,
)

/**
 * Сохраняемый прогресс игрока.
 *
 * @property unlocked открытые уровни (первый открыт всегда)
 * @property best лучший результат по каждому уровню
 * @property gamesPlayed число завершённых игровых сессий
 * @property applicationsReviewed число рассмотренных заявок за всё время
 */
@Serializable
data class Progress(
    val unlocked: Set<Difficulty> = setOf(Difficulty.INTERN),
    val best: Map<Difficulty, BestResult> = emptyMap(),
    val gamesPlayed: Int = 0,
    val applicationsReviewed: Int = 0,
) {
    /** Открыт ли уровень. */
    fun isUnlocked(level: Difficulty): Boolean = level in unlocked
}

/**
 * Итог записи сессии в прогресс.
 *
 * @property isNewBest результат стал лучшим на этом уровне
 * @property unlockedNext уровень, открытый этим прохождением (null, если ничего нового)
 */
data class RecordOutcome(
    val progress: Progress,
    val isNewBest: Boolean,
    val unlockedNext: Difficulty?,
)

/** Загрузка и сохранение [Progress] в [KeyValueStore]. Повреждённые данные заменяются прогрессом по умолчанию. */
class ProgressRepository(
    private val store: KeyValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /** Читает прогресс; при отсутствии или повреждении данных возвращает начальный прогресс. */
    fun load(): Progress {
        val raw = store.get(KEY) ?: return Progress()
        return try {
            val loaded = json.decodeFromString(Progress.serializer(), raw)
            // Первый уровень открыт всегда, даже если данные отредактированы вручную
            loaded.copy(unlocked = loaded.unlocked + Difficulty.INTERN)
        } catch (e: Exception) {
            Progress()
        }
    }

    /** Учитывает завершённую сессию: обновляет статистику, лучший результат и открывает следующий уровень. */
    fun recordSession(summary: SessionSummary): RecordOutcome {
        val current = load()
        val previousBest = current.best[summary.difficulty]
        val isNewBest = summary.answered > 0 && (previousBest == null || summary.score > previousBest.score)

        val newBest = if (isNewBest) {
            current.best + (summary.difficulty to BestResult(summary.score, summary.accuracyPercent, summary.totalTimeSec))
        } else {
            current.best
        }

        val next = Difficulty.entries.getOrNull(summary.difficulty.ordinal + 1)
        val newlyUnlocked = if (summary.passed && next != null && next !in current.unlocked) next else null
        val unlocked = if (newlyUnlocked != null) current.unlocked + newlyUnlocked else current.unlocked

        val updated = current.copy(
            unlocked = unlocked,
            best = newBest,
            gamesPlayed = current.gamesPlayed + 1,
            applicationsReviewed = current.applicationsReviewed + summary.answered,
        )
        store.put(KEY, json.encodeToString(Progress.serializer(), updated))
        return RecordOutcome(updated, isNewBest, newlyUnlocked)
    }

    /** Полностью сбрасывает прогресс. */
    fun reset() {
        store.remove(KEY)
    }

    companion object {
        /** Ключ в localStorage (версия в имени позволит сменить формат в будущем). */
        const val KEY = "tendercheck.progress.v1"
    }
}
