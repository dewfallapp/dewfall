package io.github.dewfallapp.feature.downloads.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** A screen's lifecycle that the test moves by hand, with no main thread check. */
private class TestScreen : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle
        get() = registry
}

/** A handler that waits until the test lets it return, and counts its runs and cancellations. */
private class SlowHandler {
    val done = CompletableDeferred<Unit>()
    var runs = 0
    var cancels = 0
    var finishes = 0

    suspend fun handle() {
        runs++
        try {
            done.await()
        } catch (e: CancellationException) {
            cancels++
            throw e
        }
    }
}

/**
 * Tests that an effect is handled only while the screen is started, that a stop mid-handling
 * cancels it without reporting it, and that the next start handles it again, as
 * docs/architecture.md#effects promises.
 */
// setMain, resetMain, UnconfinedTestDispatcher and runCurrent are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class HandleWhileStartedTest {
    private val screen = TestScreen()
    private val handler = SlowHandler()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun handle() =
        handleWhileStarted(screen.lifecycle, handler::handle) { handler.finishes++ }

    @Test
    fun `nothing is handled before the screen starts`() = runTest {
        screen.registry.currentState = Lifecycle.State.CREATED
        val job = launch { handle() }
        runCurrent()

        assertEquals(0, handler.runs)

        screen.registry.currentState = Lifecycle.State.STARTED
        runCurrent()
        assertEquals(1, handler.runs)
        job.cancel()
    }

    @Test
    fun `a stop mid-handling cancels it unreported, and the next start handles it again`() =
        runTest {
            screen.registry.currentState = Lifecycle.State.STARTED
            val job = launch { handle() }
            runCurrent()
            assertEquals(1, handler.runs)

            screen.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertEquals(1, handler.cancels)
            assertEquals(0, handler.finishes)

            screen.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, handler.runs)

            handler.done.complete(Unit)
            runCurrent()
            assertEquals(1, handler.finishes)

            screen.registry.currentState = Lifecycle.State.CREATED
            screen.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, handler.runs)
            assertEquals(1, handler.finishes)
            job.cancel()
        }
}
