package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.ChosenFormat
import io.github.dewfallapp.core.data.download.ChosenFormats
import io.github.dewfallapp.core.data.download.DownloadFileKind
import io.github.dewfallapp.core.data.download.QueueState
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.AudioTrack
import io.github.dewfallapp.core.model.AudioTrackKind
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.MediaRequestRefused
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

internal class DownloadRunnerTest : RunnerFixture() {
    @Test
    fun `the queue downloads one video after another in its order`() = runTest {
        waiting(FIRST)
        waiting(SECOND)

        assertEquals(RunOutcome.DONE, run())

        for (id in listOf(FIRST, SECOND)) {
            assertEquals(QueueState.DOWNLOADED, queue.row(id).state)
            assertContentEquals(
                video,
                complete(id, DownloadFileKind.VIDEO, "video/mp4").readBytes(),
            )
            assertContentEquals(
                audio,
                complete(id, DownloadFileKind.AUDIO, "audio/webm").readBytes(),
            )
            assertEquals((video.size + audio.size).toLong(), queue.row(id).downloadedBytes)
        }
        assertEquals(RunPhase.IDLE, runState.phase)
        assertEquals(DownloadProgress(310 * 1024L, 310 * 1024L), reports.last())
    }

    @Test
    fun `it keeps the 720 pixel format and remembers the choice`() = runTest {
        waiting(FIRST)

        run()

        assertEquals(
            ChosenFormats(
                ChosenFormat(136, "video/mp4", video.size.toLong()),
                ChosenFormat(251, "audio/webm", audio.size.toLong()),
            ),
            queue.row(FIRST).chosen,
        )
    }

    @Test
    fun `a run starts with what an ended process left running`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", state = QueueState.DOWNLOADING))

