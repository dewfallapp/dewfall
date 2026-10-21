package io.github.dewfallapp.core.model

/**
 * What a YouTube layer function returns: what it fetched, or why it failed. The layer reports every
 * problem with YouTube or the network as a [Failure] and never throws one, so a caller handles both
 * cases in one `when`, and a ViewModel can dispatch the result as an intent. See ADR 0008.
 */
sealed interface YouTubeResult<out T> {
    /** The call worked, and [value] holds what it fetched. */
    data class Success<out T>(val value: T) : YouTubeResult<T>

    /** The call failed. [error] says why, and holds no user data. */
    data class Failure(val error: YouTubeError) : YouTubeResult<Nothing>
}
