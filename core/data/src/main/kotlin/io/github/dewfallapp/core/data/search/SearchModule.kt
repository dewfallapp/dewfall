package io.github.dewfallapp.core.data.search

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the search repository that the Search YouTube screen uses. */
@Module
@InstallIn(SingletonComponent::class)
internal interface SearchModule {
    @Binds fun bindYouTubeSearch(repository: LayerYouTubeSearchRepository): YouTubeSearchRepository
}
