package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.database.DownloadFolderDao
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

/** Answers with made-up folders, one of them twice. */
private class FakeDownloadFolderDao : DownloadFolderDao {
    override suspend fun folders() = listOf("folder-1", "folder-2", "folder-1")

    override fun folderOf(videoId: String) = flowOf("folder-$videoId")

    override suspend fun isFolderOwned(folder: String) = folder == "folder-1"
}

class RoomDownloadFoldersTest {
    private val folders = RoomDownloadFolders(FakeDownloadFolderDao())

    @Test
    fun `the folders come from the rows`() = runTest {
        assertEquals(setOf("folder-1", "folder-2"), folders.folders())
        assertEquals("folder-aaaaaaaaaaa", folders.folderOf(VideoId("aaaaaaaaaaa")).first())
        assertTrue(folders.isFolderOwned("folder-1"))
        assertFalse(folders.isFolderOwned("folder-3"))
    }
}
