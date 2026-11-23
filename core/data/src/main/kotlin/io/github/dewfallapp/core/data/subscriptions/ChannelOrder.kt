package io.github.dewfallapp.core.data.subscriptions

import io.github.dewfallapp.core.model.ChannelOverview
import java.text.Collator
import java.util.Locale

/**
 * [channels] by name as [locale] sorts it, ignoring case, with an accented letter next to its plain
 * one, then by channel ID for names that compare equal, as the subscriptions spec's "The Channels
 * screen lists the subscribed channels" says.
 *
 * SECONDARY strength tells accents apart but not case
 * (https://developer.android.com/reference/java/text/Collator#SECONDARY), so "Élodie" sorts with
 * the E's and "orbit" next to "Orbit". SQLite's `COLLATE NOCASE` folds only ASCII letters and
 * orders the rest by byte, which would put accented and non-Latin names last.
 */
internal fun sortedByName(channels: List<ChannelOverview>, locale: Locale): List<ChannelOverview> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return channels.sortedWith(
        compareBy<ChannelOverview, String>(collator) { it.subscription.channel.name }
            .thenBy { it.subscription.channel.id.value },
    )
}
