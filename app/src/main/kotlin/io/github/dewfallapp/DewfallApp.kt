package io.github.dewfallapp

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The root of the app's UI. It uses Material 3's default light and dark color schemes, following
 * the system's dark mode, until #26 adds Dewfall's theme.
 */
@Composable
internal fun DewfallApp() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize()) { PlaceholderScreen() }
    }
}

/** Stands in for the app's screens until the first feature arrives. */
@Composable
private fun PlaceholderScreen(modifier: Modifier = Modifier) {
    // The app draws edge to edge, so safeDrawingPadding keeps the text clear of the system bars,
    // the display cutout and the keyboard.
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.placeholder_message),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Composable
private fun DewfallAppPreview() {
    DewfallApp()
}
