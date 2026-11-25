package io.github.dewfallapp.images

import android.content.Context
import androidx.startup.Initializer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.serviceLoaderEnabled
import okio.Path.Companion.toOkioPath

/** The disk cache's folder, under the app's cache folder, which Android may clear. */
internal const val IMAGE_CACHE_FOLDER = "images"

/** The most bytes of images the disk cache keeps, 64 MiB. */
private const val IMAGE_CACHE_BYTES = 64L * 1024 * 1024

/**
 * The app's image loader: [ImageLinkRule] before anything else, no fetcher that Coil finds on its
 * own, the network fetcher with [imageClient], a disk cache of 64 MiB in the app's cache folder,
 * and Coil's default memory cache. It sets no logger, so Coil writes nothing to logcat. See ADR
 * 0011.
 */
internal fun dewfallImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components {
            add(ImageLinkRule)
            add(OkHttpNetworkFetcherFactory(callFactory = { imageClient() }))
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(IMAGE_CACHE_FOLDER).toOkioPath())
                .maxSizeBytes(IMAGE_CACHE_BYTES)
                .build()
        }
        .build()

/**
 * Sets Coil's singleton to [dewfallImageLoader] when the app starts. App Startup runs it under any
 * Application class, so the emulator tests, which run under HiltTestApplication, use the same
 * loader as the app.
 */
class ImageLoaderInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        SingletonImageLoader.setSafe { dewfallImageLoader(it) }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
