"""Tests of scan_saved_responses.py: each rule finds a planted value, and placeholders pass.

The planted values are made up. Google API keys and the like are built from
pieces at run time, so this file holds none that a secret scanner would flag.
"""

import os
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import scan_saved_responses  # noqa: E402

# A made-up country that no network has, in place of a real one.
OTHER_COUNTRY = "ZZ"
API_KEY = "AI" + "za" + "x" * 35


def kinds_in(name, text):
    """The (kind, key) of each finding in a file [name] that holds [text]."""
    found = set()

    def report(line, depth, kind, key):
        found.add((kind, key))

    scan_saved_responses.scan_file(name, text.encode("utf-8"), report)
    return found


class ScanTest(unittest.TestCase):
    def test_placeholders_pass(self):
        text = (
            '{"url":"https://rr1---sn-masked.googlevideo.com/videoplayback?expire=1795040185'
            "\\u0026ei=EVENT_ID\\u0026ip=2001%3Adb8%3A%3A1\\u0026id=TOKEN\\u0026itag=MASKED"
            '\\u0026pl=0\\u0026fexp=EXPERIMENTS\\u0026mn=sn-masked",'
            '"hls":"https://manifest.googlevideo.com/api/manifest/hls_variant/expire/1/ei/'
            'EVENT_ID/ip/2001:db8::1/id/MASKED/file/MASKED",'
            '"sub":"https://www.youtube.com/api/timedtext?v=MASKED\\u0026ei=EVENT_ID'
            "\\u0026ip=MASKED\\u0026ipbits=0\\u0026signature=MASKED\\u0026kind=asr\\u0026lang=en"
            '\\u0026variant=gemini\\u0026fmt=ttml",'
            '"desc":"https://www.youtube.com/redirect?event=MASKED\\u0026q=https%3A%2F%2Fwww.'
            'blender.org%2F%3Fa%3Db%26c%3Dd\\u0026v=MASKED",'
            '"gl":"GB","countryCode":"GB","visitorData":"VISITOR_DATA_3",'
            '"feedback":{"key":"visitor_data","value":"VISITOR_DATA"},'
            '"osVersion":"26.6.0.23O770","doc":"192.0.2.1 and 198.51.100.7 and ::1",'
            '"apiKey":"API_KEY","availableCountries":["AD","GB","' + OTHER_COUNTRY + '"]}'
        )

        self.assertEqual(set(), kinds_in("02-player.json", text))

    def test_addresses_outside_documentation_and_private_ranges(self):
        found = kinds_in("x.json", '{"a":"8.8.8.8","b":"2a02:1234:5678::9","c":"10.0.0.1"}')

        self.assertEqual({("IPV4_ADDRESS", "-"), ("IPV6_ADDRESS", "-")}, found)

    def test_an_ip_parameter_other_than_the_documentation_address(self):
        cases = [
            "https://rr1---sn-masked.googlevideo.com/videoplayback?expire=1&ip=2a02%3A1234%3A%3A5",
            "https://rr1---sn-masked.googlevideo.com/videoplayback?expire=1&ip=MASKED",
            "https://manifest.googlevideo.com/api/manifest/hls_variant/ip/192.0.2.9/id/MASKED",
            "https://www.youtube.com/api/timedtext?lang=en&ip=0.0.0.0&fmt=ttml",
        ]
        for link in cases:
            with self.subTest(link=link):
                found = kinds_in("x.json", '{"u":"' + link + '"}')
                self.assertIn(("IP_NOT_DOCUMENTATION_ADDRESS", "ip"), found)

    def test_a_parameter_named_like_ip_isnt_one(self):
        link = "https://rr1---sn-masked.googlevideo.com/videoplayback?expire=1&fvip=MASKED"

        self.assertEqual(set(), kinds_in("x.json", '{"u":"' + link + '"}'))

    def test_every_country_field_must_be_GB(self):
        text = (
            '{"gl":"' + OTHER_COUNTRY + '","countryCode":"' + OTHER_COUNTRY.lower() + '",'
            '"contentRegion":"US","regionCode":"' + OTHER_COUNTRY + '",'
            '"u":"https://www.youtube.com/x?gl=' + OTHER_COUNTRY + '"}'
        )

        found = kinds_in("x.json", text)

        self.assertEqual(
            {
                ("COUNTRY_NOT_GB", "gl"),
                ("COUNTRY_NOT_GB", "countryCode"),
                ("COUNTRY_NOT_GB", "contentRegion"),
                ("COUNTRY_NOT_GB", "regionCode"),
            },
            found,
        )

    def test_a_country_slot_of_sw_js_data_that_isnt_GB(self):
        text = ")]}'\n[[\"" + OTHER_COUNTRY + '","GB"]]'

        self.assertEqual({("SW_JS_DATA_COUNTRY_NOT_GB", "-")}, kinds_in("01-sw.js_data", text))
        # Only sw.js_data holds the country as a bare string.
        self.assertEqual(set(), kinds_in("02-player.json", text))

    def test_visitor_data_that_isnt_a_placeholder(self):
        text = (
            '{"visitorData":"CgtabcDEF","feedback":{"key":"visitor_data","value":"Cgtxyz"},'
            '"u":"https://www.youtube.com/ptracking?visitor_data=CgtabcDEF"}'
        )

        found = kinds_in("x.json", text)

        self.assertIn(("VISITOR_DATA_NOT_PLACEHOLDER", "visitorData"), found)
        self.assertIn(("VISITOR_DATA_NOT_PLACEHOLDER", "visitor_data"), found)

    def test_cookies_and_api_keys(self):
        self.assertEqual({("SET_COOKIE", "-")}, kinds_in("exchanges.txt", "< set-cookie: YSC=x"))
        self.assertEqual({("API_KEY", "-")}, kinds_in("x.json", '{"k":"' + API_KEY + '"}'))

    def test_tracked_links_keep_only_their_allowlisted_parameters(self):
        text = (
            '{"a":"https://rr1---sn-masked.googlevideo.com/videoplayback?expire=1&mm=31",'
            '"b":"https://manifest.googlevideo.com/api/manifest/hls_variant/source/youtube",'
            '"c":"https://www.youtube.com/redirect?event=video_description&q=x",'
            '"d":"https://www.youtube.com/api/timedtext?v=R6MlUcmOul8\\u0026signature=AB12'
            '\\u0026lang=en\\u0026kind=asr\\u0026variant=x\\u0026fmt=ttml"}'
        )

        found = kinds_in("x.json", text)

        self.assertEqual(
            {
                ("LINK_PARAMETER_GOOGLEVIDEO", "mm"),
                ("LINK_PARAMETER_GOOGLEVIDEO", "source"),
                ("LINK_PARAMETER_REDIRECT", "event"),
                ("LINK_PARAMETER_TIMEDTEXT", "v"),
                ("LINK_PARAMETER_TIMEDTEXT", "signature"),
            },
            found,
        )

    def test_a_googlevideo_host_that_isnt_masked(self):
        link = "https://rr3---sn-abcd1234.googlevideo.com/videoplayback?expire=1"

        found = kinds_in("x.json", '{"u":"' + link + '"}')

        self.assertEqual({("GOOGLEVIDEO_HOST_NOT_MASKED", "-")}, found)

    def test_values_inside_url_encoding_are_found_up_to_three_layers_down(self):
        inner = "https://www.youtube.com/api/timedtext?ei=real&lang=en"
        once = inner.replace(":", "%3A").replace("/", "%2F").replace("?", "%3F")
        once = once.replace("&", "%26").replace("=", "%3D")
        twice = once.replace("%", "%25")

        found = kinds_in("x.json", '{"u":"' + twice + '","v":"8.8.8.8%252C1"}')

        self.assertIn(("LINK_PARAMETER_TIMEDTEXT", "ei"), found)
        self.assertIn(("IPV4_ADDRESS", "-"), found)

    def test_a_link_inside_a_value_doesnt_add_parameters_to_the_outer_link(self):
        link = (
            "https://www.youtube.com/redirect?event=MASKED"
            "&q=https%3A%2F%2Fexample.com%2F%3Fsession%3D1%26user%3D2&v=MASKED"
        )

        self.assertEqual(set(), kinds_in("x.json", '{"u":"' + link + '"}'))

    def test_values_in_links_that_arent_tracked_are_decoded_and_checked(self):
        cases = {
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fwww.youtube.com"
            "%2Fsignin%3Fnext%3D%252F%26gl%3D" + OTHER_COUNTRY: ("COUNTRY_NOT_GB", "gl"),
            "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fwww.youtube.com"
            "%2Fapi%2Ftimedtext%3Fsignature%3DAB12": ("LINK_PARAMETER_TIMEDTEXT", "signature"),
            "https://www.youtube.com/signin?next=%2Fwatch%3Fip%3D8.8.8.8": (
                "IP_NOT_DOCUMENTATION_ADDRESS",
                "ip",
            ),
            "https://www.youtube.com/watch?foo=a%3D8.8.8.8": ("IPV4_ADDRESS", "-"),
        }
        for link, expected in cases.items():
            with self.subTest(link=link):
                self.assertIn(expected, kinds_in("x.json", '{"u":"' + link + '"}'))

    def test_encoded_text_in_a_links_path_or_fragment_is_checked(self):
        for link in (
            "https://example.com/a%2F8.8.8.8",
            "https://example.com/a#x=%3D8.8.8.8",
            "https://example.com/a?b=MASKED#x=%3D8.8.8.8",
        ):
            with self.subTest(link=link):
                self.assertIn(("IPV4_ADDRESS", "-"), kinds_in("x.json", '{"u":"' + link + '"}'))

    def test_a_fourth_layer_of_encoding_fails(self):
        value = "8.8.8.8".replace(".", "%2E")
        for _ in range(3):
            value = value.replace("%", "%25")

        self.assertIn(("ENCODED_TOO_DEEP", "-"), kinds_in("x.json", '{"u":"' + value + '"}'))

    def test_a_file_that_isnt_utf_8_is_still_read(self):
        found = set()

        def report(line, depth, kind, key):
            found.add(kind)

        scan_saved_responses.scan_file("x.bin", b"\xff\xfe 8.8.8.8 \xff", report)

        self.assertEqual({"IPV4_ADDRESS"}, found)

    def test_a_folder_is_scanned_file_by_file(self):
        with tempfile.TemporaryDirectory() as folder:
            os.makedirs(os.path.join(folder, "set"))
            with open(os.path.join(folder, "set", "clean.json"), "w") as file:
                file.write('{"gl":"GB"}')
            with open(os.path.join(folder, "set", "leak.json"), "w") as file:
                file.write('{"a":"8.8.8.8"}')

            findings, files = scan_saved_responses.scan_folder(folder)

        self.assertEqual(2, files)
        self.assertEqual([(os.path.join("set", "leak.json"), 1, 0, "IPV4_ADDRESS", "-")], findings)


if __name__ == "__main__":
    unittest.main()
