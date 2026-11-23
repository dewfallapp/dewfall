package io.github.dewfallapp.core.data.search

import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import javax.inject.Inject

/**
 * Searches through the YouTube layer, and returns its result unchanged. The Search YouTube screen
 * marks the channels that the user follows itself, from SubscriptionsRepository, so a row follows a
 * subscription that changes after the search.
 */
internal class LayerYouTubeSearchRepository
@Inject
constructor(private val searchSource: SearchRemoteDataSource) : YouTubeSearchRepository {
    override suspend fun search(query: String): YouTubeResult<SearchResults> =
        searchSource.search(query)
}
