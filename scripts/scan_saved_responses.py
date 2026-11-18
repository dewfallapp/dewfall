#!/usr/bin/env python3
"""Scans saved YouTube responses for private values, on its own.

The pre-push gate runs this second check of the saved responses in
core/youtube/src/test/resources/youtube/. It shares no code with the Kotlin
masking or SavedResponsesPrivacyTest, so a mistake in one doesn't hide in the
other. docs/testing.md#the-pre-push-gate lists its rules.

It reads every file under the folder it is given, as bytes decoded as UTF-8,
with JSON's \\uXXXX and \\/ escapes undone. In that text it checks the values
below, then decodes URL encoding and checks again, up to three times. Each link,
tracked or not, has its parameters split where the link appears, and each value
is decoded and checked in turn, so a link inside a value can't make the outer
link look like it has more parameters. Text that a fourth decoding would still change
fails, so a value can't hide four layers down.

It fails when it finds:
- an ip= or /ip/ value other than the documentation address 2001:db8::1, apart
  from a subtitle link's ip, which may be MASKED;
- an IPv4 address outside the documentation and private ranges, or an IPv6
  address outside 2001:db8::/32 other than the loopback address;
- a gl, countryCode, contentRegion or regionCode that isn't GB, or a
  two-letter country string in sw.js_data that isn't GB;
- visitor data that isn't a placeholder;
- a Set-Cookie header, or a Google API key;
- a parameter of a googlevideo, redirect or subtitle link that is neither on
  its allowlist nor a placeholder, or a googlevideo host that isn't masked.

It prints each finding as the file, the line, the decoding depth, the kind and
the parameter or key, never the value, and exits with 1 if it found anything.

Usage: scan_saved_responses.py <folder>
"""

import ipaddress
import os
import re
import sys
import urllib.parse

DOCUMENTATION_IPV6 = "2001:db8::1"
MASKED = "MASKED"
MAX_DEPTH = 3

# What a masked value becomes. A value from this list says nothing about the
# machine, the session or the network.
PLACEHOLDERS = {
    "",
    "0",
    MASKED,
    "TOKEN",
    "EVENT_ID",
    "EXPERIMENTS",
    "VISITOR_DATA",
    "COOKIE_VALUE",
    "ROLLOUT_TOKEN",
    "API_KEY",
    "VIEWER_DATA",
    "sn-masked",
    "GB",
    DOCUMENTATION_IPV6,
    "192.0.2.1",
}
NUMBERED_PLACEHOLDER = re.compile(r"^(?:TOKEN_|VISITOR_DATA_)\d+$")

# The parameters that each kind of tracked link may keep, as
# docs/testing.md#links lists them. Every other parameter must hold a
# placeholder. ip has a rule of its own.
ALLOWLISTS = {
    "googlevideo": {"expire"},
    "redirect": {"q"},
    "timedtext": {"fmt", "variant", "lang", "kind"},
}
GOOGLEVIDEO_HOST = re.compile(r"^(?:rr\d+---sn-masked|manifest)\.googlevideo\.com$")

IPV4_ALLOWED = [
    ipaddress.ip_network(network)
    for network in (
        "192.0.2.0/24",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
    )
]
IPV6_DOCUMENTATION = ipaddress.ip_network("2001:db8::/32")
IPV6_LOOPBACK = ipaddress.ip_address("::1")

