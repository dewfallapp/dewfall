package io.github.dewfallapp.feature.takeoutimport.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.core.model.TakeoutImportResult
import io.github.dewfallapp.core.mvi.MviIntent
import io.github.dewfallapp.core.mvi.MviState

/**
 * A channel as the import's report lists it. It keeps the ID, as the list's key. The ID and name
 * are user data, so never log them.
 */
data class ImportedChannelRow(val id: ChannelId, val name: String)

/** The Takeout import screen's state. */
sealed interface TakeoutImportState : MviState {
    /** Before a file is picked: the steps for getting the file, and the button to choose it. */
    data object Idle : TakeoutImportState

    /** A picked file is being read, and its channels subscribed. */
    data object Reading : TakeoutImportState

    /**
     * The import finished. [channels] holds every channel in the file, once each, in the file's
     * order. [added] of them are new, [alreadySubscribed] were subscribed before, and
     * [unreadableLines] lines of the file held no channel.
     */
    data class Imported(
        val channels: List<ImportedChannelRow>,
        val added: Int,
        val alreadySubscribed: Int,
        val unreadableLines: Int,
    ) : TakeoutImportState

    /** The import subscribed to nothing, for [reason]. */
    data class Failed(val reason: TakeoutImportFailure) : TakeoutImportState
}

/**
 * What happens on the Takeout import screen. Opening the picker changes no state, so it has no
 * intent, and neither have back and Done, which go to the app's navigation.
 */
sealed interface TakeoutImportIntent : MviIntent {
    /**
     * The user picked [file] in the file picker. It is the file's content URI as text, user data,
     * so never log it.
     */
    data class FilePicked(val file: String) : TakeoutImportIntent {
        override fun toString(): String = "FilePicked(hidden)"
    }

    /** The import of the picked file ended with [result]. */
    data class ImportFinished(val result: TakeoutImportResult) : TakeoutImportIntent

    /**
     * The user left the screen, with its back arrow or the back gesture. The ViewModel cancels a
     * running import at once, before the screen's exit animation ends, so a file still being read
     * subscribes to nothing. The screen is gone, so the state stays as it is.
     */
    data object Left : TakeoutImportIntent
}
