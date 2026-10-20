package io.github.dewfallapp.core.designsystem.theme

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewDynamicColors
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.Wallpapers
import androidx.compose.ui.unit.dp
import kotlin.reflect.KProperty1

// Previews of the theme as screens get it, read from MaterialTheme: every color role except scrim
// and surfaceTint with the roles that sit on it, every text style, and every shape. Android Studio
// shows them, and docs/architecture.md#the-theme explains how to open them on a phone. The sample
// text names roles and styles, and previews never reach users, so it isn't in string resources.

/**
 * Manrope covers the first four words, in Latin, Cyrillic, Greek and Vietnamese. The rest, in
 * Japanese, Arabic, Hindi and Thai, fall back to the phone's own fonts.
 */
private const val SCRIPT_SAMPLE = "Dew Роса Δροσιά Sương 露 ندى ओस น้ำค้าง"

private const val SHAPES_PER_ROW = 3
private val StyleNameWidth = 112.dp
private val ShapeSize = 72.dp
private val OutlineWidth = 2.dp

private typealias Role = KProperty1<ColorScheme, Color>

/**
 * A tile filled with one color role. Each line of text names a role and is drawn in that role's
 * color, so the preview shows the pairs that screens use. A role that marks an edge, such as
 * outline, draws as a border on a fill instead, and names the tile.
 */
private class Swatch(val fill: Role, val text: List<Role>, val border: Role? = null) {
    val name: String
        get() = (border ?: fill).name
}

/** The accent, error, fixed and inverse roles. */
private val AccentSwatches =
    listOf(
        Swatch(ColorScheme::primary, listOf(ColorScheme::onPrimary)),
        Swatch(ColorScheme::primaryContainer, listOf(ColorScheme::onPrimaryContainer)),
        Swatch(ColorScheme::secondary, listOf(ColorScheme::onSecondary)),
        Swatch(ColorScheme::secondaryContainer, listOf(ColorScheme::onSecondaryContainer)),
        Swatch(ColorScheme::tertiary, listOf(ColorScheme::onTertiary)),
        Swatch(ColorScheme::tertiaryContainer, listOf(ColorScheme::onTertiaryContainer)),
        Swatch(ColorScheme::error, listOf(ColorScheme::onError)),
        Swatch(ColorScheme::errorContainer, listOf(ColorScheme::onErrorContainer)),
        Swatch(
            ColorScheme::primaryFixed,
            listOf(ColorScheme::onPrimaryFixed, ColorScheme::onPrimaryFixedVariant),
        ),
        Swatch(
            ColorScheme::primaryFixedDim,
            listOf(ColorScheme::onPrimaryFixed, ColorScheme::onPrimaryFixedVariant),
        ),
        Swatch(
            ColorScheme::secondaryFixed,
            listOf(ColorScheme::onSecondaryFixed, ColorScheme::onSecondaryFixedVariant),
        ),
        Swatch(
            ColorScheme::secondaryFixedDim,
            listOf(ColorScheme::onSecondaryFixed, ColorScheme::onSecondaryFixedVariant),
        ),
        Swatch(
            ColorScheme::tertiaryFixed,
            listOf(ColorScheme::onTertiaryFixed, ColorScheme::onTertiaryFixedVariant),
        ),
        Swatch(
            ColorScheme::tertiaryFixedDim,
            listOf(ColorScheme::onTertiaryFixed, ColorScheme::onTertiaryFixedVariant),
        ),
        Swatch(
            ColorScheme::inverseSurface,
            listOf(ColorScheme::inverseOnSurface, ColorScheme::inversePrimary),
        ),
    )

private val OnSurfaces = listOf(ColorScheme::onSurface, ColorScheme::onSurfaceVariant)

/**
 * The surface roles and the outlines. scrim and surfaceTint carry no text, so they aren't shown.
 * DesignTokensTest checks their values.
 */
