package io.github.dewfallapp.core.youtube.newpipe.saved

import java.util.EnumMap
import java.util.EnumSet
import java.util.Locale

/** What a masked IPv4 address becomes: a documentation address from RFC 5737. */
internal const val MASKED_IPV4 = "192.0.2.1"

/** What a masked IPv6 address becomes: a documentation address from RFC 3849. */
internal const val MASKED_IPV6 = "2001:db8::1"

/** The start of what each visitor data value becomes, followed by its place in the set. */
internal const val MASKED_VISITOR_DATA = "VISITOR_DATA_"

/**
 * The names of the answer headers that a saved set keeps: NewPipeExtractor reads an answer's
 * content type, and NewPipeDownloader the target of a redirect. The layer reads no others.
 */
internal val KEPT_RESPONSE_HEADERS = setOf("content-type", "location")

/** The header that sets a cookie, which a saved set never keeps. */
private const val SET_COOKIE = "set-cookie"

/**
 * The shortest cookie value or rollout token that is masked wherever a body repeats it. A shorter
 * one could match other text by chance.
 */
private const val MIN_TOKEN_LENGTH_TO_MASK = 8

/** The number of 16-bit groups in an IPv6 address. */
private const val IPV6_GROUPS = 8

/** The number of hex digits in a group of an IPv6 address. */
private const val IPV6_GROUP_DIGITS = 4

/** The highest value of a part of an IPv4 address. */
private const val MAX_OCTET = 255

/** The number of parts of an IPv4 address. */
private const val IPV4_OCTETS = 4

/** The most digits in a part of an IPv4 address. */
private const val MAX_OCTET_DIGITS = 3

/** The bits in an IPv4 address. */
private const val IPV4_BITS = 32

/** The placeholder of a cookie's value where an answer's body repeats it. */
private const val MASKED_COOKIE_VALUE = "COOKIE_VALUE"

/** The placeholder of the rollout token. */
private const val MASKED_ROLLOUT_TOKEN = "ROLLOUT_TOKEN"

/** The placeholder of a CDN node's name. */
private const val MASKED_CDN_NODE = "sn-masked"

/** The placeholder of an event ID. */
private const val MASKED_EVENT_ID = "EVENT_ID"

/** The placeholder of an opaque token that YouTube makes for an answer or a session. */
private const val MASKED_SESSION_TOKEN = "TOKEN"

/**
 * The start of what each other opaque value becomes, followed by its place in the set, such as
 * TOKEN_1.
 */
private const val MASKED_OPAQUE_VALUE = "TOKEN_"

/** The placeholder of the visitor ID in the visitor_data tracking parameter. */
private const val MASKED_FEEDBACK_VISITOR_DATA = "VISITOR_DATA"

/** The placeholder of the network's country: the country that every request asks for. */
private const val MASKED_COUNTRY = "GB"

/** The placeholder of YouTube's public API key. */
private const val MASKED_API_KEY = "API_KEY"

/** The placeholder of the bandwidth that YouTube measured, and of the network's prefix length. */
private const val MASKED_NUMBER = "0"

/** The placeholder of a list of experiment IDs. */
private const val MASKED_EXPERIMENT_IDS = "EXPERIMENTS"

/** IPv6's loopback and unspecified addresses, written out. */
private val NON_PUBLIC_IPV6 =
    setOf("0000:0000:0000:0000:0000:0000:0000:0001", "0000:0000:0000:0000:0000:0000:0000:0000")

/** The documentation address that a masked IPv6 address becomes, written out in full. */
private val MASKED_IPV6_EXPANDED = expandedIpv6(MASKED_IPV6)

/** The start of every documentation address from RFC 3849, written out. */
private const val IPV6_DOCUMENTATION_PREFIX = "2001:0db8:"

/** The protobuf tag of a visitor data value's first field, the visitor ID: field 1, bytes. */
private const val VISITOR_ID_TAG = 0x0a

/** Where the visitor ID starts in visitor data: after its field's tag and its length. */
private const val VISITOR_ID_START = 2

/** The length of a country code. */
private const val COUNTRY_CODE_LENGTH = 2

/** The bits of a protobuf field's tag that give its wire type. */
private const val WIRE_TYPE_BITS = 0x07

/** The wire type of a protobuf field that holds bytes, such as a string. */
private const val LENGTH_DELIMITED = 2

/** How many hex digits write an IPv4 address as one number. */
private const val IPV4_HEX_DIGITS = 8

/**
 * What may follow a handle in other text without making it a longer handle: no letter, digit,
 * underscore or hyphen, and no period that goes on with one.
 */
private const val HANDLE_END = """(?![\p{L}\p{N}_\-]|\.[\p{L}\p{N}_\-])"""

/** The start of an IPv4 address mapped into IPv6, before the address as two hex groups. */
private const val IPV4_MAPPED_PREFIX = "::ffff:"

/**
 * The start of the body of sw.js_data, the answer that NewPipeExtractor reads the web client's
 * version from. YouTube writes it before the JSON, so that no page can load the answer as a script.
 * No other answer that the layer gets starts with it.
 */
private const val SW_JS_DATA_PREFIX = ")]}'"

/**
 * What the recorder replaces or drops before it writes a set, so that no saved file says which
 * machine or session recorded it. docs/testing.md#privacy lists each one.
 */
internal enum class MaskedValue {
    /** The machine's IP address, which googlevideo links carry in their ip parameter. */
    ADDRESS,

    /** YouTube's visitor data: a visitor ID, the time it was made and the network's country. */
    VISITOR_DATA,

    /** The same visitor ID in another encoding, in the tracking parameter visitor_data. */
    FEEDBACK_VISITOR_DATA,

    /** The rollout that YouTube put the visitor in. */
    ROLLOUT_TOKEN,

    /** A Set-Cookie header, which holds the visitor's cookies. */
    COOKIE_HEADER,

    /** A cookie's value where an answer's body repeats it. */
    COOKIE_VALUE,

    /**
     * The ID of the event that made an answer or a link, which YouTube's logs tie to the session:
     * the ei parameter of links, an answer's responseId, the tracking parameters that carry it, and
     * the request IDs in the CSI tracking parameters.
     */
    EVENT_ID,

