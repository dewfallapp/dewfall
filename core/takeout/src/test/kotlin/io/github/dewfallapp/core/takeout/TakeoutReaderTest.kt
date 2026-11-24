package io.github.dewfallapp.core.takeout

import io.github.dewfallapp.core.model.ChannelId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TakeoutReaderTest {
    // A subscriptions .csv file

    @Test
    fun `reads each channel's ID and name, in the file's order`() {
        val file = read(subscriptionsCsv(3, 1, 2))

        assertEquals(
            listOf(channel(3), channel(1), channel(2)),
            file.subscriptions().channels,
        )
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `the header is neither a channel nor an unreadable line`() {
        val file = read("Kanal-ID,Kanal-URL,Kanaltitel\n${channelLine(1)}\n")

        assertEquals(listOf(channel(1)), file.subscriptions().channels)
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `a first line that is already a channel is imported`() {
        val file = read("${channelLine(1)}\n${channelLine(2)}\n")

        assertEquals(listOf(channel(1), channel(2)), file.subscriptions().channels)
    }

    @Test
    fun `a quoted name keeps its comma and one of each doubled quote`() {
        val line =
            "${testId(1)},http://www.youtube.com/channel/${testId(1)},\"Bread, \"\"slow\"\" and old\""
        val file = read("$ENGLISH_HEADER\n$line\n")

        assertEquals("Bread, \"slow\" and old", file.subscriptions().channels.single().name)
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `a quoted name may hold a line break`() {
        val line = "${testId(1)},http://www.youtube.com/channel/${testId(1)},\"Two\nlines\""
        val file = read("$ENGLISH_HEADER\n$line\n${channelLine(2)}\n")

        assertEquals(
            listOf("Two\nlines", "Channel 2"),
            file.subscriptions().channels.map { it.name },
        )
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `a quoted name that the file ends inside ends with the file`() {
        val file = read("$ENGLISH_HEADER\n${channelLine(1)}\n${testId(2)},link,\"Never closed")

        assertEquals(
            listOf(channel(1), TakeoutChannel(ChannelId(testId(2)), "Never closed")),
            file.subscriptions().channels,
        )
    }

    @Test
    fun `a quote inside a field that didn't start with one stays`() {
        val file = read("$ENGLISH_HEADER\n${testId(1)},link,The \"Quoted\" Channel\n")

        assertEquals("The \"Quoted\" Channel", file.subscriptions().channels.single().name)
    }

    @Test
    fun `text after a closing quote stays in the field`() {
        val file = read("$ENGLISH_HEADER\n${testId(1)},link,\"Part one\" and two\n")

        assertEquals("Part one and two", file.subscriptions().channels.single().name)
    }

    @Test
    fun `the reader leaves the caller's stream open`() {
        val zip = zipOf(entry("subscriptions.csv", subscriptionsCsv(1)))
        var closed = false
        val stream =
            object : java.io.FilterInputStream(ByteArrayInputStream(zip)) {
                override fun close() {
                    closed = true
                }
            }

        TakeoutReader.read(stream)

        assertEquals(false, closed)
    }

    @Test
    fun `a byte order mark and carriage returns change nothing`() {
        val plain = read(subscriptionsCsv(1, 2))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val windows = bom + subscriptionsCsv(1, 2).replace("\n", "\r\n").toByteArray()

        assertEquals(plain, TakeoutReader.read(ByteArrayInputStream(windows)))
    }

    @Test
    fun `a lone carriage return also ends a line`() {
        assertEquals(read(subscriptionsCsv(1, 2)), read(subscriptionsCsv(1, 2).replace("\n", "\r")))
    }

    @Test
    fun `blank lines are skipped and not counted`() {
        val file = read(subscriptionsCsv(1, 2) + "\n\n  \n\t\n\n")

        assertEquals(listOf(channel(1), channel(2)), file.subscriptions().channels)
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `blank lines before the header are skipped too`() {
        val file = read("\n\n" + subscriptionsCsv(1))

        assertEquals(listOf(channel(1)), file.subscriptions().channels)
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `lines that hold no channel are counted and the others imported`() {
        val good = (1..10).map { channelLine(it) }
        val bad =
            listOf(
                "@somehandle,http://www.youtube.com/@somehandle,A handle",
                "${testId(11)},http://www.youtube.com/channel/${testId(11)}",
                "${testId(12)},http://www.youtube.com/channel/${testId(12)},   ",
            )
        val file = read((listOf(ENGLISH_HEADER) + good + bad).joinToString("\n"))

        assertEquals((1..10).map(::channel), file.subscriptions().channels)
        assertEquals(3, file.subscriptions().unreadableLines)
    }

    @Test
    fun `a line with four fields is counted as unreadable`() {
        val file = read(subscriptionsCsv(1) + "${channelLine(2)},extra\n")

        assertEquals(listOf(channel(1)), file.subscriptions().channels)
        assertEquals(1, file.subscriptions().unreadableLines)
    }

    @Test
    fun `a line with a million commas is one unreadable line between channels`() {
        val commas = channelLine(2) + ",".repeat(1_000_000)
        val file = read(subscriptionsCsv(1) + commas + "\n" + channelLine(3) + "\n")

        assertEquals(listOf(channel(1), channel(3)), file.subscriptions().channels)
        assertEquals(1, file.subscriptions().unreadableLines)
    }

    @Test
    fun `an ID with a wrong length or character is counted as unreadable`() {
        val short = "UCshort,http://www.youtube.com/channel/UCshort,Short"
        val wrong = testId(2).replaceRange(5, 6, "!") + ",link,Wrong"
        val file = read(subscriptionsCsv(1) + "$short\n$wrong\n")

        assertEquals(listOf(channel(1)), file.subscriptions().channels)
        assertEquals(2, file.subscriptions().unreadableLines)
    }

    @Test
    fun `a channel listed twice counts once, with its first name`() {
        val file = read(subscriptionsCsv(1, 2) + channelLine(1, "Another name") + "\n")

        assertEquals(listOf(channel(1), channel(2)), file.subscriptions().channels)
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `names and IDs are trimmed of white space`() {
        val line =
            "  ${testId(1)} ,http://www.youtube.com/channel/${testId(1)}, \tSpaced out\u00A0 "
        val file = read("$ENGLISH_HEADER\n$line\n")

        assertEquals(
            listOf(TakeoutChannel(ChannelId(testId(1)), "Spaced out")),
            file.subscriptions().channels,
        )
    }

    @Test
    fun `names keep letters beyond ASCII`() {
        val file = read("$ENGLISH_HEADER\n${channelLine(1, "Brot und Käse – 面包")}\n")

        assertEquals("Brot und Käse – 面包", file.subscriptions().channels.single().name)
    }

    @Test
    fun `bytes that aren't UTF-8 spoil only the name`() {
        val start = "$ENGLISH_HEADER\n${testId(1)},link,Bad ".toByteArray()
        val bytes = start + byteArrayOf(0xC3.toByte(), 0x28) + " name\n".toByteArray()
        val file = TakeoutReader.read(ByteArrayInputStream(bytes))

        val only = file.subscriptions().channels.single()
        assertEquals(testId(1), only.id.value)
        assertEquals("Bad \uFFFD( name", only.name)
    }

    @Test
    fun `a file with only a header holds no subscriptions`() {
        assertEquals(TakeoutFile.NoSubscriptions, read("$ENGLISH_HEADER\n\n\n"))
    }

    @Test
    fun `an empty file holds no subscriptions`() {
        assertEquals(TakeoutFile.NoSubscriptions, read(""))
    }

    @Test
    fun `a file that isn't a subscriptions table holds no subscriptions`() {
        assertEquals(TakeoutFile.NoSubscriptions, read("name,age\nAda,36\nAlan,41\n"))
    }

    @Test
    fun `random bytes hold no subscriptions`() {
        val bytes = Random(51).nextBytes(64 * 1024)
        bytes[0] = 'x'.code.toByte()

        assertEquals(TakeoutFile.NoSubscriptions, TakeoutReader.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `a csv file at the limit is read, and one byte more is too large`() {
        val table = subscriptionsCsv(1)
        val padding = TakeoutReader.MAX_TABLE_BYTES.toInt() - table.length
        val atLimit = table + "\n".repeat(padding)

        assertEquals(listOf(channel(1)), read(atLimit).subscriptions().channels)
        assertEquals(TakeoutFile.TooLarge, read(atLimit + "\n"))
    }

    // A Takeout .zip file

    @Test
    fun `a zip file gives the same channels as its subscriptions file`() {
        val csv = subscriptionsCsv(1, 2, 3)
        val zip =
            zipOf(entry("Takeout/YouTube and YouTube Music/subscriptions/subscriptions.csv", csv))

        assertEquals(read(csv), TakeoutReader.read(ByteArrayInputStream(zip)))
    }

    @Test
    fun `a zip file with names in another language gives the same channels`() {
        val csv = "Kanal-ID,Kanal-URL,Kanaltitel\n${channelLine(1)}\n${channelLine(2)}\n"
        val zip = zipOf(entry("Takeout/YouTube und YouTube Music/Abos/Abos.csv", csv))

        assertEquals(read(subscriptionsCsv(1, 2)), TakeoutReader.read(ByteArrayInputStream(zip)))
    }

    @Test
    fun `tables before the subscriptions are passed over and not counted`() {
        val ownChannel = "Channel ID,Title,Visibility,Country\n${testId(99)},Me,Public,XX\n"
        val noIds = "Video ID,Time\nabcdefghijk,2026-01-01\n"
        val zip =
            zipOf(
                entry("Takeout/YouTube/channels/channel.csv", ownChannel),
                entry("Takeout/YouTube/history/watch.csv", noIds),
                entry("Takeout/YouTube/subscriptions/subscriptions.csv", subscriptionsCsv(1, 2)),
            )

        val file = TakeoutReader.read(ByteArrayInputStream(zip))

        assertEquals(listOf(channel(1), channel(2)), file.subscriptions().channels)
        assertEquals(0, file.subscriptions().unreadableLines)
    }

    @Test
    fun `the table with the most channels wins over a smaller one before it`() {
        val zip =
            zipOf(
                entry("a/other.csv", subscriptionsCsv(90, 91)),
                entry("b/subscriptions.csv", subscriptionsCsv(*(1..10).toList().toIntArray())),
            )

        assertEquals(
            (1..10).map(::channel),
            TakeoutReader.read(ByteArrayInputStream(zip)).subscriptions().channels,
        )
    }

    @Test
    fun `on a tie the first table wins`() {
        val zip =
            zipOf(
                entry("first.csv", subscriptionsCsv(1, 2)),
                entry("second.csv", subscriptionsCsv(3, 4)),
            )

        assertEquals(
            listOf(channel(1), channel(2)),
            TakeoutReader.read(ByteArrayInputStream(zip)).subscriptions().channels,
        )
    }

    @Test
    fun `entries whose names don't end in csv are skipped, and the suffix ignores case`() {
        val zip =
            zipOf(
                entry("Takeout/archive_browser.html", subscriptionsCsv(5, 6, 7)),
                entry("Takeout/subs.json", subscriptionsCsv(8, 9, 10)),
                entry("Takeout/SUBSCRIPTIONS.CSV", subscriptionsCsv(1)),
            )

        assertEquals(
            listOf(channel(1)),
            TakeoutReader.read(ByteArrayInputStream(zip)).subscriptions().channels,
        )
    }

    @Test
    fun `a stored entry is read as a deflated one is`() {
        val zip = zipOf(entry("subscriptions.csv", subscriptionsCsv(1, 2), stored = true))

        assertEquals(read(subscriptionsCsv(1, 2)), TakeoutReader.read(ByteArrayInputStream(zip)))
    }

    @Test
    fun `a csv entry over the limit is passed over for a table after it`() {
        val large = subscriptionsCsv(80) + "\n".repeat(TakeoutReader.MAX_TABLE_BYTES.toInt())
        val zip = zipOf(entry("large.csv", large), entry("subscriptions.csv", subscriptionsCsv(1)))

        assertEquals(
            listOf(channel(1)),
            TakeoutReader.read(ByteArrayInputStream(zip)).subscriptions().channels,
        )
    }

    @Test
    fun `the limit counts the bytes read, not the size an entry claims`() {
        val large = subscriptionsCsv(80) + "\n".repeat(TakeoutReader.MAX_TABLE_BYTES.toInt())
        val zip = zipOf(entry("large.csv", large))
        // Deflated, the entry is small, and its local header claims no size at all: the sizes are
        // in the data descriptor after it.
        check(zip.size < TakeoutReader.MAX_TABLE_BYTES / 100)

        assertEquals(TakeoutFile.NoSubscriptions, TakeoutReader.read(ByteArrayInputStream(zip)))
    }

    @Test
    fun `a zip file over its limit is too large`() {
        val stream = EndlessStoredZip("video.mp4", claimedSize = TakeoutReader.MAX_ZIP_BYTES + 1024)

        assertEquals(TakeoutFile.TooLarge, TakeoutReader.read(stream))
    }

    @Test
    fun `bytes after the last entry count toward the zip file's limit`() {
        val zip = zipOf(entry("subscriptions.csv", subscriptionsCsv(1, 2, 3)))
        val tail = ZeroBytes(TakeoutReader.MAX_ZIP_BYTES - zip.size + 1)
        val atLimit = ZeroBytes(TakeoutReader.MAX_ZIP_BYTES - zip.size)

        val over = TakeoutReader.read(SequenceInputStream(ByteArrayInputStream(zip), tail))
        val under = TakeoutReader.read(SequenceInputStream(ByteArrayInputStream(zip), atLimit))

        assertEquals(TakeoutFile.TooLarge, over)
        assertEquals((1..3).map(::channel), under.subscriptions().channels)
    }

    @Test
    fun `an empty zip file holds no subscriptions`() {
        assertEquals(TakeoutFile.NoSubscriptions, TakeoutReader.read(ByteArrayInputStream(zipOf())))
    }

    @Test
    fun `a zip file with no subscriptions table holds no subscriptions`() {
        val zip =
            zipOf(entry("Takeout/YouTube/history/watch.csv", "Video ID,Time\nabcdefghijk,x\n"))

        assertEquals(TakeoutFile.NoSubscriptions, TakeoutReader.read(ByteArrayInputStream(zip)))
    }

    @Test
    fun `a zip file cut short throws an IOException`() {
        val csv = (1..500).joinToString("\n") { channelLine(it) }
        val zip = zipOf(entry("subscriptions.csv", csv))

        assertFailsWith<IOException> {
            TakeoutReader.read(ByteArrayInputStream(zip.copyOf(zip.size / 2)))
        }
    }

    @Test
    fun `a zip file cut between entries or before its end record throws an IOException`() {
        val zip =
            zipOf(
                entry("Takeout/subscriptions.csv", subscriptionsCsv(1, 2, 3)),
                entry("Takeout/playlists.csv", "Playlist ID,Title\n"),
            )
        val secondEntry = zip.indexOf(LOCAL_HEADER, from = 1)
        val centralDirectory = zip.indexOf(CENTRAL_HEADER)
        val endRecord = zip.indexOf(END_RECORD)
        check(secondEntry > 0 && centralDirectory > secondEntry && endRecord > centralDirectory)
        val cuts =
            listOf(
                secondEntry,
                secondEntry + 10,
                centralDirectory,
                centralDirectory + 20,
                endRecord,
                endRecord + 10,
                zip.size - 1,
            )

        for (cut in cuts) {
            assertFailsWith<IOException>("cut at $cut of ${zip.size}") {
                TakeoutReader.read(ByteArrayInputStream(zip.copyOf(cut)))
            }
        }
        assertEquals(
            (1..3).map(::channel),
            TakeoutReader.read(ByteArrayInputStream(zip)).subscriptions().channels,
        )
    }

    @Test
    fun `an end record's signature inside an entry doesn't make a cut file whole`() {
        val fake = String(END_RECORD, Charsets.ISO_8859_1) + "\u0000".repeat(18)
        val zip =
            zipOf(
                entry("Takeout/subscriptions.csv", subscriptionsCsv(1)),
                entry("Takeout/notes.txt", fake, stored = true),
            )
        val cut = zip.copyOf(zip.indexOf(CENTRAL_HEADER))
        check(cut.indexOf(END_RECORD) > 0)

        assertFailsWith<IOException> { TakeoutReader.read(ByteArrayInputStream(cut)) }
    }

    @Test
    fun `an end record's signature in the zip comment changes nothing`() {
        val file = TakeoutReader.read(ByteArrayInputStream(commentedZip()))

        assertEquals(listOf(channel(1), channel(2)), file.subscriptions().channels)
    }

    @Test
    fun `a zip file cut inside its comment throws an IOException`() {
        val zip = commentedZip()

        assertFailsWith<IOException> {
            TakeoutReader.read(ByteArrayInputStream(zip.copyOf(zip.size - 1)))
        }
    }

    @Test
    fun `a damaged local header that hides later entries throws an IOException`() {
        val zip =
            zipOf(
                entry("Takeout/subscriptions.csv", subscriptionsCsv(1, 2)),
                entry("Takeout/more.csv", subscriptionsCsv(3, 4, 5)),
            )
        val damaged = zip.copyOf()
        damaged[zip.indexOf(LOCAL_HEADER, from = 1)] = 'X'.code.toByte()

        assertEquals(
            (3..5).map(::channel),
            TakeoutReader.read(ByteArrayInputStream(zip)).subscriptions().channels,
        )
        assertFailsWith<IOException> { TakeoutReader.read(ByteArrayInputStream(damaged)) }
    }

    @Test
    fun `a zip file read one byte at a time is whole`() {
        val zip = zipOf(entry("Takeout/subscriptions.csv", subscriptionsCsv(1, 2)))
        val oneByteAtATime =
            object : FilterInputStream(ByteArrayInputStream(zip)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int =
                    super.read(b, off, minOf(len, 1))
            }

        val file = TakeoutReader.read(oneByteAtATime)

        assertEquals(listOf(channel(1), channel(2)), file.subscriptions().channels)
    }

    @Test
    fun `a zip64 file with 65535 entries is whole, and a damaged one is not`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("a.csv"))
            zip.write(subscriptionsCsv(1).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("subscriptions.csv"))
            zip.write(subscriptionsCsv(2, 3).toByteArray())
            zip.closeEntry()
            for (number in 3..65_535) {
                zip.putNextEntry(ZipEntry("e/$number"))
                zip.closeEntry()
            }
        }
        val zip = out.toByteArray()
        // Java's ZipOutputStream writes zip64 records for 65535 entries or more, and keeps the
        // real size and offset in the end record, with 0xFFFF as its entry count.
        check(zip.indexOf(ZIP64_LOCATOR) > 0)
        val damaged = zip.copyOf()
        damaged[zip.indexOf(LOCAL_HEADER, from = 1)] = 'X'.code.toByte()

        val file = TakeoutReader.read(ByteArrayInputStream(zip))

        assertEquals(listOf(channel(2), channel(3)), file.subscriptions().channels)
        assertFailsWith<IOException> { TakeoutReader.read(ByteArrayInputStream(damaged)) }
    }

    @Test
    fun `a zip64 file stored in the zip doesn't make the zip whole`() {
        // The outer file has as many entries as the stored zip64 file, 65535, so only the checks
        // of the zip64 records' offsets, not the entry count, can tell the two apart.
        val inner = zip64Of(entries = 65_535)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("subscriptions.csv"))
            zip.write(subscriptionsCsv(1, 2).toByteArray())
            zip.closeEntry()
            for (number in 2..65_534) {
                zip.putNextEntry(ZipEntry("e/$number"))
                zip.closeEntry()
            }
            val stored = ZipEntry("inner.zip")
            stored.method = ZipEntry.STORED
            stored.size = inner.size.toLong()
            stored.crc = CRC32().apply { update(inner) }.value
            zip.putNextEntry(stored)
            zip.write(inner)
            zip.closeEntry()
        }
        val outer = out.toByteArray()
        val innerEnd = outer.indexOf(inner.copyOfRange(0, 64)) + inner.size
        check(innerEnd > inner.size && outer.indexOf(CENTRAL_HEADER, from = innerEnd) == innerEnd)

        val whole = TakeoutReader.read(ByteArrayInputStream(outer))

        assertEquals(listOf(channel(1), channel(2)), whole.subscriptions().channels)
        assertFailsWith<IOException> {
            TakeoutReader.read(ByteArrayInputStream(outer.copyOf(innerEnd)))
        }
    }

    @Test
    fun `an entry name that isn't UTF-8 throws a ZipException`() {
        val zip =
            withBadNameByte(
                zipOf(entry("subscriptions.csv", subscriptionsCsv(1))),
                "subscriptions.csv",
            )

        assertFailsWith<ZipException> { TakeoutReader.read(ByteArrayInputStream(zip)) }
    }

    // Exceptions from the stream

    @Test
    fun `a failing stream's IOException comes through`() {
        assertFailsWith<IOException> { TakeoutReader.read(FailingStream(IOException("made up"))) }
    }

    @Test
    fun `a cancellation from the stream comes through, for a csv file and a zip file`() {
        assertFailsWith<CancellationException> {
            TakeoutReader.read(FailingStream(CancellationException("made up")))
        }
        val zipStart = zipOf(entry("subscriptions.csv", subscriptionsCsv(1)))
        assertFailsWith<CancellationException> {
            TakeoutReader.read(
                FailingStream(CancellationException("made up"), first = zipStart.copyOf(40)),
            )
        }
    }
}

private fun read(text: String): TakeoutFile =
    TakeoutReader.read(ByteArrayInputStream(text.toByteArray()))

private fun channel(number: Int) = TakeoutChannel(ChannelId(testId(number)), "Channel $number")

private fun TakeoutFile.subscriptions(): TakeoutFile.Subscriptions =
    assertIs<TakeoutFile.Subscriptions>(this)

/** Gives [first], then throws [error] on the next read. */
private class FailingStream(private val error: Exception, first: ByteArray = ByteArray(0)) :
    InputStream() {
    private val start = ByteArrayInputStream(first)

    override fun read(): Int {
        val byte = start.read()
        if (byte >= 0) return byte
        throw error
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = start.read(b, off, len)
        if (count > 0) return count
        throw error
    }
}

private val LOCAL_HEADER = byteArrayOf(0x50, 0x4B, 3, 4)
private val CENTRAL_HEADER = byteArrayOf(0x50, 0x4B, 1, 2)
private val END_RECORD = byteArrayOf(0x50, 0x4B, 5, 6)
private val ZIP64_LOCATOR = byteArrayOf(0x50, 0x4B, 6, 7)

/** Where [pattern] first starts in this array at or after [from], or -1. */
private fun ByteArray.indexOf(pattern: ByteArray, from: Int = 0): Int =
    (from..size - pattern.size).firstOrNull { start ->
        pattern.indices.all { this[start + it] == pattern[it] }
    } ?: -1

/** A .zip file with one subscriptions table and a comment that holds an end record's signature. */
private fun commentedZip(): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.setComment("PK\u0005\u0006 at the end")
        zip.putNextEntry(ZipEntry("subscriptions.csv"))
        zip.write(subscriptionsCsv(1, 2).toByteArray())
        zip.closeEntry()
    }
    return out.toByteArray()
}

/** A zip64 .zip file of [entries] empty entries, as Java's ZipOutputStream writes 65535 or more. */
private fun zip64Of(entries: Int): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for (number in 1..entries) {
            zip.putNextEntry(ZipEntry("i/$number"))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}
