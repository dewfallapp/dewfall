package io.github.dewfallapp.core.mvi

import androidx.lifecycle.ViewModel
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The base class of every screen's ViewModel. It holds the screen's state and the effects the UI
 * hasn't handled yet, and runs every intent through the screen's reducer. Subclasses start the work
 * that intents need in [runSideEffects], and report results back through [dispatch].
 *
 * Both the state and the pending effects live as long as the ViewModel. They survive configuration
 * changes and the UI stopping, but not process death. See docs/architecture.md#the-mvi-contract.
 */
abstract class MviViewModel<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
    private val reducer: Reducer<S, I, E>,
) : ViewModel() {

    private val mutableState = MutableStateFlow(initialState)

    /** The screen's state. */
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val mutablePendingEffects = MutableStateFlow<List<PendingEffect<E>>>(emptyList())

    /**
     * The effects the UI hasn't handled yet, oldest first. The UI handles the first one, then calls
     * [effectHandled] with its id. Until then, the effect stays here.
     */
    val pendingEffects: StateFlow<List<PendingEffect<E>>> = mutablePendingEffects.asStateFlow()

    private val lastEffectId = AtomicLong()

    /**
     * The single entry point for intents: what the user does, and the results of work that
     * [runSideEffects] started. Runs the reducer, adds its effects to [pendingEffects], then calls
     * [runSideEffects]. Call it on the main thread, as the UI and viewModelScope do.
     */
    fun dispatch(intent: I) {
        lateinit var next: Next<S, E>
        // update runs the reducer again if another thread changed the state in between. The
        // reducer is pure, so that's safe, and only the result that update stores counts.
        mutableState.update { current -> reducer.reduce(current, intent).also { next = it }.state }
        if (next.effects.isNotEmpty()) {
            val added = next.effects.map { PendingEffect(lastEffectId.incrementAndGet(), it) }
            mutablePendingEffects.update { it + added }
        }
        runSideEffects(intent)
    }

    /** Removes the pending effect with this id, once the UI has handled it. */
    fun effectHandled(id: Long) {
        mutablePendingEffects.update { pending -> pending.filterNot { it.id == id } }
    }

    /**
     * Starts the work that an intent needs, such as a load, in viewModelScope. It runs after the
     * reducer, so state.value is already the new state. Results come back through [dispatch] as
     * intents of their own.
     */
    protected abstract fun runSideEffects(intent: I)
}
