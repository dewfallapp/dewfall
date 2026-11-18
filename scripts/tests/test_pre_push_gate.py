"""Tests of pre_push_gate.py's rules for commits and secrets.

The identities, tokens and keys here are made up, and the tokens and keys are
built from pieces at run time, so this file holds none that a secret scanner
would flag, and none that the gate's own check of added lines would.
"""

import os
import sys
import unittest

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import pre_push_gate  # noqa: E402

LOCAL = ("A Maintainer", "maintainer@example.com")
OFFICE = ("A Maintainer", "maintainer@office.example")
CO_AUTHOR = "Co-" + "Authored-By: Someone <someone@example.com>"


class SubjectTest(unittest.TestCase):
    def test_conventional_prefixes_pass(self):
        for subject in (
            "feat: read subtitle lines",
            "docs: describe the gate",
            "build(deps): update okhttp",
            "fix!: drop a format",
        ):
            with self.subTest(subject=subject):
                self.assertEqual([], pre_push_gate.subject_problems(subject + "\n\n- why"))

    def test_other_subjects_fail(self):
        for subject in ("Read subtitle lines", "feature: x", "feat:x", "Merge branch 'main'", "wip"):
            with self.subTest(subject=subject):
                self.assertEqual(
                    ["SUBJECT_NOT_CONVENTIONAL"], pre_push_gate.subject_problems(subject)
                )


class TrailerTest(unittest.TestCase):
    def test_a_message_without_trailers_passes(self):
        message = "feat: x\n\n- Each bullet gives a reason: like this one.\n"

        self.assertEqual([], pre_push_gate.trailer_problems(message, ""))

    def test_a_trailer_that_git_finds_fails(self):
        self.assertEqual(["TRAILER"], pre_push_gate.trailer_problems("feat: x\n", "Key: value\n"))

    def test_a_known_trailer_anywhere_in_the_body_fails_in_any_case(self):
        for trailer in (CO_AUTHOR, CO_AUTHOR.lower(), "Signed-off-by: x"):
            with self.subTest(trailer=trailer):
                message = "feat: x\n\n" + trailer + "\n\n- a bullet after it\n"
                self.assertEqual(["TRAILER"], pre_push_gate.trailer_problems(message, ""))


class ClosingTest(unittest.TestCase):
    def test_closing_words_before_an_issue_fail(self):
        for text in (
            "Closes #148",
            "fixes: #39",
            "This resolved #1 at last",
            "closes dewfallapp/dewfall#148",
            "Fixed https://github.com/dewfallapp/dewfall/issues/148",
        ):
            with self.subTest(text=text):
                self.assertEqual(
                    ["CLOSING_WORD"], pre_push_gate.closing_problems("docs: x\n\n" + text)
                )

    def test_issue_numbers_without_closing_words_pass(self):
        for text in ("Part of #148.", "See #39 and fixture #2", "#148 stays open", "closes nothing"):
            with self.subTest(text=text):
                self.assertEqual([], pre_push_gate.closing_problems("docs: x\n\n" + text))


class IdentityTest(unittest.TestCase):
    def test_the_local_identity_passes(self):
        self.assertEqual([], pre_push_gate.identity_problems(LOCAL, LOCAL, LOCAL, OFFICE[1]))

    def test_another_identity_fails(self):
        problems = pre_push_gate.identity_problems(LOCAL, OFFICE, LOCAL, None)

        self.assertEqual(["COMMITTER_NOT_LOCAL_IDENTITY"], problems)

    def test_the_global_email_fails_even_when_named_like_the_local_one(self):
        problems = pre_push_gate.identity_problems(OFFICE, LOCAL, LOCAL, OFFICE[1])

        self.assertEqual(["AUTHOR_NOT_LOCAL_IDENTITY", "AUTHOR_IS_GLOBAL_IDENTITY"], problems)

    def test_a_clone_without_a_local_identity_fails(self):
        problems = pre_push_gate.identity_problems(LOCAL, LOCAL, (None, None), None)

        self.assertEqual(["NO_LOCAL_IDENTITY"], problems)


class SecretTest(unittest.TestCase):
    def test_secret_files_fail(self):
        paths = [
            "local.properties",
            "app/local.properties",
            ".env",
            ".env.production",
            "release.jks",
            "app/upload.keystore",
            "keys/signing.p12",
            "cert.pem",
            "id.key",
        ]

        problems = pre_push_gate.secret_file_problems(paths)

        self.assertEqual(["SECRET_FILE " + path for path in paths], problems)

    def test_ordinary_files_pass(self):
        paths = [
            "gradle.properties",
            "docs/environment.md",
            "core/youtube/src/test/resources/youtube/search-nasa/02-search.json",
            "app/src/main/kotlin/KeyboardKeys.kt",
        ]

        self.assertEqual([], pre_push_gate.secret_file_problems(paths))

    def test_secret_values_in_added_lines_fail(self):
        lines = [
            "-----BEGIN " + "RSA PRIVATE KEY-----",
            "token = gh" + "p_" + "a" * 36,
            "aws = AK" + "IA" + "ABCDEFGHIJKLMNOP",
            "key = AI" + "za" + "b" * 35,
        ]

        problems = pre_push_gate.secret_content_problems(lines)

        self.assertEqual(["PRIVATE_KEY", "GITHUB_TOKEN", "AWS_ACCESS_KEY", "GOOGLE_API_KEY"], problems)

    def test_ordinary_lines_pass(self):
        lines = ["val apiKey = \"API_KEY\"", "ghp is not a token", "AKIA without sixteen"]

        self.assertEqual([], pre_push_gate.secret_content_problems(lines))


if __name__ == "__main__":
    unittest.main()