private val SurfaceSwatches =
    listOf(
        Swatch(ColorScheme::surfaceDim, OnSurfaces),
        Swatch(ColorScheme::surface, OnSurfaces),
        Swatch(ColorScheme::surfaceBright, OnSurfaces),
        Swatch(ColorScheme::surfaceContainerLowest, OnSurfaces),
        Swatch(ColorScheme::surfaceContainerLow, OnSurfaces),
        Swatch(ColorScheme::surfaceContainer, OnSurfaces),
        Swatch(ColorScheme::surfaceContainerHigh, OnSurfaces),
        Swatch(ColorScheme::surfaceContainerHighest, OnSurfaces),
        Swatch(ColorScheme::surfaceVariant, OnSurfaces),
        Swatch(ColorScheme::background, listOf(ColorScheme::onBackground)),
        Swatch(ColorScheme::surface, OnSurfaces, border = ColorScheme::outline),
        Swatch(ColorScheme::surface, OnSurfaces, border = ColorScheme::outlineVariant),
    )

/** Fills the screen with surface, keeps clear of the system bars on a phone, and scrolls. */
@Composable
private fun PreviewColumn(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .safeDrawingPadding()
                    .padding(DewfallSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DewfallSpacing.sm),
            content = content,
        )
    }
}

@Composable
private fun SwatchGrid(swatches: List<Swatch>) {
    PreviewColumn {
        swatches.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.sm)) {
                row.forEach { swatch -> SwatchTile(swatch, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SwatchTile(swatch: Swatch, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val edge = swatch.border?.let { Modifier.border(OutlineWidth, it(colors), shape) } ?: Modifier
    Column(
        modifier =
            modifier.background(swatch.fill(colors), shape).then(edge).padding(DewfallSpacing.sm)
    ) {
        Text(
            text = swatch.name,
            style = MaterialTheme.typography.labelLarge,
            color = swatch.text.first()(colors),
        )
        swatch.text.forEach { role ->
            Text(
                text = role.name,
                style = MaterialTheme.typography.labelSmall,
                color = role(colors),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun AccentColorsPreview() {
    DewfallTheme(dynamicColor = false) { SwatchGrid(AccentSwatches) }
}

@PreviewLightDark
@Composable
private fun SurfaceColorsPreview() {
    DewfallTheme(dynamicColor = false) { SwatchGrid(SurfaceSwatches) }
}

@PreviewDynamicColors
@Preview(
    name = "Dynamic dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun AccentColorsDynamicPreview() {
    DewfallTheme { SwatchGrid(AccentSwatches) }
}

@PreviewDynamicColors
@Preview(
    name = "Dynamic dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE,
)
@Composable
private fun SurfaceColorsDynamicPreview() {
    DewfallTheme { SwatchGrid(SurfaceSwatches) }
}

@PreviewLightDark
@Composable
private fun TypeScalePreview() {
    DewfallTheme(dynamicColor = false) {
        val typography = MaterialTheme.typography
        val styles =
            listOf(
                "displayLarge" to typography.displayLarge,
                "displayMedium" to typography.displayMedium,
                "displaySmall" to typography.displaySmall,
                "headlineLarge" to typography.headlineLarge,
                "headlineMedium" to typography.headlineMedium,
                "headlineSmall" to typography.headlineSmall,
                "titleLarge" to typography.titleLarge,
                "titleMedium" to typography.titleMedium,
                "titleSmall" to typography.titleSmall,
                "bodyLarge" to typography.bodyLarge,
                "bodyMedium" to typography.bodyMedium,
                "bodySmall" to typography.bodySmall,
                "labelLarge" to typography.labelLarge,
                "labelMedium" to typography.labelMedium,
                "labelSmall" to typography.labelSmall,
            )
        PreviewColumn {
            styles.forEach { (name, style) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        modifier = Modifier.width(StyleNameWidth),
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = "Dewfall", style = style)
                }
            }
            // Shows whether the fallback fonts follow the weight: 400 in bodyLarge, 600 in
            // titleMedium.
            Text(text = SCRIPT_SAMPLE, style = typography.bodyLarge)
            Text(text = SCRIPT_SAMPLE, style = typography.titleMedium)
        }
    }
}

@PreviewLightDark
@Composable
private fun ShapeScalePreview() {
    DewfallTheme(dynamicColor = false) {
        val shapes = MaterialTheme.shapes
        val scale: List<Pair<String, Shape>> =
            listOf(
                "extraSmall" to shapes.extraSmall,
                "small" to shapes.small,
                "medium" to shapes.medium,
                "large" to shapes.large,
                "extraLarge" to shapes.extraLarge,
                "CircleShape" to CircleShape,
            )
        PreviewColumn {
            scale.chunked(SHAPES_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md)) {
                    row.forEach { (name, shape) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(ShapeSize)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, shape)
                            )
                            Text(text = name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
