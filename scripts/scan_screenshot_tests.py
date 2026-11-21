#!/usr/bin/env python3
"""Checks that the screenshot test folders hold only what they are meant to.

Lint can't read a module's src/screenshotTest folder, so Dewfall's lint checks
of ADR 0007's logging rules don't reach it. That folder holds only one-call
wrappers of previews that live in src/main, where lint does read them. This
script checks that shape, so no code beyond each wrapper's one call to a preview,
a logging call included, can sit in the folder. ADR 0007's note of November 21, 2026 and
docs/testing.md#screenshot-tests explain why.

It allows only these files in a module's src/screenshotTest* folders:
- .kt files under src/screenshotTest/kotlin/;
- .png files under a src/screenshotTest<Variant>/reference/ folder, where the
  screenshot tests keep their reference images.

Each .kt file may hold only blank lines, // comment lines, one package line,
plain imports without an alias, and wrappers. A wrapper is @PreviewTest, then
one or more @Preview annotations, then @Composable, then exactly
`private fun <Name>Screenshot() = <Name>Preview()`, each on a line of its own,
or all on one line, as ktfmt writes a wrapper that fits. An annotation's arguments,
on one line or several, are each `<name> = <value>`, where a value is a string
literal with no $ and no backslash, a number, true or false, or dotted names
joined by ` or `.

Each .png file must start with PNG's signature and hold only the chunks IHDR,
IDAT and IEND, each with a correct CRC, with one IHDR, first, and nothing after
IEND. Palette images are refused, as a palette could hold unused bytes. The IDAT
chunks' zlib stream must end exactly where their data ends, and give exactly the
bytes that IHDR describes, for an image that isn't interlaced.
So no image can carry text, a time or private data, such as the name of the
machine that recorded it.

It fails closed: a file it can't decode or parse, a .kt file with a control or
line separator character other than a line feed, such as a carriage return, a
symbolic link, any other file, or an error of its own fails the check. It
prints each finding as the file, the line or chunk number, and the rule, never
the line itself, and exits with 1 if it found anything or read no file at all.

Usage: scan_screenshot_tests.py [repository root, by default the current folder]
"""

import os
import re
import struct
import sys
import unicodedata
import zlib

# A module's screenshot test folders: src/screenshotTest for the wrappers, and
# src/screenshotTest<Variant> for each variant's reference images.
# It matches the folder itself too, so a link in its place is caught. It ignores case, because
# a Mac's disk does and Gradle would compile src/ScreenshotTest there. Such a folder then fails,
# as its files don't match the exact paths below.
SCREENSHOT_FOLDER = re.compile(r"(^|/)src/screenshotTest[^/]*(/|$)", re.IGNORECASE)
KOTLIN_FILE = re.compile(r"(^|/)src/screenshotTest/kotlin/.+\.kt$")
REFERENCE_IMAGE = re.compile(r"(^|/)src/screenshotTest[A-Za-z0-9]+/reference/.+\.png$")

# Folders that hold build output or tools, never sources. A screenshot folder
# itself is read in full, whatever its subfolders are named.
SKIPPED_FOLDERS = {".git", ".gradle", ".idea", ".kotlin", "build", "node_modules"}

NAME = r"[A-Za-z_][A-Za-z0-9_]*"
DOTTED = NAME + r"(\." + NAME + r")*"
PACKAGE_LINE = re.compile(r"package [a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*")
IMPORT_LINE = re.compile(r"import " + DOTTED)
COMMENT_LINE = re.compile(r"//.*")
VALUE = (
    r'("[^"$\\]*"'
    r"|-?[0-9]+(\.[0-9]+)?[fFL]?"
    r"|true|false"
    r"|" + DOTTED + r"( or " + DOTTED + r")*)"
)
ARGUMENT = r"[a-zA-Z]+ = " + VALUE
ONE_LINE_PREVIEW = re.compile(r"@Preview\((" + ARGUMENT + r"(, " + ARGUMENT + r")*)?\)")
ARGUMENT_LINE = re.compile(r"    " + ARGUMENT + r",?")
FUNCTION = r"private fun (?P<name>[A-Z][A-Za-z0-9]*)Screenshot\(\) = (?P=name)Preview\(\)"
WRAPPER = re.compile(FUNCTION)
# ktfmt puts a wrapper on one line when it fits, annotations included.
ONE_LINE_WRAPPER = re.compile(
    r"@PreviewTest( " + ONE_LINE_PREVIEW.pattern + r")+ @Composable " + FUNCTION
)

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
# No PLTE: a palette could hold bytes that an RGBA image never uses, and the engine writes none.
PNG_CHUNKS = {b"IHDR", b"IDAT", b"IEND"}
# Samples per pixel for each PNG colour type, and the bit depths it allows.
PNG_COLOUR_TYPES = {
    0: (1, {1, 2, 4, 8, 16}),
    2: (3, {8, 16}),
    4: (2, {8, 16}),
    6: (4, {8, 16}),
}
# A bound on the pixel data, so a crafted image can't expand without limit. A 3360 x 2100
# tablet image in 8-bit RGBA is about 28 MB.
PNG_MAX_PIXEL_BYTES = 256 * 1024 * 1024


