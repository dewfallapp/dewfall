"""Tests of scan_screenshot_tests.py: the allowed shapes pass, and everything else fails.

Each test builds the files it checks, in memory or in a temporary folder.
"""

import contextlib
import io
import os
import struct
import sys
import tempfile
import unittest
import zlib

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import scan_screenshot_tests as scan  # noqa: E402

KOTLIN = "feature/x-ui/src/screenshotTest/kotlin/a/b/XScreenshots.kt"
IMAGE = "feature/x-ui/src/screenshotTestDefaultDebug/reference/a/b/XScreenshotsKt/A_Light_0.png"

GOOD_WRAPPERS = """package io.github.dewfallapp.feature.x.ui

// A comment line.
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.Wallpapers
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "Light")
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
@Preview(name = "LargeFont", fontScale = 2f)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800, showSystemUi = false)
@Preview(name = "DynamicLight", wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
@Composable
private fun ResultsScreenshot() = ResultsPreview()

@PreviewTest @Preview(name = "Light") @Composable private fun EndedScreenshot() = EndedPreview()
"""

WRAPPER_HEAD = """package a.b

@PreviewTest
@Preview(name = "Light")
@Composable
"""


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


HEADER = chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
PIXELS = chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00"))
END = chunk(b"IEND", b"")


def png(*chunks):
    return scan.PNG_SIGNATURE + b"".join(chunks)


GOOD_PNG = png(HEADER, PIXELS, END)


def kotlin_rules(text):
    return [rule for _, rule in scan.check_kotlin(text)]


def file_rules(path, data, is_link=False):
    return [rule for _, rule in scan.check_file(path, data, is_link)]


