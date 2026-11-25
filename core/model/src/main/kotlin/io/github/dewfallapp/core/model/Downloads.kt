package io.github.dewfallapp.core.model

/** The most a percentage of a download can be. */
private const val WHOLE = 100

/**
 * How far a download got: the [downloadedBytes] already on the phone, and the [totalBytes] of its
 * files, or null while the size is unknown, such as before the formats are chosen or when YouTube
 * gave no length. Sizes aren't user data, but the screens show them only as a percentage.
 */
data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long?) {
    /**
     * The percentage done, from 0 to 100, rounded down, so a download shows 100 only once every
     * byte is there. Null while the total is unknown or 0.
     */
    val percent: Int?
        get() {
            val total = totalBytes
            if (total == null || total <= 0) return null
            val done = downloadedBytes.coerceIn(0, total)
            return (done * WHOLE / total).toInt()
        }
}

/** Why a download stopped and won't go on by itself. */
enum class DownloadFailure {
    /**
     * YouTube says the video can't be watched, such as a private or removed one, it is a live
     * stream, or the phone decodes none of its formats. Its partial files are deleted.
     */
    UNAVAILABLE,

    /** YouTube couldn't be read for it three times in a row. Its partial files stay. */
    YOUTUBE,

    /** The phone's free storage can't hold the rest of its files. Its partial files stay. */
    NO_SPACE,
}

/**
 * Where a video's download stands, as the screens show it. See openspec/specs/downloads/spec.md.
 */
sealed interface DownloadStatus {
    /** The video has no download. */
    data object NotDownloaded : DownloadStatus

    /** The download waits for its turn or for a connection. */
    data class Waiting(val progress: DownloadProgress) : DownloadStatus

    /** The download runs. */
    data class Downloading(val progress: DownloadProgress) : DownloadStatus

    /** The user paused the download, or stopped the app while it ran. Its files stay. */
    data class Paused(val progress: DownloadProgress) : DownloadStatus

    /** The download stopped for [reason], and goes on only when the user tries again. */
    data class Failed(val reason: DownloadFailure) : DownloadStatus

    /** Every file is on the phone, [bytes] in all. */
    data class Downloaded(val bytes: Long) : DownloadStatus
}
