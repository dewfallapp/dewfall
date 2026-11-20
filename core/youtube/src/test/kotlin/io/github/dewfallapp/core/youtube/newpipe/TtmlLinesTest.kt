package io.github.dewfallapp.core.youtube.newpipe

import io.github.dewfallapp.core.model.SubtitleLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** The start of a TTML file as YouTube writes it, up to its body. */
private const val TTML_START =
    """<?xml version="1.0" encoding="utf-8" ?>
<tt xml:lang="en" xmlns="http://www.w3.org/ns/ttml" xmlns:tts="http://www.w3.org/ns/ttml#styling">
<head>
<styling>
<style xml:id="s2" tts:fontSize=".72c" tts:backgroundColor="black" tts:color="white"/>
</styling>
</head>
<body region="r1">
<div>
"""

/** The end of a TTML file as YouTube writes it, after its last line. */
private const val TTML_END =
    """
</div>
</body>
</tt>
"""

/** A TTML file as YouTube writes it, with [paragraphs] in its body. */
private fun ttml(vararg paragraphs: String) = TTML_START + paragraphs.joinToString("\n") + TTML_END

/** A line of a TTML file from [begin] to [end] with [content], as YouTube writes one. */
private fun p(begin: String, end: String, content: String) =
    """<p begin="$begin" end="$end" style="s2">$content</p>"""

class TtmlLinesTest {
    private val log = mutableListOf<String>()
    private val skipped = SkippedParts(YouTubeCall.SUBTITLE_LINES)

    private fun linesOf(ttml: String): List<SubtitleLine> = ttmlLinesOf(ttml, skipped)

    /** The texts of the lines in [ttml]. */
    private fun textsOf(ttml: String): List<String> = linesOf(ttml).map { it.text }

    /** The WARN line that the call logs for what it left out, or nothing. */
    private fun warnings(): List<String> {
        skipped.warnIfAny(recordingLogger(log).withTag(LOG_AREA))
        return log
    }

    @Test
    fun `reads each line's times and text, as YouTube writes them`() {
        val lines =
            linesOf(
                ttml(
                    p("00:00:01.200", "00:00:03.360", "All right, so here we are"),
                    p("00:00:05.318", "00:00:07.974", "the cool thing"),
                ),
            )

        assertEquals(
            listOf(
                SubtitleLine(1200.milliseconds, 3360.milliseconds, "All right, so here we are"),
                SubtitleLine(5318.milliseconds, 7974.milliseconds, "the cool thing"),
            ),
            lines,
        )
        assertEquals(emptyList(), warnings())
    }

    @Test
    fun `a br is a line break, and the text around it is trimmed`() {
        val texts =
            textsOf(ttml(p("00:00:01.000", "00:00:02.000", "in front of the <br /> elephants")))

        assertEquals(listOf("in front of the\nelephants"), texts)
    }

    @Test
    fun `empty rows between line breaks are left out`() {
        val texts =
            textsOf(ttml(p("00:00:01.000", "00:00:02.000", "<br/>one<br/><br/> <br/>two<br/>")))

        assertEquals(listOf("one\ntwo"), texts)
    }

    @Test
    fun `styling is left out and its text kept`() {
        val content =
            """<span tts:fontStyle="italic">quiet</span> and <span tts:color="red">loud</span>"""

        val texts = textsOf(ttml(p("00:00:01.000", "00:00:02.000", content)))

        assertEquals(listOf("quiet and loud"), texts)
    }

    @Test
    fun `white space on both sides of an element's edge becomes one space`() {
        val content = "word <span> italic </span> more<span> </span> end"

        val texts = textsOf(ttml(p("00:00:01.000", "00:00:02.000", content)))

        assertEquals(listOf("word italic more end"), texts)
    }

    @Test
    fun `a br inside styling is a line break too`() {
        val content = """<span tts:fontStyle="italic">first<br/>second</span>"""

        val texts = textsOf(ttml(p("00:00:01.000", "00:00:02.000", content)))

        assertEquals(listOf("first\nsecond"), texts)
    }