class KotlinTest(unittest.TestCase):
    def test_wrappers_in_both_layouts_pass(self):
        self.assertEqual([], kotlin_rules(GOOD_WRAPPERS))

    def test_an_empty_file_passes(self):
        self.assertEqual([], kotlin_rules(""))

    def test_a_logging_call_on_its_own_line_fails(self):
        for line in [
            'println("x")',
            'Log.d("tag", "x")',
            "Thread.dumpStack()",
            'val logger = System.getLogger("x")',
            'private val log = java.util.logging.Logger.getLogger("x")',
        ]:
            with self.subTest(line=line):
                text = "package a.b\n\n" + line + "\n"
                self.assertEqual(["LINE_NOT_ALLOWED"], kotlin_rules(text))

    def test_a_callable_reference_fails(self):
        text = WRAPPER_HEAD + "private fun AScreenshot() = listOf(1).forEach(::println)\n"

        self.assertEqual(["WRAPPER_NOT_ONE_CALL"], kotlin_rules(text))

    def test_a_block_body_fails(self):
        text = WRAPPER_HEAD + 'private fun AScreenshot() {\n    println("x")\n    APreview()\n}\n'

        self.assertEqual(
            ["WRAPPER_NOT_ONE_CALL", "LINE_NOT_ALLOWED", "LINE_NOT_ALLOWED", "LINE_NOT_ALLOWED"],
            kotlin_rules(text),
        )

    def test_a_call_with_arguments_fails(self):
        self.assertEqual(
            ["WRAPPER_NOT_ONE_CALL"],
            kotlin_rules(WRAPPER_HEAD + "private fun AScreenshot() = APreview(1)\n"),
        )

    def test_a_call_to_another_preview_fails(self):
        self.assertEqual(
            ["WRAPPER_NOT_ONE_CALL"],
            kotlin_rules(WRAPPER_HEAD + "private fun AScreenshot() = BPreview()\n"),
        )

    def test_a_wrapper_that_isnt_private_fails(self):
        self.assertEqual(
            ["WRAPPER_NOT_ONE_CALL"],
            kotlin_rules(WRAPPER_HEAD + "internal fun AScreenshot() = APreview()\n"),
        )

    def test_a_one_line_wrapper_with_another_body_fails(self):
        line = (
            '@PreviewTest @Preview(name = "Light") @Composable '
            "private fun AScreenshot() = run { }"
        )

        self.assertEqual(["LINE_NOT_ALLOWED"], kotlin_rules(line + "\n"))

    def test_a_string_with_a_template_or_a_backslash_fails(self):
        for argument in ['name = "${x}"', 'name = "$x"', 'name = "a\\u0041"']:
            with self.subTest(argument=argument):
                text = "@PreviewTest\n@Preview(" + argument + ")\n@Composable\n"
                text += "private fun AScreenshot() = APreview()\n"
                self.assertEqual(
                    ["WRAPPER_ANNOTATION_NOT_ALLOWED", "LINE_NOT_ALLOWED"], kotlin_rules(text)[:2]
                )

    def test_a_call_in_an_annotation_argument_fails(self):
        text = "@PreviewTest\n@Preview(\n    name = name(),\n)\n@Composable\n"
        text += "private fun AScreenshot() = APreview()\n"

        self.assertEqual(["PREVIEW_ARGUMENT_NOT_ALLOWED"], kotlin_rules(text))

    def test_an_import_alias_or_a_wildcard_import_fails(self):
        for line in ["import a.b.C as D", "import a.b.*"]:
            with self.subTest(line=line):
                self.assertEqual(["LINE_NOT_ALLOWED"], kotlin_rules(line + "\n"))

    def test_a_file_annotation_fails(self):
        self.assertEqual(["LINE_NOT_ALLOWED"], kotlin_rules('@file:Suppress("x")\npackage a.b\n'))

    def test_a_wrapper_without_preview_test_fails(self):
        text = '@Preview(name = "Light")\n@Composable\nprivate fun AScreenshot() = APreview()\n'

        self.assertEqual(["LINE_NOT_ALLOWED"] * 3, kotlin_rules(text))

    def test_a_wrapper_without_a_preview_fails(self):
        text = "@PreviewTest\n@Composable\nprivate fun AScreenshot() = APreview()\n"

        self.assertEqual(["WRAPPER_ANNOTATION_NOT_ALLOWED", "LINE_NOT_ALLOWED"], kotlin_rules(text))

    def test_an_annotation_left_open_fails(self):
        self.assertEqual(["WRAPPER_NOT_FINISHED"], kotlin_rules("@PreviewTest\n@Preview("))
        self.assertEqual(
            ["PREVIEW_ARGUMENT_NOT_ALLOWED", "WRAPPER_NOT_FINISHED"],
            kotlin_rules("@PreviewTest\n@Preview(\n"),
        )
        self.assertEqual(["WRAPPER_NOT_FINISHED"], kotlin_rules("@PreviewTest"))
        self.assertEqual(["WRAPPER_ANNOTATION_NOT_ALLOWED"], kotlin_rules("@PreviewTest\n"))

    def test_a_second_package_or_a_late_import_fails(self):
        self.assertEqual(["PACKAGE_NOT_FIRST"], kotlin_rules("package a\npackage b\n"))
        text = WRAPPER_HEAD + "private fun AScreenshot() = APreview()\nimport a.B\n"
        self.assertEqual(["IMPORT_AFTER_WRAPPER"], kotlin_rules(text))

    def test_windows_line_endings_fail(self):
        self.assertEqual(["CONTROL_CHARACTER"], kotlin_rules("package a.b\r\n"))

    def test_a_carriage_return_inside_a_comment_fails(self):
        # Kotlin reads the \r as a line break, so the call after it would compile.
        text = "package a.b\n\n// hi\rval x = run { println(1) }\n"

        self.assertEqual([(3, "CONTROL_CHARACTER")], scan.check_kotlin(text))

    def test_other_control_and_separator_characters_fail(self):
        for character in ["\t", "\x0b", "\x0c", "\x85", "\u2028", "\u2029", "\x00"]:
            with self.subTest(character=repr(character)):
                self.assertEqual(
                    ["CONTROL_CHARACTER"], kotlin_rules("// a" + character + "b\n")
                )

    def test_letters_outside_ascii_in_a_comment_pass(self):
        self.assertEqual([], kotlin_rules("// Dew, Ros\u00e9e\n"))


