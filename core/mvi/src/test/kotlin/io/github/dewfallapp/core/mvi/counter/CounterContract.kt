package io.github.dewfallapp.core.mvi.counter

import io.github.dewfallapp.core.mvi.MviEffect
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

// A small example screen that shows the whole MVI pattern. It lives in test code only. In the app,
// a feature's State, Intent, Effect and reducer go in its -logic module, and its ViewModel in its
// -ui module. See docs/architecture.md#the-mvi-contract.

data class CounterState(val count: Int = 0, val isLoading: Boolean = false) : MviState

sealed interface CounterIntent : MviIntent {
    /** The user taps the plus button. */
    data object Increment : CounterIntent

    /** The user taps the reset button. */
    data object Reset : CounterIntent

    /** The user asks for the saved count. */
    data object Load : CounterIntent

    /** The saved count arrived. CounterViewModel sends this when its load finishes. */
    data class Loaded(val count: Int) : CounterIntent
}

sealed interface CounterEffect : MviEffect {
    /** Tell the user that the count can't go higher. */
    data object LimitReached : CounterEffect

    /** Tell the user what the count was before the reset. */
    data class WasReset(val previousCount: Int) : CounterEffect
}
