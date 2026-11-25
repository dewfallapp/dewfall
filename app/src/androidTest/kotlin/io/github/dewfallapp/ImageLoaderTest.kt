package io.github.dewfallapp

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.SingletonImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import io.github.dewfallapp.images.IMAGE_CACHE_FOLDER
import io.github.dewfallapp.images.RefusedImageLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.junit.runner.RunWith

/**
 * Checks that the emulator tests, which run under HiltTestApplication, use the app's image loader,
 * which ImageLoaderInitializer sets when the app starts, and not Coil's default one. The links are
 * ones that Coil's default loader would load without the network: a small image that the test
 * writes in the cache folder, and one of Android's own drawables. The app's loader refuses both
 * with RefusedImageLink, as it refuses a link to any host but YouTube's image hosts. Nothing here
 * reaches the network. See ADR 0011.
 */
@RunWith(AndroidJUnit4::class)
class ImageLoaderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val loader = SingletonImageLoader.get(context)

    private fun refuses(data: Any) = runBlocking {
        val result = loader.execute(ImageRequest.Builder(context).data(data).build())
        assertIs<ErrorResult>(result, data.toString())
        assertIs<RefusedImageLink>(result.throwable, data.toString())
    }

    @Test
    fun theSingletonIsTheAppsLoader() {
        assertEquals(
            context.cacheDir.resolve(IMAGE_CACHE_FOLDER).toOkioPath(),
            loader.diskCache?.directory,
        )
    }

    @Test
    fun aFileLinkIsRefused() {
        val file = context.cacheDir.resolve("image-loader-test.png")
        file.outputStream().use { out ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        try {
            refuses("file://${file.absolutePath}")
        } finally {
            file.delete()
        }
    }

    @Test
    fun aResourceLinkIsRefused() {
        refuses("android.resource://android/${android.R.drawable.ic_menu_add}")
    }

    @Test
    fun aLinkToAnotherHostIsRefused() {
        refuses("https://images.example.invalid/a.png")
    }
}
