package io.github.dewfallapp.core.data.download

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadFilesTest {
    private val root = Files.createTempDirectory("downloads").toFile()
    private val files = DownloadFiles(root)

    @Test
    fun `each download has a folder of its own under the root`() {
        assertEquals(File(root, "folder-1"), files.folder("folder-1"))
        assertEquals(
            File(root, "folder-1/video.part"),
            files.part("folder-1", DownloadFileKind.VIDEO),
        )
        assertEquals(
            File(root, "folder-1/audio.part"),
            files.part("folder-1", DownloadFileKind.AUDIO),
        )
    }

    @Test
    fun `a complete file has its format's extension`() {
        assertEquals(
            File(root, "f/video.mp4"),
            files.complete("f", DownloadFileKind.VIDEO, "video/mp4"),
        )
        assertEquals(
            File(root, "f/audio.m4a"),
            files.complete("f", DownloadFileKind.AUDIO, "audio/mp4"),
        )
        assertEquals(
            File(root, "f/audio.webm"),
            files.complete("f", DownloadFileKind.AUDIO, "audio/webm; codecs=\"opus\""),
        )
        assertEquals(
            File(root, "f/media.webm"),
            files.complete("f", DownloadFileKind.MEDIA, "video/webm"),
        )
        assertEquals(File(root, "f/video.bin"), files.complete("f", DownloadFileKind.VIDEO, "x/y"))
    }

    @Test
    fun `deleting a download removes its folder and files only`() {
        files.part("a", DownloadFileKind.VIDEO).apply { parentFile?.mkdirs() }.writeText("12")
        files.part("b", DownloadFileKind.VIDEO).apply { parentFile?.mkdirs() }.writeText("34")

        assertTrue(files.delete("a"))

        assertFalse(files.folder("a").exists())
        assertTrue(files.part("b", DownloadFileKind.VIDEO).exists())
    }
}
