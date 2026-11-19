package io.github.dewfallapp.core.data.search

import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.youtube.SearchRemoteDataSource
import javax.inject.Inject

/**
 * Searches through the YouTube layer, and returns its result unchanged. Phase 5 gives it work of
 * its own, such as marking the channels that the user follows.
 */
internal class LayerYouTubeSearchRepository
@Inject
constructor(private val searchSource: SearchRemoteDataSource) : YouTubeSearchRepository {
    override suspend fun search(query: String): YouTubeResult<SearchResults> =
        searchSource.search(query)
}
