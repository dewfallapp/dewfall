package io.github.dewfallapp.feature.takeoutimport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.Banner
import io.github.dewfallapp.core.designsystem.theme.DewfallSpacing
import io.github.dewfallapp.core.designsystem.theme.ReadableWidth
import io.github.dewfallapp.core.model.TakeoutImportFailure
import io.github.dewfallapp.feature.takeoutimport.logic.TakeoutImportState

/** The circle around a step's number, as in the "Onboarding 2, import channels" frame. */
private val StepNumberSize = 24.dp

/** The smallest height of the bottom button, Material's 48dp touch target and more. */
internal val BottomButtonHeight = 56.dp

/**
 * The Takeout import screen, after the design's "Onboarding 2, import channels" and "Flow 1:
 * channels imported" frames, without their step counter and "Skip for now", which belong to the
 * onboarding. It is stateless: it shows [state], and reports the button that opens the file picker
 * through [onChooseFile], the back arrow through [onBack], and Done through [onDone].
 *
 * Its background fills the window behind the system bars. Its content sits inside the safe drawing
 * area, in one column at most [ReadableWidth] wide, so nothing is under the system bars or the
 * display cutout.
 */
@Composable
internal fun TakeoutImportScreen(
    state: TakeoutImportState,
    onChooseFile: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.widthIn(max = ReadableWidth).fillMaxWidth()) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(horizontal = DewfallSpacing.xs),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.takeout_import_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                when (state) {
                    is TakeoutImportState.Imported -> Report(state, onDone)
                    else -> Instructions(state, onChooseFile)
                }
            }
        }
    }
}

/** The steps for getting the file, a failure if there was one, and the button to choose it. */
@Composable
private fun ColumnScope.Instructions(state: TakeoutImportState, onChooseFile: () -> Unit) {
    Column(
        modifier =
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DewfallSpacing.margin),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.takeout_import_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(top = DewfallSpacing.md).semantics { heading() },
        )
        Text(
            text = stringResource(R.string.takeout_import_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state is TakeoutImportState.Failed) FailureBanner(state.reason)
        GettingTheFile()
    }
    BottomArea {
        if (state == TakeoutImportState.Reading) {
            ReadingRow()
        } else {
            val label =
                if (state is TakeoutImportState.Failed) {
                    R.string.takeout_import_choose_another
                } else {
                    R.string.takeout_import_choose
                }
            WideButton(text = stringResource(label), onClick = onChooseFile)
        }
    }
}

/** The card with the three steps for getting the file from Takeout. */
@Composable
private fun GettingTheFile() {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(DewfallSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DewfallSpacing.md),
    ) {
        Text(
            text = stringResource(R.string.takeout_import_steps_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        val steps =
            listOf(
                R.string.takeout_import_step_1,
                R.string.takeout_import_step_2,
                R.string.takeout_import_step_3,
            )
        for ((index, step) in steps.withIndex()) {
            Step(number = index + 1, text = stringResource(step))
        }
        Text(
            text = stringResource(R.string.takeout_import_labels_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One numbered step. The circle grows with the font size, as ChannelEmblem does, so the number
 * always fits and the circle stays round.
 */
@Composable
private fun Step(number: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(DewfallSpacing.md)) {
        val circle = StepNumberSize * LocalDensity.current.fontScale.coerceAtLeast(1f)
        Box(
            modifier =
                Modifier.size(circle)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = number.toString(), style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Why the import subscribed to nothing. */
@Composable
private fun FailureBanner(reason: TakeoutImportFailure) {
    val message =
        when (reason) {
            TakeoutImportFailure.NO_CHANNELS -> R.string.takeout_import_no_channels
            TakeoutImportFailure.NOT_READABLE -> R.string.takeout_import_not_readable
            TakeoutImportFailure.TOO_LARGE -> R.string.takeout_import_too_large
        }
    Banner(
        message = stringResource(message),
        glyph = painterResource(DesignR.drawable.ic_error),
        glyphDescription = stringResource(R.string.takeout_import_error_glyph),
        glyphTint = MaterialTheme.colorScheme.error,
    )
}

/** What the bottom shows while the file is read: a progress circle and its line. */
@Composable
private fun ReadingRow() {
    Row(
        modifier =
            Modifier.fillMaxWidth().heightIn(min = BottomButtonHeight).semantics {
                liveRegion = LiveRegionMode.Polite
            },
        horizontalArrangement =
            Arrangement.spacedBy(DewfallSpacing.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        Text(
            text = stringResource(R.string.takeout_import_reading),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The area under the scrolling content, which holds the screen's one main button. */
@Composable
internal fun BottomArea(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = DewfallSpacing.margin, vertical = DewfallSpacing.md),
    ) {
        content()
    }
}

@Composable
internal fun WideButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = BottomButtonHeight),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
