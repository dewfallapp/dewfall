package io.github.dewfallapp.navigation

import androidx.navigation3.runtime.NavKey
import io.github.dewfallapp.core.model.VideoId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val VIDEO = VideoId("aqz-KE-bpKQ")
private val OTHER = VideoId("dQw4w9WgXcQ")

class BackStackTest {
    private val backStack = mutableListOf<NavKey>(SearchKey)

    @Test
    fun `opening a video from search adds its screen`() {
        backStack.openVideo(VIDEO)

        assertEquals(2, backStack.size)
        assertEquals(VIDEO, assertIs<PlayerKey>(backStack.last()).videoId)
    }

    @Test
    fun `a second tap while the video screen opens adds nothing`() {
        backStack.openVideo(VIDEO)
        backStack.openVideo(VIDEO)
        backStack.openVideo(OTHER)

        assertEquals(2, backStack.size)
        assertEquals(VIDEO, assertIs<PlayerKey>(backStack.last()).videoId)
    }

    @Test
    fun `closing the same screen twice removes it once and keeps search`() {
        backStack.openVideo(VIDEO)
        val player = backStack.last() as PlayerKey

        backStack.close(player)
        backStack.close(player)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `closing after a back gesture already went back keeps search`() {
        backStack.openVideo(VIDEO)
        val player = backStack.last() as PlayerKey

        backStack.goBack()
        backStack.close(player)

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }

    @Test
    fun `going back never removes the first screen`() {
        backStack.openVideo(VIDEO)

        backStack.goBack()
        backStack.goBack()

        assertEquals(listOf<NavKey>(SearchKey), backStack)
    }
}
