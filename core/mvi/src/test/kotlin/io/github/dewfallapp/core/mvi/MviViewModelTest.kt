package io.github.dewfallapp.core.mvi

import io.github.dewfallapp.core.mvi.counter.CounterEffect
import io.github.dewfallapp.core.mvi.counter.CounterIntent
import io.github.dewfallapp.core.mvi.counter.CounterReducer
import io.github.dewfallapp.core.mvi.counter.CounterState
import io.github.dewfallapp.core.mvi.counter.CounterViewModel
import io.github.dewfallapp.core.mvi.counter.SavedCount
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Tests MviViewModel through the counter example in the counter package. */
// setMain, resetMain, UnconfinedTestDispatcher and advanceUntilIdle are still marked experimental.
@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {
    @BeforeTest
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which local tests don't have. Every
        // TestDispatcher made after this, including runTest's, shares this one's scheduler.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dispatch runs the reducer and shows the new state`() = runTest {
        val viewModel = counterViewModel()

        viewModel.dispatch(CounterIntent.Increment)

        assertEquals(CounterState(count = 1), viewModel.state.value)
    }

    @Test
    fun `a load result comes back through the reducer`() = runTest {
        val viewModel = counterViewModel(savedCount = 2)

        viewModel.dispatch(CounterIntent.Load)
        assertEquals(CounterState(isLoading = true), viewModel.state.value)

        // Runs the load. Its delay takes no real time.
        advanceUntilIdle()
        assertEquals(CounterState(count = 2), viewModel.state.value)
    }

    @Test
    fun `an effect stays pending until effectHandled`() = runTest {
        val viewModel = counterViewModel()
        viewModel.incrementToLimit()

        viewModel.dispatch(CounterIntent.Increment)

        // Reading the effect doesn't remove it, so a UI that stops, or a handler that's cancelled,
        // gets it again.
        val pending = viewModel.pendingEffects.value
        assertEquals(listOf(CounterEffect.LimitReached), pending.map { it.effect })
        viewModel.dispatch(CounterIntent.Load)
        assertEquals(pending, viewModel.pendingEffects.value)

        viewModel.effectHandled(pending.single().id)

        assertEquals(emptyList(), viewModel.pendingEffects.value)
    }

    @Test
    fun `effectHandled removes only that effect`() = runTest {
        val viewModel = counterViewModel()
        viewModel.incrementToLimit()
        viewModel.dispatch(CounterIntent.Increment)
        viewModel.dispatch(CounterIntent.Reset)
        val (limitReached, wasReset) = viewModel.pendingEffects.value

        viewModel.effectHandled(wasReset.id)

        assertEquals(listOf(limitReached), viewModel.pendingEffects.value)
    }

    @Test
    fun `several effects keep their order`() = runTest {
        val viewModel = counterViewModel()
        viewModel.incrementToLimit()

        viewModel.dispatch(CounterIntent.Increment)
        viewModel.dispatch(CounterIntent.Increment)
        viewModel.dispatch(CounterIntent.Reset)

        val pending = viewModel.pendingEffects.value
        assertEquals(
            listOf(
                CounterEffect.LimitReached,
                CounterEffect.LimitReached,
                CounterEffect.WasReset(CounterReducer.LIMIT),
            ),
            pending.map { it.effect },
        )
        // Equal effects still get their own ids, in the order they were added.
        val ids = pending.map { it.id }
        assertTrue(ids.zipWithNext().all { (earlier, later) -> earlier < later })
    }

    private fun TestScope.counterViewModel(savedCount: Int = 0) =
        CounterViewModel(SavedCount(savedCount, StandardTestDispatcher(testScheduler)))

    private fun CounterViewModel.incrementToLimit() =
        repeat(CounterReducer.LIMIT) { dispatch(CounterIntent.Increment) }
}
