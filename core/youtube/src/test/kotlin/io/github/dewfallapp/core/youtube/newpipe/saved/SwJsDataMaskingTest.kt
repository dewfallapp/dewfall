package io.github.dewfallapp.core.youtube.newpipe.saved

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The link of sw.js_data, the answer that NewPipeExtractor reads the web client's version from. */
private const val SW_JS_DATA_URL = "https://www.youtube.com/sw.js_data"

/** A made-up visitor ID. */
private const val VISITOR_ID = "Zv65wXyQ1aB"

/**
 * Made-up visitor data in YouTube's shape: a protobuf message whose first field is the visitor ID.
 */
private val VISITOR_DATA =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            byteArrayOf(0x0a, VISITOR_ID.length.toByte()) +
                VISITOR_ID.toByteArray() +
                byteArrayOf(0x28, 0x01),
        )
        .plus("%3D%3D")

/** A public IPv6 address that no test machine has, written out in full, as sw.js_data writes it. */
private const val ADDRESS = "2a02:1234:5678:9abc:0001:0002:0003:0004"

/**
 * A key in the shape of YouTube's public web key, built at run time so that the file holds nothing
 * that GitHub's secret scanning would flag.
 */
private val API_KEY = "AIza" + "0".repeat(35)

/**
 * sw.js_data as YouTube writes it, with made-up values: a prefix, then a JSON array without names
 * that holds the language, the network's country ZZ, the machine's address, the web client's
 * version, a list with GB, the API key, visitor data, and the value that changes on every fetch.
 */
private val SW_JS_DATA =
    """)]}'""" +
        "\n" +
        """[["x",null,[[["en","ZZ","$ADDRESS","2.20261117.01.00",["GB"]]],"$API_KEY",""" +
        """"$VISITOR_DATA"],"V1a2b3c4d||"]]"""

/**
 * Tests the masking of sw.js_data, which names the network's country and holds visitor data under
 * no name, so both are found by their shape.
 */
class SwJsDataMaskingTest {
    private val recording =
        listOf(
            SavedExchange(
                SavedRequest("GET", SW_JS_DATA_URL, emptyList(), body = null),
                SavedResponse(200, listOf(Header(CONTENT_TYPE, "application/json")), SW_JS_DATA),
            ),
        )

    @Test
    fun `its country becomes GB, and counts as the network's`() {
        val masked = mask(recording)

        assertEquals(setOf("ZZ"), realValuesOf(recording).networkCountries)
        val body = masked.exchanges.single().response.body
        assertTrue(body.startsWith(""")]}'"""), body)
        assertTrue(body.contains(""""en","GB","""), body)
        assertTrue(body.contains(""""2.20261117.01.00",["GB"]]"""), body)
        assertFalse(body.contains("ZZ"), body)
        assertEquals(1, masked.counts[MaskedValue.COUNTRY])
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
    }

    @Test
    fun `its visitor data gets a placeholder, and counts as the session's`() {
        val masked = mask(recording)

        assertEquals(listOf(VISITOR_DATA), realValuesOf(recording).visitorData)
        val body = masked.exchanges.single().response.body
        assertTrue(body.contains(""""${MASKED_VISITOR_DATA}1"]"""), body)
        assertFalse(body.contains(VISITOR_DATA), body)
    }

    @Test
    fun `its address counts as the machine's, and its value of the fetch becomes TOKEN`() {
        val masked = mask(recording)

        assertEquals(setOf(ADDRESS), realValuesOf(recording).addresses)
        val body = masked.exchanges.single().response.body
        assertFalse(body.contains("2a02"), body)
        assertTrue(body.contains(""""TOKEN||"]]"""), body)
        assertEquals(1, masked.counts[MaskedValue.SESSION_TOKEN])
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
    }

    @Test
    fun `the check that knows no real value finds a value left there`() {
        assertEquals(setOf(MaskedValue.COUNTRY), unmaskedKindsIn(""")]}'[["en","ZZ"]]"""))
        assertEquals(
            setOf(MaskedValue.VISITOR_DATA),
            unmaskedKindsIn(""")]}'[["$VISITOR_DATA"]]"""),
        )
        assertEquals(setOf(MaskedValue.ADDRESS), unmaskedKindsIn(""")]}'[["$ADDRESS"]]"""))
        assertEquals(
            setOf(MaskedValue.SESSION_TOKEN),
            unmaskedKindsIn(""")]}'[["V1a2b3c4d||"]]"""),
        )
        assertEquals(
            emptySet(),
            unmaskedKindsIn(""")]}'[["en","GB","${MASKED_VISITOR_DATA}1"],"TOKEN||"]"""),
        )
    }

    @Test
    fun `the shape counts only in sw_js_data, so a list of countries elsewhere keeps them`() {
        assertEquals(emptySet(), unmaskedKindsIn("""{"availableCountries":["GB","ZZ"]}"""))
    }
}
