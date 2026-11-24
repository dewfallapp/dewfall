package io.github.dewfallapp.feature.takeoutimport.ui

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dewfallapp.core.data.takeout.TakeoutImportRepository
import io.github.dewfallapp.core.mvi.MviViewModel
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportIntent
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportReducer
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs the Takeout import of each picked file, one at a time. Leaving the screen, which
 * [TakeoutImportIntent.Left] reports, cancels a running import, which then subscribes to nothing if
 * the file was still being read. Clearing the ViewModel cancels it too. See
 * docs/architecture.md#the-takeout-import.
 *
 * It takes no dispatcher, because the repository's call is main-safe.
 */
@HiltViewModel
internal class TakeoutImportViewModel
@Inject
constructor(private val repository: TakeoutImportRepository) :
    MviViewModel<TakeoutImportState, TakeoutImportIntent, Nothing>(
        TakeoutImportState.Idle,
        TakeoutImportReducer,
    ) {

    /** The running import. Only the main thread touches it. */
    private var importJob: Job? = null

    override fun runSideEffects(intent: TakeoutImportIntent) {
        when (intent) {
            is TakeoutImportIntent.FilePicked -> startImport(intent.file)
            is TakeoutImportIntent.ImportFinished -> Unit
            TakeoutImportIntent.Left -> importJob?.cancel()
        }
    }

    /** Imports [file], unless an import is already running, as the reducer keeps it Reading. */
    private fun startImport(file: String) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch {
            val result = repository.importFrom(file)
            dispatch(TakeoutImportIntent.ImportFinished(result))
        }
    }
}