def check_kotlin(text):
    """The (line number, rule) of each problem in a wrapper file's [text]."""
    # Kotlin also breaks lines at a lone \r, so a line that looks like a comment here could
    # hold code there. Any control or separator character other than \n fails, before the
    # text is split into lines.
    for number, line in enumerate(text.split("\n"), start=1):
        if any(unicodedata.category(c) in ("Cc", "Zl", "Zp") for c in line):
            return [(number, "CONTROL_CHARACTER")]
    problems = []
    # top: between wrappers. after_test: after @PreviewTest. in_preview: inside a
    # @Preview( that spans lines. after_preview: after a whole @Preview.
    # after_composable: after @Composable, waiting for the wrapper's function.
    state = "top"
    seen_package = False
    seen_declaration = False
    for number, line in enumerate(text.split("\n"), start=1):
        if state == "in_preview":
            if line == ")":
                state = "after_preview"
            elif not ARGUMENT_LINE.fullmatch(line):
                problems.append((number, "PREVIEW_ARGUMENT_NOT_ALLOWED"))
            continue
        if state == "top":
            if line == "" or COMMENT_LINE.fullmatch(line):
                continue
            if PACKAGE_LINE.fullmatch(line):
                if seen_package or seen_declaration:
                    problems.append((number, "PACKAGE_NOT_FIRST"))
                seen_package = True
            elif IMPORT_LINE.fullmatch(line):
                if seen_declaration:
                    problems.append((number, "IMPORT_AFTER_WRAPPER"))
            elif line == "@PreviewTest":
                seen_declaration = True
                state = "after_test"
            elif ONE_LINE_WRAPPER.fullmatch(line):
                seen_declaration = True
            else:
                problems.append((number, "LINE_NOT_ALLOWED"))
            continue
        if state in ("after_test", "after_preview"):
            if ONE_LINE_PREVIEW.fullmatch(line):
                state = "after_preview"
            elif line == "@Preview(":
                state = "in_preview"
            elif line == "@Composable" and state == "after_preview":
                state = "after_composable"
            else:
                problems.append((number, "WRAPPER_ANNOTATION_NOT_ALLOWED"))
                state = "top"
            continue
        if state == "after_composable":
            if not WRAPPER.fullmatch(line):
                problems.append((number, "WRAPPER_NOT_ONE_CALL"))
            state = "top"
    if state != "top":
        problems.append((number, "WRAPPER_NOT_FINISHED"))
    return problems


