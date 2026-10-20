package io.github.dewfallapp.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks the theme against the design tokens in
 * docs/design/claude-design/design-system/tokens.json, the design system's export. The theme copies
 * the tokens by hand, so a typo, or a token edit that wasn't copied, fails here. Each test also
 * fails when its token group gains or loses a token.
 *
 * The opacity tokens aren't checked, because the theme doesn't set them. Material 3's components
 * already use the same state layer and disabled opacities. docs/architecture.md#the-theme says
 * where scrim-modal and duration-scrim stand.
 */
class DesignTokensTest {
    private val tokens: JsonObject = run {
        // core/designsystem/build.gradle.kts passes the path to the tokens.
        val path =
            checkNotNull(System.getProperty("dewfall.designTokens")) {
                "Run the tests through Gradle, which sets dewfall.designTokens."
            }
        Json.parseToJsonElement(File(path).readText()).jsonObject
    }

    @Test
    fun `the color schemes match the color tokens`() {
        val colorTokens = tokens.tokenGroup("color")
        assertEquals(colorRoles.keys, colorTokens.map { it.string("name") }.toSet())

        colorTokens.forEach { token ->
            val name = token.string("name")
            val role = colorRoles.getValue(name)
            val value = token.getValue("value").jsonObject
            assertEquals(hexColor(value.string("light")), role(DewfallLightColors), "light $name")
            assertEquals(hexColor(value.string("dark")), role(DewfallDarkColors), "dark $name")
        }
    }

    @Test
    fun `the type scale matches the type tokens`() {
        val type = tokens.getValue("type").jsonObject
        val families = type.getValue("families").jsonObject
        assertEquals("Manrope", families.string("sans").substringBefore(","))
        val styleTokens =
            type.getValue("groups").jsonArray.flatMap { group ->
                group.jsonObject.getValue("styles").jsonArray.map { it.jsonObject }
            }
        assertEquals(textStyles.keys, styleTokens.map { it.string("name") }.toSet())

        styleTokens.forEach { token ->
            val name = token.string("name")
            val style = textStyles.getValue(name)(DewfallTypography)
            assertEquals(Manrope, style.fontFamily, name)
            val weight = FontWeight(token.getValue("fontWeight").jsonPrimitive.int)
            assertEquals(weight, style.fontWeight, name)
            assertEquals(px(token.string("fontSize")).sp, style.fontSize, name)
            assertEquals(px(token.string("lineHeight")).sp, style.lineHeight, name)
            assertEquals(px(token.string("letterSpacing")).sp, style.letterSpacing, name)
        }
    }

    @Test
    fun `Manrope has a font for every weight the type scale uses`() {
        val manropeToken =
            tokens
                .getValue("type")
                .jsonObject
                .getValue("fonts")
                .jsonArray
                .map { it.jsonObject }
                .single { it.string("family") == "Manrope" }
        val (lightest, boldest) = manropeToken.string("weight").split(" ").map { it.toInt() }
        val fontWeights = (Manrope as FontListFontFamily).fonts.map { it.weight }

        textStyles.forEach { (name, style) ->
            val weight = style(DewfallTypography).fontWeight
            assertContains(fontWeights, weight, name)
            assertContains(lightest..boldest, weight!!.weight, name)
        }
    }

    @Test
    fun `the shapes match the radius tokens`() {
        val radiusTokens = tokens.tokenGroup("radius")
        // shape-full is CircleShape, which isn't part of the theme's Shapes.
        assertEquals(shapes.keys + "shape-full", radiusTokens.map { it.string("name") }.toSet())

        radiusTokens
            .filter { it.string("name") != "shape-full" }
            .forEach { token ->
                val name = token.string("name")
                val expected = RoundedCornerShape(rem(token.string("value")))
                assertEquals(expected, shapes.getValue(name)(DewfallShapes), name)
            }
    }

    @Test
    fun `the spacing matches the spacing tokens`() {
        val spacingTokens = tokens.tokenGroup("spacing")
        assertEquals(spacing.keys, spacingTokens.map { it.string("name") }.toSet())

        spacingTokens.forEach { token ->
            val name = token.string("name")
            assertEquals(rem(token.string("value")), spacing.getValue(name), name)
        }
    }

    private fun JsonObject.tokenGroup(name: String): List<JsonObject> =
        getValue(name).jsonObject.getValue("tokens").jsonArray.map { it.jsonObject }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    /** "#26503c" to an opaque Color. */
    private fun hexColor(hex: String): Color = Color(("FF" + hex.removePrefix("#")).toLong(16))

    /** "-0.25px" to -0.25. The tokens use 1px for 1sp. */
    private fun px(value: String): Float = value.removeSuffix("px").toFloat()

    /** "0.25rem" to 4dp. The tokens use 1rem for 16dp. */
    private fun rem(value: String): Dp = (value.removeSuffix("rem").toFloat() * DP_PER_REM).dp