JSON_UNICODE_ESCAPE = re.compile(r"\\u([0-9A-Fa-f]{4})")
# No letter, digit or dot may touch an address, so a version such as
# 26.6.0.23O770 isn't one.
IPV4_TEXT = re.compile(r"(?<![\w.])(\d{1,3}(?:\.\d{1,3}){3})(?![\w.])")
IPV6_TEXT = re.compile(r"(?<![\w:.%])((?:[0-9A-Fa-f]{0,4}:){2,7}[0-9A-Fa-f]{0,4})(?![\w:%])")
IP_PARAMETER = re.compile(r"(?:[?&;/]|^)ip=([^&\"'\s<>\\#;/]*)")
IP_PATH = re.compile(r"/ip/([^/?&\"'\s<>\\#]*)")
GL_PARAMETER = re.compile(r"(?:[?&;]|^)gl=([^&\"'\s<>\\#;]*)")
COUNTRY_FIELD = re.compile(
    r"\"(gl|countryCode|contentRegion|regionCode)\"\s*:\s*\"([^\"]*)\"", re.IGNORECASE
)
TWO_CAPITALS = re.compile(r"\"([A-Z]{2})\"")
VISITOR_DATA = re.compile(r"\"visitorData\"\s*:\s*\"([^\"]*)\"")
VISITOR_DATA_PAIR = re.compile(
    r"\"key\"\s*:\s*\"visitor_data\"\s*,\s*\"value\"\s*:\s*\"([^\"]*)\""
)
VISITOR_DATA_PARAMETER = re.compile(r"(?:[?&;]|^)visitor_data=([^&\"'\s<>\\#;]*)")
SET_COOKIE = re.compile(r"set-cookie", re.IGNORECASE)
API_KEY = re.compile(r"AIza[0-9A-Za-z_\-]{35}")
LINK = re.compile(r"(?:https?:)?//([A-Za-z0-9.\-]+)(/[^\s\"'<>\\]*)?")
# A run of text that holds URL encoding, such as a link written inside a value.
ENCODED_RUN = re.compile(r"[^\s\"'<>\\]*%[0-9A-Fa-f]{2}[^\s\"'<>\\]*")


def is_placeholder(value):
    return value in PLACEHOLDERS or bool(NUMBERED_PLACEHOLDER.match(value))


def unescaped(text):
    """[text] with JSON's \\uXXXX and \\/ escapes undone."""
    text = JSON_UNICODE_ESCAPE.sub(lambda match: chr(int(match.group(1), 16)), text)
    return text.replace("\\/", "/")


def fully_decoded(value):
    for _ in range(MAX_DEPTH):
        value = urllib.parse.unquote(value)
    return value


def tracked_kind(host, path):
    host = host.lower()
    if host == "googlevideo.com" or host.endswith(".googlevideo.com"):
        return "googlevideo"
    if host in ("www.youtube.com", "youtube.com", "m.youtube.com"):
        if path == "/redirect":
            return "redirect"
        if path == "/api/timedtext":
            return "timedtext"
    return None


def link_parameters(kind, rest):
    """Each (name, value) of a link: its query, and a googlevideo link's path pairs."""
    path, _, query = rest.partition("?")
    query = query.partition("#")[0]
    pairs = []
    for part in re.split(r"[&;]", query):
        if part:
            name, _, value = part.partition("=")
            pairs.append((name, value))
    if kind == "googlevideo":
        segments = path.split("/")[1:]
        if segments[:2] == ["api", "manifest"]:
            names_and_values = segments[3:]
        elif segments[:1] == ["videoplayback"]:
            names_and_values = segments[1:]
        else:
            names_and_values = []
        for i in range(0, len(names_and_values) - 1, 2):
            pairs.append((names_and_values[i], names_and_values[i + 1]))
    return pairs


