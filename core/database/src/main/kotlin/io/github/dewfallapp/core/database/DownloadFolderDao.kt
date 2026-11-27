package io.github.dewfallapp.core.database

import androidx.room3.Dao
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * The folders that the `downloads` table's rows name, for the download job, which deletes a removed
 * download's folder once its writer stopped, and every folder that no row names at the start of a
 * run. Every function is main-safe. See docs/architecture.md#downloads-and-cleanup.
 */
@Dao
interface DownloadFolderDao {
    /** The folder of every download. */
    @Query("SELECT folder FROM downloads") suspend fun folders(): List<String>

    /** The folder of the video's download, or null when it has none. */
    @Query("SELECT folder FROM downloads WHERE video_id = :videoId")
    fun folderOf(videoId: String): Flow<String?>

    /** Whether a download's row names [folder]. */
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE folder = :folder)")
    suspend fun isFolderOwned(folder: String): Boolean
}
