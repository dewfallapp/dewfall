package io.github.dewfallapp.core.youtube

import io.github.dewfallapp.core.youtube.newpipe.MediaInterceptor
import io.github.dewfallapp.core.youtube.newpipe.MediaRequestShape
import io.github.dewfallapp.core.youtube.newpipe.MediaUserAgents
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * Makes the HTTP clients that fetch a video's bytes from googlevideo.com for the player, so every
 * request to YouTube goes through the YouTube layer. Each client sends only HTTPS requests to
 * googlevideo.com and its subdomains, checks every redirect's host before following it, and fails
 * with MediaRequestRefused for anything it won't send. See docs/architecture.md#the-player.
 *
 * Unlike the layer's data source functions, its calls run on the caller's threads and throw
 * IOException, as any OkHttp Call does. See ADR 0008.
 */
class MediaRequests
internal constructor(
    private val client: OkHttpClient,
    private val userAgents: MediaUserAgents,
) {
    /**
     * A new client for one progressive format: each request is a POST with `x\0`, numbered with
     * `rn` from 0, with the User-Agent of the YouTube client that made the link. Make a new one for
     * each media source, as NewPipe's player numbers the requests of each data source.
     */
    fun progressive(): Call.Factory = withShape(MediaRequestShape.PROGRESSIVE)

    /** A client for a live stream's HLS manifest and segments: plain GET requests. */
    fun live(): Call.Factory = withShape(MediaRequestShape.LIVE)

    private fun withShape(shape: MediaRequestShape): Call.Factory =
        client.newBuilder().addInterceptor(MediaInterceptor(shape, userAgents)).build()
}
