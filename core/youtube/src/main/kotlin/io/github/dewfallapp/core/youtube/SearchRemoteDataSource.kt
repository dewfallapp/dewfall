package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.YouTubeResult

/**
 * Searches YouTube for videos to stream and channels to follow.
 *
 * Main-safe: call it from any thread, including the main thread. The implementation moves blocking
 * work to an injected dispatcher. Any problem with YouTube or the network comes back as a
 * [YouTubeResult.Failure], never as an exception. Cancelling the calling coroutine cancels the
 * request, and the function then throws CancellationException, the only exception it throws.
 */
fun interface SearchRemoteDataSource {
    /**
     * The first page of results for [query]. A blank query returns a [YouTubeResult.Success] with
     * empty lists, without a request to YouTube. So does a query that YouTube finds nothing for.
     * The query is user data, so never log it.
     */
    suspend fun search(query: String): YouTubeResult<SearchResults>
}
