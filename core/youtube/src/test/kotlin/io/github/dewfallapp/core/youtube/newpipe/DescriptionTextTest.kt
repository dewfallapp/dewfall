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
    fun `a link that YouTube didn't shorten keeps its text`() {
        assertEquals(
            "See example.com/page now",
            htmlDescriptionToText(
                """See <a href="https://example.com/page">example.com/page</a> now""",
            ),
        )
    }

    @Test
    fun `a shortened link shows its whole address`() {
        // As NewPipeExtractor wrote the saved set VIDEO_BIG_BUCK_BUNNY's first link.
        assertEquals(
            "More info:  https://studio.blender.org/projects/big-buck-bunny/\n",
            htmlDescriptionToText(
                "More info: &nbsp;" +
                    """<a href="https://studio.blender.org/projects/big-buck-bunny/">""" +
                    "https://studio.blender.org/projects/b...</a><br>",
            ),
        )
    }

    @Test
    fun `a link shortened with an ellipsis character shows its whole address`() {
        assertEquals(
            "https://example.com/a/long/path",
            htmlDescriptionToText(
                """<a href="https://example.com/a/long/path">https://example.com/a/lo""" +
                    "\u2026</a>",
            ),
        )
    }

    @Test
    fun `a link shown whole keeps its text, even when its address differs`() {
        // The saved set's second link, whose address adds a slash.
        assertEquals(
            "at: http://bbb3d.renderfarming.net",
            htmlDescriptionToText(
                """at: <a href="http://bbb3d.renderfarming.net/">""" +
                    "http://bbb3d.renderfarming.net</a>",
            ),
        )
    }

    @Test
    fun `a timestamp, a hashtag and a title that ends with dots keep their text`() {
        assertEquals(
            "1:23 #nasa Wait for it...",
            htmlDescriptionToText(
                """<a href="https://www.youtube.com/watch?v=aqz-KE-bpKQ&amp;t=83">1:23</a> """ +
                    """<a href="https://www.youtube.com/hashtag/nasa">#nasa</a> """ +
                    """<a href="https://www.youtube.com/watch?v=aqz-KE-bpKQ">Wait for it...</a>""",
            ),
        )
    }

    @Test
    fun `a shortened link keeps its text when its address doesn't start with it`() {
        assertEquals(
            "https://youtu.be/aqz-KE...",
            htmlDescriptionToText(
                """<a href="https://www.youtube.com/watch?v=aqz-KE-bpKQ">""" +
                    "https://youtu.be/aqz-KE...</a>",
            ),
        )
    }

    @Test
    fun `a shortened link keeps its text when its address is no longer than it`() {
        assertEquals(
            "https://example.com/a...",
            htmlDescriptionToText(
                """<a href="https://example.com/a">https://example.com/a...</a>""",
            ),
        )
    }

    @Test
    fun `text that is only a scheme and dots shows the longer address`() {
        assertEquals(
            "https://example.com/a",
            htmlDescriptionToText("""<a href="https://example.com/a">https://...</a>"""),
        )
    }

    @Test
    fun `a scheme in capitals isn't read as a shortened address`() {
        assertEquals(
            "HTTPS://EXAMPLE.COM/A...",
            htmlDescriptionToText(
                """<a href="HTTPS://EXAMPLE.COM/ABC">HTTPS://EXAMPLE.COM/A...</a>""",
            ),
        )
    }

    @Test
    fun `an address with a space, control or format character keeps the shortened text`() {
        // A space, a no-break space, a line feed, the bell, a right-to-left override, a zero-width
        // space, and a tag character above U+FFFF, which Kotlin holds as two Chars.
        val characters = listOf(" ", "&nbsp;", "&#10;", "&#7;", "&#x202E;", "&#x200B;", "&#xE0041;")
        characters.forEach { character ->
            assertEquals(
                "https://example.com/a...",
                htmlDescriptionToText(
                    """<a href="https://example.com/ab${character}c">""" +
                        "https://example.com/a...</a>",
                ),
                character,
            )
        }
    }

    @Test
    fun `an escaped ampersand in a shortened link's address becomes an ampersand`() {
        assertEquals(
            "https://example.com/?a=1&b=2",
            htmlDescriptionToText(
                """<a href="https://example.com/?a=1&amp;b=2">https://example.com/?a...</a>""",
            ),
        )
    }

    @Test
    fun `a shortened link with a bold part shows its whole address`() {
        assertEquals(
            "https://example.com/a/long",
            htmlDescriptionToText(
                """<a href="https://example.com/a/long">https://example.com/<b>a/lo</b>...</a>""",
            ),
        )
    }

    @Test
    fun `a shortened link whose bold text starts inside it shows its whole address`() {
        // NewPipeExtractor splits the bold text, not the link.
        assertEquals(
            "https://example.com/a/long now",
            htmlDescriptionToText(
                """<a href="https://example.com/a/long">https:<b>//example.com/a/lo...</b></a>""" +
                    "<b> no</b>w",
            ),
        )
    }

    @Test
    fun `a shortened link that bold text splits keeps its text`() {
        // Bold text that starts before the link and ends inside it splits the link in two.
        assertEquals(
            "See https://example.com/a/lo... now",
            htmlDescriptionToText(
                """<b>See <a href="https://example.com/a/long">https:</a></b>""" +
                    """<a href="https://example.com/a/long">//example.com/a/lo...</a> now""",
            ),
        )
    }

    @Test
    fun `a shortened address that isn't a link stays as it is`() {
        assertEquals(
            "More info: https://example.com/a/lo...",
            htmlDescriptionToText("More info: https://example.com/a/lo..."),
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
    fun `a plain text or Markdown description stays as it is`() {
        assertEquals(
            "a <b> &amp; c",
            Description("a <b> &amp; c", Description.Type.PLAIN_TEXT).toPlainText(),
        )
        assertEquals("a **b**", Description("a **b**", Description.Type.MARKDOWN).toPlainText())
    }

    @Test
    fun `an HTML description becomes plain text`() {
        assertEquals("a\nb", Description("a<br>b", Description.Type.HTML).toPlainText())
    }
}
