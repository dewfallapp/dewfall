package io.github.dewfallapp.core.download

import io.github.dewfallapp.core.data.download.ChosenFormat
import io.github.dewfallapp.core.data.download.ChosenFormats
import io.github.dewfallapp.core.data.download.DownloadFileKind
import io.github.dewfallapp.core.data.download.DownloadFiles
import io.github.dewfallapp.core.data.download.DownloadQueue
import io.github.dewfallapp.core.data.download.QueuedDownload
import io.github.dewfallapp.core.data.media.ProgressiveMediaCalls
import io.github.dewfallapp.core.data.playback.StreamLinksRepository
import io.github.dewfallapp.core.logging.LogField
import io.github.dewfallapp.core.logging.Logger
import io.github.dewfallapp.core.model.DownloadFailure
import io.github.dewfallapp.core.model.DownloadProgress
import io.github.dewfallapp.core.model.FormatChoice
import io.github.dewfallapp.core.model.MediaRequestRefused
import io.github.dewfallapp.core.model.StreamLinks
import io.github.dewfallapp.core.model.YouTubeResult
import io.github.dewfallapp.core.model.chooseDownloadFormats
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import okhttp3.Call

/** The first HTTP status of a server error. */
private const val SERVER_ERROR = 500

/** The least free space a download of unknown size starts or goes on with. */
private const val MIN_FREE_BYTES = 1024L * 1024

/** How often, at most, progress is written between the ends of ranges. */
private val PROGRESS_EVERY = 1.seconds

/** One file of a download: what it holds, its format, and the link it comes from now. */
internal data class PlannedFile(
    val kind: DownloadFileKind,
    val format: ChosenFormat,
    val url: String,
)

/**
 * Downloads one video that the runner claimed: it fetches the links, chooses the formats or keeps
 * the chosen ones, writes each file with [FormatWriter] through the YouTube layer's media requests,
 * fetches new links when one expires, and handles every failure of the architecture's table through
 * [DownloadOutcomes]. See docs/architecture.md#downloads-and-cleanup.
 */
