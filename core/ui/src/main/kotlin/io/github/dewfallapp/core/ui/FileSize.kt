package io.github.dewfallapp.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** Below this many of a unit, a size shows one decimal. */
private const val ONE_DECIMAL_BELOW = 10

/** From this many of a unit, a size moves up to the next unit. */
private const val NEXT_UNIT_FROM = 1_000

/** Tenths in one. */
private const val TENTHS = 10.0

/**
 * The units that sizes show in, with 1,000 bytes to a kilobyte, as Android's own storage settings
 * count them since Android 8.0.
 */
enum class SizeUnit(internal val bytes: Long) {
    KB(1_000),
    MB(1_000_000),
    GB(1_000_000_000),
}

/** A size as it shows: [tenths] of [unit], with one decimal when [showsDecimal]. */
data class FileSize(val tenths: Long, val unit: SizeUnit) {
    /** Whether the size shows one decimal: under 10 of its unit, and not a whole number. */
    val showsDecimal: Boolean
        get() = tenths < ONE_DECIMAL_BELOW * TENTHS && tenths % TENTHS.toLong() != 0L

    /** The number, such as "3.6" or "940", in [locale]'s digits and decimal mark. */
    fun digits(locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale)
        val decimals = if (showsDecimal) 1 else 0
        format.minimumFractionDigits = decimals
        format.maximumFractionDigits = decimals
        return format.format(tenths / TENTHS)
    }
}

/** The unit that [bytes] shows in: GB from 1 GB, MB from 1 MB, else kB. */
fun sizeUnitOf(bytes: Long): SizeUnit =
    SizeUnit.entries.lastOrNull { bytes >= it.bytes } ?: SizeUnit.KB

/**
 * [bytes] in [unit], rounded to one decimal under 10 of the unit and to a whole number above. A
 * size that rounds to 1,000 of its unit, such as 999.6 kB, shows in the next unit, unless [unit]
 * was given, as for the bytes done of a total, which share the total's unit.
 */
fun fileSize(bytes: Long, unit: SizeUnit? = null): FileSize {
    val chosen = unit ?: sizeUnitOf(bytes)
    val exact = bytes.coerceAtLeast(0) * TENTHS / chosen.bytes
    val tenths =
        if (exact < ONE_DECIMAL_BELOW * TENTHS) {
            exact.roundToLong()
        } else {
            (exact / TENTHS).roundToLong() * TENTHS.toLong()
        }
    val next = SizeUnit.entries.getOrNull(chosen.ordinal + 1)
    return if (unit == null && next != null && tenths >= NEXT_UNIT_FROM * TENTHS) {
        fileSize(bytes, next)
    } else {
        FileSize(tenths, chosen)
    }
}

/** "940 MB", "3.6 GB" or "12 kB", in the phone's language. */
@Composable
fun fileSizeText(bytes: Long): String {
    val size = fileSize(bytes)
    return stringResource(size.unit.label, size.digits(currentLocale()))
}

/** "344 of 820 MB": [done] bytes of [total], both in the total's unit, in the phone's language. */
@Composable
fun fileSizeOfText(done: Long, total: Long): String {
    val whole = fileSize(total)
    val part = fileSize(done.coerceAtMost(total), whole.unit)
    val locale = currentLocale()
    return stringResource(whole.unit.ofLabel, part.digits(locale), whole.digits(locale))
}

@Composable private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@get:StringRes
private val SizeUnit.label: Int
    get() =
        when (this) {
            SizeUnit.KB -> R.string.core_ui_size_kb
            SizeUnit.MB -> R.string.core_ui_size_mb
            SizeUnit.GB -> R.string.core_ui_size_gb
        }

@get:StringRes
private val SizeUnit.ofLabel: Int
    get() =
        when (this) {
            SizeUnit.KB -> R.string.core_ui_size_of_kb
            SizeUnit.MB -> R.string.core_ui_size_of_mb
            SizeUnit.GB -> R.string.core_ui_size_of_gb
        }
