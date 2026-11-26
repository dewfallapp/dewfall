package io.github.dewfallapp.images

import coil3.toUri
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

// The links are made up: real image hosts with made-up paths, or made-up hosts.
class ImageLinkRuleTest {
    private val youTubeLink = "https://i.ytimg.com/vi/aaaaaaaaaa1/mqdefault.jpg"

    @Test
    fun `a YouTube image link passes in each form`() {
        assertTrue(isLoadableImageData(youTubeLink))
        assertTrue(isLoadableImageData(youTubeLink.toUri()))
        assertTrue(isLoadableImageData(youTubeLink.toHttpUrl()))
    }

    @Test
    fun `a file fails`() {
        assertFalse(isLoadableImageData(File("/data/data/io.github.dewfallapp/files/a.jpg")))
    }

    @Test
    fun `links to the phone and to other hosts fail in each form`() {
        val refused =
            listOf(
                "file:///data/data/io.github.dewfallapp/cache/a.png",
                "content://media/external/images/1",
                "data:image/png;base64,iVBORw0KGgo=",
                "android.resource://io.github.dewfallapp/drawable/a",
                "https://images.example.invalid/a.jpg",
                "http://i.ytimg.com/vi/aaaaaaaaaa1/default.jpg",
            )
        for (link in refused) {
            assertFalse(isLoadableImageData(link), link)
            assertFalse(isLoadableImageData(link.toUri()), link)
        }
        assertFalse(isLoadableImageData("https://images.example.invalid/a.jpg".toHttpUrl()))
    }

    @Test
    fun `other kinds of data fail`() {
        assertFalse(isLoadableImageData(1))
        assertFalse(isLoadableImageData(byteArrayOf(1, 2, 3)))
        assertFalse(isLoadableImageData(StringBuilder(youTubeLink)))
    }
}
