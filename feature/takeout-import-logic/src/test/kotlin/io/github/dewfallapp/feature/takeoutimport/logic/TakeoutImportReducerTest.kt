package io.github.dewfallapp.feature.takeoutimport.logic

import io.github.dewfallapp.core.model.Channel
import io.github.dewfallapp.core.model.ChannelId
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.core.model.TakeoutImportResult
import kotlin.test.Test
import kotlin.test.assertEquals

// Made-up channels: no real channel has these IDs or names.
private val BAKERY =
    Channel(ChannelId("UCtakeouttest00000000001"), "Made-up Bakery", emptyList(), "")
private val ORBIT = Channel(ChannelId("UCtakeouttest00000000002"), "Made-up Orbit", emptyList(), "")
private val IMPORTED =
    TakeoutImportResult.Imported(listOf(BAKERY, ORBIT), added = 1, unreadableLines = 3)
private val FAILED = TakeoutImportResult.Failed(TakeoutImportFailure.NO_CHANNELS)
private val PICKED = TakeoutImportIntent.FilePicked("content://example.invalid/document/1")

private fun reduce(state: TakeoutImportState, intent: TakeoutImportIntent) =
    TakeoutImportReducer.reduce(state, intent)

class TakeoutImportReducerTest {
    @Test
    fun `picking a file starts reading`() {
        assertEquals(TakeoutImportState.Reading, reduce(TakeoutImportState.Idle, PICKED).state)
    }

    @Test
    fun `picking another file after a result or a failure starts reading again`() {
        val imported =
            reduce(TakeoutImportState.Reading, TakeoutImportIntent.ImportFinished(IMPORTED)).state
        val failed = TakeoutImportState.Failed(TakeoutImportFailure.TOO_LARGE)

        assertEquals(TakeoutImportState.Reading, reduce(imported, PICKED).state)
        assertEquals(TakeoutImportState.Reading, reduce(failed, PICKED).state)
    }

    @Test
    fun `picking a file while reading changes nothing`() {
        assertEquals(TakeoutImportState.Reading, reduce(TakeoutImportState.Reading, PICKED).state)
    }

    @Test
    fun `an import shows its channels in order and its counts`() {
        val next = reduce(TakeoutImportState.Reading, TakeoutImportIntent.ImportFinished(IMPORTED))

        assertEquals(
            TakeoutImportState.Imported(
                channels =
                    listOf(
                        ImportedChannelRow(BAKERY.id, "Made-up Bakery"),
                        ImportedChannelRow(ORBIT.id, "Made-up Orbit"),
                    ),
                added = 1,
                alreadySubscribed = 1,
                unreadableLines = 3,
            ),
            next.state,
        )
        assertEquals(emptyList(), next.effects)
    }

    @Test
    fun `a failed import shows its reason`() {
        for (reason in TakeoutImportFailure.entries) {
            val result = TakeoutImportIntent.ImportFinished(TakeoutImportResult.Failed(reason))

            assertEquals(
                TakeoutImportState.Failed(reason),
                reduce(TakeoutImportState.Reading, result).state,
            )
        }
    }

    @Test
    fun `a result while not reading changes nothing`() {
        val shown = TakeoutImportState.Failed(TakeoutImportFailure.NOT_READABLE)
        for (state in listOf(TakeoutImportState.Idle, shown)) {
            assertEquals(state, reduce(state, TakeoutImportIntent.ImportFinished(FAILED)).state)
        }
    }

    @Test
    fun `leaving keeps the state, whatever it is`() {
        val shown = TakeoutImportState.Failed(TakeoutImportFailure.NOT_READABLE)
        for (state in listOf(TakeoutImportState.Idle, TakeoutImportState.Reading, shown)) {
            assertEquals(state, reduce(state, TakeoutImportIntent.Left).state)
        }
    }

    @Test
    fun `the picked file's URI is not in the intent's text`() {
        assertEquals("FilePicked(hidden)", PICKED.toString())
    }
}
