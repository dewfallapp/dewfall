package io.github.dewfallapp.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.dewfallapp.core.designsystem.R

/**
 * Manrope, the app's only typeface. res/font/manrope.ttf is the variable font Manrope[wght].ttf,
 * version 4.505, from github.com/googlefonts/manrope, under the SIL Open Font License 1.1. Its
 * copyright notice and license ship in the APK as assets/licenses/Manrope-OFL.txt.
 *
 * The file's default weight is 200. Compose sets the weight axis of a res/font font only after the
 * font loads. On the Android 10 and 16 emulators that had no effect, and every weight drew
 * ExtraLight. So each weight has a font XML, such as res/font/manrope_regular.xml, that sets the
 * axis as the font loads. docs/architecture.md#the-theme has the details.
 *
 * The type scale uses 400, 500 and 600. 700 and 800 are for Android's Bold text setting, which
 * makes Compose ask for heavier weights, so text with the setting on still has a heavier font to
 * draw with. With a font for each weight, text never gets a synthetic bold.
 */
internal val Manrope =
    FontFamily(
        Font(R.font.manrope_regular, FontWeight.W400),
        Font(R.font.manrope_medium, FontWeight.W500),
        Font(R.font.manrope_semibold, FontWeight.W600),
        Font(R.font.manrope_bold, FontWeight.W700),
        Font(R.font.manrope_extrabold, FontWeight.W800),
    )

// The values come from the type tokens in docs/design/claude-design/design-system/tokens.json,
// where 1px is 1sp. A token edit there is copied here by hand, and DesignTokensTest fails until
// both agree.

/** The Material 3 type scale, all in Manrope. Screens read it from MaterialTheme.typography. */
internal val DewfallTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W400,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W400,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W400,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W500,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W500,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W500,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W600,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W600,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W600,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W400,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W400,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W400,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W600,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W600,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.W500,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )
