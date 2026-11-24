package io.github.dewfallapp.core.takeout

import io.github.dewfallapp.core.model.ChannelId
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Reads the subscriptions from a Google Takeout file: the .zip file that Takeout makes, or a
 * subscriptions .csv file from it. It doesn't rely on the names of files, folders or columns,
 * because Takeout translates them. A subscriptions table is a .csv table whose lines hold three
 * fields: a channel ID, the channel's link and the channel's name. See
 * docs/architecture.md#the-takeout-import.
 *
 * Everything it reads is user data, and nothing here logs.
 */
object TakeoutReader {
    /** The most bytes of one .csv file, or of one .csv entry of a .zip file, that it reads. */
    const val MAX_TABLE_BYTES: Long = 8L * 1024 * 1024

    /** The most bytes of a .zip file that it reads. */
    const val MAX_ZIP_BYTES: Long = 256L * 1024 * 1024

    /**
     * Reads [input] to the end, or to a limit, and leaves closing it to the caller. It throws
     * IOException when the stream fails or the .zip file is damaged, and lets any other exception
     * from the stream, such as a CancellationException, through.
     */
    fun read(input: InputStream): TakeoutFile {
        val buffered = BufferedInputStream(input)
        buffered.mark(SIGNATURE_LENGTH)
        val start = ByteArray(SIGNATURE_LENGTH)
        val length = buffered.readFully(start)
        buffered.reset()
        return if (isZip(start, length)) readZip(buffered) else readCsv(buffered)
    }

    private fun readCsv(input: InputStream): TakeoutFile {
        val bytes = input.readAtMost(MAX_TABLE_BYTES) ?: return TakeoutFile.TooLarge
        return readTable(bytes).toFile()
    }

    private fun readZip(input: InputStream): TakeoutFile {
        val counted = EndRecordWatch(ZipLimit(input))
        var best: TableReading? = null
        var entriesRead = 0L
        try {
            // Closing the ZipInputStream frees its Inflater's native memory at once. The wrapper
            // keeps that close from closing the caller's stream, which the caller closes.
            ZipInputStream(KeepOpen(counted)).use { zip ->
                var entry = zip.nextEntryOrThrow()
                while (entry != null) {
                    entriesRead++
                    val isTable = entry.name.endsWith(CSV_SUFFIX, ignoreCase = true)
                    val reading = if (isTable) zip.readEntryTable() else null
                    if (reading != null && reading.channels.size > (best?.channels?.size ?: 0)) {
                        best = reading
                    }
                    entry = zip.nextEntryOrThrow()
                }
            }
            // ZipInputStream stops at the central directory, after the last entry. Reading the
            // rest counts the whole file against the limit.
            counted.discardToEnd()
        } catch (_: ZipTooLarge) {
            return TakeoutFile.TooLarge
        }
        // ZipInputStream also ends quietly when the file ends between two entries, so a file cut
        // short there would look complete. Every whole .zip file ends with its end record,
        // followed only by its comment, if it has one.
        if (!counted.sawEndRecord) throw ZipException("The .zip file has no end record")
        // ZipInputStream also stops quietly at a damaged local header, which would leave the
        // entries after it out. The end record, or a zip64 file's own record, says how many entries
        // the file has.
        if (counted.declaredEntries != entriesRead) {
            throw ZipException("The .zip file holds fewer entries than its end record says")
        }
        return best?.toFile() ?: TakeoutFile.NoSubscriptions
    }

    /** The current entry's table, or null when the entry is over [MAX_TABLE_BYTES]. */
    private fun ZipInputStream.readEntryTable(): TableReading? =
        readAtMost(MAX_TABLE_BYTES)?.let(::readTable)

    /**
     * ZipInputStream throws IllegalArgumentException, not ZipException, for an entry name that
     * isn't valid UTF-8. See docs/architecture.md#the-takeout-import.
     */
    private fun ZipInputStream.nextEntryOrThrow() =
        try {
            nextEntry
        } catch (_: IllegalArgumentException) {
            throw ZipException("An entry's name isn't valid UTF-8")
        }
}