internal class VideoDownloader
@Inject
constructor(
    private val queue: DownloadQueue,
    private val streamLinks: StreamLinksRepository,
    private val files: DownloadFiles,
    @ProgressiveMediaCalls private val mediaCalls: Provider<Call.Factory>,
    private val decoding: FormatDecoding,
    private val freeSpace: FreeSpace,
    private val writer: FormatWriter,
    private val outcomes: DownloadOutcomes,
    logger: Logger,
    private val time: TimeSource,
) {
    private val log = logger.withTag("Downloads")

    /**
     * Downloads [item], and says what the run does next, whatever goes wrong. [bytes] records
     * whether new bytes arrived, which the runner also reads when a stop cancels the attempt.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun attempt(
        item: QueuedDownload,
        report: RunReport,
        bytes: NewBytes = NewBytes(),
    ): Step {
        return try {
            download(item, bytes, report)
        } catch (e: CancellationException) {
            throw e
        } catch (e: MediaRequestRefused) {
            // The YouTube layer refused to send a request, which only an update fixes.
            log.warn("Download request refused", LogField.state("reason", e.reason))
            outcomes.youTubeTrouble(bytes.of(item))
        } catch (e: UnexpectedAnswer) {
            log.warn("Download answer unexpected", LogField.count("status", e.status))
            // A server error passes, like a lost connection. Any other answer means YouTube
            // changed something, which counts as YouTube's trouble.
            if (e.status >= SERVER_ERROR) {
                outcomes.waitForNetwork(bytes.of(item), e)
            } else {
                outcomes.youTubeTrouble(bytes.of(item))
            }
        } catch (e: IOException) {
            if (freeSpace.usableBytes(files.root) < MIN_FREE_BYTES) {
                outcomes.failed(item, DownloadFailure.NO_SPACE, keepBytes = true)
            } else {
                outcomes.waitForNetwork(bytes.of(item), e)
            }
        } catch (e: Exception) {
            // Anything else, such as a database that throws, waits for the next run.
            log.warn("Download failed on the phone", error = e)
            queue.backToWaiting(item.videoId, bytes.of(item).youtubeTries)
            Step.RETRY
        }
    }

    private suspend fun download(item: QueuedDownload, bytes: NewBytes, report: RunReport): Step =
        when (val result = streamLinks.streamLinks(item.videoId)) {
            is YouTubeResult.Failure -> outcomes.youTubeFailure(bytes.of(item), result.error)
            is YouTubeResult.Success -> {
                val links = result.value
                val saved = item.chosen
                val savedPlan = saved?.let { planFor(links, it) }
                when {
                    saved != null && savedPlan != null ->
                        writeAll(item, saved, savedPlan, bytes, report)
                    // A pause came in between, which keeps the row and its files together.
                    saved != null && !queue.clearFormats(item.videoId) -> Step.NEXT
                    else -> {
                        // No formats chosen, or new links without them: the files start
                        // again, from an empty folder, so no old file passes for a new one.
                        if (!files.delete(item.folder)) {
                            throw IOException("A download's old files couldn't be deleted")
                        }
                        val fresh = item.copy(chosen = null, downloadedBytes = 0)
                        val chosen = chooseFrom(links)
                        if (chosen == null) {
                            outcomes.failed(bytes.of(fresh), DownloadFailure.UNAVAILABLE)
                        } else {
                            queue.setFormats(item.videoId, chosen)
                            val plan = checkNotNull(planFor(links, chosen))
                            writeAll(fresh, chosen, plan, bytes, report)
                        }
                    }
                }
            }
        }

    /**
     * Writes every file of [firstPlan], fetching new links once when one expires, and again only
     * after new bytes, and marks the download done.
     */
    private suspend fun writeAll(
        item: QueuedDownload,
        chosen: ChosenFormats,
        firstPlan: List<PlannedFile>,
        bytes: NewBytes,
        report: RunReport,
    ): Step {
        var plan = firstPlan
        var index = 0
        var refreshedAt: Long? = null
        var step: Step? = null
        while (step == null && index < plan.size) {
            if (outOfSpace(item, plan.drop(index))) {
                step = outcomes.failed(item, DownloadFailure.NO_SPACE, keepBytes = true)
            } else {
                try {
                    write(item, plan, plan[index], bytes, report)
                    index++
                } catch (_: LinkRefused) {
                    val onDisk = files.bytesOnDisk(item.folder, plan)
                    if (refreshedAt == onDisk) {
                        step = outcomes.youTubeTrouble(bytes.of(item))
                    } else {
                        refreshedAt = onDisk
                        val refreshed = refresh(item, chosen, bytes, report)
                        plan = refreshed.first ?: plan
                        step = refreshed.second
                    }
                } catch (e: IOException) {
                    // A write that failed while the storage can't hold the rest of the files
                    // fails for space. Any other error goes on to the attempt's handling.
                    val noSpace =
                        e !is UnexpectedAnswer &&
                            e !is MediaRequestRefused &&
                            freeSpace.usableBytes(files.root) <
                                files.needed(item.folder, plan.drop(index))
                    if (!noSpace) throw e
                    step = outcomes.failed(item, DownloadFailure.NO_SPACE, keepBytes = true)
                }
            }
        }
        return step ?: finish(item, plan, report)
    }

    /**
     * New links for [chosen]: the new plan, or the step that ends this download, when the links
     * failed or no longer have the chosen formats, after which the files start again.
     */
    private suspend fun refresh(
        item: QueuedDownload,
        chosen: ChosenFormats,
        bytes: NewBytes,
        report: RunReport,
    ): Pair<List<PlannedFile>?, Step?> =
        when (val result = streamLinks.streamLinks(item.videoId)) {
            is YouTubeResult.Failure ->
                null to outcomes.youTubeFailure(bytes.of(item), result.error)
            is YouTubeResult.Success -> {
                val plan = planFor(result.value, chosen)
                // Without the chosen formats, download() starts the files again.
                if (plan != null) plan to null else null to download(item, bytes, report)
            }
        }

    /** Writes [file] to its part, then renames it, unless it is already complete. */
    private suspend fun write(
        item: QueuedDownload,
        plan: List<PlannedFile>,
        file: PlannedFile,
        bytes: NewBytes,
        report: RunReport,
    ) {
        val complete = files.complete(item.folder, file.kind, file.format.mimeType)
        if (complete.exists()) return
        val part = files.part(item.folder, file.kind)
        val before = plan.takeWhile { it != file }.sumOf { files.bytesOf(item.folder, it) }
        val total = totalOf(plan)
        val start = if (part.exists()) part.length() else 0L
        var lastWrite = time.markNow()
        writer.write(file.url, part, file.format.bytes, mediaCalls.get()) { written, rangeEnded ->
            if (written > start) bytes.arrived = true
            if (rangeEnded || lastWrite.elapsedNow() >= PROGRESS_EVERY) {
                lastWrite = time.markNow()
                val progress = DownloadProgress(before + written, total)
                queue.setProgress(item.videoId, progress.downloadedBytes)
                report.progress(progress)
            }
        }
        if (!part.renameTo(complete)) throw IOException("A download's file couldn't be renamed")
    }

    private suspend fun finish(
        item: QueuedDownload,
        plan: List<PlannedFile>,
        report: RunReport,
    ): Step {
        val bytes = files.bytesOnDisk(item.folder, plan)
        queue.finish(item.videoId, bytes)
        report.progress(DownloadProgress(bytes, bytes))
        log.info("Download finished")
        return Step.NEXT
    }

    private fun chooseFrom(links: StreamLinks): ChosenFormats? =
        chooseDownloadFormats(links, decoding::videoDecoding, decoding::audioDecodes).toChosen()

    /**
     * Whether the storage can't hold what is still to come of [plan], even after Android cleared
     * the cache it may clear for the app.
     */
    private fun outOfSpace(item: QueuedDownload, plan: List<PlannedFile>): Boolean =
        !freeSpace.makeRoom(files.root, files.needed(item.folder, plan))
}

