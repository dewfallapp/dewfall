package io.github.dewfallapp.core.download

import android.os.Build
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsksForForegroundTest {
    @Test
    fun `Android 10 and 11 always ask for a foreground service`() {
        for (sdk in listOf(Build.VERSION_CODES.Q, Build.VERSION_CODES.R)) {
            assertTrue(asksForForeground(sdk, inForeground = false))
            assertTrue(asksForForeground(sdk, inForeground = true))
        }
    }

    @Test
    fun `Android 12 and 13 ask only while the app is in the foreground`() {
        for (sdk in listOf(Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU)) {
            assertTrue(asksForForeground(sdk, inForeground = true))
            assertFalse(asksForForeground(sdk, inForeground = false))
        }
    }
}
