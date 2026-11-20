package io.github.dewfallapp

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Checks :app's launch window colors and themes against the design tokens and manifest rules.
 *
 * Nothing can read Compose's MaterialTheme before the first frame draws, so Android 10 and 11 copy
 * the static surface colors into XML. This test keeps them tied to tokens.json and guards the
 * manifest wiring, qualifier precedence, and system bar attribute rules.
 */
class LaunchThemeTest {
    private val tokens: JsonObject = run {
        val path = requireProperty("dewfall.designTokens")
        Json.parseToJsonElement(File(path).readText()).jsonObject
    }

    private val appResources: File = File(requireProperty("dewfall.appResources"))
    private val appManifest: File = File(requireProperty("dewfall.appManifest"))

    @Test
    fun `static launch colors match the surface token`() {
        val surface =
            tokens
                .getValue("color")
                .jsonObject
                .getValue("tokens")
                .jsonArray
                .map { it.jsonObject }
                .single { it.getValue("name").jsonPrimitive.content == "surface" }
                .getValue("value")
                .jsonObject

        val expectedLight = surface.getValue("light").jsonPrimitive.content
        val expectedDark = surface.getValue("dark").jsonPrimitive.content

        val actualLight = launchSurfaceIn("values")
        val actualDark = launchSurfaceIn("values-night")

        assertEquals(
            expectedLight.lowercase(),
            actualLight.lowercase(),
            "values/colors.xml launch_surface",
        )
        assertEquals(
            expectedDark.lowercase(),
            actualDark.lowercase(),
            "values-night/colors.xml launch_surface",
        )
    }

    @Test
    fun `every launch color folder defines the launch surface`() {
        for (folder in LAUNCH_COLOR_FOLDERS) {
            assertTrue(launchSurfaceIn(folder).isNotEmpty(), "$folder/colors.xml launch_surface")
        }
    }

    @Test
    fun `the launch themes set the launch surface and readable status bar icons`() {
        val light = stylesIn("values/themes.xml")
        val dark = stylesIn("values-night/themes.xml")
        val v31 = stylesIn("values-v31/themes.xml")

        assertEquals(
            ThemeStyle(
                parent = "android:Theme.Material.Light.NoActionBar",
                items = baseItems(lightStatusBar = true),
            ),
            light.getValue(BASE_THEME),
            "values/themes.xml $BASE_THEME",
        )
        assertEquals(
            ThemeStyle(parent = BASE_THEME, items = emptyMap()),
            light.getValue(THEME),
            "values/themes.xml $THEME",
        )
        assertEquals(
            ThemeStyle(
                parent = "android:Theme.Material.NoActionBar",
                items = baseItems(lightStatusBar = false),
            ),
            dark.getValue(BASE_THEME),
            "values-night/themes.xml $BASE_THEME",
        )
        assertEquals(
            ThemeStyle(
                parent = BASE_THEME,
                items = mapOf("android:windowSplashScreenBackground" to LAUNCH_SURFACE_REFERENCE),
            ),
            v31.getValue(THEME),
            "values-v31/themes.xml $THEME",
        )
    }

    @Test
    fun `manifest applies the launch theme on application without overriding MainActivity`() {
        val document = parseXml(appManifest)
        val application = document.elements("application").single()
        assertEquals(
            "@style/Theme.Dewfall",
            application.getAttribute("android:theme"),
            "AndroidManifest.xml application theme",
        )

        val mainActivity =
            document.elements("activity").single { activity ->
                val name = activity.getAttribute("android:name")
                name == ".MainActivity" || name == "io.github.dewfallapp.MainActivity"
            }
        assertFalse(
            mainActivity.hasAttribute("android:theme"),
            "MainActivity should inherit @style/Theme.Dewfall from <application>",
        )
    }

    @Test
    fun `values-night themes xml does not define Theme Dewfall`() {
        assertTrue(THEME in stylesIn("values/themes.xml"), "values/themes.xml")
        assertTrue(THEME in stylesIn("values-v31/themes.xml"), "values-v31/themes.xml")
        assertFalse(
            THEME in stylesIn("values-night/themes.xml"),
            "values-night/themes.xml must not define $THEME because night outranks v31",
        )
    }

