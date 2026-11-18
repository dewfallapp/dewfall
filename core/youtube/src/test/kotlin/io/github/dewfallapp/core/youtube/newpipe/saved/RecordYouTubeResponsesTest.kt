package io.github.dewfallapp.core.youtube.newpipe.saved

import io.github.dewfallapp.core.model.YouTubeError
import io.github.dewfallapp.core.youtube.newpipe.recordingLogger
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A public IPv6 address that no test machine has, written where no masking rule looks. */
private const val UNMASKED_ADDRESS_BODY = """{"note":"2a02:1234:5678:9abc:1:2:3:4"}"""

/** An answer that names the machine's address, a documentation address, in a stream link. */
private const val NAMING_BODY =
    """{"url":"https://x.googlevideo.com/videoplayback?expire=1&ip=198.51.100.7"}"""

/** sw.js_data, which names the network's country, a code that no country has, as YouTube's does. */
private const val SW_JS_DATA_BODY = """)]}'[["x",null,[[["en","ZZ"]]]]]"""

/**
 * Tests that the recorder writes nothing unless every set passes its checks. The calls are made up,
 * so nothing reaches YouTube, and the files go to a temporary folder.
 */
class RecordYouTubeResponsesTest {
    private val folder = createTempDirectory("saved-sets").toFile()
    private val lines = mutableListOf<String>()

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun `a run writes every set when each one passes its checks`() {
        val written = record(SavedSet.UPLOADS_JAWED, SavedSet.CHANNEL_DELETED) { recordingOf(it) }

        assertTrue(written)
        assertEquals(
            setOf(EXCHANGES_FILE, "01-sw.js_data", "02-browse.json"),
            File(folder, SavedSet.UPLOADS_JAWED.directory).list().orEmpty().toSet(),
        )
        assertTrue(File(folder, SavedSet.CHANNEL_DELETED.directory).isDirectory)
    }

    @Test
    fun `a run writes no set when one still holds a real value`() {
        val written =
            record(SavedSet.UPLOADS_JAWED, SavedSet.CHANNEL_DELETED) { set ->
                if (set == SavedSet.CHANNEL_DELETED) {
                    recordingOf(set, body = UNMASKED_ADDRESS_BODY)
                } else {
                    recordingOf(set)
                }
            }

        assertFalse(written)
        assertEquals(emptyList(), writtenFiles())
        assertTrue(
            "WARN Dewfall.Recorder A real value is still in a masked set set=CHANNEL_DELETED " +
                "value=ADDRESS" in lines,
            lines.toString(),
        )
    }

    @Test
    fun `a run writes no set when a call sends other requests, and logs the ones it sent`() {
        val written =
            record(SavedSet.UPLOADS_JAWED) {
                recordingOf(it, requests = it.requests + Endpoint.NEXT)
            }

        assertFalse(written)
        assertEquals(emptyList(), writtenFiles())
        assertTrue(
            "WARN Dewfall.Recorder A saved set's call sent a request set=UPLOADS_JAWED " +
                "position=3 endpoint=NEXT" in lines,
            lines.toString(),
        )
    }

    @Test
    fun `a run whose sets name no address calls another set to learn it, and doesn't write it`() {
        val called = mutableListOf<SavedSet>()

        val written =
            record(SavedSet.UPLOADS_JAWED) { set ->
                called += set
                recordingOf(set, body = if (set == LEARNING_SET) NAMING_BODY else "{}")
            }

        assertTrue(written)
        assertEquals(listOf(SavedSet.UPLOADS_JAWED, LEARNING_SET), called)
        assertEquals(
            listOf(SavedSet.UPLOADS_JAWED.directory),
            folder.list().orEmpty().toList(),
        )
    }

    @Test
    fun `a run writes no set when no answer names the address or the country`() {
        val written = record(SavedSet.UPLOADS_JAWED) { recordingOf(it, body = "{}") }

        assertFalse(written)
        assertEquals(emptyList(), writtenFiles())
        assertTrue(
            "ERROR Dewfall.Recorder Wrote no saved set, because no answer named the address or " +
                "the country" in lines,
            lines.toString(),
        )
    }

    @Test
    fun `a run writes no set when a call ends otherwise than its set expects`() {
        val written =
            record(SavedSet.UPLOADS_JAWED) { recordingOf(it, error = YouTubeError.NETWORK) }

        assertFalse(written)
        assertEquals(emptyList(), writtenFiles())
    }

    private fun record(vararg sets: SavedSet, call: (SavedSet) -> Recording): Boolean =
        recordSets(sets.toList(), folder, "v0.26.5", recordingLogger(lines), call)

    private fun writtenFiles(): List<File> = folder.walk().filter { it.isFile }.toList()

    /**
     * A made-up recording of [set] that sends [requests], each answered with [body], but
     * sw.js_data, which names the network's country.
     */
    private fun recordingOf(
        set: SavedSet,
        requests: List<Endpoint> = set.requests,
        error: YouTubeError? = set.expectedError,
        body: String = NAMING_BODY,
    ) =
        Recording(
            error,
            requests.map { endpoint ->
                SavedExchange(
                    SavedRequest(
                        endpoint.method,
                        "https://${endpoint.host}${endpoint.path}",
                        emptyList(),
                        body = if (endpoint.method == "POST") "{}" else null,
                    ),
                    SavedResponse(
                        200,
                        listOf(Header(CONTENT_TYPE, "application/json")),
                        if (endpoint == Endpoint.SW_JS_DATA) SW_JS_DATA_BODY else body,
                    ),
                )
            },
        )
}
