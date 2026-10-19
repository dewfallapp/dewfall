package io.github.dewfallapp.core.mvi.counter

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Stands in for a repository. Like a real one, it moves its work to the dispatcher it receives, so
 * a test can pass a TestDispatcher, and the delay takes no real time.
 */
class SavedCount(private val count: Int, private val dispatcher: CoroutineDispatcher) {
    suspend fun load(): Int =
        withContext(dispatcher) {
            delay(1.seconds)
            count
        }
}
