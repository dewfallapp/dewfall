package io.github.dewfallapp.core.data.watch

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the repository of the videos' watch states. */
@Module
@InstallIn(SingletonComponent::class)
internal interface WatchStateModule {
    @Binds fun bindWatchState(repository: RoomWatchStateRepository): WatchStateRepository
}
