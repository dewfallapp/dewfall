package io.github.dewfallapp.core.youtube.newpipe

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class RequestSettingsTest {
    @Test
    fun `every request asks for British English`() {
        assertEquals(Localization("en", "GB"), RequestSettings(ContentCountry("BD")).localization)
    }

    @Test
    fun `the country is the device's region when YouTube supports it`() {
        assertEquals("BD", contentCountryFor(Locale.forLanguageTag("bn-BD")).countryCode)
        assertEquals("US", contentCountryFor(Locale.forLanguageTag("en-US")).countryCode)
    }

    @Test
    fun `the country is GB when YouTube doesn't support the device's region`() {
        // Antarctica, which YouTube's country list doesn't have.
        assertEquals("GB", contentCountryFor(Locale.forLanguageTag("en-AQ")).countryCode)
        assertEquals("GB", contentCountryFor(Locale.forLanguageTag("es-419")).countryCode)
    }

    @Test
    fun `the country is GB when the device's locale has no region`() {
        assertEquals("GB", contentCountryFor(Locale.forLanguageTag("bn")).countryCode)
    }

    @Test
    fun `an extractor asks for the call's language and country`() {
        // Creating an extractor sends nothing. Only fetchPage would.
        val extractor = ServiceList.YouTube.getSearchExtractor("query")

        RequestSettings(ContentCountry("BD")).applyTo(extractor)

        assertEquals(Localization("en", "GB"), extractor.extractorLocalization)
        assertEquals(ContentCountry("BD"), extractor.extractorContentCountry)
    }
}