/** The channels of one table, and the lines it couldn't read. */
private class TableReading(val channels: List<TakeoutChannel>, val unreadableLines: Int) {
    fun toFile(): TakeoutFile =
        if (channels.isEmpty()) {
            TakeoutFile.NoSubscriptions
        } else {
            TakeoutFile.Subscriptions(channels, unreadableLines)
        }
}

/** Reads one table's [bytes] as UTF-8, by the rules in design.md's line table. */
private fun readTable(bytes: ByteArray): TableReading {
    val table = TableBuilder()
    CsvParser(decodeUtf8(bytes), keptFields = FIELD_COUNT, onRow = table::add).parse()
    return table.build()
}

/** Gathers a table's channels and counts its unreadable lines, one row at a time. */
private class TableBuilder {
    private val channels = mutableListOf<TakeoutChannel>()
    private val seen = HashSet<ChannelId>()
    private var unreadable = 0
    private var headerChecked = false

    fun add(row: CsvRow) {
        if (row.isBlank) return
        val id = ChannelId.parseOrNull(row.fields[0].trim())
        val isFirstLine = !headerChecked
        headerChecked = true
        val name = row.fields.getOrNull(NAME_FIELD)?.trim().orEmpty()
        when {
            isFirstLine && id == null -> Unit
            id == null || row.fieldCount != FIELD_COUNT || name.isEmpty() -> unreadable++
            seen.add(id) -> channels += TakeoutChannel(id, name)
        }
    }

    fun build(): TableReading = TableReading(channels.toList(), unreadable)
}

/**
 * Decodes [bytes] as UTF-8, without a byte order mark at the start. Bytes that aren't UTF-8 become
 * U+FFFD, so they can only spoil a name, never an ID, which is ASCII.
 */
private fun decodeUtf8(bytes: ByteArray): String {
    val offset = if (bytes.startsWith(UTF8_BOM)) UTF8_BOM.size else 0
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
        .toString()
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

/** The bytes up to [limit], or null if the stream holds more. */
private fun InputStream.readAtMost(limit: Long): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return out.toByteArray()
        total += count
        if (total > limit) return null
        out.write(buffer, 0, count)
    }
}

/** Reads the stream to its end and drops what it read. */
private fun InputStream.discardToEnd() {
    val buffer = ByteArray(BUFFER_SIZE)
    var count = 0
    while (count >= 0) {
        count = read(buffer)
    }
}

/** Reads up to [target]'s size, and returns how many bytes it read, fewer only at the end. */
private fun InputStream.readFully(target: ByteArray): Int {
    var length = 0
    while (length < target.size) {
        val count = read(target, length, target.size - length)
        if (count < 0) break
        length += count
    }
    return length
}

private fun isZip(start: ByteArray, length: Int): Boolean =
    length == SIGNATURE_LENGTH &&
        (start.contentEquals(LOCAL_HEADER_SIGNATURE) || start.contentEquals(END_RECORD_SIGNATURE))

/**
 * Thrown when a .zip file holds more than [TakeoutReader.MAX_ZIP_BYTES]. It isn't an IOException,
 * so nothing in ZipInputStream can take it for a damaged file.
 */
private class ZipTooLarge : RuntimeException("The .zip file is too large")

/** Passes everything through except close, which leaves the stream open for its owner. */
private class KeepOpen(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
}

/**
 * Watches the bytes read for a .zip file's end of central directory record (APPNOTE 6.3.9 section
 * 4.3.16): 22 bytes that start with `PK` 5 6, followed by the file's comment, if it has one. After
 * every byte, it checks whether the last 22 bytes are such a record, and keeps the last
 * [WINDOW_SIZE] bytes, enough for a zip64 file's records too, so a record that spans two reads, or
 * overlaps another signature, is still found.
 *
 * A record counts only where a whole file has it: right after the central directory, so the
 * directory's offset plus its size is where the record starts, or right after a zip64 file's two
 * records, which [zip64EntriesBefore] checks by their offsets in this file. So the signatures
 * alone, inside an entry's data, a comment or a zip64 file stored in the .zip, don't count. The
 * record that counts gives [declaredEntries], from the zip64 record in a zip64 file. A file cut
 * short before its end record, inside it, or inside its comment leaves [sawEndRecord] false. Bytes
 * after the comment are allowed, as Java's own zip reader allows them.
 */