    @Test
    fun `character references are decoded once`() {
        val content = "that&#39;s &amp; &lt;b&gt; &#x1F418; &amp;#39; a&#160;b"

        val texts = textsOf(ttml(p("00:00:01.000", "00:00:02.000", content)))

        // &amp;#39; is the text &#39;, as YouTube would show it, so it isn't decoded again. The
        // no-break space becomes an ordinary space.
        assertEquals(listOf("that's & <b> 🐘 &#39; a b"), texts)
    }

    @Test
    fun `white space and line breaks in the file become single spaces`() {
        val content = "  several\n   words\t\tand  spaces  "

        val texts = textsOf(ttml(p("00:00:01.000", "00:00:02.000", content)))

        assertEquals(listOf("several words and spaces"), texts)
    }

    @Test
    fun `a line with no text is left out without a warning`() {
        val texts =
            textsOf(
                ttml(
                    p("00:00:01.000", "00:00:02.000", ""),
                    p("00:00:02.000", "00:00:03.000", " <br/> "),
                    p("00:00:03.000", "00:00:04.000", "<span> </span>"),
                    p("00:00:04.000", "00:00:05.000", "kept"),
                ),
            )

        assertEquals(listOf("kept"), texts)
        assertEquals(emptyList(), warnings())
    }

    @Test
    fun `reads clock times without a fraction, with a short fraction, and past 99 hours`() {
        val lines =
            linesOf(
                ttml(
                    p("00:00:01", "00:00:02.5", "a"),
                    p("01:02:03.04", "12:00:00.000", "b"),
                    p("100:00:00.000", "100:00:01.001", "c"),
                ),
            )

        assertEquals(
            listOf(
                SubtitleLine(1.seconds, 2500.milliseconds, "a"),
                SubtitleLine(1.hours + 2.minutes + 3040.milliseconds, 12.hours, "b"),
                SubtitleLine(100.hours, 100.hours + 1001.milliseconds, "c"),
            ),
            lines,
        )
    }

    @Test
    fun `a line whose times are missing or can't be read is left out and counted`() {
        val texts =
            textsOf(
                ttml(
                    """<p end="00:00:02.000">no begin</p>""",
                    """<p begin="00:00:01.000">no end</p>""",
                    p("1.5s", "00:00:02.000", "an offset time"),
                    p("00:00:01:12", "00:00:02.000", "frames"),
                    p("00:61:00.000", "01:02:00.000", "61 minutes"),
                    p("00:00:01.0000", "00:00:02.000", "four digits of a second"),
                    p("00:00:01.000", "00:00:02.000", "kept"),
                ),
            )

        assertEquals(listOf("kept"), texts)
        assertEquals(
            listOf(
                "WARN Dewfall.YouTube YouTube answer read in part call=SUBTITLE_LINES " +
                    "dropped=6 parseErrors=0",
            ),
            warnings(),
        )
    }

    @Test
    fun `a line that ends at or before its start is left out and counted`() {
        val texts =
            textsOf(
                ttml(
                    p("00:00:02.000", "00:00:02.000", "no length"),
                    p("00:00:03.000", "00:00:02.000", "backwards"),
                    p("00:00:04.000", "00:00:05.000", "kept"),
                ),
            )

        assertEquals(listOf("kept"), texts)
        assertEquals(1, warnings().size)
    }

    @Test
    fun `lines are sorted by their start, and lines that start together keep their order`() {
        val texts =
            textsOf(
                ttml(
                    p("00:00:05.000", "00:00:06.000", "third"),
                    p("00:00:01.000", "00:00:02.000", "first"),
                    p("00:00:03.000", "00:00:04.000", "second, a"),
                    p("00:00:03.000", "00:00:05.000", "second, b"),
                ),
            )

        assertEquals(listOf("first", "second, a", "second, b", "third"), texts)
    }

    @Test
    fun `overlapping lines keep the times that YouTube gives them`() {
        // As in an auto-generated track, where each line ends when the line after the next one
        // starts, and as in a written track that shows two lines at once.
        val rolling =
            listOf(
                p("00:00:22.160", "00:00:27.760", "you're a jerk"),
                p("00:00:25.800", "00:00:29.480", "follow our passions"),
                p("00:00:27.760", "00:00:30.679", "Robotics"),
            )
        val inside =
            listOf(
                p("00:00:42.231", "00:00:45.231", "a longer line"),
                p("00:00:43.000", "00:00:45.000", "a line shown with it"),
            )

        val lines = linesOf(ttml(*(rolling + inside).toTypedArray()))

        assertEquals(
            listOf(
                22160 to 27760,
                25800 to 29480,
                27760 to 30679,
                42231 to 45231,
                43000 to 45000,
            ),
            lines.map {
                it.start.inWholeMilliseconds.toInt() to it.end.inWholeMilliseconds.toInt()
            },
        )
    }

