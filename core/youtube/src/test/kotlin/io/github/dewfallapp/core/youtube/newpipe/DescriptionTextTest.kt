package io.github.dewfallapp.core.youtube.newpipe

import kotlin.test.Test
import kotlin.test.assertEquals
import org.schabi.newpipe.extractor.stream.Description

class DescriptionTextTest {
    @Test
    fun `a line break becomes a newline`() {
        assertEquals("First line\nSecond line", htmlDescriptionToText("First line<br>Second line"))
    }

    @Test
    fun `a run of spaces keeps its spaces`() {
        // NewPipeExtractor writes two spaces as a space followed by &nbsp;.
        assertEquals("a  b", htmlDescriptionToText("a &nbsp;b"))
    }

    @Test
    fun `a link keeps only its text`() {
        assertEquals(
            "See example.com/page now",
            htmlDescriptionToText(
                """See <a href="https://example.com/page">example.com/page</a> now"""
            ),
        )
    }

    @Test
    fun `bold, italic and struck-through text keep their text`() {
        assertEquals(
            "bold italic struck",
            htmlDescriptionToText("<b>bold</b> <i>italic</i> <s>struck</s>"),
        )
    }

    @Test
    fun `escaped text comes back as written`() {
        // The path that escapes the text, from YouTube's attributedDescription.
        assertEquals(
            "1 < 2 & <b> is text",
            htmlDescriptionToText("1 &lt; 2 &amp; &lt;b&gt; is text"),
        )
    }

    @Test
    fun `a less-than sign that NewPipeExtractor didn't escape stays text`() {
        // The older path, from the description's runs, which leaves the text unescaped.
        assertEquals(
            "I <3 you\nif a<b and x <y> z, then <a> isn't a link",
            htmlDescriptionToText("I <3 you<br>if a<b and x <y> z, then <a> isn't a link"),
        )
    }

    @Test
    fun `an unescaped less-than sign next to a real tag stays text too`() {
        assertEquals(
            "a <b c and bold",
            htmlDescriptionToText("""a <b c and <a href="https://x.example">bold</a>"""),
        )
    }

    @Test
    fun `a plain text description stays as it is`() {
        assertEquals(
            "a <b> &amp; c",
            Description("a <b> &amp; c", Description.PLAIN_TEXT).toPlainText(),
        )
    }

    @Test
    fun `an HTML description becomes plain text`() {
        assertEquals("a\nb", Description("a<br>b", Description.HTML).toPlainText())
    }
}
