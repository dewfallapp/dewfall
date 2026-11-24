package io.github.dewfallapp.core.takeout

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Made-up Takeout files for the reader's tests. Every ID is "UCtakeouttest" and 11 digits, 24
// characters in all, like a real channel ID, and no real channel's. Names are made up too.

/** The made-up channel ID number [number]. */
internal fun testId(number: Int): String = "UCtakeouttest" + number.toString().padStart(11, '0')

/** A subscriptions line for channel [number], named "Channel [number]". */
internal fun channelLine(number: Int, name: String = "Channel $number"): String =
    "${testId(number)},http://www.youtube.com/channel/${testId(number)},$name"

/** A subscriptions table with the English header and channels [numbers], ending in a line feed. */
internal fun subscriptionsCsv(vararg numbers: Int): String =
    (listOf(ENGLISH_HEADER) + numbers.map { channelLine(it) }).joinToString("\n", postfix = "\n")

internal const val ENGLISH_HEADER = "Channel Id,Channel Url,Channel Title"

/** One entry of a made-up .zip file. */
internal class Entry(val name: String, val bytes: ByteArray, val stored: Boolean = false)

internal fun entry(name: String, text: String, stored: Boolean = false) =
    Entry(name, text.toByteArray(), stored)

/**
 * A .zip file with [entries], in that order. Deflated entries get a data descriptor after their
 * data, as APPNOTE.TXT's section 4.3.9 allows.
 */
internal fun zipOf(vararg entries: Entry): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for (entry in entries) {
            val zipEntry = ZipEntry(entry.name)
            if (entry.stored) {
                zipEntry.method = ZipEntry.STORED
                zipEntry.size = entry.bytes.size.toLong()
                zipEntry.crc = CRC32().apply { update(entry.bytes) }.value
            }
            zip.putNextEntry(zipEntry)
            zip.write(entry.bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * A stream that starts with a .zip local file header for one stored entry, [name], that claims
 * [claimedSize] bytes, and then gives zero bytes without end. It makes its bytes as they are read,
 * so a test can read hundreds of MiB without holding them.
 */
internal class EndlessStoredZip(name: String, claimedSize: Long) : InputStream() {
    private val header: ByteArray = localHeader(name, claimedSize)
    private var position = 0

    override fun read(): Int =
        if (position < header.size) header[position++].toInt() and 0xFF else 0

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var written = 0
        while (written < len && position < header.size) {
            b[off + written++] = header[position++]
        }
        b.fill(0, off + written, off + len)
        return len
    }

    private fun localHeader(name: String, size: Long): ByteArray {
        val nameBytes = name.toByteArray()
        val out = ByteArrayOutputStream()
        fun short(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
        fun int(value: Long) {
            short((value and 0xFFFF).toInt())
            short(((value shr 16) and 0xFFFF).toInt())
        }
        int(0x04034b50L) // local file header signature
        short(10) // version needed
        short(0) // flags: no data descriptor
        short(0) // method: stored
        short(0) // time
        short(0) // date
        int(0) // CRC-32, never reached
        int(size) // compressed size
        int(size) // uncompressed size
        short(nameBytes.size)
        short(0) // extra field length
        out.write(nameBytes)
        return out.toByteArray()
    }
}

/** [bytes] with every copy of [name]'s first byte, where [name] appears, set to 0xFF. */
internal fun withBadNameByte(bytes: ByteArray, name: String): ByteArray {
    val target = name.toByteArray()
    val copy = bytes.copyOf()
    var found = 0
    for (start in 0..copy.size - target.size) {
        if (target.indices.all { copy[start + it] == target[it] }) {
            copy[start] = 0xFF.toByte()
            found++
        }
    }
    check(found == 2) { "Expected the name in the local header and the central directory" }
    return copy
}

/** A stream of [length] zero bytes, made as it is read, so a test needs no array that large. */
internal class ZeroBytes(private var length: Long) : InputStream() {
    override fun read(): Int {
        if (length <= 0) return -1
        length--
        return 0
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (length <= 0) return if (len == 0) 0 else -1
        val count = minOf(len.toLong(), length).toInt()
        b.fill(0, off, off + count)
        length -= count
        return count
    }
}
