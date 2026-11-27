package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.model.VideoId
import java.io.File

/**
 * Finds the files that a video plays from when its download is done, so the player plays them
 * instead of streaming. See openspec/specs/player/spec.md and docs/architecture.md#the-player.
 *
 * Main-safe. It never changes a download. The files' paths are user data, so never log them.
 */
fun interface PlayableDownloads {
    /** Whether the video with [videoId] can play from its downloaded files, and which they are. */
    suspend fun playable(videoId: VideoId): PlayableDownload
}

/**
 * What [PlayableDownloads] found for a video. Every kind is a plain class or object whose text
 * shows no path, because paths are user data.
 */
sealed interface PlayableDownload {
    /** Every file of a finished download is on the phone, at the size the download recorded. */
    sealed interface Files : PlayableDownload

    /** A video file and an audio file, played together. */
    class Separate(val video: File, val audio: File) : Files {
        override fun equals(other: Any?): Boolean =
            other is Separate && video == other.video && audio == other.audio

        override fun hashCode(): Int = 31 * video.hashCode() + audio.hashCode()

        override fun toString(): String = "Separate"
    }

    /** One file with both sound and picture. */
    class Combined(val media: File) : Files {
        override fun equals(other: Any?): Boolean = other is Combined && media == other.media

        override fun hashCode(): Int = media.hashCode()

        override fun toString(): String = "Combined"
    }

    /** The video has no download, or its download isn't done, so its files are partial. */
    data object NotDownloaded : PlayableDownload

    /** The download is done, but its files on the phone can't play, for [reason]. */
    data class Incomplete(val reason: IncompleteReason) : PlayableDownload

    /** The database or the file system couldn't be read. */
    data object Unreadable : PlayableDownload
}

/** Why a finished download's files can't play. Constant text, so the log may name it. */
enum class IncompleteReason {
    /**
     * A file is gone, isn't a regular file, sits outside the downloads' folder, or the row lacks a
     * file's MIME type, so the file can't be named.
     */
    MISSING,

    /** A file is empty, or its length differs from what the download recorded. */
    SIZE,
}
