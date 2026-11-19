package io.github.dewfallapp.feature.youtubedebug.logic

import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.ChannelUploads
import io.github.dewfallapp.core.model.SearchResults
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.mvi.Next
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus.Cancelled
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus.Failed
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus.Idle
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus.InvalidInput
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus.Running
import io.github.dewfallapp.feature.youtubedebug.logic.CallStatus.Succeeded
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.Cancel
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.InputChanged
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.LatestUploadsFinished
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.Run
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.SearchFinished
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.StreamLinksFinished
import io.github.dewfallapp.feature.youtubedebug.logic.YouTubeDebugIntent.VideoDetailsFinished
import kotlin.test.Test
import kotlin.test.assertEquals

/** A reducer is a plain function, so its tests need no coroutines and no ViewModel. */
class YouTubeDebugReducerTest {
    private val emptySearch = SearchResults(channels = emptyList(), videos = emptyList())

    private fun reduce(state: YouTubeDebugState, intent: YouTubeDebugIntent): YouTubeDebugState {
        val next = YouTubeDebugReducer.reduce(state, intent)
        // The screen has no effects, so every intent returns only a state.
        assertEquals(Next(next.state), next)
        return next.state
    }

    private fun YouTubeDebugState.after(vararg intents: YouTubeDebugIntent): YouTubeDebugState =
        intents.fold(this) { state, intent -> reduce(state, intent) }

    @Test
    fun `the inputs start with the saved sets and nothing runs`() {
        val state = YouTubeDebugState()

        assertEquals(Section<String, SearchSummary>("nasa"), state.search)
        assertEquals(Section<VideoId, VideoDetailsSummary>("aqz-KE-bpKQ"), state.videoDetails)
        assertEquals(
            Section<ChannelId, UploadsSummary>("UCLA_DiR1FfKNvjuUpBHmylQ"),
            state.latestUploads,
        )
        assertEquals(Section<VideoId, StreamLinksSummary>("aqz-KE-bpKQ"), state.streamLinks)
    }

    @Test
    fun `an input change touches only its own section`() {
        val running = YouTubeDebugState().after(Run(DebugCall.STREAM_LINKS))

        val state = running.after(InputChanged(DebugCall.STREAM_LINKS, "edited"))

        assertEquals(
            running.copy(streamLinks = Section("edited", Running(VideoId("aqz-KE-bpKQ")))),
            state,
        )
    }

    @Test
    fun `run search sends the text as typed`() {
        val state =
            YouTubeDebugState()
                .after(InputChanged(DebugCall.SEARCH, " two words "), Run(DebugCall.SEARCH))

        assertEquals(Running(" two words "), state.search.status)
    }

    @Test
    fun `run search sends blank text too`() {
        val state =
            YouTubeDebugState().after(InputChanged(DebugCall.SEARCH, ""), Run(DebugCall.SEARCH))

        assertEquals(Running(""), state.search.status)
    }

    @Test
    fun `run video details and stream links send the trimmed video ID`() {
        val state =
            YouTubeDebugState()
                .after(
                    InputChanged(DebugCall.VIDEO_DETAILS, " aqz-KE-bpKQ\n"),
                    InputChanged(DebugCall.STREAM_LINKS, "\taqz-KE-bpKQ "),
                    Run(DebugCall.VIDEO_DETAILS),
                    Run(DebugCall.STREAM_LINKS),
                )

        assertEquals(Running(VideoId("aqz-KE-bpKQ")), state.videoDetails.status)
        assertEquals(Running(VideoId("aqz-KE-bpKQ")), state.streamLinks.status)
    }

