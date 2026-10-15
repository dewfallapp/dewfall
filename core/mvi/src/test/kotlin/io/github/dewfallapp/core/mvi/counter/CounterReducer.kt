package io.github.dewfallapp.core.mvi.counter

import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/** Counts up to [LIMIT], then reports [CounterEffect.LimitReached] instead. */
object CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    const val LIMIT = 3

    override fun reduce(
        state: CounterState,
        intent: CounterIntent,
    ): Next<CounterState, CounterEffect> =
        when (intent) {
            CounterIntent.Increment ->
                if (state.count < LIMIT) {
                    Next(state.copy(count = state.count + 1))
                } else {
                    Next(state, listOf(CounterEffect.LimitReached))
                }
            CounterIntent.Reset ->
                Next(state.copy(count = 0), listOf(CounterEffect.WasReset(state.count)))
            CounterIntent.Load -> Next(state.copy(isLoading = true))
            is CounterIntent.Loaded -> Next(state.copy(count = intent.count, isLoading = false))
        }
}