    /**
     * A token that YouTube makes for an answer or a session, and encrypts or hashes, so no check
     * can see what it holds: plid, vm and of in playback statistics links, redir_token in the links
     * of a description, bui and spc in googlevideo links, the signatures sig and lsig of
     * googlevideo links, which cover their ip parameter, a googlevideo link's id, the attestation
     * challenge, videoPlaybackUstreamerConfig, continue_action and continueAction in sign-in links,
     * and the number at the start of sw.js, which NewPipeExtractor read before v0.27.0.
     */
    SESSION_TOKEN,

    /**
     * Any other opaque value that [keptValuesOf] doesn't keep, under [KEPT_KEYS] or
     * [KEPT_REQUEST_KEYS], found as SavedResponsesPrivacyTest finds it.
     */
    OPAQUE_VALUE,

    /** The CDN node that serves the network, in a googlevideo link's host and mn parameter. */
    CDN_NODE,

    /** The bandwidth that YouTube measured for the connection, in the initcwndbps parameter. */
    BANDWIDTH,

    /**
     * The length of the address prefix that a googlevideo link was made for, in its pl parameter,
     * which tells how big the machine's network is.
     */
    NETWORK_SIZE,

    /**
     * The experiments that YouTube put the visitor in: the e tracking parameter, and fexp in links.
     * Some of them change from one visitor to the next.
     */
    EXPERIMENT_IDS,

    /**
     * The network's country, which sw.js_data names as its one string of two capital letters, and
     * visitor data in a field of its own. It is masked wherever a country field names it, and in
     * sw.js_data.
     */
    COUNTRY,

    /** YouTube's public API key for its web client, which GitHub's secret scanning would flag. */
    API_KEY,

    /**
     * A parameter of a stream link, an ad link, a link that the player reports playback to, or a
     * link through YouTube's redirect, that neither Dewfall nor NewPipeExtractor reads, see
     * [maskTrackedLinks].
     */
    LINK_PARAMETER,

    /** Data about a viewer, such as a comment and who wrote it, see [maskViewerData]. */
    VIEWER_DATA,

    /**
     * The experiment flags that sw.js and the Android client's player answer held before
     * NewPipeExtractor v0.27.0, see [maskExperimentFlags].
     */
    EXPERIMENT_FLAGS,
}

/**
 * A rule that replaces each match of [pattern]'s first group with [placeholder]. A rule finds the
 * values to mask by where they stand, so it also proves that a saved file holds only placeholders.
 */
private class Rule(val kind: MaskedValue, val pattern: Regex, val placeholder: String)

/**
 * The rules for a parameter of a link: in its query, and, for the parameters of googlevideo links,
 * which an HLS manifest's link carries as path segments, in its path too.
 */
private fun parameterRules(
    kind: MaskedValue,
    name: String,
    placeholder: String,
    inPath: Boolean = true,
): List<Rule> =
    listOfNotNull(
        // JSON may write an ampersand in a link as \u0026.
        Rule(kind, Regex("""(?:[?&]|\\u0026)$name=([^&"\\\s]*)"""), placeholder),
        if (inPath) Rule(kind, Regex("""/$name/([^/?&"\\\s]*)"""), placeholder) else null,
    )