private class EndRecordWatch(input: InputStream) : FilterInputStream(input) {
    private val window = ByteArray(WINDOW_SIZE)
    private var position = 0L
    private var recordEnd = NO_RECORD

    /**
     * Whether a record counted, and the file has reached that record's end, after its comment. A
     * later record that counts replaces an earlier one.
     */
    val sawEndRecord: Boolean
        get() = recordEnd != NO_RECORD && recordEnd <= position

    /**
     * The number of entries that the last counted record, or its zip64 record, says the file has.
     */
    var declaredEntries = 0L
        private set

    override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) watch(byte.toByte())
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        for (index in off until off + count) {
            watch(b[index])
        }
        return count
    }

    // Skipping would hide bytes from the watch, so it reads them instead.
    override fun skip(n: Long): Long {
        if (n <= 0) return 0
        val buffer = ByteArray(minOf(n, BUFFER_SIZE.toLong()).toInt())
        return maxOf(read(buffer, 0, buffer.size), 0).toLong()
    }

    private fun watch(byte: Byte) {
        window[(position % WINDOW_SIZE).toInt()] = byte
        position++
        val recordStart = position - END_RECORD_SIZE
        if (recordStart >= 0 && startsWith(recordStart, END_RECORD_SIGNATURE)) {
            val entries = entriesOfRecordAt(recordStart)
            if (entries != null) {
                recordEnd = position + uintAt(recordStart + COMMENT_LENGTH_AT, UINT16_BYTES)
                declaredEntries = entries
            }
        }
    }

    /**
     * The number of entries of the end record at [recordStart], or null when the record isn't where
     * a whole file has it: right after the central directory, or right after a zip64 file's
     * records.
     */
    private fun entriesOfRecordAt(recordStart: Long): Long? {
        val size = uintAt(recordStart + CENTRAL_DIRECTORY_SIZE_AT, UINT32_BYTES)
        val offset = uintAt(recordStart + CENTRAL_DIRECTORY_OFFSET_AT, UINT32_BYTES)
        return if (offset + size == recordStart) {
            uintAt(recordStart + ENTRY_COUNT_AT, UINT16_BYTES)
        } else {
            zip64EntriesBefore(recordStart)
        }
    }

    /**
     * The number of entries in the zip64 end of central directory record before the record at
     * [recordStart], or null when there is no such record where a whole zip64 file has it: 56 bytes
     * that start with `PK` 6 6 and have no extensible data (section 4.3.14), then the 20-byte
     * locator that points at them (section 4.3.15), with the central directory ending where the
     * zip64 record starts. A zip64 file stored inside the .zip points at its own offsets, not the
     * outer file's, so it doesn't count.
     */
    private fun zip64EntriesBefore(recordStart: Long): Long? {
        val locatorStart = recordStart - ZIP64_LOCATOR_SIZE
        val zip64Start = locatorStart - ZIP64_RECORD_SIZE
        val whole =
            zip64Start >= 0 &&
                startsWith(locatorStart, ZIP64_LOCATOR_SIGNATURE) &&
                startsWith(zip64Start, ZIP64_RECORD_SIGNATURE) &&
                uintAt(zip64Start + ZIP64_RECORD_REST_AT, UINT64_BYTES) == ZIP64_RECORD_REST &&
                uintAt(locatorStart + ZIP64_LOCATOR_TARGET_AT, UINT64_BYTES) == zip64Start &&
                uintAt(zip64Start + ZIP64_DIRECTORY_OFFSET_AT, UINT64_BYTES) +
                    uintAt(zip64Start + ZIP64_DIRECTORY_SIZE_AT, UINT64_BYTES) == zip64Start
        return if (whole) uintAt(zip64Start + ZIP64_ENTRY_COUNT_AT, UINT64_BYTES) else null
    }

    /** The byte at [at], a position in the file among the last [WINDOW_SIZE] bytes read. */
    private fun byteAt(at: Long): Byte = window[(at % WINDOW_SIZE).toInt()]

    private fun startsWith(at: Long, signature: ByteArray): Boolean =
        signature.indices.all { byteAt(at + it) == signature[it] }

    /**
     * The little-endian number of [bytes] bytes at [at], unsigned up to 32 bits. A 64-bit number is
     * read as a Long, which fits any real file.
     */
    private fun uintAt(at: Long, bytes: Int): Long {
        var value = 0L
        for (shift in 0 until bytes) {
            val byte = byteAt(at + shift).toLong() and BYTE_MASK
            value = value or (byte shl (shift * BITS_PER_BYTE))
        }
        return value
    }
}

