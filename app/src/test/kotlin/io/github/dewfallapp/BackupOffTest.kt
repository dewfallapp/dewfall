package io.github.dewfallapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Every domain that backup rules can name, from developer.android.com/identity/data/autobackup.
 * Android backs up root, file, database and sharedpref, and their device_ twins, in separate passes
 * that each start at their own folder, and an exclude matches only an exact path, so each one needs
 * its own exclude.
 */
private val EVERY_DOMAIN =
    setOf(
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )

/**
 * Checks that Android copies none of the app's files off the phone: no backup to Google Drive, and
 * no transfer to a new phone. Android applies these rules itself, and a transfer between two real
 * phones can't run in a test, so this checks the rules. See
 * docs/architecture.md#backups-and-phone-transfers.
 */
class BackupOffTest {
    private val appResources = File(requireProperty("dewfall.appResources"))
    private val application: Element = run {
        val manifest = parse(File(requireProperty("dewfall.appManifest")))
        manifest.getElementsByTagName("application").item(0) as Element
    }

    @Test
    fun `backup is off`() {
        assertEquals("false", application.getAttribute("android:allowBackup"))
        assertFalse(application.hasAttribute("android:backupAgent"))
    }

    @Test
    fun `the manifest names both sets of rules`() {
        assertEquals(
            "@xml/data_extraction_rules",
            application.getAttribute("android:dataExtractionRules"),
        )
        assertEquals(
            "@xml/full_backup_content",
            application.getAttribute("android:fullBackupContent"),
        )
    }

    @Test
    fun `Android 10 and 11 leave every file out too`() {
        val rules = parse(File(appResources, "xml/full_backup_content.xml")).documentElement

        assertEquals("full-backup-content", rules.tagName)
        assertLeavesEveryFileOut(rules)
    }

    @Test
    fun `backups and transfers leave every file out`() {
        val rules = parse(File(appResources, "xml/data_extraction_rules.xml")).documentElement

        assertEquals("data-extraction-rules", rules.tagName)
        assertEquals(
            listOf("cloud-backup", "device-transfer"),
            rules.childElements().map { it.tagName },
        )
        for (section in rules.childElements()) {
            assertLeavesEveryFileOut(section)
            assertFalse(section.hasAttribute("disableIfNoEncryptionCapabilities"), section.tagName)
        }
    }

    private fun assertLeavesEveryFileOut(rules: Element) {
        assertTrue(rules.childElements().none { it.tagName == "include" }, rules.tagName)
        assertEquals(
            EVERY_DOMAIN,
            rules
                .childElements()
                .filter { it.tagName == "exclude" && it.getAttribute("path") == "." }
                .map { it.getAttribute("domain") }
                .toSet(),
            rules.tagName,
        )
    }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    private fun parse(file: File) =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).also {
            it.documentElement.normalize()
        }

    private fun requireProperty(name: String): String =
        checkNotNull(System.getProperty(name)) { "Run the tests through Gradle, which sets $name." }
}