        run()

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
    }

    @Test
    fun `the original sound track is kept when tracks share an itag`() = runTest {
        waiting(FIRST)
        val dub = AudioTrack("ar.10", name = null, languageTag = "ar", AudioTrackKind.DUBBED)
        val original = AudioTrack("en.4", name = null, languageTag = "en", AudioTrackKind.ORIGINAL)
        val dubLink = link(251) + "&xtags=dub"
        val originalLink = link(251) + "&xtags=original"
        val dubBytes = fileOf(5_000)
        val tracks = StreamLinksRepository { id ->
            linkCalls++
            YouTubeResult.Success(
                links(id)
                    .copy(
                        audioFormats =
                            listOf(
                                audioFormat(251, dubBytes.size.toLong())
                                    .copy(url = dubLink, track = dub),
                                audioFormat(251, audio.size.toLong())
                                    .copy(url = originalLink, track = original),
                            ),
                    ),
            )
        }
        routed =
            RoutedMedia(
                mapOf(
                    link(136) to FakeMedia(video),
                    dubLink to FakeMedia(dubBytes),
                    originalLink to FakeMedia(audio),
                ),
            )

        runner(links = tracks).run {}

        assertEquals("en.4", queue.row(FIRST).chosen?.audio?.track)
        assertContentEquals(
            audio,
            complete(FIRST, DownloadFileKind.AUDIO, "audio/webm").readBytes(),
        )
    }

    @Test
    fun `a media request that keeps failing counts in a row though the links come`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", youtubeTries = 2))
        routed = media(1, video = FakeMedia(video) { _, chain -> status(chain, 404) })

        assertEquals(RunOutcome.DONE, run())

        assertEquals(QueueState.FAILED, queue.row(FIRST).state)
        assertEquals(DownloadFailure.YOUTUBE, queue.row(FIRST).failure)
    }

    @Test
    fun `new bytes start the tries again`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", youtubeTries = 2))
        routed =
            media(
                1,
                video = FakeMedia(video) { n, chain -> if (n == 1) status(chain, 404) else null },
            )

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
        assertEquals(1, queue.row(FIRST).youtubeTries)
        assertEquals(100L * 1024, part(FIRST, DownloadFileKind.VIDEO).length())
    }

    @Test
    fun `old files that can't be deleted keep new formats out of the folder`() = runTest {
        // The old choice's video file has the same name as the new one's, with other bytes.
        val chosen = ChosenFormats(ChosenFormat(999, "video/mp4", 10), null)
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", chosen = chosen, downloadedBytes = 10))
        val old = complete(FIRST, DownloadFileKind.VIDEO, "video/mp4")
        old.apply { parentFile?.mkdirs() }.writeBytes(ByteArray(10))
        val folder = checkNotNull(old.parentFile)
        folder.setWritable(false)

        try {
            assertEquals(RunOutcome.RETRY, run())
        } finally {
            folder.setWritable(true)
        }

        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
        assertNull(queue.row(FIRST).chosen)
        assertEquals(RunOutcome.DONE, run())
        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
        assertContentEquals(video, complete(FIRST, DownloadFileKind.VIDEO, "video/mp4").readBytes())
    }

    @Test
    fun `progress is written once a second between the ends of ranges`() = runTest {
        waiting(FIRST)
        // Each request of the video takes 2 seconds, so its first buffer comes after a second.
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { _, _ ->
                        clock += 2.seconds
                        null
                    },
            )

        run()

        // Each of the video's 3 ranges: its first buffer and its end. The audio's range: its end.
        assertEquals(7, queue.progressWrites)
    }

    @Test
    fun `a database that throws waits for the next run, keeping its tries`() = runTest {
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", youtubeTries = 1))
        queue.failNextFormats = true

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
        assertEquals(1, queue.row(FIRST).youtubeTries)
        assertTrue(lines.any { "Download failed on the phone" in it })
    }

    @Test
    fun `a write that fails while space runs out fails for space, keeping the bytes`() = runTest {
        waiting(FIRST)
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { n, _ ->
                        if (n == 1) {
                            free = 1_000
                            throw java.io.IOException("No space left")
                        }
                        null
                    },
            )

        assertEquals(RunOutcome.DONE, run())

        assertEquals(DownloadFailure.NO_SPACE, queue.row(FIRST).failure)
        assertEquals(100L * 1024, part(FIRST, DownloadFileKind.VIDEO).length())
    }

    @Test
    fun `after a run that waits for the network, a tap schedules a run at once`() = runTest {
        waiting(FIRST)
        linkAnswers += YouTubeResult.Failure(YouTubeError.NETWORK)
        assertEquals(RunOutcome.RETRY, run())
        var launches = 0
        val scheduler =
            QueueScheduler(
                queue,
                runState,
                { true.also { launches++ } },
                StopCheck {},
                backgroundScope,
                Logger.Silent,
            )

        scheduler.startNow()

        assertEquals(1, launches)
    }

    @Test
    fun `after a run ends, a start schedules the next download`() = runTest {
        waiting(FIRST)
        assertEquals(RunOutcome.DONE, run())
        assertEquals(RunPhase.IDLE, runState.phase)
        var launches = 0
        val scheduler =
            QueueScheduler(
                queue,
                runState,
                { true.also { launches++ } },
                StopCheck {},
                backgroundScope,
                Logger.Silent,
            )

        waiting(SECOND)
        scheduler.startNow()

        assertEquals(1, launches)
        assertEquals(RunPhase.SCHEDULED, runState.phase)
    }

    @Test
    fun `a live stream's links, only an HLS manifest, fail as unavailable`() = runTest {
        waiting(FIRST)
        linkAnswers +=
            YouTubeResult.Success(
                StreamLinks(
                    FIRST,
                    audioFormats = emptyList(),
                    videoFormats = emptyList(),
                    hlsManifestUrl = "https://manifest.googlevideo.com/api/manifest/hls_variant",
                    expiresAt = null,
                ),
            )

        assertEquals(RunOutcome.DONE, run())

        assertEquals(DownloadFailure.UNAVAILABLE, queue.row(FIRST).failure)
    }

    @Test
    fun `a partial file goes on from its bytes`() = runTest {
        val chosen =
            ChosenFormats(
                ChosenFormat(136, "video/mp4", video.size.toLong()),
                ChosenFormat(251, "audio/webm", audio.size.toLong()),
            )
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", chosen = chosen, downloadedBytes = 150_000))
        part(FIRST, DownloadFileKind.VIDEO)
            .apply { parentFile?.mkdirs() }
            .writeBytes(video.copyOfRange(0, 150_000))
        val videoMedia = FakeMedia(video)
        routed = media(1, video = videoMedia)

        run()

        assertEquals("bytes=150000-252399", videoMedia.ranges.first())
        assertContentEquals(video, complete(FIRST, DownloadFileKind.VIDEO, "video/mp4").readBytes())
    }

    @Test
    fun `a lost connection waits with the bytes and runs again`() = runTest {
        waiting(FIRST)
        // The connection drops after the first range.
        val offlineAfterFirst =
            FakeMedia(video) { n, _ -> if (n >= 1) throw java.io.IOException("Offline") else null }
        routed = media(1, video = offlineAfterFirst)

        assertEquals(RunOutcome.RETRY, run())

        val row = queue.row(FIRST)
        assertEquals(QueueState.WAITING, row.state)
        assertEquals(0, row.youtubeTries)
        assertEquals(100L * 1024, part(FIRST, DownloadFileKind.VIDEO).length())
        assertEquals(RunPhase.IDLE, runState.phase)
    }

    @Test
    fun `no connection for the links waits and runs again`() = runTest {
        waiting(FIRST)
        linkAnswers += YouTubeResult.Failure(YouTubeError.NETWORK)

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(QueueState.WAITING, queue.row(FIRST).state)
        assertEquals(0, queue.row(FIRST).youtubeTries)
    }

    @Test
    fun `a video YouTube won't show fails and deletes its files`() = runTest {
        waiting(FIRST)
        part(FIRST, DownloadFileKind.VIDEO)
            .apply { parentFile?.mkdirs() }
            .writeBytes(video.copyOfRange(0, 10))
        linkAnswers += YouTubeResult.Failure(YouTubeError.PRIVATE)

        assertEquals(RunOutcome.DONE, run())

        assertEquals(DownloadFailure.UNAVAILABLE, queue.row(FIRST).failure)
        assertFalse(files.folder("aaaaaaaaaaa-folder").exists())
    }

    @Test
    fun `a video the phone can't decode fails`() = runTest {
        waiting(FIRST)
        videoItags = emptyList()

        run()

        assertEquals(DownloadFailure.UNAVAILABLE, queue.row(FIRST).failure)
        assertNull(queue.row(FIRST).chosen)
    }

    @Test
    fun `a full phone fails before writing, keeping the bytes`() = runTest {
        waiting(FIRST)
        free = 1_000

        assertEquals(RunOutcome.DONE, run())

        assertEquals(DownloadFailure.NO_SPACE, queue.row(FIRST).failure)
        assertTrue(queue.row(FIRST).chosen != null)
    }

    @Test
    fun `a server error waits like a lost connection`() = runTest {
        waiting(FIRST)
        routed = media(1, video = FakeMedia(video) { _, chain -> status(chain, 503) })

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(0, queue.row(FIRST).youtubeTries)
    }

    @Test
    fun `another unexpected answer counts as YouTube's trouble`() = runTest {
        waiting(FIRST)
        routed = media(1, video = FakeMedia(video) { _, chain -> status(chain, 404) })

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(1, queue.row(FIRST).youtubeTries)
    }

    @Test
    fun `a refused media request counts as YouTube's trouble`() = runTest {
        waiting(FIRST)
        routed =
            media(
                1,
                video =
                    FakeMedia(video) { _, _ ->
                        throw MediaRequestRefused(MediaRequestRefused.Reason.HOST_NOT_ALLOWED)
                    },
            )

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(1, queue.row(FIRST).youtubeTries)
    }

    @Test
    fun `an expired link is fetched again and the file goes on`() = runTest {
        waiting(FIRST)
        val first = FakeMedia(video) { n, chain -> if (n == 1) status(chain, 403) else null }
        val second = FakeMedia(video)
        routed =
            RoutedMedia(
                mapOf(
                    link(136, 1) to first,
                    link(251, 1) to FakeMedia(audio),
                    link(136, 2) to second,
                    link(251, 2) to FakeMedia(audio),
                ),
            )
        val refreshed = StreamLinksRepository { id ->
            linkCalls++
            version = if (linkCalls == 1) 1 else 2
            YouTubeResult.Success(links(id))
        }

        val runner = runner(links = refreshed)

        assertEquals(RunOutcome.DONE, runner.run {})

        assertEquals(2, linkCalls)
        assertEquals("bytes=102400-204799", second.ranges.first())
        assertContentEquals(video, complete(FIRST, DownloadFileKind.VIDEO, "video/mp4").readBytes())
    }

    @Test
    fun `a second refusal with no new bytes counts as YouTube's trouble`() = runTest {
        waiting(FIRST)
        routed =
            media(
                1,
                2,
                video = FakeMedia(video) { n, chain -> if (n >= 1) status(chain, 410) else null },
            )

        assertEquals(RunOutcome.RETRY, run())

        assertEquals(1, queue.row(FIRST).youtubeTries)
        assertEquals(100L * 1024, part(FIRST, DownloadFileKind.VIDEO).length())
    }

    @Test
    fun `new links without the chosen format start the files again`() = runTest {
        val chosen = ChosenFormats(ChosenFormat(999, "video/mp4", 10), null)
        queue.add(FakeRow(FIRST, "aaaaaaaaaaa-folder", chosen = chosen, downloadedBytes = 5))
        files
            .part("aaaaaaaaaaa-folder", DownloadFileKind.MEDIA)
            .apply { parentFile?.mkdirs() }
            .writeBytes(ByteArray(5))

        run()

        assertEquals(QueueState.DOWNLOADED, queue.row(FIRST).state)
        assertEquals(136, queue.row(FIRST).chosen?.video?.itag)
        assertFalse(files.part("aaaaaaaaaaa-folder", DownloadFileKind.MEDIA).exists())
    }

    @Test
    fun `a download added while the run goes is taken too`() = runTest {
        waiting(FIRST)

        val outcome = runner().run { if (it.downloadedBytes == it.totalBytes) waitingOnce() }

        assertEquals(RunOutcome.DONE, outcome)
        assertEquals(QueueState.DOWNLOADED, queue.row(SECOND).state)
    }

    private var addedSecond = false

    private fun waitingOnce() {
        if (!addedSecond) {
            addedSecond = true
            waiting(SECOND)
        }
    }

    @Test
    fun `progress is written at the end of each range when no time passes`() = runTest {
        waiting(FIRST)

        run()

        // 3 ranges of the video file, 1 of the audio file.
        assertEquals(4, queue.progressWrites)
    }

    @Test
    fun `log lines name no video, link or file`() = runTest {
        waiting(FIRST)
        linkAnswers += YouTubeResult.Failure(YouTubeError.PRIVATE)

        run()

        assertTrue(lines.isNotEmpty())
        for (line in lines) {
            assertFalse(FIRST.value in line, line)
            assertFalse("googlevideo" in line, line)
            assertFalse("folder" in line, line)
        }
    }

    @Test
    fun `YouTube blocking three times in a row fails, keeping the bytes`() = runTest {
        waiting(FIRST)
        part(FIRST, DownloadFileKind.VIDEO)
            .apply { parentFile?.mkdirs() }
            .writeBytes(video.copyOfRange(0, 10))

        for (tries in 1..2) {
            linkAnswers +=
                YouTubeResult.Failure(if (tries == 1) YouTubeError.BLOCKED else YouTubeError.BROKEN)
            assertEquals(RunOutcome.RETRY, run())
            assertEquals(QueueState.WAITING, queue.row(FIRST).state)
            assertEquals(tries, queue.row(FIRST).youtubeTries)
        }
        linkAnswers += YouTubeResult.Failure(YouTubeError.BLOCKED)
        assertEquals(RunOutcome.DONE, run())

        assertEquals(QueueState.FAILED, queue.row(FIRST).state)
        assertEquals(DownloadFailure.YOUTUBE, queue.row(FIRST).failure)
        assertEquals(10, part(FIRST, DownloadFileKind.VIDEO).length())
    }
}