/** The rollout token in a JSON answer. */
private val ROLLOUT_TOKEN = Regex(""""rolloutToken":"([^"]*)"""")

/**
 * The steps that mask a value by where it stands, after the rules, each with the kind of value it
 * masks.
 */
private val MASKING_STEPS: List<Pair<MaskedValue, (String) -> MaskedText>> =
    listOf(
        MaskedValue.LINK_PARAMETER to ::maskTrackedLinks,
        MaskedValue.VIEWER_DATA to ::maskViewerData,
        MaskedValue.EXPERIMENT_FLAGS to ::maskExperimentFlags,
    )

/** The rules that the recorder applies after it replaces the values it collected. */
private val RULES =
    listOf(
        Rule(
            MaskedValue.FEEDBACK_VISITOR_DATA,
            Regex(""""key":"visitor_data","value":"([^"]*)""""),
            MASKED_FEEDBACK_VISITOR_DATA,
        ),
        Rule(MaskedValue.ROLLOUT_TOKEN, ROLLOUT_TOKEN, MASKED_ROLLOUT_TOKEN),
        Rule(
            MaskedValue.CDN_NODE,
            Regex("""[a-z0-9]+---(sn-[a-z0-9-]+)\.googlevideo\.com"""),
            MASKED_CDN_NODE,
        ),
        Rule(MaskedValue.COUNTRY, Regex(""""gl":"([A-Z]{2})""""), MASKED_COUNTRY),
        Rule(MaskedValue.API_KEY, Regex("""(AIza[0-9A-Za-z_-]{35})"""), MASKED_API_KEY),
        Rule(
            MaskedValue.EVENT_ID,
            Regex(
                """"(?:responseId|trackingParams|clickTrackingParams|trackingParam)":"([^"]*)"""",
            ),
            MASKED_EVENT_ID,
        ),
        Rule(
            MaskedValue.EVENT_ID,
            Regex(""""key":"[A-Za-z]+_rid","value":"([^"]*)""""),
            MASKED_EVENT_ID,
        ),
        Rule(MaskedValue.SESSION_TOKEN, Regex(""""challenge":"([^"]*)""""), MASKED_SESSION_TOKEN),
        Rule(
            MaskedValue.EXPERIMENT_IDS,
            Regex(""""key":"e","value":"([^"]*)""""),
            MASKED_EXPERIMENT_IDS,
        ),
        Rule(
            MaskedValue.SESSION_TOKEN,
            Regex(""""(?:videoPlaybackUstreamerConfig|continueAction)":"([^"]*)""""),
            MASKED_SESSION_TOKEN,
        ),
        // In the continue parameter of a sign-in link, URL-encoded once or twice.
        Rule(
            MaskedValue.SESSION_TOKEN,
            Regex("""continue_action(?:=|%3[Dd]|%253[Dd])([A-Za-z0-9_\-]*)"""),
            MASKED_SESSION_TOKEN,
        ),
        // A googlevideo link's ID, which starts with o-, in its query or an HLS link's path.
        Rule(
            MaskedValue.SESSION_TOKEN,
            Regex("""(?:[?&]|\\u0026)id=(o-[^&"\\\s]*)"""),
            MASKED_SESSION_TOKEN,
        ),
        Rule(MaskedValue.SESSION_TOKEN, Regex("""/id/(o-[^/?&"\\\s]*)"""), MASKED_SESSION_TOKEN),
        // The number in the comment at the start of sw.js, which changed on every fetch.
        Rule(MaskedValue.SESSION_TOKEN, Regex("""^/\*\*\s*(\d+)\s*\*/"""), MASKED_SESSION_TOKEN),
    ) +
        parameterRules(MaskedValue.EVENT_ID, "ei", MASKED_EVENT_ID) +
        parameterRules(MaskedValue.CDN_NODE, "mn", MASKED_CDN_NODE) +
        parameterRules(MaskedValue.BANDWIDTH, "initcwndbps", MASKED_NUMBER) +
        parameterRules(MaskedValue.NETWORK_SIZE, "pl", MASKED_NUMBER) +
        parameterRules(MaskedValue.EXPERIMENT_IDS, "fexp", MASKED_EXPERIMENT_IDS) +
        listOf("bui", "spc", "sig", "lsig").flatMap {
            parameterRules(MaskedValue.SESSION_TOKEN, it, MASKED_SESSION_TOKEN)
        } +
        listOf("plid", "vm", "of", "redir_token").flatMap {
            parameterRules(MaskedValue.SESSION_TOKEN, it, MASKED_SESSION_TOKEN, inPath = false)
        }

/**
 * Where a link names the machine's address: the ip parameter of a googlevideo link, or /ip/ in its
 * path. A subtitle link has an ip parameter too, which named no address, 0.0.0.0, in the
 * recordings.
 */
private val ADDRESS_CARRIERS =
    listOf(
        Regex("""(?:[?&]|\\u0026)ip=([0-9A-Fa-f.:%]+)"""),
        Regex("""/ip/([0-9A-Fa-f.:%]+)/"""),
    )

/** Visitor data in a JSON answer or request. */
private val VISITOR_DATA = Regex(""""visitorData":"([^"]+)"""")

/** A visitor data value after masking. */
private val MASKED_VISITOR_DATA_VALUE = Regex("""$MASKED_VISITOR_DATA\d+""")

/**
 * Text that looks like an IPv4 address. A letter, a digit or a dot next to it means it is part of
 * something else, such as the visionOS version 25.6.0.23O471 that NewPipeExtractor sends.
 */
private val IPV4_LITERAL = Regex("""(?<![\w.])(\d{1,3}(?:\.\d{1,3}){3})(?![\w.])""")

/**
 * Text that looks like an IPv6 address, with its colons as they are, URL-encoded, or URL-encoded
 * twice, as in a link inside another link.
 */
private val IPV6_LITERAL =
    Regex(
        """(?i)(?<![0-9a-f:%])""" +
            """((?:[0-9a-f]{1,4}(?::|%3a|%253a)){2,7}(?::|%3a|%253a)?[0-9a-f]{0,4})""" +
            """(?![0-9a-f:%])""",
    )

/**
 * A string of two capital letters in sw.js_data. sw.js_data is a JSON array without names, and its
 * only such string is the network's country, next to the language that the request asked for. It
 * names the country even though the request asks for GB.
 */
private val SW_JS_DATA_COUNTRY = Regex(""""([A-Z]{2})"""")

/**
 * A string in sw.js_data that may be visitor data, which sw.js_data holds under no name. Each one
 * that decodes to a visitor ID counts, see [visitorIdOf].
 */
private val SW_JS_DATA_STRING = Regex(""""([A-Za-z0-9_%=-]{16,})"""")

/**
 * Any string in sw.js_data, such as the machine's address, which sw.js_data holds under no name.
 */
private val SW_JS_DATA_ANY_STRING = Regex(""""([^"\\]*)"""")

/**
 * The value at the end of sw.js_data, before two vertical bars, such as V and eight hex digits. It
 * changes on every fetch, so it says which session fetched it, and NewPipeExtractor doesn't read
 * it.
 */
private val SW_JS_DATA_FETCH_TOKEN = Regex(""""([^"|]+)\|\|"""")

/** The names of the fields that hold a country: gl, a country code, or a region. */
private const val COUNTRY_NAMES = "gl|[a-z_]+_gl|country|country_?code|region_?code|content_?region"

/** The name of a field that holds a country. */
private val COUNTRY_KEY = Regex(COUNTRY_NAMES, RegexOption.IGNORE_CASE)

/** A country field in a JSON body, with its value. */
private val COUNTRY_FIELD = Regex(""""(?i:$COUNTRY_NAMES)"\s*:\s*"([A-Za-z]{2})"""")

/** A country field written as a key and a value, as YouTube's tracking parameters are. */
private val COUNTRY_PAIR =
    Regex(""""key"\s*:\s*"(?i:gl|country|country_?code)"\s*,\s*"value"\s*:\s*"([A-Za-z]{2})"""")

/** A country as a link's parameter. */
private val COUNTRY_PARAMETER =
    Regex("""(?:[?&]|\\u0026)(?i:gl|country)=([A-Za-z]{2})(?![A-Za-z])""")

/** The placeholders that masking writes, which a check accepts where a real value was. */
private val PLACEHOLDERS =
    setOf(
        MASKED_IPV4,
        MASKED_IPV6,
        MASKED_COOKIE_VALUE,
        MASKED_ROLLOUT_TOKEN,
        MASKED_CDN_NODE,
        MASKED_EVENT_ID,
        MASKED_SESSION_TOKEN,
        MASKED_FEEDBACK_VISITOR_DATA,
        MASKED_API_KEY,
        MASKED_NUMBER,
        MASKED_EXPERIMENT_IDS,
        MASKED_LINK_VALUE,
        MASKED_VIEWER_DATA,
        "",
    )

/** A placeholder with a number: an opaque value's or visitor data's. */
private val NUMBERED_PLACEHOLDER = Regex("""(?:$MASKED_OPAQUE_VALUE|$MASKED_VISITOR_DATA)\d+""")

/**
 * The IPv4 networks whose addresses aren't public: this network, private, shared, loopback,
 * link-local, documentation, multicast and reserved addresses.
 */
private val NON_PUBLIC_IPV4_NETWORKS =
    listOf(
        "0.0.0.0/8",
        "10.0.0.0/8",
        "100.64.0.0/10",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "172.16.0.0/12",
        "192.0.2.0/24",
        "192.168.0.0/16",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "224.0.0.0/3",
    )

/**
 * The values in a recording that say which machine, session, network or viewer it came from. The
 * recorder collects them from every set that it records in a run, and masks and looks for each in
 * every set.
 */
internal class RealValues(
    /** The machine's addresses, as googlevideo links and sw.js_data carry them, decoded. */
    val addresses: Set<String> = emptySet(),
    /** YouTube's visitor data values, in the order they first appear. */
    val visitorData: List<String> = emptyList(),
    /** The visitor IDs inside the visitor data values. */
    val visitorIds: Set<String> = emptySet(),
    /** The values of the cookies that YouTube set. */
    val cookieValues: Set<String> = emptySet(),
    /** The rollout tokens that YouTube gave. */
    val rolloutTokens: Set<String> = emptySet(),
    /**
     * The countries that YouTube placed the network in, as sw.js_data and visitor data name them.
     */
    val countries: Set<String> = emptySet(),
    /** The strings in data about a viewer that are long enough to look for anywhere. */
    val viewerValues: Set<String> = emptySet(),
) {
    /**
     * The network's countries but the one that every request asks for, which a saved set may name,
     * as it says nothing about the network.
     */
    val networkCountries: Set<String>
        get() = countries - MASKED_COUNTRY

    /**
     * Whether these values name the machine's address and the network's country, so the check can
     * look for them.
     */
    val isComplete: Boolean
        get() = addresses.isNotEmpty() && countries.isNotEmpty()

    operator fun plus(other: RealValues): RealValues =
        RealValues(
            addresses + other.addresses,
            (visitorData + other.visitorData).distinct(),
            visitorIds + other.visitorIds,
            cookieValues + other.cookieValues,
            rolloutTokens + other.rolloutTokens,
            countries + other.countries,
            viewerValues + other.viewerValues,
        )
}

/**
 * A real value as masking and the check find it in text: anywhere, or, for a viewer's handle, only
 * where it ends, so that a viewer's @al doesn't match a channel's @alpha.
 */
private class Needle(val value: String, isHandle: Boolean) {
    private val pattern = if (isHandle) Regex(Regex.escape(value) + HANDLE_END) else null

    fun count(text: String): Int = pattern?.findAll(text)?.count() ?: occurrences(text, value)

    fun replace(text: String, placeholder: String): String =
        pattern?.replace(text, Regex.escapeReplacement(placeholder))
            ?: text.replace(value, placeholder)

    fun isIn(text: String): Boolean = pattern?.containsMatchIn(text) ?: (value in text)
}

/** Whether [value], of [kind], is a viewer's handle, see [Needle]. */
private fun isHandle(kind: MaskedValue, value: String): Boolean =
    kind == MaskedValue.VIEWER_DATA && value.startsWith("@")

/** A masked recording, and how many values each kind of masking replaced or dropped. */
internal class MaskedExchanges(
    val exchanges: List<SavedExchange>,
    val counts: Map<MaskedValue, Int>,
)

/**
 * The values in [exchanges] that say which machine, session, network or viewer they came from,
 * found where YouTube puts them.
 */
internal fun realValuesOf(exchanges: List<SavedExchange>): RealValues {
    val texts = exchanges.flatMap(::textsOf)
    val addresses = mutableSetOf<String>()
    for (text in texts) {
        for (carrier in ADDRESS_CARRIERS) {
            for (match in carrier.findAll(text)) {
                val address = urlDecodedColons(match.groupValues[1])
                // A subtitle link names 0.0.0.0, which stands for no address. It isn't the
                // machine's, and as a number, 0, it would match all kinds of text.
                val isMachines =
                    isAddress(address) &&
                        !isUnspecifiedAddress(address) &&
                        address != MASKED_IPV4 &&
                        address != MASKED_IPV6
                if (isMachines) addresses += address
            }
        }
    }
    val visitorData = texts.flatMap(::visitorDataIn).distinct()
    val rolloutTokens = texts.flatMap { text ->
        ROLLOUT_TOKEN.findAll(text).map { it.groupValues[1] }
    }
    val cookieValues = mutableSetOf<String>()
    for (exchange in exchanges) {
        for (header in exchange.response.headers) {
            val value = header.value.substringBefore(';').substringAfter('=')
            if (
                header.name.equals(SET_COOKIE, ignoreCase = true) &&
                    value.length >= MIN_TOKEN_LENGTH_TO_MASK
            ) {
                cookieValues += value
            }
        }
    }
    val swJsData = exchanges.map { it.response.body }.filter(::isSwJsData)
    addresses += swJsData.flatMap(::addressStringsIn)
    val countries =
        swJsData.flatMap { text ->
            SW_JS_DATA_COUNTRY.findAll(text).map { it.groupValues[1] }
        } + visitorData.flatMap(::countriesInVisitorData)
    val viewerData = texts.map(::maskViewerData)
    // A handle can be short, and is looked for whatever its length. Other strings only from 8
    // characters, as a shorter one could match other text by chance.
    val viewerValues =
        viewerData.flatMap { it.realValues }.filter { it.length >= MIN_TOKEN_LENGTH_TO_MASK } +
            viewerData.flatMap { it.profiles }.filter { it.length > 1 }
    return RealValues(
        addresses,
        visitorData,
        visitorData.mapNotNull(::visitorIdOf).toSet(),
        cookieValues,
        rolloutTokens.filter { it.length >= MIN_TOKEN_LENGTH_TO_MASK }.toSet(),
        countries.toSet(),
        viewerValues.toSet(),
    )
}

/**
 * [exchanges] with every value that says which machine, session, network or viewer they came from
 * replaced by a placeholder, and every answer header dropped but those in [KEPT_RESPONSE_HEADERS].
 * [real] adds the real values that the other sets of the recorder's run hold. A value is replaced
 * with the same placeholder in every request and answer, so a request that carries a value from an
 * earlier answer, such as visitor data, still matches when the test replays the set.
 */
internal fun mask(
    exchanges: List<SavedExchange>,
    real: RealValues = RealValues(),
): MaskedExchanges {
    val counts = EnumMap<MaskedValue, Int>(MaskedValue::class.java)
    // The set's own values first, so its visitor data is numbered in the order it appears.
    val values = realValuesOf(exchanges) + real
    val replacements =
        replacementsFor(values).map { (real, replacement) ->
            Needle(real, isHandle(replacement.second, real)) to replacement
        }
    fun count(kind: MaskedValue, found: Int) {
        if (found > 0) counts.merge(kind, found, Int::plus)
    }
    fun maskText(text: String): String {
        var masked = text
        for ((needle, replacement) in replacements) {
            val (placeholder, kind) = replacement
            count(kind, needle.count(masked))
            masked = needle.replace(masked, placeholder)
        }
        for (rule in RULES) {
            masked = rule.pattern.replace(masked) { match -> replaceValue(match, rule, counts) }
        }
        for (pattern in listOf(COUNTRY_FIELD, COUNTRY_PAIR, COUNTRY_PARAMETER)) {
            masked =
                pattern.replace(masked) { match ->
                    val country = match.groupValues[1].uppercase(Locale.ROOT)
                    if (country in values.networkCountries) {
                        count(MaskedValue.COUNTRY, 1)
                        withValueReplaced(match, MASKED_COUNTRY)
                    } else {
                        match.value
                    }
                }
        }
        // sw.js_data names the network's country in no field, so it is found by its shape there.
        if (isSwJsData(masked)) {
            masked =
                SW_JS_DATA_COUNTRY.replace(masked) { match ->
                    if (match.groupValues[1] == MASKED_COUNTRY) {
                        match.value
                    } else {
                        count(MaskedValue.COUNTRY, 1)
                        withValueReplaced(match, MASKED_COUNTRY)
                    }
                }
            masked =
                SW_JS_DATA_FETCH_TOKEN.replace(masked) { match ->
                    if (match.groupValues[1] == MASKED_SESSION_TOKEN) {
                        match.value
                    } else {
                        count(MaskedValue.SESSION_TOKEN, 1)
                        withValueReplaced(match, MASKED_SESSION_TOKEN)
                    }
                }
        }
        for ((kind, step) in MASKING_STEPS) {
            val result = step(masked)
            count(kind, result.count)
            masked = result.text
        }
        return masked
    }
    val masked = exchanges.map { exchange ->
        val (keptHeaders, droppedHeaders) =
            exchange.response.headers.partition {
                it.name.lowercase(Locale.ROOT) in KEPT_RESPONSE_HEADERS
            }
        val cookieHeaders = droppedHeaders.count { it.name.equals(SET_COOKIE, ignoreCase = true) }
        if (cookieHeaders > 0) counts.merge(MaskedValue.COOKIE_HEADER, cookieHeaders, Int::plus)
        val withKeptHeaders =
            SavedExchange(
                exchange.request,
                SavedResponse(exchange.response.code, keptHeaders, exchange.response.body),
            )
        withKeptHeaders.withTexts(::maskText)
    }
    return MaskedExchanges(maskOpaqueValues(masked, counts), counts)
}

/**
 * [exchanges] with each opaque value that [keptValuesOf] doesn't keep replaced by TOKEN_1, TOKEN_2
 * and so on, in the order the values first appear, or by the number alone for a value that is a
 * number. A value gets the same placeholder in every request and answer, so a request that carries
 * one on from an earlier answer still matches. A value that [isKept] stays everywhere.
 */
private fun maskOpaqueValues(
    exchanges: List<SavedExchange>,
    counts: MutableMap<MaskedValue, Int>,
): List<SavedExchange> {
    val found = exchanges.flatMap(::opaqueValuesIn)
    val certainKeys = certainKeysOf(found)
    val kept = keptValuesOf(exchanges)
    val toMask =
        found.filter { countsAsOpaque(it, certainKeys) && !isKept(it, kept) }.map { it.value }
    val replacements = linkedMapOf<String, String>()
    for ((position, value) in toMask.distinct().withIndex()) {
        // A number outside a string stays a number, so the answer stays valid JSON.
        val number = position + 1
        val placeholder =
            if (value.all { it.isDigit() }) "$number" else "$MASKED_OPAQUE_VALUE$number"
        for (form in encodedFormsOf(value)) {
            replacements.putIfAbsent(form, placeholder)
        }
    }
    // A longer value first, so a value that contains another is replaced whole.
    val ordered = replacements.entries.sortedByDescending { it.key.length }
    fun maskText(text: String): String {
        var masked = text
        for ((real, placeholder) in ordered) {
            val found = occurrences(masked, real)
            if (found > 0) {
                masked = masked.replace(real, placeholder)
                counts.merge(MaskedValue.OPAQUE_VALUE, found, Int::plus)
            }
        }
        return masked
    }
    return exchanges.map { it.withTexts(::maskText) }
}

/** This exchange with [transform] applied to its link, the values of its headers and its bodies. */
private fun SavedExchange.withTexts(transform: (String) -> String): SavedExchange =
    SavedExchange(
        SavedRequest(
            request.method,
            transform(request.url),
            request.headers.map { Header(it.name, transform(it.value)) },
            request.body?.let(transform),
        ),
        SavedResponse(
            response.code,
            response.headers.map { Header(it.name, transform(it.value)) },
            transform(response.body),
        ),
    )

/**
 * The kinds of real value from [original] that are still in [masked], the same recording after
 * [mask]. It collects the real values from the original again, so it doesn't depend on what [mask]
 * collected.
 */
internal fun unmaskedKinds(
    original: List<SavedExchange>,
    masked: List<SavedExchange>,
): Set<MaskedValue> = unmaskedKinds(realValuesOf(original), masked)

/**
 * The kinds of [real] value that are still in [masked], a masked recording. The recorder writes a
 * set only when this is empty. It looks for each real value in every text of the set, and in every
 * layer that the check of opaque values decodes, with the same code, URL encoding and base64 nested
 * in each other included, as text and as raw bytes: the address in every form it knows, with its 4
 * or 16 bytes, the visitor data and visitor IDs, every cookie value, the rollout tokens, the
 * strings in data about a viewer, and the network's country in a country field. It also runs the
 * checks that [unmaskedKindsIn] runs on each saved file, and fails on any opaque value that
 * [keptValuesOf] doesn't keep.
 */
internal fun unmaskedKinds(real: RealValues, masked: List<SavedExchange>): Set<MaskedValue> {
    val search = RealValueSearch(real)
    val found = EnumSet.noneOf(MaskedValue::class.java)
    for (exchange in masked) {
        found += search.kindsIn(exchange)
    }
    val opaque = masked.flatMap(::opaqueValuesIn)
    val certainKeys = certainKeysOf(opaque)
    val kept = keptValuesOf(masked)
    if (opaque.any { countsAsOpaque(it, certainKeys) && !isKept(it, kept) }) {
        found += MaskedValue.OPAQUE_VALUE
    }
    val cookieHeaders =
        masked.flatMap { it.response.headers }.any { it.name.equals(SET_COOKIE, ignoreCase = true) }
    if (cookieHeaders) found += MaskedValue.COOKIE_HEADER
    return found
}

/**
 * A real value to look for in decoded bytes, of [kind]: in the bytes one character each, or, when
 * it [readsUtf8], in the bytes read as UTF-8.
 */
private class BytePattern(val kind: MaskedValue, val needle: Needle, val readsUtf8: Boolean)

/** Looks for [real] values in a masked exchange, see [unmaskedKinds]. */
private class RealValueSearch(private val real: RealValues) {
    private val searches =
        searchesFor(real).map { (kind, form) -> kind to Needle(form, isHandle(kind, form)) }
    private val bytePatterns =
        searchesFor(real).map { (kind, form) ->
            if (isHandle(kind, form)) {
                // A handle is looked for in the bytes read as UTF-8, so a character outside ASCII
                // after it reads as one character, and its end is still found.
                BytePattern(kind, Needle(form, isHandle = true), readsUtf8 = true)
            } else {
                val bytes = String(form.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
                BytePattern(kind, Needle(bytes, isHandle = false), readsUtf8 = false)
            }
        } +
            real.addresses.mapNotNull(::bytesOf).map {
                val bytes = String(it, Charsets.ISO_8859_1)
                BytePattern(MaskedValue.ADDRESS, Needle(bytes, isHandle = false), readsUtf8 = false)
            }
    private val expandedAddresses =
        real.addresses.flatMap { listOfNotNull(expandedIpv6(it) ?: it, mappedIpv6(it)) }.toSet()

    /** The kinds of real value in [exchange], as text, in a country field, or as bytes. */
    fun kindsIn(exchange: SavedExchange): Set<MaskedValue> {
        val found = EnumSet.noneOf(MaskedValue::class.java)
        val decoded = decodedTextsOf(exchange)
        val raw = textsOf(exchange)
        for (text in raw + decoded.texts.map { it.second }) {
            found += kindsInText(text)
        }
        for ((key, text) in decoded.texts) {
            val isCountry = text.uppercase(Locale.ROOT) in real.networkCountries
            if (isCountry && COUNTRY_KEY.matches(key)) found += MaskedValue.COUNTRY
        }
        for (bytes in decoded.bytes) {
            val asBytes = String(bytes, Charsets.ISO_8859_1)
            val asUtf8 = String(bytes, Charsets.UTF_8)
            found +=
                bytePatterns
                    .filter { it.needle.isIn(if (it.readsUtf8) asUtf8 else asBytes) }
                    .map { it.kind }
        }
        for (text in raw) {
            found += unmaskedKindsIn(text)
        }
        return found
    }

    private fun kindsInText(text: String): Set<MaskedValue> {
        val found =
            searches.filter { (_, needle) -> needle.isIn(text) }.map { it.first }.toMutableSet()
        if (addressLiteralsIn(text).any { it in expandedAddresses }) found += MaskedValue.ADDRESS
        if (countriesIn(text).any { it in real.networkCountries }) found += MaskedValue.COUNTRY
        return found
    }
}

/** Each real value as text to look for, with its kind. */
private fun searchesFor(real: RealValues): List<Pair<MaskedValue, String>> =
    real.addresses.flatMap { address ->
        addressFormsOf(address).map { MaskedValue.ADDRESS to it }
    } +
        (real.visitorData + real.visitorIds).map { MaskedValue.VISITOR_DATA to it } +
        real.cookieValues.map { MaskedValue.COOKIE_VALUE to it } +
        real.rolloutTokens.map { MaskedValue.ROLLOUT_TOKEN to it } +
        real.viewerValues.map { MaskedValue.VIEWER_DATA to it }

/**
 * [address] in every form that the check looks for: as text, see [textFormsOf], as one number in
 * decimal and in hex, and an IPv4 address as an IPv4-mapped IPv6 address, written with hex groups.
 */
private fun addressFormsOf(address: String): List<String> {
    val forms =
        if (isIpv4(address)) {
            val value = ipv4Value(address)
            val hex = value.toString(HEX_RADIX).padStart(IPV4_HEX_DIGITS, '0')
            val high = hex.take(IPV6_GROUP_DIGITS)
            val low = hex.drop(IPV6_GROUP_DIGITS)
            val trimmed =
                "${high.trimStart('0').ifEmpty { "0" }}:${low.trimStart('0').ifEmpty { "0" }}"
            listOf(
                value.toString(),
                hex,
                "$IPV4_MAPPED_PREFIX$high:$low",
                "$IPV4_MAPPED_PREFIX$trimmed",
            )
        } else {
            val hex = expandedIpv6(address)?.replace(":", "").orEmpty()
            listOfNotNull(hex.ifEmpty { null }, hex.toBigIntegerOrNull(HEX_RADIX)?.toString())
        }
    return textFormsOf(address) + forms.flatMap { listOf(it, it.uppercase(Locale.ROOT)) }.distinct()
}

/**
 * [address], an IPv4 address, as an IPv4-mapped IPv6 address written out in full, or null for an
 * IPv6 address.
 */
private fun mappedIpv6(address: String): String? =
    if (isIpv4(address)) {
        val hex = ipv4Value(address).toString(HEX_RADIX).padStart(IPV4_HEX_DIGITS, '0')
        expandedIpv6(
            "$IPV4_MAPPED_PREFIX${hex.take(IPV6_GROUP_DIGITS)}:${hex.drop(IPV6_GROUP_DIGITS)}",
        )
    } else {
        null
    }

/**
 * The IP addresses written in [text], public or not, IPv6 ones written out in full, so an address
 * written another way still matches.
 */
private fun addressLiteralsIn(text: String): Set<String> =
    (IPV4_LITERAL.findAll(text).map { it.groupValues[1] } +
            IPV6_LITERAL.findAll(text).mapNotNull {
                expandedIpv6(urlDecodedColons(it.groupValues[1]))
            })
        .toSet()

/** The countries that a country field in [text] names, in upper case. */
private fun countriesIn(text: String): List<String> =
    listOf(COUNTRY_FIELD, COUNTRY_PAIR, COUNTRY_PARAMETER).flatMap { pattern ->
        pattern.findAll(text).map { it.groupValues[1].uppercase(Locale.ROOT) }
    }

/**
 * Whether [value] is a placeholder that masking writes, as it is or URL-encoded, so a check accepts
 * it where a real value was.
 */
internal fun isPlaceholder(value: String): Boolean {
    val decoded = urlDecoded(urlDecoded(value))
    return decoded in PLACEHOLDERS || NUMBERED_PLACEHOLDER.matches(decoded)
}

/**
 * The kinds of value in [text], a saved file, that aren't masked, found without knowing any real
 * value: an IP address, an address in a googlevideo link that isn't a documentation address,
 * visitor data that isn't a placeholder, any value that a masking rule finds where its placeholder
 * should be, a country or a value of the fetch in sw.js_data that isn't its placeholder, a tracked
 * link's parameter that is neither kept nor masked, data about a viewer that isn't masked, and an
 * experiment flag that isn't masked. The recorder runs this before it writes a set, and
 * SavedResponsesPrivacyTest runs it on every saved file.
 */
internal fun unmaskedKindsIn(text: String): Set<MaskedValue> {
    val found = mutableSetOf<MaskedValue>()
    if (addressesIn(text).isNotEmpty()) found += MaskedValue.ADDRESS
    for (carrier in ADDRESS_CARRIERS) {
        for (match in carrier.findAll(text)) {
            val address = urlDecodedColons(match.groupValues[1])
            if (address != MASKED_IPV4 && address != MASKED_IPV6) found += MaskedValue.ADDRESS
        }
    }
    if (visitorDataIn(text).any { !MASKED_VISITOR_DATA_VALUE.matches(it) }) {
        found += MaskedValue.VISITOR_DATA
    }
    for (rule in RULES) {
        if (rule.pattern.findAll(text).any { it.groupValues[1] != rule.placeholder }) {
            found += rule.kind
        }
    }
    if (isSwJsData(text)) found += unmaskedKindsInSwJsData(text)
    if (unmaskedLinkParametersIn(text).isNotEmpty()) found += MaskedValue.LINK_PARAMETER
    if (hasUnmaskedViewerData(text)) found += MaskedValue.VIEWER_DATA
    if (hasUnmaskedExperimentFlags(text)) found += MaskedValue.EXPERIMENT_FLAGS
    return found
}

/**
 * The public IP addresses written in [text], as they are or with URL-encoded colons. Documentation,
 * private and loopback addresses don't count, so the placeholders don't either.
 */
private fun addressesIn(text: String): List<String> {
    val ipv4 = IPV4_LITERAL.findAll(text).map { it.groupValues[1] }.filter(::isPublicIpv4)
    val ipv6 =
        IPV6_LITERAL.findAll(text)
            .map { urlDecodedColons(it.groupValues[1]) }
            .filter(::isPublicIpv6)
    return (ipv4 + ipv6).toList()
}

/** The real values to replace, each with its placeholder and the kind of value it is. */
private fun replacementsFor(values: RealValues): Map<String, Pair<String, MaskedValue>> {
    val replacements = linkedMapOf<String, Pair<String, MaskedValue>>()
    for (address in values.addresses) {
        val placeholder = if (':' in address) MASKED_IPV6 else MASKED_IPV4
        for ((form, placeholderForm) in textFormsOf(address).zip(textFormsOf(placeholder))) {
            replacements[form] = placeholderForm to MaskedValue.ADDRESS
        }
    }
    for ((position, visitorData) in values.visitorData.withIndex()) {
        replacements[visitorData] =
            "$MASKED_VISITOR_DATA${position + 1}" to MaskedValue.VISITOR_DATA
    }
    for (cookieValue in values.cookieValues) {
        replacements[cookieValue] = MASKED_COOKIE_VALUE to MaskedValue.COOKIE_VALUE
    }
    for (rolloutToken in values.rolloutTokens) {
        replacements[rolloutToken] = MASKED_ROLLOUT_TOKEN to MaskedValue.ROLLOUT_TOKEN
    }
    for (viewerValue in values.viewerValues) {
        replacements[viewerValue] = MASKED_VIEWER_DATA to MaskedValue.VIEWER_DATA
    }
    // A longer value first, so a value that contains another is replaced whole.
    return replacements.entries.sortedByDescending { it.key.length }.associate { it.toPair() }
}

/** The match's value replaced by [rule]'s placeholder, counted in [counts] if it was real. */
private fun replaceValue(
    match: MatchResult,
    rule: Rule,
    counts: MutableMap<MaskedValue, Int>,
): String {
    if (match.groupValues[1] == rule.placeholder) return match.value
    counts.merge(rule.kind, 1, Int::plus)
    return withValueReplaced(match, rule.placeholder)
}

/** The text of [match] with its first group replaced by [placeholder]. */
private fun withValueReplaced(match: MatchResult, placeholder: String): String {
    val value = checkNotNull(match.groups[1])
    val start = value.range.first - match.range.first
    return match.value.replaceRange(start, start + value.value.length, placeholder)
}

/** Whether [text] is the body of sw.js_data, which starts with [SW_JS_DATA_PREFIX]. */
private fun isSwJsData(text: String): Boolean = text.startsWith(SW_JS_DATA_PREFIX)

/**
 * The kinds of value in [text], the body of sw.js_data, that a rule for sw.js_data finds where its
 * placeholder should be: a country that isn't GB, and a value of the fetch that isn't TOKEN.
 */
private fun unmaskedKindsInSwJsData(text: String): Set<MaskedValue> {
    val found = mutableSetOf<MaskedValue>()
    val countries = SW_JS_DATA_COUNTRY.findAll(text).map { it.groupValues[1] }
    if (countries.any { it != MASKED_COUNTRY }) found += MaskedValue.COUNTRY
    val tokens = SW_JS_DATA_FETCH_TOKEN.findAll(text).map { it.groupValues[1] }
    if (tokens.any { it != MASKED_SESSION_TOKEN }) found += MaskedValue.SESSION_TOKEN
    return found
}

/**
 * The IP addresses that [text], the body of sw.js_data, holds as strings of their own, as it holds
 * the machine's, written out in full for IPv6. The placeholders don't count.
 */
private fun addressStringsIn(text: String): List<String> =
    SW_JS_DATA_ANY_STRING.findAll(text)
        .map { it.groupValues[1] }
        .filter { isAddress(it) && it != MASKED_IPV4 && expandedIpv6(it) != MASKED_IPV6_EXPANDED }
        .toList()

/**
 * The visitor data in [text], in the order it appears: every visitorData member of a JSON body, and
 * in sw.js_data, every string that decodes to a visitor ID.
 */
private fun visitorDataIn(text: String): List<String> {
    val named = VISITOR_DATA.findAll(text).map { it.groupValues[1] }.toList()
    if (!isSwJsData(text)) return named
    val unnamed = SW_JS_DATA_STRING.findAll(text).map { it.groupValues[1] }
    return named + unnamed.filter { visitorIdOf(it) != null }
}

/** Every text of [exchange] that a real value could be in. */
private fun textsOf(exchange: SavedExchange): List<String> =
    listOfNotNull(exchange.request.url, exchange.request.body, exchange.response.body) +
        exchange.request.headers.map { it.value } +
        exchange.response.headers.map { it.value }

/**
 * [address] as text can carry it: an IPv4 address as it is, and an IPv6 address as it is, with its
 * colons URL-encoded once or twice, in either case, and with all eight groups written out.
 */
private fun textFormsOf(address: String): List<String> {
    val expanded = expandedIpv6(address) ?: return listOf(address)
    val encodedColons = listOf("%3A", "%3a", "%253A", "%253a")
    return listOf(address) + encodedColons.map { address.replace(":", it) } + expanded
}

/** How many times [part] appears in [text], without overlapping. */
private fun occurrences(text: String, part: String): Int {
    var count = 0
    var from = text.indexOf(part)
    while (from >= 0) {
        count++
        from = text.indexOf(part, from + part.length)
    }
    return count
}

/**
 * [text] with colons that are URL-encoded once or twice decoded. An address's colons are its only
 * encoded characters.
 */
private fun urlDecodedColons(text: String): String =
    text.replace("%253A", ":").replace("%253a", ":").replace("%3A", ":").replace("%3a", ":")

private fun isAddress(text: String): Boolean = isIpv4(text) || expandedIpv6(text) != null

/** Whether [address] is 0.0.0.0 or ::, the address that stands for no address. */
private fun isUnspecifiedAddress(address: String): Boolean =
    if (isIpv4(address)) {
        ipv4Value(address) == 0L
    } else {
        expandedIpv6(address)?.all { it == '0' || it == ':' } == true
    }

private fun isIpv4(text: String): Boolean {
    val octets = text.split('.')
    return octets.size == IPV4_OCTETS &&
        octets.all { octet ->
            octet.length in 1..MAX_OCTET_DIGITS &&
                octet.all { it in '0'..'9' } &&
                octet.toInt() <= MAX_OCTET
        }
}

/** [address], an IPv4 address, as one number. */
private fun ipv4Value(address: String): Long =
    address.split('.').fold(0L) { value, octet -> (value shl Byte.SIZE_BITS) + octet.toLong() }

private fun isPublicIpv4(text: String): Boolean {
    if (!isIpv4(text)) return false
    val value = ipv4Value(text)
    return NON_PUBLIC_IPV4_NETWORKS.none { network ->
        val (base, prefixLength) = network.split('/')
        val shift = IPV4_BITS - prefixLength.toInt()
        value shr shift == ipv4Value(base) shr shift
    }
}

private fun isPublicIpv6(text: String): Boolean {
    val expanded = expandedIpv6(text) ?: return false
    return !expanded.startsWith(IPV6_DOCUMENTATION_PREFIX) && expanded !in NON_PUBLIC_IPV6
}

/**
 * [address] with all eight groups of four hex digits written out, in lower case, or null if it
 * isn't an IPv6 address.
 */
internal fun expandedIpv6(address: String): String? {
    val halves = address.lowercase(Locale.ROOT).split("::")
    val groups = halves.map { half -> if (half.isEmpty()) emptyList() else half.split(':') }
    val count = groups.sumOf { it.size }
    val wellFormed =
        halves.size <= 2 &&
            groups.flatten().all { group ->
                group.length in 1..IPV6_GROUP_DIGITS &&
                    group.all { it.digitToIntOrNull(HEX_RADIX) != null }
            } &&
            if (halves.size == 2) count < IPV6_GROUPS else count == IPV6_GROUPS
    if (!wellFormed) return null
    val zeros = List(IPV6_GROUPS - count) { "0" }
    return (groups.first() + zeros + groups.getOrElse(1) { emptyList() }).joinToString(":") {
        it.padStart(IPV6_GROUP_DIGITS, '0')
    }
}

/** The bytes of [address] as a packet carries them, or null if it isn't an IP address. */
private fun bytesOf(address: String): ByteArray? =
    if (isIpv4(address)) {
        address.split('.').map { it.toInt().toByte() }.toByteArray()
    } else {
        expandedIpv6(address)
            ?.split(':')
            ?.flatMap { group ->
                val value = group.toInt(HEX_RADIX)
                listOf((value shr Byte.SIZE_BITS).toByte(), value.toByte())
            }
            ?.toByteArray()
    }

/** The visitor ID inside [visitorData], the first field of a base64-encoded protobuf message. */
private fun visitorIdOf(visitorData: String): String? {
    val bytes = base64Bytes(visitorData) ?: ByteArray(0)
    val length = bytes.getOrNull(1)?.toInt() ?: 0
    val hasVisitorId =
        bytes.firstOrNull()?.toInt() == VISITOR_ID_TAG &&
            length > 0 &&
            bytes.size >= VISITOR_ID_START + length
    return if (hasVisitorId) String(bytes, VISITOR_ID_START, length, Charsets.US_ASCII) else null
}

/**
 * The countries in [visitorData]: each two capital letters that the protobuf message holds as a
 * field of its own, as it holds the network's country.
 */
private fun countriesInVisitorData(visitorData: String): List<String> {
    val bytes = base64Bytes(visitorData) ?: return emptyList()
    return (0 until bytes.size - COUNTRY_CODE_LENGTH - 1).mapNotNull { start ->
        val isCountry =
            bytes[start].toInt() and WIRE_TYPE_BITS == LENGTH_DELIMITED &&
                bytes[start + 1].toInt() == COUNTRY_CODE_LENGTH &&
                (start + 2..start + COUNTRY_CODE_LENGTH + 1).all {
                    bytes[it].toInt().toChar() in 'A'..'Z'
                }
        if (isCountry) String(bytes, start + 2, COUNTRY_CODE_LENGTH, Charsets.US_ASCII) else null
    }
}
