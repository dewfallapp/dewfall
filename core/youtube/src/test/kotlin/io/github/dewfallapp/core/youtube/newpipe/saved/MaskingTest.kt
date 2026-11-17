package io.github.dewfallapp.core.youtube.newpipe.saved

import java.net.URLEncoder
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Values in the shapes that YouTube's answers carry them: made up, but CHANNEL_ID, which is NASA's
// public channel ID. The addresses are public ones that no test machine has.
private const val IPV6 = "2a02:1234:5678:9abc:1:2:3:4"
private const val IPV6_ENCODED = "2a02%3A1234%3A5678%3A9abc%3A1%3A2%3A3%3A4"
private const val IPV4 = "93.184.216.34"
private const val VISITOR_ID = "Zv65wXyQ1aB"
private const val COOKIE = "CgJHQhIEGgAgOQ%3D%3D"
private const val TOKEN = "Zv65wXyQ1aBcD9eFgH2iJ"
private const val OTHER_TOKEN = "EgthcXotS0UtYnBLUSA-KAE"
private const val CHANNEL_ID = "UCLA_DiR1FfKNvjuUpBHmylQ"
private const val VISITOR_ID_URL = "https://www.youtube.com/youtubei/v1/visitor_id"
private const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player"
private const val SW_JS_URL = "https://www.youtube.com/sw.js"

/**
 * A key in the shape of YouTube's public web key, built at run time so that the file holds nothing
 * that GitHub's secret scanning would flag.
 */
private val API_KEY = "AIza" + "0".repeat(35)

/** Visitor data as YouTube writes it: a protobuf message whose first field is the visitor ID. */
private val VISITOR_DATA =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(
            byteArrayOf(0x0a, VISITOR_ID.length.toByte()) +
                VISITOR_ID.toByteArray() +
                byteArrayOf(0x28, 0x01)
        )
        .plus("%3D%3D")

/** The same visitor ID in the other encoding that the visitor_data tracking parameter uses. */
private val FEEDBACK_VISITOR_DATA =
    Base64.getEncoder().encodeToString(("\u0000" + VISITOR_ID).toByteArray())

class MaskingTest {
    @Test
    fun `an IPv6 address is masked in a link's query and path, as it is and URL-encoded`() {
        val ipv6EncodedTwice = IPV6_ENCODED.replace("%", "%25")
        val body =
            """{"url":"https://rr1---sn-masked.googlevideo.com/videoplayback""" +
                """?expire=1&ip=$IPV6_ENCODED",""" +
                """"hls":"https://manifest.googlevideo.com/api/manifest""" +
                """/hls_variant/expire/1/ip/$IPV6/id/x",""" +
                """"next":"https%3A%2F%2Fwww.youtube.com%3Fx%3D$ipv6EncodedTwice"}"""

        val masked = mask(listOf(exchange(responseBody = body)))

        val maskedBody = masked.exchanges.single().response.body
        assertTrue(maskedBody.contains("ip=2001%3Adb8%3A%3A1\""), maskedBody)
        assertTrue(maskedBody.contains("/ip/$MASKED_IPV6/"), maskedBody)
        assertTrue(maskedBody.contains("x%3D2001%253Adb8%253A%253A1\""), maskedBody)
        assertFalse(maskedBody.contains("2a02"), maskedBody)
        assertEquals(3, masked.counts[MaskedValue.ADDRESS])
    }

