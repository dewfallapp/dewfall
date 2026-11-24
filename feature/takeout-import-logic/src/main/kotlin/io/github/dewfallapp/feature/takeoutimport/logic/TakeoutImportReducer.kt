package io.github.dewfallapp.feature.takeoutimport.logic

import io.github.dewfallapp.core.model.TakeoutImportResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.core.mvi.Reducer

/**
 * The Takeout import screen's reducer. One import runs at a time: a picked file starts reading from
 * any state but Reading, and a result counts only while Reading. The screen has no effects, so its
 * effect type is Nothing.
 */
object TakeoutImportReducer : Reducer<TakeoutImportState, TakeoutImportIntent, Nothing> {
    override fun reduce(
        state: TakeoutImportState,
        intent: TakeoutImportIntent,
    ): Next<TakeoutImportState, Nothing> =
        Next(
            when (intent) {
                is TakeoutImportIntent.FilePicked -> TakeoutImportState.Reading
                is TakeoutImportIntent.ImportFinished -> {
                    if (state == TakeoutImportState.Reading) intent.result.toState() else state
                }
                TakeoutImportIntent.Left -> state
            },
        )
}

private fun TakeoutImportResult.toState(): TakeoutImportState =
    when (this) {
        is TakeoutImportResult.Imported -> {
            TakeoutImportState.Imported(
                channels = channels.map { ImportedChannelRow(it.id, it.name) },
                added = added,
                alreadySubscribed = alreadySubscribed,
                unreadableLines = unreadableLines,
            )
        }
        is TakeoutImportResult.Failed -> TakeoutImportState.Failed(reason)
    }
