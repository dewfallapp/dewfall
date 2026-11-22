package io.github.dewfallapp.core.data.takeout

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject

/**
 * Opens a file that the user picked with Android's file picker, by its content URI as text. The URI
 * is user data, so never log it. See ADR 0007.
 */
interface DocumentOpener {
    /**
     * Opens [file] for reading, or returns null if the app that holds it gives no stream. It may
     * throw IOException, SecurityException, or IllegalArgumentException for a URI it can't use.
     * Blocking, so call it off the main thread.
     */
    fun open(file: String): InputStream?
}

/** Opens a picked file through ContentResolver, with no lasting permission. */
internal class ContentResolverDocumentOpener
@Inject
constructor(@ApplicationContext private val context: Context) : DocumentOpener {
    override fun open(file: String): InputStream? =
        context.contentResolver.openInputStream(file.toUri())
}