    private companion object {
        const val DP_PER_REM = 16

        val colorRoles: Map<String, (ColorScheme) -> Color> =
            mapOf(
                "primary" to ColorScheme::primary,
                "on-primary" to ColorScheme::onPrimary,
                "primary-container" to ColorScheme::primaryContainer,
                "on-primary-container" to ColorScheme::onPrimaryContainer,
                "inverse-primary" to ColorScheme::inversePrimary,
                "secondary" to ColorScheme::secondary,
                "on-secondary" to ColorScheme::onSecondary,
                "secondary-container" to ColorScheme::secondaryContainer,
                "on-secondary-container" to ColorScheme::onSecondaryContainer,
                "tertiary" to ColorScheme::tertiary,
                "on-tertiary" to ColorScheme::onTertiary,
                "tertiary-container" to ColorScheme::tertiaryContainer,
                "on-tertiary-container" to ColorScheme::onTertiaryContainer,
                "error" to ColorScheme::error,
                "on-error" to ColorScheme::onError,
                "error-container" to ColorScheme::errorContainer,
                "on-error-container" to ColorScheme::onErrorContainer,
                "surface" to ColorScheme::surface,
                "on-surface" to ColorScheme::onSurface,
                "surface-variant" to ColorScheme::surfaceVariant,
                "on-surface-variant" to ColorScheme::onSurfaceVariant,
                "surface-dim" to ColorScheme::surfaceDim,
                "surface-bright" to ColorScheme::surfaceBright,
                "surface-container-lowest" to ColorScheme::surfaceContainerLowest,
                "surface-container-low" to ColorScheme::surfaceContainerLow,
                "surface-container" to ColorScheme::surfaceContainer,
                "surface-container-high" to ColorScheme::surfaceContainerHigh,
                "surface-container-highest" to ColorScheme::surfaceContainerHighest,
                "surface-tint" to ColorScheme::surfaceTint,
                "inverse-surface" to ColorScheme::inverseSurface,
                "inverse-on-surface" to ColorScheme::inverseOnSurface,
                "outline" to ColorScheme::outline,
                "outline-variant" to ColorScheme::outlineVariant,
                "scrim" to ColorScheme::scrim,
                "background" to ColorScheme::background,
                "on-background" to ColorScheme::onBackground,
                "primary-fixed" to ColorScheme::primaryFixed,
                "primary-fixed-dim" to ColorScheme::primaryFixedDim,
                "on-primary-fixed" to ColorScheme::onPrimaryFixed,
                "on-primary-fixed-variant" to ColorScheme::onPrimaryFixedVariant,
                "secondary-fixed" to ColorScheme::secondaryFixed,
                "secondary-fixed-dim" to ColorScheme::secondaryFixedDim,
                "on-secondary-fixed" to ColorScheme::onSecondaryFixed,
                "on-secondary-fixed-variant" to ColorScheme::onSecondaryFixedVariant,
                "tertiary-fixed" to ColorScheme::tertiaryFixed,
                "tertiary-fixed-dim" to ColorScheme::tertiaryFixedDim,
                "on-tertiary-fixed" to ColorScheme::onTertiaryFixed,
                "on-tertiary-fixed-variant" to ColorScheme::onTertiaryFixedVariant,
            )

        val textStyles: Map<String, (Typography) -> TextStyle> =
            mapOf(
                "display-large" to Typography::displayLarge,
                "display-medium" to Typography::displayMedium,
                "display-small" to Typography::displaySmall,
                "headline-large" to Typography::headlineLarge,
                "headline-medium" to Typography::headlineMedium,
                "headline-small" to Typography::headlineSmall,
                "title-large" to Typography::titleLarge,
                "title-medium" to Typography::titleMedium,
                "title-small" to Typography::titleSmall,
                "body-large" to Typography::bodyLarge,
                "body-medium" to Typography::bodyMedium,
                "body-small" to Typography::bodySmall,
                "label-large" to Typography::labelLarge,
                "label-medium" to Typography::labelMedium,
                "label-small" to Typography::labelSmall,
            )

        val shapes: Map<String, (Shapes) -> Any> =
            mapOf(
                "shape-extra-small" to Shapes::extraSmall,
                "shape-small" to Shapes::small,
                "shape-medium" to Shapes::medium,
                "shape-large" to Shapes::large,
                "shape-extra-large" to Shapes::extraLarge,
            )

        val spacing: Map<String, Dp> =
            mapOf(
                "space-xs" to DewfallSpacing.xs,
                "space-sm" to DewfallSpacing.sm,
                "space-md" to DewfallSpacing.md,
                "space-lg" to DewfallSpacing.lg,
                "space-xl" to DewfallSpacing.xl,
                "margin" to DewfallSpacing.margin,
                "gutter" to DewfallSpacing.gutter,
            )
    }
}
