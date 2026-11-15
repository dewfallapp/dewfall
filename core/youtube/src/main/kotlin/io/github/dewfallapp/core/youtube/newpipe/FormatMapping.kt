package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.AudioTrack
import io.github.dewfallapp.core.model.AudioTrackKind
import io.github.dewfallapp.core.model.VideoFormat
import kotlin.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Where a stream link says when it stops working, in seconds since 1970: a query parameter of a
 * format's link, and a path segment, followed by the value, of an HLS manifest's link.
 */
private const val EXPIRE = "expire"

/**
 * Whether this stream is a single file that HTTP range requests can read. A stream that needs a
 * manifest, such as an OTF format or any format of a live stream, isn't.
 */
internal fun Stream.isSingleFile(): Boolean =
    deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isUrl

/** This stream as an audio format, or null if it has no itag or no media type. */
internal fun AudioStream.toAudioFormatOrNull(): AudioFormat? {
    val itag: ItagItem? = itagItem
    val mimeType = format?.mimeType
    if (itag == null || mimeType == null) return null
    return AudioFormat(
        url = content,
        itag = itag.id,
        mimeType = mimeType,
        codec = codec?.ifEmpty { null },
        bitrate = itag.bitrate,
        contentLength = itag.knownContentLength(),
        track = audioTrack(),
    )
}

/** This stream as a video format, or null if it has no itag or no media type. */
internal fun VideoStream.toVideoFormatOrNull(): VideoFormat? {
    val itag: ItagItem? = itagItem
    val mimeType = format?.mimeType
    if (itag == null || mimeType == null) return null
    return VideoFormat(
        url = content,
        itag = itag.id,
        mimeType = mimeType,
        codec = codec?.ifEmpty { null },
        bitrate = itag.bitrate,
        contentLength = itag.knownContentLength(),
        width = width,
        height = height,
        frameRate = fps,
        // The method, not the public field of the same name, which NewPipeExtractor deprecates.
        includesAudio = !isVideoOnly(),
    )
}

/**
 * When [link] stops working, from its `expire` query parameter or, for an HLS manifest, its
 * `/expire/` path segment. Null if it has neither.
 */
internal fun expiryOf(link: String): Instant? {
    val url = link.toHttpUrlOrNull()
    val seconds =
        url?.queryParameter(EXPIRE)
            ?: url?.pathSegments?.zipWithNext()?.firstOrNull { it.first == EXPIRE }?.second
    return seconds?.toLongOrNull()?.let(Instant::fromEpochSeconds)
}

private fun ItagItem.knownContentLength(): Long? = contentLength.takeIf {
    it != ItagItem.CONTENT_LENGTH_UNKNOWN
}

/** The sound track that this stream holds, or null if the video has only one. */
private fun AudioStream.audioTrack(): AudioTrack? = audioTrackId?.let { id ->
    AudioTrack(
        id = id,
        name = audioTrackName,
        languageTag = audioLocale?.toLanguageTag(),
        kind = audioTrackType.toKind(),
    )
}

private fun AudioTrackType?.toKind(): AudioTrackKind =
    when (this) {
        AudioTrackType.ORIGINAL -> AudioTrackKind.ORIGINAL
        AudioTrackType.DUBBED -> AudioTrackKind.DUBBED
        AudioTrackType.DESCRIPTIVE -> AudioTrackKind.DESCRIPTIVE
        // An alternate track, in the original language or another one.
        AudioTrackType.SECONDARY,
        null -> AudioTrackKind.OTHER
    }
