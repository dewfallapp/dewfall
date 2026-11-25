package io.github.dewfallapp.core.data.subscriptions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the repository of the subscribed channels, subscribing with uploads, and the check. */
@Module
@InstallIn(SingletonComponent::class)
internal interface SubscriptionsModule {
    @Binds fun bindSubscriptions(repository: RoomSubscriptionsRepository): SubscriptionsRepository

    @Binds
    fun bindSubscribeWithUploads(
        subscribe: RepositoriesSubscribeWithUploads,
    ): SubscribeWithUploads

    @Binds fun bindUploadsChecker(checker: RepositoriesUploadsChecker): UploadsChecker
}
