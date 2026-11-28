package io.github.dewfallapp.fake

import java.util.concurrent.CountDownLatch

/**
 * Stands in for the network in the emulator tests. While [youTubeReachable] is off, the fake
 * YouTube layer answers the details and the stream links with NETWORK, as the real layer does with
 * the network off. While [mediaReachable] is off, AssetMedia fails every request with an
 * IOException, which Media3 reports as a network error. Both start on, so a test that never touches
 * them runs as with a network. Hilt makes one for each test. See docs/testing.md#the-fakes.
 */
class FakeNetwork {
    @Volatile var youTubeReachable: Boolean = true

    @Volatile var mediaReachable: Boolean = true

    /**
     * While it is closed, AssetMedia holds every media request until it opens, so a download stays
     * in DOWNLOADING while a test looks at it. Null, the default, holds nothing.
     */
    @Volatile var mediaHold: CountDownLatch? = null

    /** Turns both off, or both on. */
    fun setReachable(reachable: Boolean) {
        youTubeReachable = reachable
        mediaReachable = reachable
    }
}
