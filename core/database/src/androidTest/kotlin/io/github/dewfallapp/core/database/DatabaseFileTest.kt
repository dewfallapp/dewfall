package io.github.dewfallapp.core.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/**
 * The "after the app closes" scenarios: what one database wrote to its file, another one opened
 * later on the same file reads back. The file lives in the test app's own cache folder, never in
 * the real dewfall.db.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseFileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "database-file-test.db")

    private fun open(): DewfallDatabase =
        Room.databaseBuilder<DewfallDatabase>(context, file.absolutePath)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @AfterTest
    fun deleteFile() {
        context.deleteDatabase(file.absolutePath)
    }

    @Test
    fun whatWasSavedIsThereAfterOpeningAgain() = runTest {
        val video = video(videoId(1))
        open().run {
            channelDao().subscribe(channel())
            videoDao().saveChannelUploads(details(), listOf(video))
            watchStateDao().savePosition(video.id, 300_000, TUESDAY)
            close()
        }

        val again = open()

        assertEquals(channel(), again.channelDao().subscribedChannel(CHANNEL_A).first())
        assertEquals(video.copy(listingNumber = 1), again.videoDao().video(video.id))
        assertEquals(
            WatchStateEntity(video.id, 300_000, null, TUESDAY),
            again.watchStateDao().watchState(video.id).first(),
        )
        again.close()
    }
}
