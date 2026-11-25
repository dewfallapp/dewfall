package io.github.dewfallapp.images

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import io.github.dewfallapp.core.model.isYouTubeImageLink
import java.io.File
import okhttp3.HttpUrl

/**
 * Why the image loader refused a request: its data wasn't a YouTube image link or a file. The
 * message is constant text, because the link is user data. See ADR 0007.
 */
internal class RefusedImageLink : Exception("The image loader refused a link")

/**
 * Whether the image loader may load [data]: a [File], which only the app's own code makes, or a
 * link that is a YouTube image link. A link may come as a String, a Coil Uri, an Android Uri or an
 * OkHttp HttpUrl, and each is judged by its text, so a `file:`, `content:`, `data:` or
 * `android.resource:` link never reaches Coil's own fetchers. Any other data is refused.
 */
internal fun isLoadableImageData(data: Any): Boolean =
    when (data) {
        is File -> true
        is String -> isYouTubeImageLink(data)
        is coil3.Uri,
        is android.net.Uri,
        is HttpUrl -> isYouTubeImageLink(data.toString())
        else -> false
    }

/**
 * The image loader's first interceptor, which runs before Coil maps or fetches anything: it lets a
 * request through only when [isLoadableImageData] allows its data, and fails any other with
 * [RefusedImageLink], so the row keeps its placeholder. See ADR 0011.
 */
internal object ImageLinkRule : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        return if (isLoadableImageData(request.data)) {
            chain.proceed()
        } else {
            ErrorResult(image = null, request = request, throwable = RefusedImageLink())
        }
    }
}
