package io.github.dewfallapp.core.data.video

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the video details repository that the video screen uses, and the saved videos' one. */
@Module
@InstallIn(SingletonComponent::class)
internal interface VideoModule {
    @Binds fun bindVideoDetails(repository: LayerVideoDetailsRepository): VideoDetailsRepository

    @Binds fun bindStoredVideos(repository: RoomStoredVideosRepository): StoredVideosRepository
}
