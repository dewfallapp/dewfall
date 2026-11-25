package io.github.dewfallapp.images

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

// The links are made up: real image hosts with made-up paths, or made-up hosts. Nothing here
// reaches the network: the image client gets one more interceptor at the end, which answers every
// request that gets that far itself, before OkHttp would look up a host or connect.
class ImageClientTest {
    private val sent = mutableListOf<Request>()

    private val answering = Interceptor { chain ->
        val request = chain.request()
        sent += request
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
    }

    private val client = imageClient().newBuilder().addInterceptor(answering).build()

    private fun get(link: String): Response =
        client.newCall(Request.Builder().url(link).build()).execute()

    @Test
    fun `a YouTube image link goes through`() {
        get("https://yt3.ggpht.com/made-up-avatar=s88").close()

        assertEquals(1, sent.size)
    }

    @Test
    fun `other links are refused before they are sent, and the error names no link`() {
        val refused =
            listOf(
                "http://i.ytimg.com/vi/aaaaaaaaaa1/default.jpg",
                "https://images.example.invalid/secret-path.jpg",
                "https://i.ytimg.com.example.org/a.jpg",
                "https://i.ytimg.com:8443/a.jpg",
            )
        for (link in refused) {
            val error = assertFailsWith<IOException> { get(link) }

            assertEquals("The image client refused a request", error.message, link)
        }
        assertEquals(0, sent.size)
    }

    @Test
    fun `requests ask for British English and keep their link`() {
        val link = "https://i.ytimg.com/vi/aaaaaaaaaa1/mqdefault.jpg"

        get(link).close()

        val request = sent.single()
        assertEquals("en-GB", request.header("Accept-Language"))
        assertEquals(link, request.url.toString())
    }

    @Test
    fun `the client follows no redirects, keeps no cookies or cache, and runs the host rule first`() {
        val client = imageClient()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(CookieJar.NO_COOKIES, client.cookieJar)
        assertNull(client.cache)
        assertEquals(listOf<Interceptor>(ImageHostRule, BritishEnglish), client.interceptors)
    }
}