class PngTest(unittest.TestCase):
    def test_a_plain_image_passes(self):
        self.assertEqual([], scan.check_png(GOOD_PNG))

    def test_a_palette_fails(self):
        data = png(HEADER, chunk(b"PLTE", b"\x00" * 3), PIXELS, END)

        self.assertEqual([(2, "PNG_CHUNK_NOT_ALLOWED PLTE")], scan.check_png(data))

    def test_a_second_header_fails(self):
        data = png(HEADER, HEADER, PIXELS, END)

        self.assertEqual([(2, "PNG_IHDR_NOT_FIRST_AND_ONLY")], scan.check_png(data))

    def test_every_other_chunk_fails(self):
        kinds = [b"tEXt", b"iTXt", b"zTXt", b"eXIf", b"tIME", b"iCCP", b"pHYs", b"PLTE", b"prVt"]
        for kind in kinds:
            with self.subTest(kind=kind):
                data = png(HEADER, chunk(kind, b"user=someone"), PIXELS, END)
                self.assertEqual(
                    [(2, "PNG_CHUNK_NOT_ALLOWED " + kind.decode())], scan.check_png(data)
                )

    def test_a_chunk_type_that_isnt_letters_is_not_shown(self):
        data = png(HEADER, chunk(b"\x00\x01ab", b""), PIXELS, END)

        self.assertEqual([(2, "PNG_CHUNK_NOT_ALLOWED ?")], scan.check_png(data))

    def test_a_file_without_the_signature_fails(self):
        self.assertEqual([(0, "NOT_A_PNG")], scan.check_png(b"GIF89a"))

    def test_a_truncated_image_fails(self):
        self.assertEqual("PNG_TRUNCATED", scan.check_png(GOOD_PNG[:-6])[-1][1])
        self.assertEqual("PNG_TRUNCATED", scan.check_png(scan.PNG_SIGNATURE + HEADER[:5])[-1][1])

    def test_a_missing_end_fails(self):
        self.assertEqual([(3, "PNG_TRUNCATED")], scan.check_png(png(HEADER, PIXELS)))

    def test_a_bad_crc_fails(self):
        broken = HEADER[:-1] + bytes([HEADER[-1] ^ 1])

        self.assertEqual([(1, "PNG_BAD_CRC")], scan.check_png(png(broken, PIXELS, END)))

    def test_data_after_the_end_fails(self):
        self.assertEqual([(3, "PNG_DATA_AFTER_IEND")], scan.check_png(GOOD_PNG + b"x"))

    def test_a_first_chunk_other_than_the_header_fails(self):
        self.assertEqual(
            [
                (1, "PNG_IHDR_NOT_FIRST_AND_ONLY"),
                (2, "PNG_IHDR_NOT_FIRST_AND_ONLY"),
                (0, "PNG_HEADER_NOT_ALLOWED"),
            ],
            scan.check_png(png(PIXELS, HEADER, END)),
        )

    def test_bytes_after_the_zlib_stream_fail(self):
        # A decoder ignores them, so text could hide there.
        hidden = chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00") + b"hostname=machine")

        self.assertEqual([(0, "PNG_PIXELS_NOT_EXACT")], scan.check_png(png(HEADER, hidden, END)))

    def test_more_or_fewer_pixel_bytes_than_the_header_says_fail(self):
        for raw in [b"\x00\x00\x00\x00secret", b"\x00\x00"]:
            with self.subTest(raw=raw):
                data = png(HEADER, chunk(b"IDAT", zlib.compress(raw)), END)
                self.assertEqual([(0, "PNG_PIXELS_NOT_EXACT")], scan.check_png(data))

    def test_a_stream_split_over_several_chunks_passes(self):
        stream = zlib.compress(b"\x00\x00\x00\x00")
        data = png(HEADER, chunk(b"IDAT", stream[:3]), chunk(b"IDAT", stream[3:]), END)

        self.assertEqual([], scan.check_png(data))

    def test_an_unfinished_or_broken_stream_fails(self):
        stream = zlib.compress(b"\x00\x00\x00\x00")
        unfinished = png(HEADER, chunk(b"IDAT", stream[:-4]), END)
        broken = png(HEADER, chunk(b"IDAT", b"not zlib"), END)

        self.assertEqual([(0, "PNG_PIXELS_NOT_EXACT")], scan.check_png(unfinished))
        self.assertEqual([(0, "PNG_PIXELS_NOT_ZLIB")], scan.check_png(broken))

    def test_an_interlaced_or_odd_header_fails(self):
        for fields in [
            (1, 1, 8, 2, 0, 0, 1),
            (1, 1, 8, 7, 0, 0, 0),
            (1, 1, 3, 2, 0, 0, 0),
            (1, 1, 8, 3, 0, 0, 0),
        ]:
            with self.subTest(fields=fields):
                header = chunk(b"IHDR", struct.pack(">IIBBBBB", *fields))
                self.assertEqual(
                    [(0, "PNG_HEADER_NOT_ALLOWED")], scan.check_png(png(header, PIXELS, END))
                )

    def test_an_image_too_big_to_check_fails(self):
        header = chunk(b"IHDR", struct.pack(">IIBBBBB", 100_000, 100_000, 8, 6, 0, 0, 0))

        self.assertEqual([(0, "PNG_HEADER_NOT_ALLOWED")], scan.check_png(png(header, PIXELS, END)))


