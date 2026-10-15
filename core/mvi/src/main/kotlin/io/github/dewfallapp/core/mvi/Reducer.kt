package io.github.dewfallapp.core.mvi

/**
 * Turns the old state and an intent into the new state and any one-time effects. It must be a pure
 * function, with no coroutines, I/O, clock, randomness or shared mutable state, so it can be tested
 * without a device. Every reducer has unit tests.
 */
fun interface Reducer<S : MviState, in I : MviIntent, out E : MviEffect> {
    fun reduce(state: S, intent: I): Next<S, E>
}

/** What a reducer returns: the new state, and the effects for the UI to handle, in order. */
data class Next<out S : MviState, out E : MviEffect>(
    val state: S,
    val effects: List<E> = emptyList(),
)
