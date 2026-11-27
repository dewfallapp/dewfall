package io.github.dewfallapp.core.data.download

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The dispatcher that the repository deletes a removed download's files on, because a delete
 * blocks. Tests replace it.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) internal annotation class DownloadFilesDispatcher

/** The name of the folder under noBackupFilesDir that holds every download's folder. */
private const val DOWNLOADS_FOLDER = "downloads"

/**
 * Binds the downloads' repository and queue, and provides where their files live. DownloadScheduler
 * is bound in `:core:download`, which runs the queue.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface DownloadModule {
    @Binds fun bindDownloads(repository: RoomDownloadsRepository): DownloadsRepository

    @Binds fun bindQueue(queue: RoomDownloadQueue): DownloadQueue

    @Binds fun bindFolders(folders: RoomDownloadFolders): DownloadFolders

    @Binds fun bindFolderNames(names: RandomFolderNames): FolderNames

    companion object {
        @Provides
        @DownloadFilesDispatcher
        fun provideFilesDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideDownloadFiles(@ApplicationContext context: Context): DownloadFiles =
            DownloadFiles(File(context.noBackupFilesDir, DOWNLOADS_FOLDER))
    }
}
