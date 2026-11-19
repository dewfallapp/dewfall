package io.github.dewfallapp.core.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Whether the network counts as metered: there is [hasNetwork] and it is [notMetered], Android's
 * NET_CAPABILITY_NOT_METERED. A network that is only temporarily not metered counts as metered, as
 * does no network, which errs toward saving data.
 */
internal fun isMetered(hasNetwork: Boolean, notMetered: Boolean): Boolean =
    !hasNetwork || !notMetered

/** Reads whether the active network is metered, when playback starts. */
internal class NetworkCheck @Inject constructor(@ApplicationContext context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun isMetered(): Boolean {
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        return isMetered(
            hasNetwork = capabilities != null,
            notMetered =
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true,
        )
    }
}
