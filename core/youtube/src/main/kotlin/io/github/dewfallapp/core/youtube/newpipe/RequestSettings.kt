package io.github.dewfallapp.core.youtube.newpipe

import java.util.Locale
import org.schabi.newpipe.extractor.Extractor
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * The language of every request to YouTube: British English. NewPipeExtractor reads some of
 * YouTube's answers by their English text, such as the marks of upcoming streams and the reasons
 * why a video can't be played, and parses dates in en-GB's format. See ADR 0009.
 */
internal val REQUEST_LOCALIZATION = Localization("en", "GB")

/** The country that a request names when YouTube doesn't support the device's. */
private val FALLBACK_COUNTRY = ContentCountry("GB")

/**
 * The language and country that one call asks YouTube for. The country is user data, so never log
 * it. See ADR 0007.
 */
internal class RequestSettings(val contentCountry: ContentCountry) {
    val localization: Localization = REQUEST_LOCALIZATION

    /** [extractor], set to ask YouTube for this call's language and country. */
    fun <E : Extractor> applyTo(extractor: E): E {
        extractor.forceLocalization(localization)
        extractor.forceContentCountry(contentCountry)
        return extractor
    }
}

/** The country that a request names for a device set to [locale]: its region, or else GB. */
internal fun contentCountryFor(locale: Locale): ContentCountry {
    val country = ContentCountry(locale.country)
    return if (country in ServiceList.YouTube.supportedCountries) country else FALLBACK_COUNTRY
}