/** Counts the bytes read from a .zip file, and throws [ZipTooLarge] past the limit. */
private class ZipLimit(input: InputStream) : FilterInputStream(input) {
    private var total = 0L

    override fun read(): Int {
        val byte = super.read()
        if (byte >= 0) count(1)
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) count(count.toLong())
        return count
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) count(skipped)
        return skipped
    }

    private fun count(bytes: Long) {
        total += bytes
        if (total > TakeoutReader.MAX_ZIP_BYTES) throw ZipTooLarge()
    }
}

private const val SIGNATURE_LENGTH = 4

// APPNOTE.TXT 6.3.9: the end of central directory record is 22 bytes without its comment
// (section 4.3.16), with the number of entries at byte 10, the central directory's size and offset
// at bytes 12 and 16, and the comment's length at byte 20, and the zip64
// end of central directory locator before it is 20 bytes (section 4.3.15).
private const val END_RECORD_SIZE = 22
private const val ENTRY_COUNT_AT = 10
private const val CENTRAL_DIRECTORY_SIZE_AT = 12
private const val CENTRAL_DIRECTORY_OFFSET_AT = 16
private const val COMMENT_LENGTH_AT = 20
private const val ZIP64_LOCATOR_SIZE = 20

// The zip64 end of central directory record without extensible data is 56 bytes (section 4.3.14):
// its size after the first 12 bytes, 44, at byte 4, the number of entries at byte 32, and the
// central directory's size and offset at bytes 40 and 48. The locator points at it from byte 8.
private const val ZIP64_RECORD_SIZE = 56
private const val ZIP64_RECORD_REST_AT = 4
private const val ZIP64_RECORD_REST = 44L
private const val ZIP64_ENTRY_COUNT_AT = 32
private const val ZIP64_DIRECTORY_SIZE_AT = 40
private const val ZIP64_DIRECTORY_OFFSET_AT = 48
private const val ZIP64_LOCATOR_TARGET_AT = 8
private const val WINDOW_SIZE = ZIP64_RECORD_SIZE + ZIP64_LOCATOR_SIZE + END_RECORD_SIZE
private const val UINT16_BYTES = 2
private const val UINT32_BYTES = 4
private const val UINT64_BYTES = 8

/** No end record has counted yet. */
private const val NO_RECORD = -1L
private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFFL
private const val BUFFER_SIZE = 64 * 1024
private const val CSV_SUFFIX = ".csv"
private const val FIELD_COUNT = 3
private const val NAME_FIELD = 2

// APPNOTE.TXT 6.3.9: a local file header starts with PK 3 4 (section 4.3.7), and the end of
// central directory record with PK 5 6 (section 4.3.16), and the zip64 end of central directory
// locator with PK 6 7 (section 4.3.15). An empty .zip file is only the end record.
private val LOCAL_HEADER_SIGNATURE = "PK\u0003\u0004".toByteArray(Charsets.US_ASCII)
private val END_RECORD_SIGNATURE = "PK\u0005\u0006".toByteArray(Charsets.US_ASCII)
private val ZIP64_LOCATOR_SIGNATURE = "PK\u0006\u0007".toByteArray(Charsets.US_ASCII)
private val ZIP64_RECORD_SIGNATURE = "PK\u0006\u0006".toByteArray(Charsets.US_ASCII)

private val UTF8_BOM = "\uFEFF".toByteArray(Charsets.UTF_8)
