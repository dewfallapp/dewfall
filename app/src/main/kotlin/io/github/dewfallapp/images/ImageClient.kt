package io.github.dewfallapp.images

import io.github.dewfallapp.core.model.isYouTubeImageLink
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * The image client's check, as a second line behind [ImageLinkRule]: a request goes on only when
 * its URL is a YouTube image link. Any other fails with an IOException whose message is constant
 * text, before DNS or a connection. See ADR 0011.
 */
internal object ImageHostRule : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isYouTubeImageLink(request.url.toString())) {
            throw IOException("The image client refused a request")
        }
        return chain.proceed(request)
    }
}

/**
 * Asks for British English, as every request to YouTube does, and names no country: an image's
 * bytes hold no text, and the image links need no country. See ADR 0009 and ADR 0011.
 */
internal object BritishEnglish : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("Accept-Language", "en-GB").build())
}

/**
 * The HTTP client of the image loader, apart from the YouTube layer's: [ImageHostRule] first, then
 * [BritishEnglish], no redirects, so no request reaches a host that the rule didn't check, no
 * cookies, which OkHttp keeps none of by default, and no cache of its own, because Coil keeps one.
 */
internal fun imageClient(): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(ImageHostRule)
        .addInterceptor(BritishEnglish)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