/** The bytes still to come of [plan] for the download in [folder], and at least a megabyte. */
private fun DownloadFiles.needed(folder: String, plan: List<PlannedFile>): Long {
    val remaining = plan.sumOf { file ->
        val size = file.format.bytes ?: MIN_FREE_BYTES
        (size - bytesOf(folder, file)).coerceAtLeast(0)
    }
    return maxOf(remaining, MIN_FREE_BYTES)
}

/** The bytes of [plan]'s files on the phone, complete or not. */
private fun DownloadFiles.bytesOnDisk(folder: String, plan: List<PlannedFile>): Long = plan.sumOf {
    bytesOf(folder, it)
}

/** The bytes of [file] on the phone, complete or not. */
private fun DownloadFiles.bytesOf(folder: String, file: PlannedFile): Long {
    val complete = complete(folder, file.kind, file.format.mimeType)
    val part = part(folder, file.kind)
    return when {
        complete.exists() -> complete.length()
        part.exists() -> part.length()
        else -> 0
    }
}

/** The formats a download keeps for this choice, or null when it keeps none. */
private fun FormatChoice.toChosen(): ChosenFormats? =
    when (this) {
        is FormatChoice.Separate -> {
            ChosenFormats(
                ChosenFormat(video.itag, video.mimeType, video.contentLength),
                ChosenFormat(audio.itag, audio.mimeType, audio.contentLength, audio.track?.id),
            )
        }
        is FormatChoice.Combined -> {
            ChosenFormats(ChosenFormat(video.itag, video.mimeType, video.contentLength), null)
        }
        is FormatChoice.Live,
        FormatChoice.NotDecodable -> null
    }

/** The files to write for [chosen], from [links], or null when the links lack a chosen format. */
internal fun planFor(links: StreamLinks, chosen: ChosenFormats): List<PlannedFile>? {
    val video = links.videoFormats.firstOrNull { it.itag == chosen.video.itag }
    val audio = chosen.audio
    // Formats of different sound tracks share an itag, so the track finds the chosen one.
    val audioLink = audio?.let { wanted ->
        links.audioFormats.firstOrNull { it.itag == wanted.itag && it.track?.id == wanted.track }
    }
    return when {
        video == null -> null
        audio == null -> listOf(PlannedFile(DownloadFileKind.MEDIA, chosen.video, video.url))
        audioLink == null -> null
        else -> {
            listOf(
                PlannedFile(DownloadFileKind.VIDEO, chosen.video, video.url),
                PlannedFile(DownloadFileKind.AUDIO, audio, audioLink.url),
            )
        }
    }
}

/** The size of every file in [plan], or null if one is unknown. */
private fun totalOf(plan: List<PlannedFile>): Long? =
    if (plan.all { it.format.bytes != null }) plan.sumOf { it.format.bytes ?: 0 } else null

/**
 * Whether new bytes of a download arrived in this attempt. A links call that YouTube answers
 * doesn't show that its files can be read, so only new bytes start the tries in a row that YouTube
 * blocked or broke again from 0.
 */
internal class NewBytes {
    /** Written by the attempt and read by the runner after a stop, on other threads. */
    @Volatile var arrived = false

    /** [item], with its tries from 0 when new bytes arrived. */
    fun of(item: QueuedDownload): QueuedDownload =
        if (arrived) item.copy(youtubeTries = 0) else item
}
