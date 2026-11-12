package pro.tendercheck.storage

import pro.tendercheck.engine.SessionSummary
import pro.tendercheck.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Тесты сохранения прогресса и лучших результатов. */
class ProgressRepositoryTest {

    private fun summary(level: Difficulty, score: Int, passed: Boolean, answered: Int = 8) = SessionSummary(
        difficulty = level, score = score, total = 8, answered = answered, correct = 0, partial = 0,
        mistakes = 0, accuracyPercent = 80, totalTimeSec = 300, passed = passed, results = emptyList(),
    )

    @Test
    fun initialProgressHasOnlyFirstLevelUnlocked() {
        val progress = ProgressRepository(InMemoryStore()).load()

        assertEquals(setOf(Difficulty.INTERN), progress.unlocked)
        assertTrue(progress.best.isEmpty())
        assertEquals(0, progress.gamesPlayed)
    }

    @Test
    fun passingLevelUnlocksNextAndSavesBest() {
        val repo = ProgressRepository(InMemoryStore())
        val outcome = repo.recordSession(summary(Difficulty.INTERN, score = 700, passed = true))

        assertEquals(Difficulty.ANALYST, outcome.unlockedNext)
        assertTrue(outcome.isNewBest)
        assertTrue(repo.load().isUnlocked(Difficulty.ANALYST))
        assertEquals(700, repo.load().best[Difficulty.INTERN]?.score)
        assertEquals(1, repo.load().gamesPlayed)
        assertEquals(8, repo.load().applicationsReviewed)
    }

    @Test
    fun worseResultDoesNotReplaceBest() {
        val repo = ProgressRepository(InMemoryStore())
        repo.recordSession(summary(Difficulty.INTERN, score = 700, passed = true))
        val outcome = repo.recordSession(summary(Difficulty.INTERN, score = 500, passed = true))

        assertFalse(outcome.isNewBest)
        assertNull(outcome.unlockedNext) // второй уровень уже был открыт
        assertEquals(700, repo.load().best[Difficulty.INTERN]?.score)
        assertEquals(2, repo.load().gamesPlayed)
    }

    @Test
    fun failedSessionDoesNotUnlockNextLevel() {
        val repo = ProgressRepository(InMemoryStore())
        val outcome = repo.recordSession(summary(Difficulty.INTERN, score = 300, passed = false, answered = 5))

        assertNull(outcome.unlockedNext)
        assertFalse(repo.load().isUnlocked(Difficulty.ANALYST))
        assertEquals(5, repo.load().applicationsReviewed)
    }

    @Test
    fun lastLevelHasNothingToUnlock() {
        val repo = ProgressRepository(InMemoryStore())
        val outcome = repo.recordSession(summary(Difficulty.HEAD, score = 900, passed = true))

        assertNull(outcome.unlockedNext)
    }

    @Test
    fun progressSurvivesRecreationOfRepository() {
        val store = InMemoryStore()
        ProgressRepository(store).recordSession(summary(Difficulty.INTERN, score = 650, passed = true))

        val reloaded = ProgressRepository(store).load()
        assertTrue(reloaded.isUnlocked(Difficulty.ANALYST))
        assertEquals(650, reloaded.best[Difficulty.INTERN]?.score)
    }

    @Test
    fun corruptedDataFallsBackToInitialProgress() {
        val store = InMemoryStore()
        store.put(ProgressRepository.KEY, "{это не json")

        assertEquals(Progress(), ProgressRepository(store).load())
    }

    @Test
    fun resetClearsEverything() {
        val store = InMemoryStore()
        val repo = ProgressRepository(store)
        repo.recordSession(summary(Difficulty.INTERN, score = 650, passed = true))
        repo.reset()

        assertEquals(Progress(), repo.load())
    }
}
