package io.github.dewfallapp.core.data.takeout

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** The dispatcher that the Takeout import's blocking reads run on. Tests replace it. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class TakeoutReadDispatcher

/** Binds the Takeout import's repository and the opener of picked files. */
@Module
@InstallIn(SingletonComponent::class)
internal interface TakeoutModule {
    @Binds fun bindImport(repository: ReaderTakeoutImportRepository): TakeoutImportRepository

    @Binds fun bindOpener(opener: ContentResolverDocumentOpener): DocumentOpener

    companion object {
        @Provides
        @TakeoutReadDispatcher
        fun provideReadDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
