package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.database.DownloadFolderDao
import io.github.dewfallapp.core.model.VideoId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Which folders the downloads name, as the download job reads them: it deletes a removed download's
 * folder once its writer stopped, ends an attempt whose row names another folder, and deletes every
 * folder that no download names at the start of a run. Only `:core:download` uses it. Folder names
 * are user data, so never log them. See docs/architecture.md#downloads-and-cleanup.
 */
interface DownloadFolders {
    /** The folder of the video's download, or null when it has none. */
    fun folderOf(videoId: VideoId): Flow<String?>

    /** The folder of every download. */
    suspend fun folders(): Set<String>

    /** Whether a download names [folder]. */
    suspend fun isFolderOwned(folder: String): Boolean
}

/** The downloads' folders from the `downloads` table. */
internal class RoomDownloadFolders @Inject constructor(private val dao: DownloadFolderDao) :
    DownloadFolders {
    override fun folderOf(videoId: VideoId): Flow<String?> =
        dao.folderOf(videoId.value).distinctUntilChanged()

    override suspend fun folders(): Set<String> = dao.folders().toSet()

    override suspend fun isFolderOwned(folder: String): Boolean = dao.isFolderOwned(folder)
}