class Scanner:
    def __init__(self, file_name, report):
        self.is_sw_js_data = file_name.endswith("sw.js_data")
        self.report = report

    def scan(self, text, depth, line):
        """Checks [text], found at [depth] decodings, at [line] of the file."""
        self.check_values(text, depth, line)
        link_spans = []
        timedtext_spans = []
        for match in LINK.finditer(text):
            link_spans.append(match.span())
            rest = match.group(2) or ""
            kind = tracked_kind(match.group(1), rest.partition("?")[0])
            if kind == "timedtext":
                timedtext_spans.append(match.span())
            self.check_link(match.group(1), kind, rest, depth, line)
        self.check_ip(text, depth, line, timedtext_spans)
        for match in ENCODED_RUN.finditer(text):
            if any(start <= match.start() < end for start, end in link_spans):
                continue
            self.scan_decoded(match.group(0), depth, line)

    def scan_decoded(self, value, depth, line):
        decoded = urllib.parse.unquote(value)
        if decoded == value:
            return
        if depth >= MAX_DEPTH:
            self.report(line, depth, "ENCODED_TOO_DEEP", "-")
            return
        self.scan(decoded, depth + 1, line)

    def check_link(self, host, kind, rest, depth, line):
        """Decodes and checks each parameter's value of any link, and a tracked link's allowlist."""
        if kind == "googlevideo" and not GOOGLEVIDEO_HOST.match(host.lower()):
            self.report(line, depth, "GOOGLEVIDEO_HOST_NOT_MASKED", "-")
        # The path and the fragment can hold encoded text too, outside any parameter.
        path, _, query = rest.partition("?")
        fragment = (query if query else path).partition("#")[2]
        for part in (path.partition("#")[0], fragment):
            if "%" in part:
                self.scan_decoded(part, depth, line)
        for name, value in link_parameters(kind, rest):
            if "%" in value:
                self.scan_decoded(value, depth, line)
            if kind is None or name == "ip" or name in ALLOWLISTS[kind]:
                continue
            if not is_placeholder(fully_decoded(value)):
                self.report(line, depth, "LINK_PARAMETER_" + kind.upper(), name)

    def check_ip(self, text, depth, line, timedtext_spans):
        for pattern in (IP_PARAMETER, IP_PATH):
            for match in pattern.finditer(text):
                value = fully_decoded(match.group(1))
                in_timedtext = any(start <= match.start() < end for start, end in timedtext_spans)
                if value == DOCUMENTATION_IPV6 or (in_timedtext and value == MASKED):
                    continue
                self.report(line, depth, "IP_NOT_DOCUMENTATION_ADDRESS", "ip")

    def check_values(self, text, depth, line):
        for match in IPV4_TEXT.finditer(text):
            try:
                address = ipaddress.IPv4Address(match.group(1))
            except ValueError:
                continue
            if not any(address in network for network in IPV4_ALLOWED):
                self.report(line, depth, "IPV4_ADDRESS", "-")
        for match in IPV6_TEXT.finditer(text):
            try:
                address = ipaddress.IPv6Address(match.group(1))
            except ValueError:
                continue
            if address not in IPV6_DOCUMENTATION and address != IPV6_LOOPBACK:
                self.report(line, depth, "IPV6_ADDRESS", "-")
        for match in COUNTRY_FIELD.finditer(text):
            if match.group(2) != "GB":
                self.report(line, depth, "COUNTRY_NOT_GB", match.group(1))
        for match in GL_PARAMETER.finditer(text):
            if fully_decoded(match.group(1)) != "GB":
                self.report(line, depth, "COUNTRY_NOT_GB", "gl")
        if self.is_sw_js_data:
            for match in TWO_CAPITALS.finditer(text):
                if match.group(1) != "GB":
                    self.report(line, depth, "SW_JS_DATA_COUNTRY_NOT_GB", "-")
        for pattern, key in (
            (VISITOR_DATA, "visitorData"),
            (VISITOR_DATA_PAIR, "visitor_data"),
            (VISITOR_DATA_PARAMETER, "visitor_data"),
        ):
            for match in pattern.finditer(text):
                if not is_placeholder(fully_decoded(match.group(1))):
                    self.report(line, depth, "VISITOR_DATA_NOT_PLACEHOLDER", key)
        if SET_COOKIE.search(text):
            self.report(line, depth, "SET_COOKIE", "-")
        if API_KEY.search(text):
            self.report(line, depth, "API_KEY", "-")


def scan_file(name, data, report):
    """Scans [data], the bytes of the file [name], and reports each finding to [report]."""
    text = unescaped(data.decode("utf-8", errors="replace"))
    scanner = Scanner(name, report)
    for line_number, line in enumerate(text.split("\n"), start=1):
        scanner.scan(line, 0, line_number)


def scan_files(files):
    """The findings in [files], (relative path, bytes) pairs, sorted, and how many it read."""
    findings = set()
    count = 0
    for relative, data in files:
        count += 1

        def report(line, depth, kind, key, relative=relative):
            findings.add((relative, line, depth, kind, key))

        scan_file(os.path.basename(relative), data, report)
    return sorted(findings), count


def scan_folder(folder):
    """The findings in every file under [folder], sorted, and how many files it read."""

    def files():
        for directory, _, names in os.walk(folder):
            for file_name in sorted(names):
                path = os.path.join(directory, file_name)
                with open(path, "rb") as file:
                    yield os.path.relpath(path, folder), file.read()

    return scan_files(files())


def main(arguments):
    if len(arguments) != 1 or not os.path.isdir(arguments[0]):
        print("Usage: scan_saved_responses.py <folder>")
        return 2
    findings, files = scan_folder(arguments[0])
    for relative, line, depth, kind, key in findings:
        print(f"  {relative}:{line} depth={depth} {kind} {key}")
    print(f"Scanned {files} files, found {len(findings)} problems.")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
