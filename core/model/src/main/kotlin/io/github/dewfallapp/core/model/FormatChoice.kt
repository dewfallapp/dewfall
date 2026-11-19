package io.github.dewfallapp.core.model

/** The largest shorter side of the picture on a metered network, such as mobile data. */
const val METERED_LIMIT = 480

/** The largest shorter side of the picture on an unmetered network, such as Wi-Fi. */
const val UNMETERED_LIMIT = 1080

/** How the phone decodes a format, which the player finds out from Android's decoders. */
enum class Decoding {
    HARDWARE,
    SOFTWARE,
    NONE,
}

/** What the player plays for a video, as [chooseFormats] picks it. */
sealed interface FormatChoice {
    /** One video format and one audio format, played together. */
    data class Separate(val video: VideoFormat, val audio: AudioFormat) : FormatChoice

    /** One format with both sound and picture. */
    data class Combined(val video: VideoFormat) : FormatChoice

    /** The HLS manifest of a stream with no formats, which counts as live. */
    data class Live(val manifestUrl: String) : FormatChoice

    /** The phone decodes no video format, or no audio format. */
    data object NotDecodable : FormatChoice
}

/**
 * The largest shorter side of the picture that playback starts with, by whether the network is
 * [metered]. The shorter side keeps portrait videos at the same quality as landscape ones.
 */
fun pictureLimit(metered: Boolean): Int = if (metered) METERED_LIMIT else UNMETERED_LIMIT

/**
 * The formats to play from [links], by whether the network is [metered]:
 * - Links with no formats and an HLS manifest count as live and play the manifest.
 * - Video: within the limit of [pictureLimit], formats decoded in hardware come first, then the
 *   largest shorter side, then the lowest bitrate. With none within the limit, the decodable one
 *   with the smallest shorter side.
 * - Audio: the original track's formats, or all of them when no format has a track, or else the
 *   first track in YouTube's order. The lowest bitrate on a metered network, the highest otherwise.
 *
 * [videoDecoding] and [audioDecodes] say how the phone decodes a format. A format with no codec
 * counts as not decodable. See docs/architecture.md#the-player.
 */
fun chooseFormats(
    links: StreamLinks,
    metered: Boolean,
    videoDecoding: (VideoFormat) -> Decoding,
    audioDecodes: (AudioFormat) -> Boolean,
): FormatChoice {
    val manifest = links.hlsManifestUrl
    if (links.videoFormats.isEmpty() && links.audioFormats.isEmpty() && manifest != null) {
        return FormatChoice.Live(manifest)
    }
    val video = chooseVideo(links.videoFormats, pictureLimit(metered), videoDecoding)
    val needsAudio = video != null && !video.includesAudio
    val audio = if (needsAudio) chooseAudio(links.audioFormats, metered, audioDecodes) else null
    return when {
        video == null -> FormatChoice.NotDecodable
        video.includesAudio -> FormatChoice.Combined(video)
        audio == null -> FormatChoice.NotDecodable
        else -> FormatChoice.Separate(video, audio)
    }
}

/**
 * The indices of the live variants, given as their [sizes] in pixels, that playback may adapt
 * among: those whose shorter side is within [limit], or else the one with the smallest shorter
 * side. A variant with no size counts as larger than any limit.
 */
fun liveVariantsWithin(sizes: List<Pair<Int, Int>?>, limit: Int): List<Int> {
    val shorter = sizes.map { size -> size?.let { minOf(it.first, it.second) } }
    val within = shorter.indices.filter { index -> shorter[index]?.let { it <= limit } == true }
    if (within.isNotEmpty() || sizes.isEmpty()) return within
    return listOf(shorter.indices.minBy { shorter[it] ?: Int.MAX_VALUE })
}

private fun chooseVideo(
    formats: List<VideoFormat>,
    limit: Int,
    decoding: (VideoFormat) -> Decoding,
): VideoFormat? {
    val decodable = formats.mapNotNull { format ->
        val how = if (format.codec == null) Decoding.NONE else decoding(format)
        if (how == Decoding.NONE) null else format to how
    }
    val within = decodable.filter { (format, _) ->
        shorterSide(format)?.let { it <= limit } == true
    }
    return when {
        decodable.isEmpty() -> null
        within.isEmpty() -> {
            decodable.minBy { (format, _) -> shorterSide(format) ?: Int.MAX_VALUE }.first
        }
        else -> largestWithin(within)
    }
}

/**
 * Among [within], formats decoded in hardware first, then the largest shorter side, then the lowest
 * bitrate.
 */
private fun largestWithin(within: List<Pair<VideoFormat, Decoding>>): VideoFormat {
    val pool = within.filter { (_, how) -> how == Decoding.HARDWARE }.ifEmpty { within }
    val largest = pool.maxOf { (format, _) -> shorterSide(format) ?: 0 }
    return pool
        .filter { (format, _) -> shorterSide(format) == largest }
        .minBy { (format, _) -> format.bitrate ?: Int.MAX_VALUE }
        .first
}

private fun chooseAudio(
    formats: List<AudioFormat>,
    metered: Boolean,
    decodes: (AudioFormat) -> Boolean,
): AudioFormat? {
    val track = chooseTrack(formats)
    val candidates = formats.filter { format ->
        format.track?.id == track && format.codec != null && decodes(format)
    }
    if (candidates.isEmpty()) return null
    return if (metered) {
        candidates.minBy { it.bitrate ?: Int.MAX_VALUE }
    } else {
        candidates.maxBy { it.bitrate ?: 0 }
    }
}

/**
 * The ID of the audio track to play: the original one, or null when no format has a track, or else
 * the first track in YouTube's order.
 */
private fun chooseTrack(formats: List<AudioFormat>): String? {
    val tracks = formats.mapNotNull { it.track }
    if (tracks.isEmpty()) return null
    return (tracks.firstOrNull { it.kind == AudioTrackKind.ORIGINAL } ?: tracks.first()).id
}

private fun shorterSide(format: VideoFormat): Int? {
    val width = format.width
    val height = format.height
    return if (width == null || height == null) null else minOf(width, height)
}
