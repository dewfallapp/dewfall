package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlinx.serialization.json.Json

private const val ID = "aqz-KE-bpKQ"

class NavKeysTest {
    @Test
    fun `a PlayerKey's text holds no video ID`() {
        val key = PlayerKey(VideoId(ID), entryId = "6f1c0d4e-0000-4000-8000-000000000000")

        assertFalse(ID in key.toString())
    }

    @Test
    fun `entries opened for the same video get content keys without the ID, and different ones`() {
        val backStack = mutableListOf<NavKey>(SearchKey)

        backStack.openVideo(VideoId(ID))
        val first = backStack.last() as PlayerKey
        backStack.close(first)
        backStack.openVideo(VideoId(ID))
        val second = backStack.last() as PlayerKey

        assertFalse(ID in first.entryId)
        assertFalse(ID in second.entryId)
        assertNotEquals(first.entryId, second.entryId)
    }

    @Test
    fun `a PlayerKey survives being saved and restored`() {
        val key = PlayerKey(VideoId(ID), entryId = "6f1c0d4e-0000-4000-8000-000000000000")

        val saved = Json.encodeToString(PlayerKey.serializer(), key)

        assertEquals(key, Json.decodeFromString(PlayerKey.serializer(), saved))
        assertEquals(SearchKey, Json.decodeFromString(SearchKey.serializer(), "{}"))
    }

    @Test
    fun `restoring a saved key checks the video ID`() {
        val saved = """{"videoId":"not an ID","entryId":"x"}"""

        // VideoId's constructor refuses the text, and its message doesn't repeat it.
        val error =
            assertFailsWith<IllegalArgumentException> {
                Json.decodeFromString(PlayerKey.serializer(), saved)
            }
        assertFalse("not an ID" in error.message.orEmpty())
    }
}
