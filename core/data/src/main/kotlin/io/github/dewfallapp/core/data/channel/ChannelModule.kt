package io.github.dewfallapp.core.data.channel

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the channel uploads repository that the channel page uses. */
@Module
@InstallIn(SingletonComponent::class)
internal interface ChannelModule {
    @Binds
    fun bindChannelUploads(repository: LayerChannelUploadsRepository): ChannelUploadsRepository
}
