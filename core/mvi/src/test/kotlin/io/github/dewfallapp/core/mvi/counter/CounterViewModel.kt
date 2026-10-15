package io.github.dewfallapp.core.mvi.counter

import androidx.lifecycle.viewModelScope
import io.github.dewfallapp.core.mvi.MviViewModel
import kotlinx.coroutines.launch

class CounterViewModel(private val savedCount: SavedCount) :
    MviViewModel<CounterState, CounterIntent, CounterEffect>(CounterState(), CounterReducer) {

    override fun runSideEffects(intent: CounterIntent) {
        when (intent) {
            // The reducer has already shown loading. The result goes back through the reducer.
            CounterIntent.Load ->
                viewModelScope.launch { dispatch(CounterIntent.Loaded(savedCount.load())) }
            CounterIntent.Increment,
            CounterIntent.Reset,
            is CounterIntent.Loaded -> Unit
        }
    }
}