    @Test
    fun `run latest uploads sends the trimmed channel ID`() {
        val state =
            YouTubeDebugState()
                .after(
                    InputChanged(DebugCall.LATEST_UPLOADS, " UCLA_DiR1FfKNvjuUpBHmylQ "),
                    Run(DebugCall.LATEST_UPLOADS),
                )

        assertEquals(Running(ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ")), state.latestUploads.status)
    }

    @Test
    fun `run with text that isn't a video ID shows invalid input`() {
        val notVideoIds =
            listOf("", "aqz-KE-bpK", "aqz-KE-bpKQQ", "aqz KE bpKQ", "https://youtu.be/aqz-KE-bpKQ")

        for (text in notVideoIds) {
            val state =
                YouTubeDebugState()
                    .after(
                        InputChanged(DebugCall.VIDEO_DETAILS, text),
                        InputChanged(DebugCall.STREAM_LINKS, text),
                        Run(DebugCall.VIDEO_DETAILS),
                        Run(DebugCall.STREAM_LINKS),
                    )

            assertEquals(InvalidInput, state.videoDetails.status, text)
            assertEquals(InvalidInput, state.streamLinks.status, text)
        }
    }

    @Test
    fun `run with text that isn't a channel ID shows invalid input`() {
        val notChannelIds =
            listOf("", "@NASA", "nasa", "XXLA_DiR1FfKNvjuUpBHmylQ", "UCLA_DiR1FfKNvjuUpBHmyl")

        for (text in notChannelIds) {
            val state =
                YouTubeDebugState()
                    .after(
                        InputChanged(DebugCall.LATEST_UPLOADS, text),
                        Run(DebugCall.LATEST_UPLOADS),
                    )

            assertEquals(InvalidInput, state.latestUploads.status, text)
        }
    }

    @Test
    fun `run while running starts again with the input as it is now`() {
        val state =
            YouTubeDebugState()
                .after(
                    Run(DebugCall.SEARCH),
                    InputChanged(DebugCall.SEARCH, "blender"),
                    Run(DebugCall.SEARCH),
                )

        assertEquals(Running("blender"), state.search.status)
    }

    @Test
    fun `run while running with invalid input stops the call and shows invalid input`() {
        val state =
            YouTubeDebugState()
                .after(
                    Run(DebugCall.VIDEO_DETAILS),
                    InputChanged(DebugCall.VIDEO_DETAILS, "nope"),
                    Run(DebugCall.VIDEO_DETAILS),
                )

        assertEquals(InvalidInput, state.videoDetails.status)
    }

    @Test
    fun `cancel stops a running call`() {
        for (call in DebugCall.entries) {
            val state = YouTubeDebugState().after(Run(call), Cancel(call))

            assertEquals(Cancelled, state.statusOf(call), call.name)
        }
    }

    @Test
    fun `cancel changes nothing when the call isn't running`() {
        val idle = YouTubeDebugState()
        val invalid =
            idle.after(InputChanged(DebugCall.VIDEO_DETAILS, "nope"), Run(DebugCall.VIDEO_DETAILS))
        val succeeded =
            idle.after(
                Run(DebugCall.SEARCH),
                SearchFinished("nasa", YouTubeResult.Success(emptySearch)),
            )
        val failed =
            idle.after(
                Run(DebugCall.SEARCH),
                SearchFinished("nasa", YouTubeResult.Failure(YouTubeError.NETWORK)),
            )
        val cancelled = idle.after(Run(DebugCall.SEARCH), Cancel(DebugCall.SEARCH))

        for (state in listOf(idle, invalid, succeeded, failed, cancelled)) {
            for (call in DebugCall.entries) {
                assertEquals(state, state.after(Cancel(call)))
            }
        }
    }

    @Test
    fun `cancel touches only its own section`() {
        val running =
            YouTubeDebugState()
                .after(
                    Run(DebugCall.SEARCH),
                    Run(DebugCall.VIDEO_DETAILS),
                    Run(DebugCall.LATEST_UPLOADS),
                    Run(DebugCall.STREAM_LINKS),
                )

        val state = running.after(Cancel(DebugCall.SEARCH))

        assertEquals(running.copy(search = running.search.copy(status = Cancelled)), state)
    }

    @Test
    fun `the answer to the running request shows its summary`() {
        val results = SearchResults(channels = listOf(channel("NASA")), videos = listOf(video()))

        val state =
            YouTubeDebugState()
                .after(
                    Run(DebugCall.SEARCH),
                    SearchFinished("nasa", YouTubeResult.Success(results)),
                )

        assertEquals(Succeeded(results.toSummary()), state.search.status)
    }

    @Test
    fun `each section shows its own answer`() {
        val uploads = ChannelUploads(channel = channel(), videos = listOf(video()))
        val links = streamLinks()

        val state =
            YouTubeDebugState()
                .after(
                    InputChanged(DebugCall.LATEST_UPLOADS, TEST_CHANNEL_ID.value),
                    InputChanged(DebugCall.STREAM_LINKS, TEST_VIDEO_ID.value),
                    Run(DebugCall.LATEST_UPLOADS),
                    Run(DebugCall.STREAM_LINKS),
                    LatestUploadsFinished(TEST_CHANNEL_ID, YouTubeResult.Success(uploads)),
                    StreamLinksFinished(TEST_VIDEO_ID, YouTubeResult.Success(links)),
                )

        assertEquals(
            Succeeded(uploads.toSummary(askedFor = TEST_CHANNEL_ID)),
            state.latestUploads.status,
        )
        assertEquals(Succeeded(links.toSummary()), state.streamLinks.status)
        assertEquals(Idle, state.search.status)
        assertEquals(Idle, state.videoDetails.status)
    }

    @Test
    fun `a failure shows its category`() {
        for (error in YouTubeError.entries) {
            val state =
                YouTubeDebugState()
                    .after(
                        Run(DebugCall.VIDEO_DETAILS),
                        VideoDetailsFinished(VideoId("aqz-KE-bpKQ"), YouTubeResult.Failure(error)),
                    )

            assertEquals(Failed(error), state.videoDetails.status, error.name)
        }
    }

    @Test
    fun `an answer that arrives after a cancel is dropped`() {
        val cancelled = YouTubeDebugState().after(Run(DebugCall.SEARCH), Cancel(DebugCall.SEARCH))

        val state = cancelled.after(SearchFinished("nasa", YouTubeResult.Success(emptySearch)))

        assertEquals(cancelled, state)
    }

    @Test
    fun `an answer for a request that a new run replaced is dropped`() {
        val rerun =
            YouTubeDebugState()
                .after(
                    Run(DebugCall.STREAM_LINKS),
                    InputChanged(DebugCall.STREAM_LINKS, OTHER_VIDEO_ID.value),
                    Run(DebugCall.STREAM_LINKS),
                )

        val state =
            rerun.after(
                StreamLinksFinished(VideoId("aqz-KE-bpKQ"), YouTubeResult.Success(streamLinks()))
            )

        assertEquals(rerun, state)
        assertEquals(Running(OTHER_VIDEO_ID), state.streamLinks.status)
    }

    @Test
    fun `an answer when nothing runs is dropped`() {
        val idle = YouTubeDebugState()
        val answers =
            listOf(
                SearchFinished("nasa", YouTubeResult.Success(emptySearch)),
                VideoDetailsFinished(
                    VideoId("aqz-KE-bpKQ"),
                    YouTubeResult.Failure(YouTubeError.BROKEN),
                ),
                LatestUploadsFinished(
                    ChannelId("UCLA_DiR1FfKNvjuUpBHmylQ"),
                    YouTubeResult.Failure(YouTubeError.BLOCKED),
                ),
                StreamLinksFinished(VideoId("aqz-KE-bpKQ"), YouTubeResult.Success(streamLinks())),
            )

        for (answer in answers) {
            assertEquals(idle, idle.after(answer))
        }
    }

    @Test
    fun `a second answer to a call that already finished is dropped`() {
        val finished =
            YouTubeDebugState()
                .after(
                    Run(DebugCall.SEARCH),
                    SearchFinished("nasa", YouTubeResult.Success(emptySearch)),
                )

        val state =
            finished.after(SearchFinished("nasa", YouTubeResult.Failure(YouTubeError.NETWORK)))

        assertEquals(finished, state)
    }

    private fun streamLinks(): StreamLinks =
        StreamLinks(
            videoId = TEST_VIDEO_ID,
            audioFormats = listOf(audioFormat()),
            videoFormats = listOf(videoFormat()),
            hlsManifestUrl = null,
            expiresAt = null,
        )

    private fun YouTubeDebugState.statusOf(call: DebugCall): CallStatus<*, *> =
        when (call) {
            DebugCall.SEARCH -> search.status
            DebugCall.VIDEO_DETAILS -> videoDetails.status
            DebugCall.LATEST_UPLOADS -> latestUploads.status
            DebugCall.STREAM_LINKS -> streamLinks.status
            DebugCall.SUBTITLE_TRACKS -> subtitleTracks.status
        }
}
