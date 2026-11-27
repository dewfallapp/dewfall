package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.dewfallapp.core.mvi.MviEffect
import io.github.dewfallapp.core.mvi.PendingEffect
import kotlinx.coroutines.flow.first

/**
 * Handles [pendingEffects] one at a time, oldest first, as docs/architecture.md#effects describes:
 * it waits until the screen is started, runs [handle] on the first effect, then reports it through
 * [onEffectFinish]. It is keyed by the effect's id, so a recomposition doesn't handle the same
 * effect twice, and an effect whose handling a stop or a rotation cancelled is handled again. The
 * Downloads screen is the first screen with effects; this moves to a shared module when a second
 * one needs it. Its lambda is named in the present tense, as the Compose rules that
 * config/detekt/detekt.yml lists ask.
 */
@Composable
internal fun <E : MviEffect> HandleEffects(
    pendingEffects: List<PendingEffect<E>>,
    onEffectFinish: (id: Long) -> Unit,
    handle: suspend (E) -> Unit,
) {
    val first = pendingEffects.firstOrNull()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentHandle by rememberUpdatedState(handle)
    val currentOnEffectFinish by rememberUpdatedState(onEffectFinish)
    if (first != null) {
        LaunchedEffect(first.id, lifecycle) {
            lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
            currentHandle(first.effect)
            currentOnEffectFinish(first.id)
        }
    }
}
