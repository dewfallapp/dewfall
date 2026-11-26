package io.github.dewfallapp.core.model

import java.net.URI
import java.net.URISyntaxException

/**
 * The hosts that YouTube serves thumbnails and channel avatars from, the only hosts that the app
 * loads images from: i.ytimg.com for video thumbnails, and yt3.ggpht.com and
 * yt3.googleusercontent.com for avatars. The saved YouTube responses hold image links on these
 * three only. If YouTube moves images to another host, rows show their placeholders, and the live
 * YouTube check fails with IMAGE_HOST_UNKNOWN. See docs/architecture.md#network-access.
 */
val YouTubeImageHosts: Set<String> =
    setOf("i.ytimg.com", "yt3.ggpht.com", "yt3.googleusercontent.com")

/** HTTPS's default port, which a link may name. */
private const val HTTPS_PORT = 443

/**
 * Whether [link] is a link that the app may load an image from: an absolute `https` link with no
 * user info, the default port, and a host that is exactly one of [YouTubeImageHosts], in any case.
 * Anything else, such as a `file:`, `content:` or `data:` link, another host or a subdomain, is
 * refused.
 */
fun isYouTubeImageLink(link: String): Boolean {
    val uri =
        try {
            URI(link)
        } catch (_: URISyntaxException) {
            null
        }
    val host = uri?.host ?: return false
    return uri.isAbsolute &&
        uri.scheme.equals("https", ignoreCase = true) &&
        uri.rawUserInfo == null &&
        (uri.port == -1 || uri.port == HTTPS_PORT) &&
        host.lowercase() in YouTubeImageHosts
}
