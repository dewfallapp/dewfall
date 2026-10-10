# Compose theme

Drop these four files into the app's `ui/theme` package. The values are generated from this system's `tokens.json`, so a token edit here should be copied there. The package name `app.dewfall` is a placeholder.

## Color.kt

Every role, light and dark. Fixed roles keep the same value in both schemes; pass them to the scheme builders if your Material 3 version accepts fixed roles, otherwise keep them as plain values. Dewfall's components don't use them yet.

```kotlin
package app.dewfall.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val DewfallLightColors = lightColorScheme(
    primary = Color(0xFF26503C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3E6853),
    onPrimaryContainer = Color(0xFFB7E5CA),
    inversePrimary = Color(0xFFA3D1B7),
    secondary = Color(0xFF4F6354),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE5D2),
    onSecondaryContainer = Color(0xFF536758),
    tertiary = Color(0xFF264D5B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF3F6574),
    onTertiaryContainer = Color(0xFFB9E1F3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    surface = Color(0xFFF5FBF2),
    onSurface = Color(0xFF171D18),
    surfaceVariant = Color(0xFFDEE4DC),
    onSurfaceVariant = Color(0xFF414943),
    surfaceDim = Color(0xFFD5DCD3),
    surfaceBright = Color(0xFFF5FBF2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5ED),
    surfaceContainer = Color(0xFFE9F0E7),
    surfaceContainerHigh = Color(0xFFE3EAE1),
    surfaceContainerHighest = Color(0xFFDEE4DC),
    surfaceTint = Color(0xFF3D6752),
    inverseSurface = Color(0xFF2B322C),
    inverseOnSurface = Color(0xFFECF3EA),
    outline = Color(0xFF717973),
    outlineVariant = Color(0xFFC1C8C2),
    scrim = Color(0xFF000000),
    background = Color(0xFFF5FBF2),
    onBackground = Color(0xFF171D18),
)

val DewfallDarkColors = darkColorScheme(
    primary = Color(0xFFA5D0B8),
    onPrimary = Color(0xFF0A3724),
    primaryContainer = Color(0xFF264F3B),
    onPrimaryContainer = Color(0xFFC0ECD3),
    inversePrimary = Color(0xFF3D6752),
    secondary = Color(0xFFB6CCBA),
    onSecondary = Color(0xFF223528),
    secondaryContainer = Color(0xFF384B3D),
    onSecondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFA6CCDE),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC2E8FB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF313632),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceDim = Color(0xFF101411),
    surfaceBright = Color(0xFF363A36),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF181D19),
    surfaceContainer = Color(0xFF1C211D),
    surfaceContainerHigh = Color(0xFF272B27),
    surfaceContainerHighest = Color(0xFF313632),
    surfaceTint = Color(0xFFA5D0B8),
    inverseSurface = Color(0xFFDFE4DE),
    inverseOnSurface = Color(0xFF2B322C),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414942),
    scrim = Color(0xFF000000),
    background = Color(0xFF101411),
    onBackground = Color(0xFFDFE4DE),
)

// Fixed roles: identical in light and dark.
val PrimaryFixed = Color(0xFFBFEDD2)
val PrimaryFixedDim = Color(0xFFA3D1B7)
val OnPrimaryFixed = Color(0xFF002114)
val OnPrimaryFixedVariant = Color(0xFF254F3B)
val SecondaryFixed = Color(0xFFD2E8D5)
val SecondaryFixedDim = Color(0xFFB6CCBA)
val OnSecondaryFixed = Color(0xFF0D1F14)
val OnSecondaryFixedVariant = Color(0xFF384B3D)
val TertiaryFixed = Color(0xFFC1E9FA)
val TertiaryFixedDim = Color(0xFFA5CCDE)
val OnTertiaryFixed = Color(0xFF001F29)
val OnTertiaryFixedVariant = Color(0xFF244C5A)
```

## Type.kt

Bundle `Manrope-Variable.ttf` (from Google Fonts, same file as `fonts/Manrope-Variable.woff2` here) as `res/font/manrope.ttf`. One `Font` entry per weight Dewfall uses. On older Compose versions `FontVariation` needs `@OptIn(ExperimentalTextApi::class)`.

```kotlin
package app.dewfall.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.dewfall.R

private fun manrope(weight: Int) = Font(
    resId = R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600))

val DewfallTypography = Typography(
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W400, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W400, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W400, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W500, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W500, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W500, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W600, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W600, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.W500, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
```

## Shape.kt and spacing

```kotlin
package app.dewfall.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DewfallShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),  // shape-extra-small: badges, duration tags
    small = RoundedCornerShape(8.dp),       // shape-small: thumbnails
    medium = RoundedCornerShape(12.dp),     // shape-medium: video row state layer, chips, storage meter
    large = RoundedCornerShape(16.dp),      // shape-large: channel groups, bottom sheets
    extraLarge = RoundedCornerShape(24.dp), // shape-extra-large: dialogs
)
// shape-full is CircleShape.

object DewfallSpacing {
    val xs = 4.dp      // only inside badges and chips
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val margin = 16.dp
    val gutter = 16.dp
}
```

## Theme.kt

Dynamic color is on by default on Android 12 and later. Because it can replace every color, components must never rely on color alone.

```kotlin
package app.dewfall.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun DewfallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
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
```
