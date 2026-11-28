package io.github.dewfallapp.core.download

import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dewfallapp.core.model.AudioFormat
import io.github.dewfallapp.core.model.Decoding
import io.github.dewfallapp.core.model.VideoFormat
import io.github.dewfallapp.core.playback.Decoders
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex

/** Marks the dispatcher that the download's file and network work runs on, Dispatchers.IO. */
@Qualifier @Retention(AnnotationRetention.BINARY) internal annotation class DownloadDispatcher

/**
 * Marks the dispatcher that the download job's service starts its run and finishes the job on,
 * Dispatchers.Main.immediate, as a JobService's callbacks come on the main thread.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) internal annotation class DownloadJobDispatcher

/** Says which formats the phone decodes, so a download keeps only formats the player can play. */
internal interface FormatDecoding {
    fun videoDecoding(format: VideoFormat): Decoding

    fun audioDecodes(format: AudioFormat): Boolean
}

/** The player's own check, from `:core:playback`. */
internal class PlayerFormatDecoding @Inject constructor(private val decoders: Decoders) :
    FormatDecoding {
    override fun videoDecoding(format: VideoFormat): Decoding = decoders.videoDecoding(format)

    override fun audioDecodes(format: AudioFormat): Boolean = decoders.audioDecodes(format)
}

/** The storage's room for a download. */
internal interface FreeSpace {
    /**
     * Makes room for [bytes] on the storage that holds [folder], clearing the cache that Android
     * may clear for the app if it must, and says whether the room is there.
     */
    fun makeRoom(folder: File, bytes: Long): Boolean

    /** The bytes free on the storage that holds [folder] now, clearing nothing. */
    fun usableBytes(folder: File): Long
}

/**
 * StorageManager's `getAllocatableBytes`, which counts the cache that Android can clear for the
 * app, then `allocateBytes`, which clears it, as Android's guide to querying free space advises. If
 * the storage can't be found, the folder's usable space.
 */
internal class StorageFreeSpace @Inject constructor(@ApplicationContext context: Context) :
    FreeSpace {
    private val storage = context.getSystemService(StorageManager::class.java)

    override fun makeRoom(folder: File, bytes: Long): Boolean {
        folder.mkdirs()
        return try {
            val uuid = storage.getUuidForPath(folder)
            if (storage.getAllocatableBytes(uuid) < bytes) {
                false
            } else {
                storage.allocateBytes(uuid, bytes)
                true
            }
        } catch (_: IOException) {
            folder.usableSpace >= bytes
        }
    }

    override fun usableBytes(folder: File): Long = folder.usableSpace
}

/** Where the queue's run stands in this process. */
internal enum class RunPhase {
    /** No run is scheduled or running, so a start schedules one. */
    IDLE,

    /** A run is scheduled and hasn't started, so a start does nothing. */
    SCHEDULED,

    /** A run takes downloads from the queue. */
    RUNNING,
}

/**
 * The run's phase, which the scheduler and the runner change only while they hold [mutex]. The
 * runner checks the queue under the same lock before it ends, so a download added in that moment is
 * never left behind, and a running job is never scheduled again.
 */
@Singleton
internal class DownloadRunState @Inject constructor() {
    val mutex = Mutex()

    /** Held for a whole run, its cleanup included, so a second run waits for the first to end. */
    val runLock = Mutex()
    var phase = RunPhase.IDLE

    /**
     * Whether a backup job's run, which Android shows in no Task Manager, is registered, so the
     * scheduler hands the download back to the user-initiated job when the app shows or the user
     * taps a download control. DownloadJobService sets it and clears it, and so does a successful
     * hand-back. See ADR 0010's note of November 28, 2026.
     */
    @Volatile var backupRun = false
}
