package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.VideoId
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl

/** The domain of the links that play or download a video. */
private const val GOOGLEVIDEO_DOMAIN = ".googlevideo.com"

/**
 * Tests the links that play a video, from the same saved set as video details, because both read
 * the same page. A test can't play anything, so "playable" here means what a player needs: a sound
 * format and a picture format that play together, and a format that has both, each with a known
 * length, and the HLS manifest, all HTTPS links to googlevideo.com. The layer keeps a format only
 * when it is a single file with a direct link, and no link needed YouTube's player code to decode,
 * which would have added a request. Fetching real bytes from a link is left to the daily check.
 */
class StreamLinksReplayTest {
    private val lines = mutableListOf<String>()

    @Test
    fun `a video has a sound format and a picture format that play together`() = runTest {
        val links = streamLinks()

        val sound = links.audioFormats.first { it.mimeType == "audio/mp4" }
        val picture = links.videoFormats.first { !it.includesAudio && it.mimeType == "video/mp4" }
        assertGoogleVideoLink(sound.url)
        assertGoogleVideoLink(picture.url)
        assertNotNull(sound.contentLength)
        assertNotNull(picture.contentLength)
        assertNotNull(picture.width)
        assertNotNull(picture.height)
        assertEquals(VideoId(BIG_BUCK_BUNNY_ID), links.videoId)
        assertEquals(emptyList(), lines)
    }

    @Test
    fun `a video has a format with sound and picture, and an HLS manifest`() = runTest {
        val links = streamLinks()

        val both = links.videoFormats.first { it.includesAudio }
        assertGoogleVideoLink(both.url)
        assertGoogleVideoLink(assertNotNull(links.hlsManifestUrl))
    }

    @Test
    fun `the links expire at the earliest time that any of them names`() = runTest {
        val links = streamLinks()

        val formatExpiries =
            (links.audioFormats.map { it.url } + links.videoFormats.map { it.url }).map {
                assertNotNull(it.toHttpUrl().queryParameter("expire")).toLong()
            }
        val hlsSegments = assertNotNull(links.hlsManifestUrl).toHttpUrl().pathSegments
        val hlsExpiry = hlsSegments[hlsSegments.indexOf("expire") + 1].toLong()
        assertEquals(
            Instant.fromEpochSeconds((formatExpiries + hlsExpiry).min()),
            links.expiresAt,
        )
    }

    @Test
    fun `stream links send the requests of video details, and no link needs the player code`() =
        runTest {
            val youTube = savedYouTube()

            youTube.call { streamLinks(VideoId(BIG_BUCK_BUNNY_ID)) }

            // A link that needed decoding would add a request for YouTube's player code.
            assertEquals(SavedSet.VIDEO_BIG_BUCK_BUNNY.requests, youTube.sentRequests)
            assertEquals(7, youTube.countedRequests)
        }

    private suspend fun TestScope.streamLinks(): StreamLinks =
        savedYouTube().call { streamLinks(VideoId(BIG_BUCK_BUNNY_ID)) }.valueOrFail()

    private fun TestScope.savedYouTube() =
        SavedYouTube(
            SavedSet.VIDEO_BIG_BUCK_BUNNY,
            recordingLogger(lines),
            StandardTestDispatcher(testScheduler),
        )

    private fun assertGoogleVideoLink(link: String) {
        val url = link.toHttpUrl()
        assertTrue(url.isHttps, link)
        assertTrue(url.host.endsWith(GOOGLEVIDEO_DOMAIN), link)
    }
}
