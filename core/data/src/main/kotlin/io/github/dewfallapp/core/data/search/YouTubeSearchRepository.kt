package io.github.dewfallapp.core.data.search

import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Searches YouTube for the Search YouTube screen. Features reach YouTube only through repositories
 * like this one, never through the YouTube layer itself. See
 * docs/architecture.md#reading-from-youtube.
 *
 * Main-safe, and any problem comes back as a [YouTubeResult.Failure]. It throws only
 * CancellationException, when the calling coroutine is cancelled.
 */
fun interface YouTubeSearchRepository {
    /**
     * The first page of results for [query], as YouTube gave them. A blank query returns empty
     * results without a request. The query and the results are user data, so never log them.
     */
    suspend fun search(query: String): YouTubeResult<SearchResults>
}
