package io.github.dewfallapp

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.dewfallapp.feature.inbox.ui.R as InboxR

/** How long a test waits for the inbox's search button after the app starts. */
private const val INBOX_TIMEOUT_MS = 10_000L

/**
 * Opens Search YouTube from the inbox, the app's first screen, with the top bar's search button. It
 * finds the button by its content description, "Search YouTube", which no other node has: the "No
 * channels yet" button says it as text, and its glyph has no description of its own. See
 * docs/testing.md#emulator-tests.
 */
internal fun AndroidComposeTestRule<*, *>.openSearchFromInbox() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val search = context.getString(InboxR.string.inbox_search)
    waitUntil(INBOX_TIMEOUT_MS) {
        onAllNodes(hasContentDescription(search)).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithContentDescription(search).performClick()
}
