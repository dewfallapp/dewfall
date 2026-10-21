package io.github.dewfallapp.core.model

import kotlin.time.Instant

/**
 * The links that play or download one video. YouTube serves most of a video's sound and picture as
 * separate formats, so a player usually plays one audio format and one video format together. Every
 * format is a single file that HTTP GET requests with byte ranges can read.
 *
 * The links stop working after some time. Fetch new ones just before playing or downloading, and
 * again when a link stops working. Every URL is user data, so never log it. See ADR 0007.
 */
data class StreamLinks(
    val videoId: VideoId,
    /** The formats with sound only. */
    val audioFormats: List<AudioFormat>,
    /** The formats with picture, with or without sound. See [VideoFormat.includesAudio]. */
    val videoFormats: List<VideoFormat>,
    /**
     * An HLS manifest that plays the video with its sound, or null if YouTube gave none. A live
     * stream has no formats in the lists above, so this is the only way to play it.
     */
    val hlsManifestUrl: String?,
    /** When the links stop working, or null if YouTube didn't say. */
    val expiresAt: Instant?,
)

/** One format of a video's sound. */
data class AudioFormat(
    val url: String,
    /** YouTube's number for this format. The same itag is the same format of the same video. */
    val itag: Int,
    /** The container's MIME type, such as audio/mp4 or audio/webm. */
    val mimeType: String,
    /** The codec, such as opus or mp4a.40.2, or null if YouTube didn't say. */
    val codec: String?,
    /** The bitrate in bits per second. */
    val bitrate: Int,
    /** The file's size in bytes, or null if YouTube didn't say. */
    val contentLength: Long?,
    /** Which of the video's sound tracks this is, or null if the video has only one. */
    val track: AudioTrack?,
)

/** One format of a video's picture. */
data class VideoFormat(
    val url: String,
    /** YouTube's number for this format. The same itag is the same format of the same video. */
    val itag: Int,
    /** The container's MIME type, such as video/mp4 or video/webm. */
    val mimeType: String,
    /** The codec, such as avc1.640028 or vp9, or null if YouTube didn't say. */
    val codec: String?,
    /** The bitrate in bits per second. */
    val bitrate: Int,
    /** The file's size in bytes, or null if YouTube didn't say. */
    val contentLength: Long?,
    /** The width in pixels. */
    val width: Int,
    /** The height in pixels. */
    val height: Int,
    /** The frames per second. */
    val frameRate: Int,
    /** True if the file has the sound too, so it plays on its own. */
    val includesAudio: Boolean,
)

/** One of a video's sound tracks, for a video with more than one, such as dubbed languages. */
data class AudioTrack(
    /** YouTube's ID for the track. Formats of the same track share it. */
    val id: String,
    /** The track's name as YouTube shows it, or null if YouTube gave none. */
    val name: String?,
    /** The track's language as a BCP 47 tag, such as en or pt-BR, or null if unknown. */
    val languageTag: String?,
    val kind: AudioTrackKind,
)

/** What a sound track holds. */
enum class AudioTrackKind {
    /** The video's own sound. */
    ORIGINAL,

    /** A translation of the sound into another language. */
    DUBBED,

    /** The sound with a spoken description of what the picture shows. */
    DESCRIPTIVE,

    /** Any other track. */
    OTHER,
}