    @Test
    fun `theme xmls forbid deprecated bar colors and light navigation bar below API 34`() {
        val valueDirs =
            appResources
                .listFiles()
                ?.filter { dir ->
                    dir.isDirectory && (dir.name == "values" || dir.name.startsWith("values-"))
                }
                .orEmpty()
        assertTrue(valueDirs.isNotEmpty(), "Expected values* directories under $appResources")

        var styleItemCount = 0
        for (dir in valueDirs) {
            val apiLevel = apiQualifierOf(dir.name)
            for ((location, attrName) in styleItemsIn(dir)) {
                styleItemCount++
                assertFalse(
                    attrName in DEPRECATED_BAR_ATTRIBUTES,
                    "$location must not set deprecated $attrName",
                )
                if (apiLevel < API_34) {
                    assertFalse(
                        attrName == LIGHT_NAVIGATION_BAR_ATTRIBUTE,
                        "$location must not set $attrName below API $API_34",
                    )
                }
            }
        }
        assertTrue(styleItemCount > 0, "Expected at least one style item in app/src/main/res")
    }

    private fun launchSurfaceIn(folder: String): String =
        parseXml(File(appResources, "$folder/colors.xml"))
            .elements("color")
            .single { it.getAttribute("name") == "launch_surface" }
            .textContent
            .trim()

    private fun stylesIn(path: String): Map<String, ThemeStyle> =
        parseXml(File(appResources, path)).elements("style").associate { style ->
            val items = style.getElementsByTagName("item")
            style.getAttribute("name") to
                ThemeStyle(
                    parent = style.getAttribute("parent"),
                    items =
                        List(items.length) { index -> items.item(index) as Element }
                            .associate { it.getAttribute("name") to it.textContent.trim() },
                )
        }

    private fun baseItems(lightStatusBar: Boolean): Map<String, String> =
        mapOf(
            "android:colorBackground" to LAUNCH_SURFACE_REFERENCE,
            "android:colorPrimaryDark" to LAUNCH_SURFACE_REFERENCE,
            "android:windowLightStatusBar" to lightStatusBar.toString(),
        )

    private data class ThemeStyle(val parent: String, val items: Map<String, String>)

    private fun requireProperty(name: String): String =
        checkNotNull(System.getProperty(name)) {
            "Run the tests through Gradle, which sets $name."
        }

    private fun parseXml(file: File): Document {
        assertTrue(file.isFile, "Expected file to exist: $file")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun Document.elements(tagName: String): List<Element> {
        val nodes = getElementsByTagName(tagName)
        return List(nodes.length) { index -> nodes.item(index) as Element }
    }

    private fun styleItemsIn(dir: File): List<Pair<String, String>> {
        val xmlFiles = dir.listFiles()?.filter { it.isFile && it.extension == "xml" }.orEmpty()
        return xmlFiles.flatMap { xmlFile ->
            val location = "${dir.name}/${xmlFile.name}"
            parseXml(xmlFile).elements("style").flatMap { style ->
                val items = style.getElementsByTagName("item")
                List(items.length) { index ->
                    val item = items.item(index) as Element
                    location to item.getAttribute("name")
                }
            }
        }
    }

    private fun apiQualifierOf(dirName: String): Int =
        dirName
            .split("-")
            .firstNotNullOfOrNull { segment -> API_QUALIFIER_REGEX.matchEntire(segment) }
            ?.groupValues
            ?.get(1)
            ?.toInt() ?: 0

    private companion object {
        const val THEME = "Theme.Dewfall"
        const val BASE_THEME = "Base.Theme.Dewfall"
        const val LAUNCH_SURFACE_REFERENCE = "@color/launch_surface"
        const val API_34 = 34
        const val LIGHT_NAVIGATION_BAR_ATTRIBUTE = "android:windowLightNavigationBar"
        val LAUNCH_COLOR_FOLDERS =
            listOf(
                "values",
                "values-night",
                "values-v31",
                "values-night-v31",
                "values-v34",
                "values-night-v34",
            )
        val DEPRECATED_BAR_ATTRIBUTES =
            setOf("android:statusBarColor", "android:navigationBarColor")
        val API_QUALIFIER_REGEX = Regex("v(\\d+)")
    }
}
