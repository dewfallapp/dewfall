package io.github.dewfallapp.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

/** The player spec's requirements about remembering positions and watched marks. */
@RunWith(AndroidJUnit4::class)
class WatchStateDaoTest {
    private val database = inMemoryDatabase()
    private val states = database.watchStateDao()
    private val video = videoId(1)

    @AfterTest fun closeDatabase() = database.close()

    @Test
    fun savingAPositionKeepsItWithItsTime() = runTest {
        states.savePosition(video, 750_000, MONDAY)

        assertEquals(
            WatchStateEntity(video, 750_000, null, MONDAY),
            states.watchState(video).first(),
        )
    }

    @Test
    fun aLaterPositionReplacesTheEarlierOne() = runTest {
        states.savePosition(video, 720_000, MONDAY)

        states.savePosition(video, 1_200_000, TUESDAY)

        assertEquals(
            WatchStateEntity(video, 1_200_000, null, TUESDAY),
            states.watchState(video).first(),
        )
    }

    @Test
    fun aPositionNeedsNoSavedVideoOrChannel() = runTest {
        states.savePosition(video, 60_000, MONDAY)

        assertNull(database.videoDao().video(video))
        assertEquals(60_000, states.watchState(video).first()?.positionMs)
    }

    @Test
    fun aPositionBelowZeroIsSavedAsZero() = runTest {
        states.savePosition(video, -5, MONDAY)

        assertEquals(0, states.watchState(video).first()?.positionMs)
    }

    @Test
    fun aVideoNeverPlayedHasNoWatchState() = runTest {
        states.savePosition(videoId(2), 60_000, MONDAY)

        assertNull(states.watchState(video).first())
    }

    @Test
    fun markingWatchedKeepsThePosition() = runTest {
        states.savePosition(video, 2_400_000, MONDAY)

        states.markWatched(video, TUESDAY)

        assertEquals(
            WatchStateEntity(video, 2_400_000, TUESDAY, TUESDAY),
            states.watchState(video).first(),
        )
    }

    @Test
    fun markingWatchedWithNoPositionStartsAtZero() = runTest {
        states.markWatched(video, MONDAY)

        assertEquals(WatchStateEntity(video, 0, MONDAY, MONDAY), states.watchState(video).first())
    }

    @Test
    fun markingWatchedAgainKeepsTheFirstTime() = runTest {
        states.markWatched(video, MONDAY)

        states.markWatched(video, WEDNESDAY)

        assertEquals(MONDAY, states.watchState(video).first()?.watchedAt)
    }

    @Test
    fun markingUnwatchedKeepsThePosition() = runTest {
        states.savePosition(video, 2_400_000, MONDAY)
        states.markWatched(video, TUESDAY)

        states.markUnwatched(video, WEDNESDAY)

        assertEquals(
            WatchStateEntity(video, 2_400_000, null, WEDNESDAY),
            states.watchState(video).first(),
        )
    }

    @Test
    fun markingUnwatchedWithNoWatchStateAddsNone() = runTest {
        states.markUnwatched(video, MONDAY)

        assertNull(states.watchState(video).first())
    }

    @Test
    fun aNewPositionAfterWatchingKeepsTheMark() = runTest {
        states.markWatched(video, MONDAY)

        states.savePosition(video, 90_000, TUESDAY)

        assertEquals(
            WatchStateEntity(video, 90_000, MONDAY, TUESDAY),
            states.watchState(video).first(),
        )
    }
}
