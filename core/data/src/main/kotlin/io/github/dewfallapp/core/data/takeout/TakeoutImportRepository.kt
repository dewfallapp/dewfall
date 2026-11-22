package io.github.dewfallapp.core.data.takeout

import io.github.dewfallapp.core.model.TakeoutImportResult

/**
 * Imports the subscriptions from a Google Takeout file that the user picked. See
 * docs/architecture.md#the-takeout-import.
 *
 * Main-safe. Cancelling the caller while the file is read subscribes to nothing. Once subscribing
 * has started, it finishes. The file, its URI and its channels are user data, so never log them.
 */
interface TakeoutImportRepository {
    /** Reads [file], a picked file's content URI as text, and subscribes to its channels. */
    suspend fun importFrom(file: String): TakeoutImportResult
}
