package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.DownloadFileKind
import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.model.VideoId
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** What the run does with the files of a download that the user cancels or removes. */
internal class RunnerRemovesTest : RunnerFixture() {
    private fun folderOf(id: VideoId) = File(root, id.value + "-folder")

    /**
     * Answers the video's second range with [action] and then the range's right header, but a body
     * that stalls until the call is cancelled, so only a stop ends the attempt.
     */
    private fun stallAfter(action: () -> Unit): FakeMedia =
        FakeMedia(video) { n, chain ->
            if (n == 1) {
                action()
                val (start, end) =
                    Regex("""bytes=(\d+)-(\d+)""")
                        .matchEntire(chain.request().header("Range").orEmpty())!!
                        .destructured
                val last = minOf(end.toLong(), video.size - 1L)
                stalled(
                    chain,
                    CompletableDeferred(),
                    contentRange = "bytes $start-$last/${video.size}",
                    length = last - start.toLong() + 1,
                )
            } else {
                null
            }
        }

    @Test
    fun `a removed download stops, its folder goes once the attempt ended, and the next runs`() =
        runTest {
            waiting(FIRST)
            waiting(SECOND)
            routed =
                RoutedMedia(
                    mapOf(
                        link(136) to stallAfter { queue.remove(FIRST) },
                        link(251) to FakeMedia(audio),
                    ),
                )

            assertEquals(RunOutcome.DONE, run())

            assertFalse(folderOf(FIRST).exists())
            assertEquals(QueueState.DOWNLOADED, queue.row(SECOND).state)
            assertTrue(folderOf(SECOND).exists())
        }

    @Test
    fun `a download removed as its attempt finishes loses its folder`() = runTest {
        waiting(FIRST)
        queue.beforeFinish = { queue.remove(FIRST) }

        assertEquals(RunOutcome.DONE, run())

        assertFalse(folderOf(FIRST).exists())
    }

    @Test
    fun `a remove and a new download of the video stop the attempt and keep only the new folder`() =
        runTest {
            waiting(FIRST)
            routed =
                RoutedMedia(
                    mapOf(
                        link(136) to stallAfter { queue.replace(FIRST, "new-folder") },
                        link(251) to FakeMedia(audio),
                    ),
                )

            assertEquals(RunOutcome.DONE, run())

            assertFalse(folderOf(FIRST).exists())
            assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
            assertEquals("new-folder", queue.row(FIRST).folder)
            assertTrue(File(root, "new-folder").exists())
        }

    @Test
    fun `a run first deletes the folders that no download names`() = runTest {
        queue.add(FakeRow(FIRST, FIRST.value + "-folder", state = QueueState.PAUSED))
        waiting(SECOND)
        part(FIRST, DownloadFileKind.VIDEO).apply { parentFile?.mkdirs() }.writeBytes(ByteArray(10))
        val stray = File(root, "stray").apply { mkdirs() }
        File(stray, "video.part").writeBytes(ByteArray(10))
        var strayAtClaim: Boolean? = null
        queue.afterClaim = { if (strayAtClaim == null) strayAtClaim = stray.exists() }

        assertEquals(RunOutcome.DONE, run())

        assertEquals(false, strayAtClaim)
        assertFalse(stray.exists())
        assertEquals(10, part(FIRST, DownloadFileKind.VIDEO).length())
        assertTrue(lines.any { it.contains("Unowned download folders deleted") })
    }
}