    @Test
    fun `an IPv4 address is masked wherever it appears, as a link names it`() {
        val body =
            """{"url":"https://x.googlevideo.com/videoplayback?ip=$IPV4&expire=1","also":"$IPV4"}"""

        val maskedBody =
            mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """{"url":"https://x.googlevideo.com/videoplayback?ip=$MASKED_IPV4&expire=1",""" +
                """"also":"$MASKED_IPV4"}""",
            maskedBody,
        )
    }

    @Test
    fun `visitor data gets one placeholder in the answer and in the request that carries it on`() {
        val recording =
            listOf(
                exchange(
                    url = VISITOR_ID_URL,
                    responseBody = """{"responseContext":{"visitorData":"$VISITOR_DATA"}}""",
                ),
                exchange(
                    url = PLAYER_URL,
                    requestBody = """{"context":{"client":{"visitorData":"$VISITOR_DATA"}}}""",
                ),
            )

        val masked = mask(recording).exchanges

        assertEquals(
            """{"responseContext":{"visitorData":"${MASKED_VISITOR_DATA}1"}}""",
            masked[0].response.body,
        )
        assertEquals(
            """{"context":{"client":{"visitorData":"${MASKED_VISITOR_DATA}1"}}}""",
            masked[1].request.body,
        )
        assertEquals(emptySet(), unmaskedKinds(recording, masked))
    }

    @Test
    fun `the visitor ID in the tracking parameter is masked too`() {
        val body = """{"params":[{"key":"visitor_data","value":"$FEEDBACK_VISITOR_DATA"}]}"""
        val recording =
            listOf(
                exchange(responseBody = """{"visitorData":"$VISITOR_DATA"}"""),
                exchange(responseBody = body),
            )

        val masked = mask(recording)

        assertEquals(
            """{"params":[{"key":"visitor_data","value":"VISITOR_DATA"}]}""",
            masked.exchanges[1].response.body,
        )
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
    }

    @Test
    fun `the check finds a visitor ID that masking missed, even inside another token`() {
        val recording = listOf(exchange(responseBody = """{"visitorData":"$VISITOR_DATA"}"""))
        val unknownToken =
            Base64.getUrlEncoder()
                .encodeToString(("prefix-" + VISITOR_ID + "-suffix").toByteArray())
        val missed =
            listOf(
                exchange(
                    responseBody =
                        """{"visitorData":"${MASKED_VISITOR_DATA}1","x":"$unknownToken"}"""
                )
            )

        assertEquals(
            setOf(MaskedValue.VISITOR_DATA, MaskedValue.OPAQUE_VALUE),
            unmaskedKinds(recording, missed),
        )
    }

    @Test
    fun `cookies are dropped, and their values masked where a body repeats them`() {
        val recording =
            listOf(
                exchange(
                    responseHeaders =
                        listOf(
                            Header(CONTENT_TYPE, "application/json"),
                            Header("Date", "Tue, 17 Nov 2026 10:00:00 GMT"),
                            Header(
                                "Set-Cookie",
                                "VISITOR_PRIVACY_METADATA=$COOKIE; Domain=.youtube.com",
                            ),
                        ),
                    responseBody = """{"cookie":"$COOKIE"}""",
                )
            )

        val masked = mask(recording)

        val response = masked.exchanges.single().response
        assertEquals(listOf(Header(CONTENT_TYPE, "application/json")), response.headers)
        assertEquals("""{"cookie":"COOKIE_VALUE"}""", response.body)
        assertEquals(1, masked.counts[MaskedValue.COOKIE_HEADER])
        assertEquals(1, masked.counts[MaskedValue.COOKIE_VALUE])
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
    }

    @Test
    fun `the network's country, event IDs, CDN nodes, bandwidth and the API key are masked`() {
        val body =
            """var ytcfg={"INNERTUBE_API_KEY":"$API_KEY",""" +
                """"INNERTUBE_CONTEXT":{"client":{"gl":"ZZ"}}};""" +
                """u="https://rr3---sn-abcd12-ef34.googlevideo.com/videoplayback?expire=1""" +
                """\u0026ei=Ab12Cd34Ef\u0026mn=sn-abcd12-ef34%2Csn-gh5678ij&initcwndbps=1234567";"""

        val maskedBody =
            mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """var ytcfg={"INNERTUBE_API_KEY":"API_KEY",""" +
                """"INNERTUBE_CONTEXT":{"client":{"gl":"GB"}}};""" +
                """u="https://rr3---sn-masked.googlevideo.com/videoplayback?expire=1""" +
                """\u0026ei=EVENT_ID\u0026mn=sn-masked&initcwndbps=0";""",
            maskedBody,
        )
    }

    @Test
    fun `event IDs and the tokens that YouTube makes for an answer or a session are masked`() {
        val body =
            """{"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":""" +
                """[{"key":"GetPlayer_rid","value":"0x1a2b3c4d"}]}],""" +
                """"mainAppWebResponseContext":{"trackingParam":"Qq9_wErTyUiO"}},""" +
                """"responseId":"IhMIxQabc","trackingParams":"CAAQzz9kAbcd",""" +
                """"clickTrackingParams":"CBAQ","challenge":"a=6&b=xyz&hh=abc",""" +
                """"stats":"https://s.youtube.com/api/stats/playback""" +
                """?cl=1&plid=AAA&of=BBB&vm=CCC",""" +
                """"link":"https://www.youtube.com/redirect""" +
                """?redir_token=QUFF&q=https%3A%2F%2Fa.org",""" +
                """"gv":"https://x.googlevideo.com/videoplayback""" +
                """?bui=Xb1Gh&spc=Yc2Jk&itag=18&sig=AJf&lsig=APa"}"""

        val masked = mask(listOf(exchange(responseBody = body)))

        assertEquals(
            """{"responseContext":{"serviceTrackingParams":[{"service":"CSI","params":""" +
                """[{"key":"GetPlayer_rid","value":"EVENT_ID"}]}],""" +
                """"mainAppWebResponseContext":{"trackingParam":"EVENT_ID"}},""" +
                """"responseId":"EVENT_ID","trackingParams":"EVENT_ID",""" +
                """"clickTrackingParams":"EVENT_ID","challenge":"TOKEN",""" +
                """"stats":"https://s.youtube.com/api/stats/playback""" +
                """?cl=MASKED&plid=TOKEN&of=TOKEN&vm=TOKEN",""" +
                """"link":"https://www.youtube.com/redirect""" +
                """?redir_token=TOKEN&q=https%3A%2F%2Fa.org",""" +
                """"gv":"https://x.googlevideo.com/videoplayback""" +
                """?bui=TOKEN&spc=TOKEN&itag=MASKED&sig=TOKEN&lsig=TOKEN"}""",
            masked.exchanges.single().response.body,
        )
        assertEquals(5, masked.counts[MaskedValue.EVENT_ID])
        assertEquals(9, masked.counts[MaskedValue.SESSION_TOKEN])
        assertEquals(2, masked.counts[MaskedValue.LINK_PARAMETER])
    }

    @Test
    fun `the tokens that the second review found, and the number of sw_js, are masked`() {
        val body =
            """/** 12345678909876543210 */var x={"videoPlaybackUstreamerConfig":"Cpo$TOKEN",""" +
                """"continueAction":"QUFF$TOKEN","link":"https://accounts.google.com/x""" +
                """?continue=https%3A%2F%2Fa.org%26continue_action%3DQUFF$TOKEN",""" +
                """"gv":"https://x.googlevideo.com/videoplayback?id=o-$TOKEN&itag=18"};"""

        val maskedBody =
            mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """/** TOKEN */var x={"videoPlaybackUstreamerConfig":"TOKEN",""" +
                """"continueAction":"TOKEN","link":"https://accounts.google.com/x""" +
                """?continue=https%3A%2F%2Fa.org%26continue_action%3DTOKEN",""" +
                """"gv":"https://x.googlevideo.com/videoplayback?id=TOKEN&itag=MASKED"};""",
            maskedBody,
        )
    }

    @Test
    fun `any other opaque value gets a numbered placeholder, the same in answer and request`() {
        val recording =
            listOf(
                exchange(responseBody = """{"entityKey":"$OTHER_TOKEN","other":"$TOKEN"}"""),
                exchange(requestBody = """{"entityKey":"$OTHER_TOKEN"}"""),
            )

        val masked = mask(recording)

        assertEquals(
            """{"entityKey":"TOKEN_1","other":"TOKEN_2"}""",
            masked.exchanges[0].response.body,
        )
        assertEquals("""{"entityKey":"TOKEN_1"}""", masked.exchanges[1].request.body)
        assertEquals(3, masked.counts[MaskedValue.OPAQUE_VALUE])
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
    }

    @Test
    fun `a kept value stays wherever it appears, and a masked number stays a number`() {
        val body =
            """{"channelId":"$CHANNEL_ID","r":"/channel/$CHANNEL_ID",""" +
                """"playlistId":"UU$TOKEN","n":12345678901234567890}"""

        val maskedBody =
            mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """{"channelId":"$CHANNEL_ID","r":"/channel/$CHANNEL_ID",""" +
                """"playlistId":"TOKEN_1","n":2}""",
            maskedBody,
        )
    }

    @Test
    fun `experiment IDs and the size of the network are masked`() {
        val body =
            """{"params":[{"key":"e","value":"51849937,51534669"}],""" +
                """"gv":"https://x.googlevideo.com/videoplayback?pl=36&fexp=24004644%2C51010235&itag=18"}"""

        val maskedBody =
            mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """{"params":[{"key":"e","value":"EXPERIMENTS"}],""" +
                """"gv":"https://x.googlevideo.com/videoplayback""" +
                """?pl=0&fexp=EXPERIMENTS&itag=MASKED"}""",
            maskedBody,
        )
    }

    @Test
    fun `masking leaves alone what the requests and the layer read`() {
        val requestBody =
            """{"context":{"client":{"osVersion":"25.6.0.23O471","hl":"en-GB","gl":"GB"}},"cpn":"HixlqCKwYnI4sczA"}"""
        val responseBody =
            """{"url":"https://x.googlevideo.com/videoplayback?expire=1794871994",""" +
                """"link":"https://www.youtube.com/redirect?q=https%3A%2F%2Fwww.blender.org"}"""
        val recording = listOf(exchange(requestBody = requestBody, responseBody = responseBody))

        val masked = mask(recording)

        assertEquals(requestBody, masked.exchanges.single().request.body)
        assertEquals(responseBody, masked.exchanges.single().response.body)
        assertEquals(emptyMap(), masked.counts)
        assertEquals(emptySet(), unmaskedKindsIn(requestBody))
        assertEquals(emptySet(), unmaskedKindsIn(responseBody))
    }

    @Test
    fun `a saved file with a real value fails the check that needs no real values`() {
        assertEquals(setOf(MaskedValue.ADDRESS), unmaskedKindsIn("""{"note":"from $IPV6"}"""))
        assertEquals(setOf(MaskedValue.ADDRESS), unmaskedKindsIn("""{"note":"from $IPV4"}"""))
        assertEquals(setOf(MaskedValue.ADDRESS), unmaskedKindsIn("""videoplayback?ip=10.0.0.1"""))
        assertEquals(
            setOf(MaskedValue.VISITOR_DATA),
            unmaskedKindsIn("""{"visitorData":"$VISITOR_DATA"}"""),
        )
        assertEquals(
            setOf(MaskedValue.ROLLOUT_TOKEN),
            unmaskedKindsIn("""{"rolloutToken":"CLnHkJ"}"""),
        )
        assertEquals(setOf(MaskedValue.API_KEY), unmaskedKindsIn(API_KEY))
    }

    @Test
    fun `placeholders and addresses that aren't public pass the check`() {
        val text =
            """{"visitorData":"${MASKED_VISITOR_DATA}12","a":"$MASKED_IPV4","b":"$MASKED_IPV6",""" +
                """"c":"ip=2001%3Adb8%3A%3A1","d":"127.0.0.1","e":"time 12:30:45","f":"v1.20261112.01.00"}"""

        assertEquals(emptySet(), unmaskedKindsIn(text))
    }

    @Test
    fun `an IPv6 address is written out in full, and other text isn't one`() {
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", expandedIpv6(MASKED_IPV6))
        assertEquals("2a02:1234:5678:9abc:0001:0002:0003:0004", expandedIpv6(IPV6))
        assertNull(expandedIpv6("12:30:45"))
        assertNull(expandedIpv6("1::2::3"))
        assertNull(expandedIpv6("std::map"))
    }

    @Test
    fun `the check finds a real address, visitor ID or cookie hidden in nested encodings`() {
        val recording =
            listOf(
                exchange(
                    responseHeaders =
                        listOf(Header("Set-Cookie", "YSC=$COOKIE; Domain=.youtube.com")),
                    responseBody =
                        """{"url":"https://x.googlevideo.com/videoplayback?ip=$IPV4",""" +
                            """"v6":"https://x.googlevideo.com/videoplayback?ip=$IPV6_ENCODED",""" +
                            """"visitorData":"$VISITOR_DATA","gl":"ZZ"}""",
                )
            )
        val masked = mask(recording).exchanges
        val hiding =
            mapOf(
                MaskedValue.ADDRESS to listOf(ipv4Bytes(IPV4), ipv6Bytes(), IPV4.toByteArray()),
                MaskedValue.VISITOR_DATA to listOf(VISITOR_ID.toByteArray()),
                MaskedValue.COOKIE_VALUE to listOf(COOKIE.toByteArray()),
            )

        assertEquals(emptySet(), unmaskedKinds(recording, masked))
        for ((kind, hidden) in hiding) {
            for (bytes in hidden) {
                val tampered =
                    masked + exchange(responseBody = """{"blob":"${hiddenInLayers(bytes)}"}""")
                assertTrue(kind in unmaskedKinds(recording, tampered), kind.name)
            }
        }
    }

    @Test
    fun `the check finds an address written as one number or with other zeros`() {
        val recording =
            listOf(
                exchange(
                    responseBody =
                        """{"a":"https://x.googlevideo.com/videoplayback?ip=$IPV4",""" +
                            """"b":"https://x.googlevideo.com/videoplayback?ip=$IPV6_ENCODED"}"""
                )
            )
        val masked = mask(recording).exchanges
        val asNumber = IPV4.split('.').fold(0L) { value, octet -> value * 256 + octet.toLong() }
        val v6AsNumber = java.math.BigInteger(1, ipv6Bytes()).toString()
        val mapped =
            "::ffff:" +
                ipv4Bytes(IPV4).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(":")

        for (written in
            listOf(
                "$asNumber",
                "2a02:1234:5678:9abc:0001:2:3:4",
                v6AsNumber,
                mapped,
                "0:0:0:0:0:FFFF:" + mapped.removePrefix("::ffff:"),
            )) {
            val tampered = masked + exchange(responseBody = """{"n":"x $written y"}""")
            assertTrue(MaskedValue.ADDRESS in unmaskedKinds(recording, tampered), written)
        }
    }

    @Test
    fun `the network's country is masked in a country field, and a list of countries keeps it`() {
        val recording =
            listOf(
                exchange(
                    url = SW_JS_URL,
                    responseBody = """var ytcfg={"INNERTUBE_CONTEXT":{"client":{"gl":"ZZ"}}};""",
                ),
                exchange(
                    responseBody =
                        """{"countryCode":"ZZ","availableCountries":["GB","ZZ"],"region":"FR"}"""
                ),
            )

        val masked = mask(recording)

        assertEquals(
            """{"countryCode":"GB","availableCountries":["GB","ZZ"],"region":"FR"}""",
            masked.exchanges[1].response.body,
        )
        assertEquals(2, masked.counts[MaskedValue.COUNTRY])
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
        val missed = masked.exchanges + exchange(responseBody = """{"x":{"country":"zz"}}""")
        assertEquals(setOf(MaskedValue.COUNTRY), unmaskedKinds(recording, missed))
    }

    @Test
    fun `only sw_js and visitor data say which country the network is in`() {
        val echo = exchange(responseBody = """{"responseContext":{"gl":"ZZ"}}""")

        assertEquals(emptySet(), realValuesOf(listOf(echo)).countries)
    }

    @Test
    fun `the country in visitor data counts as the network's`() {
        val visitorData =
            Base64.getUrlEncoder()
                .encodeToString(
                    byteArrayOf(0x0a, VISITOR_ID.length.toByte()) +
                        VISITOR_ID.toByteArray() +
                        byteArrayOf(0x32, 0x04, 0x0a, 0x02) +
                        "ZZ".toByteArray()
                )

        val real =
            realValuesOf(listOf(exchange(responseBody = """{"visitorData":"$visitorData"}""")))

        assertEquals(setOf("ZZ"), real.countries)
    }

    @Test
    fun `stream, ad and tracking links keep only the parameters that something reads`() {
        val body =
            """{"a":"https://rr1---sn-masked.googlevideo.com/videoplayback""" +
                """?expire=1&mh=aP&mm=31%2C29&mv=m&cps=12&rms=au%2Cau&itag=18&ip=$MASKED_IPV4",""" +
                """"b":"https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1""" +
                """/mh/aP/pcm2cms/yes/file/index.m3u8",""" +
                """"c":"https://ad.doubleclick.net/ddm/activity""" +
                """/src=1;type=a;dc_rdid=x;ord=123?",""" +
                """"d":"https://s.youtube.com/api/stats/qoe?cl=1&docid=aqz-KE-bpKQ#t=2",""" +
                """"e":"https://www.youtube.com/ptracking?pltype=content&ptk=youtube_none",""" +
                """"f":"https://www.youtube.com/redirect?event=video_description""" +
                """&q=https%3A%2F%2Fwww.blender.org&v=aqz-KE-bpKQ",""" +
                """"g":"https://www.youtube.com/watch?v=aqz-KE-bpKQ&pp=0"}"""

        val masked = mask(listOf(exchange(responseBody = body)))

        assertEquals(
            """{"a":"https://rr1---sn-masked.googlevideo.com/videoplayback""" +
                """?expire=1&mh=MASKED&mm=MASKED&mv=MASKED&cps=MASKED&rms=MASKED&itag=MASKED""" +
                """&ip=$MASKED_IPV4",""" +
                """"b":"https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1""" +
                """/mh/MASKED/pcm2cms/MASKED/file/MASKED",""" +
                """"c":"https://ad.doubleclick.net/ddm/activity""" +
                """/src=MASKED;type=MASKED;dc_rdid=MASKED;ord=MASKED?",""" +
                """"d":"https://s.youtube.com/api/stats/qoe?cl=MASKED&docid=MASKED#MASKED",""" +
                """"e":"https://www.youtube.com/ptracking?pltype=MASKED&ptk=MASKED",""" +
                """"f":"https://www.youtube.com/redirect?event=MASKED""" +
                """&q=https%3A%2F%2Fwww.blender.org&v=MASKED",""" +
                """"g":"https://www.youtube.com/watch?v=aqz-KE-bpKQ&pp=0"}""",
            masked.exchanges.single().response.body,
        )
        assertEquals(setOf(MaskedValue.LINK_PARAMETER), unmaskedKindsIn(body))
        assertEquals(emptySet(), unmaskedKindsIn(masked.exchanges.single().response.body))
    }

    @Test
    fun `comments and the people who wrote them are masked, wherever their values appear`() {
        val comment =
            """{"@type":"https://schema.org/Comment","text":"Lovely film, watched it twice",""" +
                """"upvoteCount":12,"author":{"@type":"https://schema.org/Person",""" +
                """"name":"Some Viewer","url":"http://www.youtube.com/@someviewer42"}}"""
        val body =
            """{"microformat":{"videoDetails":{"comments":[$comment]}},""" +
                """"title":"Big Buck Bunny","note":"a reply to @someviewer42"}"""
        val recording = listOf(exchange(responseBody = body))

        val masked = mask(recording)

        assertEquals(
            """{"microformat":{"videoDetails":{"comments":[""" +
                """{"@type":"https://schema.org/Comment",""" +
                """"text":"VIEWER_DATA","upvoteCount":0,"author":{""" +
                """"@type":"https://schema.org/Person","name":"VIEWER_DATA",""" +
                """"url":"VIEWER_DATA"}}]}},"title":"Big Buck Bunny",""" +
                """"note":"a reply to VIEWER_DATA"}""",
            masked.exchanges.single().response.body,
        )
        assertTrue(hasUnmaskedViewerData(body))
        assertEquals(setOf(MaskedValue.VIEWER_DATA), unmaskedKindsIn(body))
        assertEquals(emptySet(), unmaskedKinds(recording, masked.exchanges))
        val missed = masked.exchanges + exchange(responseBody = """{"x":"by @someviewer42"}""")
        assertEquals(setOf(MaskedValue.VIEWER_DATA), unmaskedKinds(recording, missed))
    }

    @Test
    fun `a short handle is masked and looked for anywhere, whatever its length`() {
        val comment =
            """{"@type":"https://schema.org/Comment","text":"Nice","author":""" +
                """{"name":"Al","url":"http://www.youtube.com/@al"}}"""
        val recording =
            listOf(exchange(responseBody = """{"comments":[$comment],"note":"thanks @al"}"""))

        val masked = mask(recording).exchanges

        assertEquals(
            """{"comments":[{"@type":"https://schema.org/Comment","text":"VIEWER_DATA",""" +
                """"author":{"name":"VIEWER_DATA","url":"VIEWER_DATA"}}],""" +
                """"note":"thanks VIEWER_DATA"}""",
            masked.single().response.body,
        )
        assertEquals(emptySet(), unmaskedKinds(recording, masked))
        val missed = masked + exchange(responseBody = """{"x":"@al"}""")
        assertEquals(setOf(MaskedValue.VIEWER_DATA), unmaskedKinds(recording, missed))
    }

    @Test
    fun `a handle is masked only where it ends, so a channel's longer handle stays`() {
        val comment =
            """{"@type":"https://schema.org/Comment","text":"Nice","author":""" +
                """{"name":"Al","alternateName":"@al","url":"http://www.youtube.com/@al"}}"""
        val recording =
            listOf(
                exchange(
                    responseBody =
                        """{"comments":[$comment],"note":"@alpha.tv and @al_b, thanks @al."}"""
                )
            )

        val masked = mask(recording).exchanges.single().response.body

        assertTrue(
            masked.endsWith(""""note":"@alpha.tv and @al_b, thanks VIEWER_DATA."}"""),
            masked,
        )
        assertEquals(emptySet(), unmaskedKinds(recording, listOf(exchange(responseBody = masked))))
    }

    @Test
    fun `a handle on its own, or written in a link with URL encoding, is looked for too`() {
        val comment =
            """{"@type":"https://schema.org/Comment","text":"Nice","author":""" +
                """{"alternateName":"@bo","url":"http://www.youtube.com/@%C3%A9mi"}}"""
        val recording = listOf(exchange(responseBody = """{"comments":[$comment]}"""))
        val masked = mask(recording).exchanges

        for (left in listOf("@bo", "@émi")) {
            val missed = masked + exchange(responseBody = """{"x":"by $left"}""")
            assertEquals(setOf(MaskedValue.VIEWER_DATA), unmaskedKinds(recording, missed), left)
        }
    }

    @Test
    fun `the check finds a handle in decoded bytes when a character outside ASCII follows it`() {
        val comment =
            """{"@type":"https://schema.org/Comment","text":"Nice","author":""" +
                """{"url":"http://www.youtube.com/@al"}}"""
        val recording = listOf(exchange(responseBody = """{"comments":[$comment]}"""))
        val masked = mask(recording).exchanges

        // Too short a printable run to be read as text, so only the bytes give the handle away.
        for (after in listOf("\u00a0", "\u3002")) {
            val hidden = hiddenInLayers("@al$after".toByteArray(Charsets.UTF_8))
            val missed = masked + exchange(responseBody = """{"blob":"$hidden"}""")
            assertTrue(MaskedValue.VIEWER_DATA in unmaskedKinds(recording, missed), after)
        }
    }

    @Test
    fun `comment renderers and live chat count as data about viewers too`() {
        val body =
            """{"commentRenderer":{"authorText":{"simpleText":"Someone"},"likeCount":3},""" +
                """"liveChatTextMessageRenderer":{"message":"hi"},"commentCount":"7"}"""

        val masked = mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """{"commentRenderer":{"authorText":{"simpleText":"VIEWER_DATA"},"likeCount":0},""" +
                """"liveChatTextMessageRenderer":{"message":"VIEWER_DATA"},""" +
                """"commentCount":"VIEWER_DATA"}""",
            masked,
        )
        assertFalse(hasUnmaskedViewerData(masked))
    }

    @Test
    fun `the experiment flags of sw_js are masked, and a flag left on fails the check`() {
        val body =
            """/** TOKEN */var ytcfg={"EXPERIMENT_FLAGS":{"a":true,"b":12,"c":"x",""" +
                """"d":["v"],"e":false,"f":0.5}};"""

        val masked = mask(listOf(exchange(responseBody = body)))

        assertEquals(
            """/** TOKEN */var ytcfg={"EXPERIMENT_FLAGS":{"a":false,"b":0,"c":"",""" +
                """"d":[],"e":false,"f":0}};""",
            masked.exchanges.single().response.body,
        )
        assertEquals(5, masked.counts[MaskedValue.EXPERIMENT_FLAGS])
        assertEquals(setOf(MaskedValue.EXPERIMENT_FLAGS), unmaskedKindsIn(body))
    }

    @Test
    fun `the experiments of the Android client's player answer are masked too`() {
        val body =
            """{"context":{"mainAppContext":{"experiments":{"a":true,"b":3}},""" +
                """"adsContext":{"experimentFlags":{"c":"x"}}}}"""

        val masked = mask(listOf(exchange(responseBody = body))).exchanges.single().response.body

        assertEquals(
            """{"context":{"mainAppContext":{"experiments":{"a":false,"b":0}},""" +
                """"adsContext":{"experimentFlags":{"c":""}}}}""",
            masked,
        )
        assertEquals(setOf(MaskedValue.EXPERIMENT_FLAGS), unmaskedKindsIn(body))
        assertEquals(emptySet(), unmaskedKindsIn(masked))
    }

    @Test
    fun `a value that an answer gave gets one placeholder there and in the request after it`() {
        val recording =
            listOf(
                exchange(
                    requestBody = """{"params":"$OTHER_TOKEN"}""",
                    responseBody = """{"tab":{"params":"$TOKEN"}}""",
                ),
                exchange(requestBody = """{"params":"$TOKEN"}"""),
            )

        val masked = mask(recording).exchanges

        // NewPipeExtractor made the first request's value, so it stays.
        assertEquals("""{"params":"$OTHER_TOKEN"}""", masked[0].request.body)
        assertEquals("""{"tab":{"params":"TOKEN_1"}}""", masked[0].response.body)
        assertEquals("""{"params":"TOKEN_1"}""", masked[1].request.body)
        assertEquals(emptySet(), unmaskedKinds(recording, masked))
    }

    /**
     * [bytes] hidden in layers: in a protobuf field, as base64url, URL-encoded in a link's
     * parameter, the link in text as base64, and that URL-encoded.
     */
    private fun hiddenInLayers(bytes: ByteArray): String {
        val protobuf = byteArrayOf(0x12, bytes.size.toByte()) + bytes
        val inner = Base64.getUrlEncoder().encodeToString(protobuf)
        val link = "see https://a.org/p?t=" + URLEncoder.encode(inner, Charsets.UTF_8)
        val outer = Base64.getEncoder().encodeToString(link.toByteArray())
        return URLEncoder.encode(outer, Charsets.UTF_8)
    }

    private fun ipv4Bytes(address: String): ByteArray =
        address.split('.').map { it.toInt().toByte() }.toByteArray()

    private fun ipv6Bytes(): ByteArray =
        checkNotNull(expandedIpv6(IPV6))
            .split(':')
            .flatMap { group ->
                val value = group.toInt(16)
                listOf((value shr 8).toByte(), value.toByte())
            }
            .toByteArray()

    private fun exchange(
        url: String = PLAYER_URL,
        requestBody: String? = null,
        responseHeaders: List<Header> = listOf(Header(CONTENT_TYPE, "application/json")),
        responseBody: String = "{}",
    ) =
        SavedExchange(
            SavedRequest(if (requestBody == null) "GET" else "POST", url, emptyList(), requestBody),
            SavedResponse(200, responseHeaders, responseBody),
        )
}
