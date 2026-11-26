package io.github.dewfallapp.core.data.download

import java.io.File

/** What a download's file holds. Its name is the file's name without the extension. */
enum class DownloadFileKind(internal val fileName: String) {
    /** The picture of a video whose sound is a file of its own. */
    VIDEO("video"),

    /** The sound of a video whose picture is a file of its own. */
    AUDIO("audio"),

    /** A format with both sound and picture. */
    MEDIA("media"),
}

/**
 * Where downloads keep their files: one folder per download under [root], which Hilt sets to
 * `noBackupFilesDir/downloads`, app-specific internal storage that no other app can read and that
 * Auto Backup never copies. Each folder's name is a random UUID, so no path holds a video ID. A
 * file being written ends in `.part`, and a complete one has its format's extension. #56 plays the
 * complete files, and #57 adds up their sizes. See docs/architecture.md#downloads-and-cleanup.
 *
 * Paths are user data, as file names are, so never log them.
 */
class DownloadFiles(val root: File) {
    /** The folder of the download named [folder]. */
    fun folder(folder: String): File = File(root, folder)

    /** The file that a [kind] of the download named [folder] is written to. */
    fun part(folder: String, kind: DownloadFileKind): File =
        File(folder(folder), kind.fileName + PART_SUFFIX)

    /** The complete file of a [kind] of the download named [folder], in [mimeType]. */
    fun complete(folder: String, kind: DownloadFileKind, mimeType: String): File =
        File(folder(folder), kind.fileName + "." + extensionOf(mimeType))

    /** Deletes the download named [folder] with its files. Returns whether nothing is left. */
    fun delete(folder: String): Boolean = folder(folder).deleteRecursively()

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}

/**
 * The file extension for a format's MIME type: mp4 for video/mp4, m4a for audio/mp4, webm for
 * either webm type, and bin for anything else, which no format of NewPipeExtractor v0.27.1 has.
 */
internal fun extensionOf(mimeType: String): String =
    when (mimeType.substringBefore(';').trim().lowercase()) {
        "video/mp4" -> "mp4"
        "audio/mp4" -> "m4a"
        "video/webm",
        "audio/webm" -> "webm"
        else -> "bin"
    }
