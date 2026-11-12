package pro.tendercheck.data

import kotlinx.serialization.json.Json
import pro.tendercheck.model.Difficulty
import pro.tendercheck.model.Scenario
import pro.tendercheck.model.ScenarioFile

/**
 * Доступ к заявкам. Данные берутся из data/scenarios.json,
 * который при сборке встраивается в константу [SCENARIOS_JSON].
 */
object ScenarioRepository {
    private val json = Json { ignoreUnknownKeys = true }

    /** Все заявки из файла контента. */
    val all: List<Scenario> by lazy {
        json.decodeFromString(ScenarioFile.serializer(), SCENARIOS_JSON).scenarios
    }

    /** Заявки одного уровня сложности. */
    fun forLevel(level: Difficulty): List<Scenario> = all.filter { it.level == level }
}
