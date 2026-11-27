package io.github.dewfallapp.feature.downloads.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.dewfallapp.core.mvi.MviEffect
import io.github.dewfallapp.core.mvi.PendingEffect

/**
 * Handles [pendingEffects] one at a time, oldest first, as docs/architecture.md#effects describes:
 * it runs [handle] on the first effect while the screen is started, then reports it through
 * [onEffectFinish]. It is keyed by the effect's id, so a recomposition doesn't handle the same
 * effect twice. A stop, such as the app going to the background, cancels a [handle] that hasn't
 * returned, such as a message that still shows, and the next start runs it again, so an effect is
 * never reported handled while the screen didn't show it. The Downloads screen is the first screen
 * with effects; this moves to a shared module when a second one needs it. Its lambda is named in
 * the present tense, as the Compose rules that config/detekt/detekt.yml lists ask.
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
            handleWhileStarted(
                lifecycle = lifecycle,
                handle = { currentHandle(first.effect) },
                finish = { currentOnEffectFinish(first.id) },
            )
        }
    }
}

/**
 * Runs [handle] while [lifecycle] is at least STARTED, then [finish] once [handle] returned. A stop
 * cancels a [handle] that hasn't returned, and the next start runs it again from the beginning.
 * Once [finish] ran, a later start runs nothing. It returns when [lifecycle] is destroyed, and its
 * caller cancels it once the effect is gone.
 */
internal suspend fun handleWhileStarted(
    lifecycle: Lifecycle,
    handle: suspend () -> Unit,
    finish: () -> Unit,
) {
    var finished = false
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        if (!finished) {
            handle()
            finished = true
            finish()
        }
    }
}
