package io.github.dewfallapp.core.data.download

import io.github.dewfallapp.core.database.DownloadDao
import io.github.dewfallapp.core.database.DownloadEntity
import io.github.dewfallapp.core.database.DownloadState
import io.github.dewfallapp.core.model.VideoId
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.withContext

/**
 * Reads the video's row in `downloads`, then checks its files through [files] on [dispatcher]. A
 * read or a check that throws, such as on a damaged database, gives Unreadable, so the video
 * streams instead of failing.
 */
internal class RoomPlayableDownloads
@Inject
constructor(
    private val downloadDao: DownloadDao,
    private val files: DownloadFiles,
    @DownloadFilesDispatcher private val dispatcher: CoroutineDispatcher,
) : PlayableDownloads {
    override suspend fun playable(videoId: VideoId): PlayableDownload = flow {
        val row = downloadDao.row(videoId.value)
        emit(
            if (row == null) {
                PlayableDownload.NotDownloaded
            } else {
                withContext(dispatcher) { playableFiles(row, files) }
            },
        )
    }
        .catch { emit(PlayableDownload.Unreadable) }
        .single()
}

/**
 * Whether [row]'s files in [files] can play: the download is done, and every file it kept is a
 * regular file inside the downloads' folder, not empty, as long as the size the row recorded for
 * it, if any, and all of them as long as the bytes the row says are on the phone. It blocks on the
 * file system, and may throw an IOException or a SecurityException.
 */
internal fun playableFiles(row: DownloadEntity, files: DownloadFiles): PlayableDownload {
    val wanted = if (row.state == DownloadState.DOWNLOADED) wantedFiles(row, files) else null
    val problem = wanted?.let { problemWith(it, row.downloadedBytes, files.root.canonicalFile) }
    return when {
        row.state != DownloadState.DOWNLOADED -> PlayableDownload.NotDownloaded
        wanted == null -> missing
        problem != null -> PlayableDownload.Incomplete(problem)
        wanted.size == 1 -> PlayableDownload.Combined(wanted[0].file)
        else -> PlayableDownload.Separate(video = wanted[0].file, audio = wanted[1].file)
    }
}

/**
 * The files that [row]'s download kept, the video file first, or null when the row lacks a MIME
 * type that names one of them.
 */
private fun wantedFiles(row: DownloadEntity, files: DownloadFiles): List<Wanted>? {
    val videoMime = row.videoMime
    val audioMime = row.audioMime
    return when {
        videoMime == null -> null
        row.audioItag == null -> {
            listOf(
                Wanted(
                    files.complete(row.folder, DownloadFileKind.MEDIA, videoMime),
                    row.videoBytes,
                ),
            )
        }
        audioMime == null -> null
        else -> {
            listOf(
                Wanted(
                    files.complete(row.folder, DownloadFileKind.VIDEO, videoMime),
                    row.videoBytes,
                ),
                Wanted(
                    files.complete(row.folder, DownloadFileKind.AUDIO, audioMime),
                    row.audioBytes,
                ),
            )
        }
    }
}

/**
 * Why [wanted] can't play, or null when it can: a file outside [root] or not a regular file, a file
 * of the wrong size, or files that don't add up to [downloadedBytes].
 */
private fun problemWith(
    wanted: List<Wanted>,
    downloadedBytes: Long,
    root: File,
): IncompleteReason? =
    when {
        wanted.any { !it.file.isInside(root) || !it.file.isFile } -> IncompleteReason.MISSING
        wanted.any { it.hasWrongSize() } -> IncompleteReason.SIZE
        wanted.sumOf { it.file.length() } != downloadedBytes -> IncompleteReason.SIZE
        else -> null
    }

private val missing = PlayableDownload.Incomplete(IncompleteReason.MISSING)

/** A file a download kept, with the size the row recorded for it, or null if it recorded none. */
private class Wanted(val file: File, val bytes: Long?) {
    /** Whether the file is empty, or not as long as the size the row recorded for it. */
    fun hasWrongSize(): Boolean {
        val length = file.length()
        return length <= 0 || (bytes != null && length != bytes)
    }
}

/** Whether this file, with every link and `..` resolved, sits under [root], itself canonical. */
private fun File.isInside(root: File): Boolean =
    canonicalFile.path.startsWith(root.path + File.separator)