def expected_pixel_bytes(header):
    """How many bytes IHDR's [header] says the decompressed image data holds, or None."""
    if len(header) != 13:
        return None
    width, height, depth, colour, compression, filtering, interlace = struct.unpack(
        ">IIBBBBB", header
    )
    samples, depths = PNG_COLOUR_TYPES.get(colour, (0, set()))
    # Interlaced images are refused, because the engine writes none and their size is harder
    # to check.
    if depth not in depths or (compression, filtering, interlace) != (0, 0, 0):
        return None
    return height * (1 + (width * samples * depth + 7) // 8)


def pixel_problem(header, pixels):
    """The rule that the image data [pixels], joined from every IDAT, breaks, or None.

    The zlib stream must end exactly where the data ends, and give exactly the bytes that IHDR
    describes, so no text can hide after the stream or after the pixels.
    """
    expected = expected_pixel_bytes(header)
    if expected is None or expected > PNG_MAX_PIXEL_BYTES:
        return "PNG_HEADER_NOT_ALLOWED"
    stream = zlib.decompressobj()
    produced = 0
    try:
        produced += len(stream.decompress(pixels, expected + 1))
        while stream.unconsumed_tail and produced <= expected:
            produced += len(stream.decompress(stream.unconsumed_tail, expected + 1 - produced))
    except zlib.error:
        return "PNG_PIXELS_NOT_ZLIB"
    if not stream.eof or stream.unused_data or stream.unconsumed_tail or produced != expected:
        return "PNG_PIXELS_NOT_EXACT"
    return None


def check_png(data):
    """The (chunk number, rule) of each problem in a reference image's [data]."""
    if not data.startswith(PNG_SIGNATURE):
        return [(0, "NOT_A_PNG")]
    position = len(PNG_SIGNATURE)
    number = 0
    problems = []
    header = b""
    pixels = []
    while True:
        number += 1
        if position + 8 > len(data):
            return problems + [(number, "PNG_TRUNCATED")]
        length, kind = struct.unpack(">I4s", data[position : position + 8])
        end = position + 8 + length + 4
        if end > len(data):
            return problems + [(number, "PNG_TRUNCATED")]
        body = data[position + 8 : position + 8 + length]
        (crc,) = struct.unpack(">I", data[position + 8 + length : end])
        if zlib.crc32(kind + body) != crc:
            problems.append((number, "PNG_BAD_CRC"))
        if kind not in PNG_CHUNKS:
            # A chunk's type is four letters, which name the kind of chunk, never its data.
            shown = kind.decode("ascii") if kind.isalpha() else "?"
            problems.append((number, "PNG_CHUNK_NOT_ALLOWED " + shown))
        elif (number == 1) != (kind == b"IHDR"):
            problems.append((number, "PNG_IHDR_NOT_FIRST_AND_ONLY"))
        if kind == b"IHDR" and number == 1:
            header = body
        elif kind == b"IDAT":
            pixels.append(body)
        position = end
        if kind == b"IEND":
            if position != len(data):
                problems.append((number, "PNG_DATA_AFTER_IEND"))
            rule = pixel_problem(header, b"".join(pixels))
            if rule:
                problems.append((0, rule))
            return problems


def check_file(path, data, is_link=False):
    """The (location, rule) of each problem in the file at repository [path]."""
    if is_link:
        return [(0, "SYMBOLIC_LINK")]
    if KOTLIN_FILE.search(path):
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            return [(0, "NOT_UTF8")]
        return check_kotlin(text)
    if REFERENCE_IMAGE.search(path):
        return check_png(data)
    return [(0, "FILE_NOT_ALLOWED")]


def check_files(files):
    """The findings in [files], (path, bytes, is_link) triples, sorted, and how many it read."""
    findings = []
    count = 0
    for path, data, is_link in files:
        count += 1
        for location, rule in check_file(path, data, is_link):
            findings.append((path, location, rule))
    return sorted(findings), count


def repository_files(root):
    """Each (path, bytes, is_link) under every screenshot folder below [root]."""
    def stop(error):
        # os.walk skips a folder it can't list unless told otherwise, which would let the scan
        # pass without reading it.
        raise error

    for directory, folders, names in os.walk(root, onerror=stop):
        relative = os.path.relpath(directory, root)
        relative = "" if relative == "." else relative.replace(os.sep, "/") + "/"
        if SCREENSHOT_FOLDER.search(relative):
            # Inside a screenshot folder: read everything, links included.
            entries = sorted(folders + names)
            folders[:] = [
                name for name in folders if not os.path.islink(os.path.join(directory, name))
            ]
            for name in entries:
                full = os.path.join(directory, name)
                if os.path.islink(full):
                    yield relative + name, b"", True
                elif os.path.isfile(full):
                    with open(full, "rb") as file:
                        yield relative + name, file.read(), False
            continue
        for name in sorted(folders + names):
            # os.walk doesn't follow a linked folder, and lists a link to nothing as a file, so a
            # linked screenshot folder would otherwise go unread.
            if SCREENSHOT_FOLDER.search(relative + name + "/") and os.path.islink(
                os.path.join(directory, name)
            ):
                yield relative + name, b"", True
        # A folder below the root with a .git entry is another repository or another worktree,
        # such as the ones under .claude/worktrees/, whose files aren't this checkout's.
        folders[:] = sorted(
            name
            for name in folders
            if (name not in SKIPPED_FOLDERS or SCREENSHOT_FOLDER.search(relative + name + "/"))
            and not os.path.lexists(os.path.join(directory, name, ".git"))
        )


def main(arguments):
    root = arguments[0] if arguments else "."
    try:
        findings, count = check_files(repository_files(root))
    except Exception as error:  # noqa: BLE001 - any error must fail the check, never pass it.
        print(f"FAIL the scan stopped: {type(error).__name__}")
        return 1
    for path, location, rule in findings:
        print(f"{path}:{location} {rule}")
    if count == 0:
        print("FAIL no screenshot test file was found")
        return 1
    if findings:
        print(f"FAIL {len(findings)} problems in {count} files")
        return 1
    print(f"PASS {count} files")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
