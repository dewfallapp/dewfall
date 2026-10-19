package io.github.dewfallapp.core.mvi.counter

import io.github.dewfallapp.core.mvi.Next
import kotlin.test.Test
import kotlin.test.assertEquals

/** A reducer is a plain function, so its tests need no coroutines and no ViewModel. */
class CounterReducerTest {
    @Test
    fun `increment adds one`() {
        val next = CounterReducer.reduce(CounterState(count = 1), CounterIntent.Increment)

        assertEquals(Next(CounterState(count = 2)), next)
    }

    @Test
    fun `increment at the limit keeps the count and reports LimitReached`() {
        val atLimit = CounterState(count = CounterReducer.LIMIT)

        val next = CounterReducer.reduce(atLimit, CounterIntent.Increment)

        assertEquals(Next(atLimit, listOf(CounterEffect.LimitReached)), next)
    }

    @Test
    fun `reset sets the count to zero and reports the old count`() {
        val next = CounterReducer.reduce(CounterState(count = 2), CounterIntent.Reset)

        assertEquals(Next(CounterState(count = 0), listOf(CounterEffect.WasReset(2))), next)
    }

    @Test
    fun `load shows loading until loaded sets the count`() {
        val loading = CounterReducer.reduce(CounterState(), CounterIntent.Load)
        assertEquals(Next(CounterState(isLoading = true)), loading)

        val loaded = CounterReducer.reduce(loading.state, CounterIntent.Loaded(count = 2))
        assertEquals(Next(CounterState(count = 2)), loaded)
    }
}
