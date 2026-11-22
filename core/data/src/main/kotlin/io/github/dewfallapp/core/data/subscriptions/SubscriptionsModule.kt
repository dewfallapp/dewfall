package io.github.dewfallapp.core.data.subscriptions

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the repository of the subscribed channels. */
@Module
@InstallIn(SingletonComponent::class)
internal interface SubscriptionsModule {
    @Binds fun bindSubscriptions(repository: RoomSubscriptionsRepository): SubscriptionsRepository
}
