package io.github.dewfallapp.feature.youtubedebug.ui

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.model.PublishedTime
import io.github.dewfallapp.core.model.VideoKind
import io.github.dewfallapp.feature.youtubedebug.logic.AudioFormatLine
import io.github.dewfallapp.feature.youtubedebug.logic.VideoFormatLine
import kotlin.time.Duration

// Turns the summaries' values into the debug screen's text. Times are ISO 8601 in UTC and lengths
// are Kotlin's Duration text, such as 10m 35s, because the screen checks values, not how they read.

/** [value]'s text, or "unknown" when YouTube didn't give it. */
@Composable
internal fun textOrUnknown(value: Any?): String =
    value?.toString() ?: stringResource(R.string.youtube_debug_unknown)

@Composable
internal fun yesNoText(value: Boolean): String =
    stringResource(if (value) R.string.youtube_debug_yes else R.string.youtube_debug_no)

@Composable internal fun durationText(duration: Duration?): String = textOrUnknown(duration)

@Composable
internal fun publishedText(published: PublishedTime?): String =
    when {
        published == null -> stringResource(R.string.youtube_debug_unknown)
        published.isApproximate -> {
            stringResource(R.string.youtube_debug_time_approximate, published.instant.toString())
        }
        else -> published.instant.toString()
    }

/** Counts of each video kind, such as "VIDEO: 18, SHORT: 2, LIVE: 0, and UPCOMING: 0". */
@Composable
internal fun kindCountsText(counts: Map<VideoKind, Int>): String =
    countsText(counts.mapKeys { (kind, _) -> kind.name })

/** Counts with their names, joined as a list in the phone's language. */
@Composable
internal fun countsText(counts: Map<String, Int>): String {
    val entries = mutableListOf<String>()
    for ((name, count) in counts) {
        entries += stringResource(R.string.youtube_debug_count, name, count)
    }
    return ListFormatter.getInstance().format(entries)
}

@Composable
internal fun audioFormatText(format: AudioFormatLine): String {
    val trackKind = format.trackKind
    val track =
        if (trackKind == null) {
            stringResource(R.string.youtube_debug_only_track)
        } else {
            stringResource(
                R.string.youtube_debug_track,
                trackKind.name,
                textOrUnknown(format.trackLanguageTag),
            )
        }
    return stringResource(
        R.string.youtube_debug_audio_format,
        format.itag,
        format.mimeType,
        textOrUnknown(format.codec),
        textOrUnknown(format.bitrate),
        textOrUnknown(format.contentLength),
        track,
    )
}

@Composable
internal fun videoFormatText(format: VideoFormatLine): String =
    stringResource(
        R.string.youtube_debug_video_format,
        format.itag,
        format.mimeType,
        textOrUnknown(format.codec),
        textOrUnknown(format.width),
        textOrUnknown(format.height),
        textOrUnknown(format.frameRate),
        textOrUnknown(format.bitrate),
        textOrUnknown(format.contentLength),
        yesNoText(format.includesAudio),
    )