    @Test
    fun `reads lines in any div of the body, and nothing outside the body`() {
        val ttml =
            """<tt xmlns="http://www.w3.org/ns/ttml"><head><metadata>""" +
                p("00:00:00.000", "00:00:01.000", "in the head") +
                """</metadata></head><body><div>""" +
                p("00:00:01.000", "00:00:02.000", "first div") +
                """<div>""" +
                p("00:00:02.000", "00:00:03.000", "nested div") +
                """</div></div><div>""" +
                p("00:00:03.000", "00:00:04.000", "second div") +
                """</div></body></tt>"""

        assertEquals(listOf("first div", "nested div", "second div"), textsOf(ttml))
    }

    @Test
    fun `a TTML file with no lines gives no lines`() {
        assertEquals(emptyList(), linesOf(ttml()))
        assertEquals(emptyList(), linesOf("""<tt xmlns="http://www.w3.org/ns/ttml"/>"""))
        assertEquals(emptyList(), warnings())
    }

    @Test
    fun `an empty answer isn't a subtitle file`() {
        assertFailsWith<NotSubtitlesException> { linesOf("") }
        assertFailsWith<NotSubtitlesException> { linesOf(" \n ") }
    }

    @Test
    fun `Google's error page isn't a subtitle file`() {
        // The start of the page that YouTube sent with 404 for an expired link.
        val page =
            "<!DOCTYPE html>\n<html lang=en>\n  <meta charset=utf-8>\n  <title>Error 404 " +
                "(Not Found)!!1</title>\n  <p><b>404.</b> <ins>That's an error.</ins>"

        assertFailsWith<NotSubtitlesException> { linesOf(page) }
    }

    @Test
    fun `an HTML page without a document type isn't a subtitle file`() {
        assertFailsWith<NotSubtitlesException> {
            linesOf("<html><body><p>Sorry...</p></body></html>")
        }
    }

    @Test
    fun `a file in YouTube's srv3 format isn't a TTML file`() {
        val srv3 =
            """<?xml version="1.0" encoding="utf-8" ?><timedtext format="3"><body>""" +
                """<p t="1200" d="2160">All right</p></body></timedtext>"""

        assertFailsWith<NotSubtitlesException> { linesOf(srv3) }
    }

    @Test
    fun `a tt element in another namespace isn't a TTML file`() {
        val other =
            """<tt xmlns="http://example.com/other"><body><div>""" +
                p("00:00:01.000", "00:00:02.000", "text") +
                "</div></body></tt>"

        assertFailsWith<NotSubtitlesException> { linesOf(other) }
    }

    @Test
    fun `a file with a document type declaration is refused, so its entities never reach a line`() {
        // An external entity that a parser which resolves them would read from the disk.
        val withEntities =
            """<?xml version="1.0"?><!DOCTYPE tt [<!ENTITY secret SYSTEM "file:///etc/hosts">""" +
                """<!ENTITY lol "lol">]>""" +
                ttml(p("00:00:01.000", "00:00:02.000", "a &secret; b &lol;"))
                    .removePrefix("""<?xml version="1.0" encoding="utf-8" ?>""")

        assertFailsWith<NotSubtitlesException> { linesOf(withEntities) }
    }

    @Test
    fun `a file with a plain document type declaration is refused too`() {
        val withDoctype = "<!DOCTYPE tt>" + ttml(p("00:00:01.000", "00:00:02.000", "text"))

        assertFailsWith<NotSubtitlesException> { linesOf(withDoctype) }
    }

    @Test
    fun `deeply nested styling doesn't overflow the stack`() {
        val depth = 20_000
        val content = "<span>".repeat(depth) + "deep" + "</span>".repeat(depth)

        assertEquals(listOf("deep"), textsOf(ttml(p("00:00:01.000", "00:00:02.000", content))))
    }
}
