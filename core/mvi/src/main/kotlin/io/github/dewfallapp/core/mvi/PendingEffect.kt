package io.github.dewfallapp.core.mvi

/**
 * An effect that the UI hasn't handled yet. The id is unique within one ViewModel, so the UI can
 * report exactly which effect it handled, even when two effects are equal.
 */
data class PendingEffect<out E : MviEffect>(val id: Long, val effect: E)