class FileTest(unittest.TestCase):
    def test_a_wrapper_file_and_an_image_pass(self):
        self.assertEqual([], file_rules(KOTLIN, GOOD_WRAPPERS.encode()))
        self.assertEqual([], file_rules(IMAGE, GOOD_PNG))

    def test_files_in_the_wrong_place_fail(self):
        for path in [
            "feature/x-ui/src/screenshotTest/java/a/B.java",
            "feature/x-ui/src/screenshotTest/kotlin/a/B.java",
            "feature/x-ui/src/screenshotTestDefaultDebug/kotlin/a/B.kt",
            "feature/x-ui/src/screenshotTest/res/values/strings.xml",
            "feature/x-ui/src/screenshotTest/kotlin/a/b.png",
            "feature/x-ui/src/screenshotTest/reference/a.png",
            "feature/x-ui/src/screenshotTestDefaultDebug/reference/a.txt",
        ]:
            with self.subTest(path=path):
                self.assertEqual(["FILE_NOT_ALLOWED"], file_rules(path, b""))

    def test_a_symbolic_link_fails(self):
        self.assertEqual(["SYMBOLIC_LINK"], file_rules(KOTLIN, b"", is_link=True))

    def test_a_file_that_isnt_utf8_fails(self):
        self.assertEqual(["NOT_UTF8"], file_rules(KOTLIN, b"package a\n\xff\n"))


class RepositoryTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = self.folder.name

    def tearDown(self):
        self.folder.cleanup()

    def write(self, path, data):
        full = os.path.join(self.root, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as file:
            file.write(data)

    def run_main(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = scan.main([self.root])
        return code, output.getvalue()

    def test_a_clean_repository_passes(self):
        self.write(KOTLIN, GOOD_WRAPPERS.encode())
        self.write(IMAGE, GOOD_PNG)
        self.write("feature/x-ui/src/main/kotlin/a/b/Logging.kt", b'println("x")\n')
        self.write("feature/x-ui/build/src/screenshotTest/kotlin/a/Bad.kt", b'println("x")\n')

        code, output = self.run_main()

        self.assertEqual(0, code, output)
        self.assertEqual("PASS 2 files\n", output)

    def test_a_problem_fails_and_names_the_file_and_rule_but_not_the_line(self):
        self.write(KOTLIN, b'package a.b\n\nprintln("secret text")\n')

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn(KOTLIN + ":3 LINE_NOT_ALLOWED", output)
        self.assertNotIn("secret text", output)

    def test_a_folder_named_build_inside_a_screenshot_folder_is_read(self):
        self.write("feature/x-ui/src/screenshotTest/build/a.kt", b"")

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn("src/screenshotTest/build/a.kt:0 FILE_NOT_ALLOWED", output)

    def test_a_linked_screenshot_folder_fails(self):
        self.write("elsewhere/kotlin/a/B.kt", b'println("x")\n')
        os.makedirs(os.path.join(self.root, "feature/x-ui/src"))
        os.symlink(
            os.path.join(self.root, "elsewhere"),
            os.path.join(self.root, "feature/x-ui/src/screenshotTest"),
        )

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn("feature/x-ui/src/screenshotTest:0 SYMBOLIC_LINK", output)

    def test_a_link_to_nothing_in_place_of_a_screenshot_folder_fails(self):
        self.write(KOTLIN, GOOD_WRAPPERS.encode())
        os.makedirs(os.path.join(self.root, "feature/y-ui/src"))
        os.symlink(
            os.path.join(self.root, "nowhere"),
            os.path.join(self.root, "feature/y-ui/src/screenshotTest"),
        )

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn("feature/y-ui/src/screenshotTest:0 SYMBOLIC_LINK", output)

    def test_a_linked_file_or_folder_inside_a_screenshot_folder_fails(self):
        self.write(KOTLIN, GOOD_WRAPPERS.encode())
        self.write("elsewhere/B.kt", b'println("x")\n')
        kotlin_folder = os.path.join(self.root, os.path.dirname(KOTLIN))
        os.symlink(os.path.join(self.root, "elsewhere/B.kt"), os.path.join(kotlin_folder, "B.kt"))
        os.symlink(os.path.join(self.root, "elsewhere"), os.path.join(kotlin_folder, "more"))

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn("B.kt:0 SYMBOLIC_LINK", output)
        self.assertIn("more:0 SYMBOLIC_LINK", output)

    def test_a_folder_it_cant_list_fails(self):
        self.write(KOTLIN, GOOD_WRAPPERS.encode())
        hidden = os.path.join(self.root, "feature/y-ui/src/screenshotTest/kotlin")
        os.makedirs(hidden)
        os.chmod(hidden, 0)
        try:
            code, output = self.run_main()
        finally:
            os.chmod(hidden, 0o755)

        self.assertEqual(1, code)
        self.assertIn("FAIL the scan stopped: PermissionError", output)

    def test_the_folder_pattern_matches_the_folder_itself_and_its_files(self):
        for path in [
            "feature/x-ui/src/screenshotTest",
            "feature/x-ui/src/screenshotTestDefaultDebug",
            "feature/x-ui/src/screenshotTest/kotlin/a/B.kt",
            "src/screenshotTest/kotlin/a/B.kt",
        ]:
            with self.subTest(path=path):
                self.assertTrue(scan.SCREENSHOT_FOLDER.search(path))
        for path in ["feature/x-ui/src/main/kotlin/a/B.kt", "feature/x-ui/src/test/screenshotTest"]:
            with self.subTest(path=path):
                self.assertFalse(scan.SCREENSHOT_FOLDER.search(path))

    def test_a_folder_named_in_other_case_is_read_and_fails(self):
        self.write("feature/x-ui/src/ScreenshotTest/kotlin/a/B.kt", b'println("x")\n')

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn("src/ScreenshotTest/kotlin/a/B.kt:0 FILE_NOT_ALLOWED", output)

    def test_another_worktree_inside_the_root_is_left_alone(self):
        self.write(KOTLIN, GOOD_WRAPPERS.encode())
        other = ".claude/worktrees/other/"
        self.write(other + ".git", b"gitdir: /somewhere\n")
        self.write(other + KOTLIN, b'println("x")\n')

        code, output = self.run_main()

        self.assertEqual(0, code, output)
        self.assertEqual("PASS 1 files\n", output)

    def test_no_screenshot_file_fails(self):
        self.write("feature/x-ui/src/main/kotlin/a/B.kt", b"")

        code, output = self.run_main()

        self.assertEqual(1, code)
        self.assertIn("FAIL no screenshot test file was found", output)

    def test_an_error_fails_the_scan(self):
        code, output = self.run_main_with_broken_reader()

        self.assertEqual(1, code)
        self.assertIn("FAIL the scan stopped: OSError", output)

    def run_main_with_broken_reader(self):
        def broken(_root):
            raise OSError("unreadable")
            yield  # A generator, like repository_files.

        saved = scan.repository_files
        scan.repository_files = broken
        try:
            return self.run_main()
        finally:
            scan.repository_files = saved


if __name__ == "__main__":
    unittest.main()
