package io.github.dewfallapp

import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.Hyphens
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * The largest font scale that Android's settings offer since Android 14. Android 10's settings stop
 * at 1.3, but the shell sets 2 on any version, as the screenshot tests' large font previews use.
 */
private const val LARGEST_FONT_SCALE = "2.0"

/** How far, in pixels, a measured edge may differ from another without counting, for rounding. */
private const val EDGE_PX = 0.5f

/** How long the test waits for the navigation bar after the app starts. */
private const val SCREEN_TIMEOUT_MS = 10_000L

/**
 * The navigation bar at the largest font size, as the design system's NavigationBar notes ask: the
 * bar grows rather than cut a label off. It sets the device's font scale to 2, the largest that
 * Android 14 and later offer in their settings, starts the app, and checks that each label lies
 * inside its item, and that its text has no ellipsis, fits its height, has no line wider than its
 * box, and breaks inside a word only with a hyphen, as the maintainer chose on November 28, 2026
 * for "Downloads". It puts the font scale back after. See docs/testing.md#emulator-tests.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationBarLargeFontTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    /** The font scale before the test, or "null" when the setting was never set. */
    private var fontScaleBefore = "null"

    @BeforeTest
    fun setUp() {
        hilt.inject()
        fontScaleBefore = shell("settings get system font_scale")
        shell("settings put system font_scale $LARGEST_FONT_SCALE")
    }

    @AfterTest
    fun tearDown() {
        // A setting that was never set reads as "null", so it goes back to unset.
        if (fontScaleBefore == "null" || fontScaleBefore.isBlank()) {
            shell("settings delete system font_scale")
        } else {
            shell("settings put system font_scale $fontScaleBefore")
        }
    }

    @Test
    fun noLabelIsCutOffAtTheLargestFont() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val labels =
                listOf(
                    R.string.navigation_inbox,
                    R.string.navigation_channels,
                    R.string.navigation_downloads,
                )
            for (label in labels) {
                val text = context.getString(label)
                val item = hasText(text) and isSelectable()
                compose.waitUntil(SCREEN_TIMEOUT_MS) {
                    compose.onAllNodes(item).fetchSemanticsNodes().isNotEmpty()
                }
                val itemBounds = compose.onNode(item).getUnclippedBoundsInRoot()
                // The label's own text node, inside the item, so not the top bar's title.
                val labelNode =
                    compose.onNode(
                        hasText(text) and hasAnyAncestor(isSelectable()),
                        useUnmergedTree = true,
                    )
                val labelBounds = labelNode.getUnclippedBoundsInRoot()
                assertTrue(
                    labelBounds.top >= itemBounds.top && labelBounds.bottom <= itemBounds.bottom,
                    "A label leaves the navigation bar",
                )
                val layouts = mutableListOf<TextLayoutResult>()
                labelNode
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(layouts)
                val layout = layouts.single()
                // Not hasVisualOverflow: a label laid out in a wider box than it needs reports
                // didOverflowWidth although every glyph shows. A cut-off label has an ellipsis,
                // overflows its height, or has lines past its box.
                val lastLine = layout.lineCount - 1
                assertFalse(layout.didOverflowHeight, "A label is cut off at the bottom")
                assertFalse(layout.isLineEllipsized(lastLine), "A label ends with an ellipsis")
                assertTrue(
                    layout.getLineBottom(lastLine) <= layout.size.height,
                    "A label is cut off",
                )
                for (line in 0..lastLine) {
                    assertTrue(
                        // The line's own width: a centered label's lines are placed in the
                        // width it was offered, which can be wider than its box.
                        layout.getLineRight(line) - layout.getLineLeft(line) <=
                            layout.size.width + EDGE_PX,
                        "A label is wider than its box",
                    )
                }
                assertBreaksOnlyWithAHyphen(layout)
            }
        }
    }

    /**
     * Checks that every line of [layout] that ends inside a word draws a hyphen there: the line is
     * wider than its own letters, measured alone with the same style and no hyphenation. A word
     * broken where it ran out of room, such as "Download" and "s", fails.
     */
    private fun assertBreaksOnlyWithAHyphen(layout: TextLayoutResult) {
        val input = layout.layoutInput
        val text = input.text.text
        val measurer =
            TextMeasurer(createFontFamilyResolver(context), input.density, input.layoutDirection)
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            if (end !in 1 until text.length || !text[end - 1].isLetter() || !text[end].isLetter()) {
                continue
            }
            val start = layout.getLineStart(line)
            val letters =
                measurer
                    .measure(text.substring(start, end), input.style.copy(hyphens = Hyphens.None))
                    .size
                    .width
            val drawn = layout.getLineRight(line) - layout.getLineLeft(line)
            assertTrue(drawn > letters + EDGE_PX, "A label breaks inside a word without a hyphen")
        }
    }

    /**
     * Runs [command] as the shell user, which may change system settings, and returns its output.
     */
    private fun shell(command: String): String {
        val output = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).use {
            it.readBytes().decodeToString().trim()
        }
    }
}
