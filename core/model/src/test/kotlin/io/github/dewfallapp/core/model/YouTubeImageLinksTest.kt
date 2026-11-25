package io.github.dewfallapp.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The links are made up: real hosts with made-up paths, never a real video's or channel's.
class YouTubeImageLinksTest {
    @Test
    fun `each image host passes over HTTPS`() {
        assertTrue(isYouTubeImageLink("https://i.ytimg.com/vi/aaaaaaaaaa1/mqdefault.jpg"))
        assertTrue(isYouTubeImageLink("https://yt3.ggpht.com/made-up-avatar=s88"))
        assertTrue(isYouTubeImageLink("https://yt3.googleusercontent.com/made-up-avatar=s176"))
    }

    @Test
    fun `hosts and schemes in capitals and the default port pass`() {
        assertTrue(isYouTubeImageLink("https://I.YTIMG.COM/vi/aaaaaaaaaa1/default.jpg"))
        assertTrue(isYouTubeImageLink("HTTPS://i.ytimg.com/vi/aaaaaaaaaa1/default.jpg"))
        assertTrue(isYouTubeImageLink("https://i.ytimg.com:443/vi/aaaaaaaaaa1/default.jpg"))
    }

    @Test
    fun `other schemes fail`() {
        assertFalse(isYouTubeImageLink("http://i.ytimg.com/vi/aaaaaaaaaa1/default.jpg"))
        assertFalse(isYouTubeImageLink("file:///data/data/io.github.dewfallapp/cache/a.png"))
        assertFalse(isYouTubeImageLink("content://media/external/images/1"))
        assertFalse(isYouTubeImageLink("data:image/png;base64,iVBORw0KGgo="))
        assertFalse(isYouTubeImageLink("android.resource://io.github.dewfallapp/drawable/a"))
    }

    @Test
    fun `relative links and text that isn't a link fail`() {
        assertFalse(isYouTubeImageLink("/vi/aaaaaaaaaa1/default.jpg"))
        assertFalse(isYouTubeImageLink("//i.ytimg.com/vi/aaaaaaaaaa1/default.jpg"))
        assertFalse(isYouTubeImageLink(""))
        assertFalse(isYouTubeImageLink("https://i.ytimg.com/a b"))
    }

    @Test
    fun `other hosts, look-alikes and subdomains fail`() {
        assertFalse(isYouTubeImageLink("https://images.example.invalid/a.jpg"))
        assertFalse(isYouTubeImageLink("https://i.ytimg.com.example.org/a.jpg"))
        assertFalse(isYouTubeImageLink("https://evil-ytimg.com/a.jpg"))
        assertFalse(isYouTubeImageLink("https://ytimg.com/a.jpg"))
        assertFalse(isYouTubeImageLink("https://a.i.ytimg.com/a.jpg"))
        assertFalse(isYouTubeImageLink("https://lh3.googleusercontent.com/a.jpg"))
        assertFalse(isYouTubeImageLink("https://192.0.2.1/a.jpg"))
        assertFalse(isYouTubeImageLink("https://[2001:db8::1]/a.jpg"))
    }

    @Test
    fun `user info and other ports fail`() {
        assertFalse(isYouTubeImageLink("https://user@i.ytimg.com/a.jpg"))
        assertFalse(isYouTubeImageLink("https://user:secret@i.ytimg.com/a.jpg"))
        assertFalse(isYouTubeImageLink("https://i.ytimg.com:8443/a.jpg"))
        assertFalse(isYouTubeImageLink("https://i.ytimg.com:80/a.jpg"))
    }
}
