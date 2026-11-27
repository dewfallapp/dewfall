package io.github.dewfallapp.fake

import io.github.dewfallapp.core.data.download.DownloadScheduler
import java.util.concurrent.atomic.AtomicInteger

/**
 * A download scheduler that starts no run, for WaitingChipTest, which binds it in place of
 * DownloadSchedulerModule's QueueScheduler. With no run, nothing claims a download, so a row that a
 * tap wrote stays WAITING for as long as the test needs. It counts the calls to [start], which come
 * from the app scope's thread while the test reads them on its own, so the count is atomic. See
 * docs/testing.md#the-fakes.
 */
class HeldScheduler : DownloadScheduler {
    private val calls = AtomicInteger()

    /** How many times something asked for a run. */
    val starts: Int
        get() = calls.get()

    override fun start() {
        calls.incrementAndGet()
    }
}
