package io.github.dewfallapp.feature.inbox.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.dewfallapp.core.designsystem.R as DesignR
import io.github.dewfallapp.core.designsystem.component.Banner
import io.github.dewfallapp.core.model.UploadsCheckFailure

// The inbox's parts for the check for new videos: the top bar's button and the banner.

/** How long the check button's glyph takes to turn once, as the design system's TopAppBar says. */
private const val TURN_MILLIS = 1_600

/**
 * The "Check for new videos" button. While [checking], it can't be tapped, and its glyph turns once
 * every 1.6 seconds, linear, when [glyphTurns]. The glyph keeps its color, so the turn shows.
 */
@Composable
internal fun CheckButton(
    checking: Boolean,
    glyphTurns: Boolean,
    onCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onCheck, enabled = !checking, modifier = modifier) {
        val glyph = painterResource(DesignR.drawable.ic_sync)
        val description = stringResource(R.string.inbox_check)
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        if (checking && glyphTurns) {
            val turn = rememberInfiniteTransition(label = "check")
            val angle =
                turn.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(TURN_MILLIS, easing = LinearEasing)),
                    label = "turn",
                )
            // The angle is read in the draw phase only, so the turn recomposes nothing.
            Icon(
                painter = glyph,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.graphicsLayer { rotationZ = angle.value },
            )
        } else {
            Icon(painter = glyph, contentDescription = description, tint = tint)
        }
    }
}

/**
 * The banner after a check that stopped early, as the design system's Banner gives its kinds:
 * wifi_off when YouTube couldn't be reached, the error glyph in the error color when it isn't
 * loading, each with "Try again", which calls [onTryAgain].
 */
@Composable
internal fun CheckBanner(
    failure: UploadsCheckFailure,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (failure) {
        UploadsCheckFailure.CANT_REACH_YOUTUBE -> {
            Banner(
                message = stringResource(R.string.inbox_check_failed_network),
                glyph = painterResource(DesignR.drawable.ic_wifi_off),
                glyphDescription = stringResource(R.string.inbox_check_failed_network_icon),
                glyphTint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier,
                actionLabel = stringResource(R.string.inbox_try_again),
                onAction = onTryAgain,
            )
        }
        UploadsCheckFailure.YOUTUBE_NOT_LOADING -> {
            Banner(
                message = stringResource(R.string.inbox_check_failed_youtube),
                glyph = painterResource(DesignR.drawable.ic_error),
                glyphDescription = stringResource(R.string.inbox_check_failed_youtube_icon),
                glyphTint = MaterialTheme.colorScheme.error,
                modifier = modifier,
                actionLabel = stringResource(R.string.inbox_try_again),
                onAction = onTryAgain,
            )
        }
    }
}
