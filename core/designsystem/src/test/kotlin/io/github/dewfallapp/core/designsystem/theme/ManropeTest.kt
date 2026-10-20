package io.github.dewfallapp.core.designsystem.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.ResourceFont
import io.github.dewfallapp.core.designsystem.R
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.w3c.dom.Element

/**
 * Guards the way Manrope's weights load. Compose's own weight setting had no effect on the variable
 * font, so each weight loads a font XML that sets the weight axis. See Type.kt.
 */
class ManropeTest {
    private val fontResources = File(checkNotNull(System.getProperty("dewfall.fontResources")))

    private val fontXmls =
        mapOf(
            R.font.manrope_regular to "manrope_regular.xml",
            R.font.manrope_medium to "manrope_medium.xml",
            R.font.manrope_semibold to "manrope_semibold.xml",
            R.font.manrope_bold to "manrope_bold.xml",
            R.font.manrope_extrabold to "manrope_extrabold.xml",
        )

    @Test
    fun `each Manrope font loads a font XML that sets its own weight`() {
        val fonts = (Manrope as FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(fontXmls.size, fonts.size)

        fonts.forEach { font ->
            val weight = font.weight.weight
            assertContains(fontXmls.keys, font.resId, "The $weight font must load a font XML")
            val xml = File(fontResources, fontXmls.getValue(font.resId))
            val entry =
                DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(xml)
                    .getElementsByTagName("font")
                    .item(0) as Element

            assertEquals("@font/manrope", entry.getAttribute("android:font"), xml.name)
            assertEquals("$weight", entry.getAttribute("android:fontWeight"), xml.name)
            assertEquals(
                "'wght' $weight",
                entry.getAttribute("android:fontVariationSettings"),
                xml.name,
            )
        }
    }
}
