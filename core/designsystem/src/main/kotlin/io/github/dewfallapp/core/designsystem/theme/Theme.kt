package io.github.dewfallapp.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Dewfall's Material 3 theme. Every screen draws inside it and reads colors, text styles and shapes
 * from MaterialTheme, never from fixed values. docs/design/claude-design/design-system/README.md
 * says which role and style each part of a screen uses.
 *
 * @param darkTheme whether to use the dark scheme. It follows the system's dark theme by default.
 *   The fullscreen player passes true, because it uses the dark scheme in light mode too.
 * @param dynamicColor whether to take the colors from the wallpaper on Android 12 and later, as the
 *   design brief asks. Older versions always get Dewfall's own palette. The app has no setting to
 *   turn dynamic color off, so only previews pass false.
 */
@Composable
fun DewfallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DewfallDarkColors
            else -> DewfallLightColors
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DewfallTypography,
        shapes = DewfallShapes,
        content = content,
    )
}
