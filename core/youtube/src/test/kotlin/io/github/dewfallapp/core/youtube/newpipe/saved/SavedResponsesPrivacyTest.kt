package io.github.dewfallapp.core.youtube.newpipe.saved

import java.io.File
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The line of exchanges.txt that names the file of an answer's body. */
private val BODY_FILE_LINE = Regex("""^< @(.+)$""", RegexOption.MULTILINE)

/**
 * Checks every saved file for anything that says which machine, session or network recorded it, and
 * for data about viewers. The repository is public, so this runs on every pull request. It knows no
 * real value, so it checks that every place that held one holds a placeholder, that no file holds a
 * public IP address, that every opaque value is masked or kept, so a kind of value that the masking
 * doesn't know yet fails here, that tracked links keep only the parameters that something reads,
 * and that no comment or other data about a viewer is left. The recorder checks for the real values
 * themselves before it writes a set.
 */
class SavedResponsesPrivacyTest {
    private val folder =
        File(checkNotNull(javaClass.getResource("/$SAVED_SETS_FOLDER")) { "No saved sets" }.toURI())

    @Test
    fun `every saved set has a folder, and every saved file belongs to a set`() {
        val children = folder.listFiles().orEmpty()

        assertEquals(
            SavedSet.entries.map { it.directory }.toSet(),
            children.map { it.name }.toSet(),
        )
        for (set in SavedSet.entries) {
            val setFolder = File(folder, set.directory)
            val index = File(setFolder, EXCHANGES_FILE).readText()
            val named = BODY_FILE_LINE.findAll(index).map { it.groupValues[1] }.toSet()
            assertEquals(named + EXCHANGES_FILE, setFolder.list().orEmpty().toSet(), set.name)
        }
    }

    @Test
    fun `no saved file holds an IP address, visitor data, a token or a key`() {
        for (file in folder.walk().filter { it.isFile }) {
            assertEquals(emptySet(), unmaskedKindsIn(file.readText()), file.relativeTo(folder).path)
        }
    }

    @Test
    fun `no saved file keeps data about a viewer`() {
        for (file in folder.walk().filter { it.isFile }) {
            assertFalse(hasUnmaskedViewerData(file.readText()), file.relativeTo(folder).path)
        }
    }

    @Test
    fun `every opaque value is masked or sits under a kept key`() {
        val unmasked = sortedSetOf<String>()
        for (set in SavedSet.entries) {
            val files = File(folder, set.directory).listFiles().orEmpty()
            val found = files.associateWith { opaqueValuesInFile(it.name, it.readText()) }
            val certainKeys = certainKeysOf(found.values.flatten())
            val kept = keptValuesOf(loadSavedSet(set).exchanges)
            for ((file, values) in found) {
                for (value in values) {
                    if (countsAsOpaque(value, certainKeys) && !isKept(value, kept)) {
                        unmasked += "${set.directory}/${file.name}: ${keyForMessage(value.key)}"
                    }
                }
            }
        }
        // The message names each file and key, never the value, which may be real, and never a key
        // that may itself be opaque or real, such as a path segment.
        assertTrue(
            unmasked.isEmpty(),
            "These files hold opaque values that are neither masked nor under a kept key, by " +
                "key. Mask them in Masking.kt, or, if NewPipeExtractor reads them or a test " +
                "checks them and they can't identify anyone, add the key to KEPT_KEYS or " +
                "KEPT_REQUEST_KEYS with the reason. $OPAQUE_KEY stands for a key that may itself " +
                "be opaque:\n" +
                unmasked.joinToString("\n"),
        )
    }

    @Test
    fun `no saved answer keeps a cookie or another header that the layer doesn't read`() {
        for (set in SavedSet.entries) {
            for (exchange in loadSavedSet(set).exchanges) {
                for (header in exchange.response.headers) {
                    assertTrue(
                        header.name.lowercase(Locale.ROOT) in KEPT_RESPONSE_HEADERS,
                        "${set.name} keeps the header ${header.name}",
                    )
                }
            }
        }
    }
}
