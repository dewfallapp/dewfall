"""Tests of pre_push_gate.py's check of the manifest merge reports (#232).

Each test writes reports into a checkout in a temporary folder. Their lines have
the shapes of AGP 9.5.1's reports and blame files, with made-up paths.
"""

import os
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import pre_push_gate  # noqa: E402

APP_REPORT = "app/build/outputs/logs/manifest-merger-debug-report.txt"
LIBRARY_BLAME = (
    "core/data/build/intermediates/manifest_merge_blame_file/debug/processDebugManifest/"
    "manifest-merger-blame-debug-report.txt"
)


def report_text(checkout, gradle_home):
    return (
        "-- Merging decision tree log ---\n"
        "manifest\n"
        f"ADDED from {checkout}/app/src/main/AndroidManifest.xml:2:1-67:12\n"
        f"INJECTED from {checkout}/app/src/main/AndroidManifest.xml:2:1-67:12\n"
        f"MERGED from [:core:data] {checkout}/core/data/build/intermediates/merged_manifest/"
        "debug/processDebugManifest/AndroidManifest.xml:2:1-7:12\n"
        "MERGED from [androidx.core:core:1.18.0] "
        f"{gradle_home}/caches/9.9.2/transforms/abc/transformed/core-1.18.0/AndroidManifest.xml"
        ":22:5-24:19\n"
        "\tandroid:authorities\n"
        f"\t\tINJECTED from {checkout}/app/src/main/AndroidManifest.xml\n"
        f"\t\tADDED from {checkout}/app/src/main/AndroidManifest.xml:58:13-68\n"
    )


def blame_text(checkout, gradle_home):
    return (
        '1<?xml version="1.0" encoding="utf-8"?>\n'
        '5    <uses-sdk android:minSdkVersion="29" />\n'
        "5-->INJECTED\n"
        '13    <uses-permission android:name="android.permission.INTERNET" />\n'
        f"13-->{checkout}/core/data/src/main/AndroidManifest.xml:9:5-67\n"
        f"13-->[androidx.core:core:1.18.0] {gradle_home}/caches/transformed/AndroidManifest.xml"
        ":22:5\n"
    )


class ManifestReportTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        base = os.path.realpath(self.folder.name)
        self.checkout = os.path.join(base, "dewfall")
        self.gradle_home = os.path.join(base, "home", ".gradle")
        self.other = os.path.join(base, "other-checkout")
        os.makedirs(self.checkout)

    def tearDown(self):
        self.folder.cleanup()

    def write(self, path, text, checkout=None):
        full = os.path.join(checkout or self.checkout, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as file:
            file.write(text)

    def problems(self, checkout=None, gradle_home=None):
        return pre_push_gate.manifest_report_problems(
            checkout or self.checkout, gradle_home or self.gradle_home
        )

    def test_clean_reports_pass(self):
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(self.checkout, self.gradle_home))

        self.assertEqual([], self.problems())

    def test_a_report_with_another_checkouts_path_fails_on_that_line(self):
        text = report_text(self.checkout, self.gradle_home).replace(
            f"MERGED from [:core:data] {self.checkout}", f"MERGED from [:core:data] {self.other}"
        )
        self.write(APP_REPORT, text)

        self.assertEqual(["FOREIGN_PATH " + APP_REPORT + ":5"], self.problems())

    def test_a_blame_file_with_another_checkouts_path_fails_on_that_line(self):
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(self.other, self.gradle_home))

        self.assertEqual(["FOREIGN_PATH " + LIBRARY_BLAME + ":5"], self.problems())

    def test_the_problem_never_names_the_path_it_found(self):
        self.write(APP_REPORT, report_text(self.other, self.gradle_home))

        problems = self.problems()

        self.assertEqual(5, len(problems))
        self.assertFalse(any(self.other in problem for problem in problems))

    def test_a_folder_whose_name_starts_with_the_checkouts_name_is_foreign(self):
        sibling = self.checkout + "-other"
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(sibling, self.gradle_home))

        self.assertEqual(["FOREIGN_PATH " + LIBRARY_BLAME + ":5"], self.problems())

    def test_a_path_outside_gradles_user_home_is_foreign(self):
        self.write(APP_REPORT, report_text(self.checkout, os.path.dirname(self.gradle_home)))

        self.assertEqual(["FOREIGN_PATH " + APP_REPORT + ":6"], self.problems())

    def test_a_gradle_user_home_with_a_tilde_doesnt_allow_the_home_folder(self):
        # Gradle reads ~/gradle as a folder named ~ in the checkout, so the user's own
        # ~/gradle is outside it.
        self.write(APP_REPORT, report_text(self.checkout, os.path.expanduser("~/gradle")))
        saved = os.getcwd()
        os.chdir(self.checkout)
        try:
            problems = self.problems(gradle_home="~/gradle")
        finally:
            os.chdir(saved)

        self.assertEqual(["FOREIGN_PATH " + APP_REPORT + ":6"], problems)

    def test_a_checkout_with_no_reports_fails(self):
        self.write("app/build/outputs/apk/debug/app-debug.apk", "")

        self.assertEqual(["NO_MANIFEST_MERGE_REPORT"], self.problems())

    def test_blame_files_without_a_merge_report_fail(self):
        self.write(LIBRARY_BLAME, blame_text(self.checkout, self.gradle_home))

        self.assertEqual(["NO_MANIFEST_MERGE_REPORT"], self.problems())

    def test_reports_without_any_absolute_path_fail(self):
        self.write(APP_REPORT, "-- Merging decision tree log ---\nmanifest\nINJECTED from x:1:1\n")
        self.write(LIBRARY_BLAME, "5-->INJECTED\n")

        self.assertEqual(["NO_PATH_READ"], self.problems())

    def test_a_checkout_named_through_a_link_passes_with_resolved_paths(self):
        link = os.path.join(os.path.dirname(self.checkout), "link-to-dewfall")
        os.symlink(self.checkout, link)
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))

        self.assertEqual([], self.problems(checkout=link))

    def test_a_path_through_a_link_into_the_checkout_passes(self):
        link = os.path.join(os.path.dirname(self.checkout), "link-to-dewfall")
        os.symlink(self.checkout, link)
        self.write(APP_REPORT, report_text(link, self.gradle_home))

        self.assertEqual([], self.problems())

    def test_a_link_inside_the_checkout_to_another_checkout_is_foreign(self):
        os.makedirs(self.other)
        link = os.path.join(self.checkout, "shared")
        os.symlink(self.other, link)
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(link, self.gradle_home))

        self.assertEqual(["FOREIGN_PATH " + LIBRARY_BLAME + ":5"], self.problems())

    def test_a_checkout_named_build_still_finds_its_modules_reports(self):
        checkout = os.path.join(os.path.dirname(self.checkout), "build")
        self.write(APP_REPORT, report_text(checkout, self.gradle_home), checkout=checkout)

        self.assertEqual([], self.problems(checkout=checkout))

    def test_another_checkout_inside_this_one_is_skipped(self):
        nested = os.path.join(self.checkout, ".claude", "worktrees", "other")
        self.write(".git", "gitdir: /somewhere\n", checkout=nested)
        self.write(APP_REPORT, report_text(nested, self.gradle_home), checkout=nested)
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))

        self.assertEqual([], self.problems())

    def test_a_path_into_another_checkout_inside_this_one_is_foreign(self):
        nested = os.path.join(self.checkout, ".claude", "worktrees", "other")
        self.write(".git", "gitdir: /somewhere\n", checkout=nested)
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(nested, self.gradle_home))

        self.assertEqual(["FOREIGN_PATH " + LIBRARY_BLAME + ":5"], self.problems())

    def test_a_worktree_inside_the_checkouts_git_folder_is_foreign_and_skipped(self):
        os.makedirs(os.path.join(self.checkout, ".git", "objects"))
        worktree = os.path.join(self.checkout, ".git", "maintainer-check", "pr-1-abc")
        self.write(".git", "gitdir: /somewhere\n", checkout=worktree)
        self.write(APP_REPORT, report_text(worktree, self.gradle_home), checkout=worktree)
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(worktree, self.gradle_home))

        self.assertEqual(["FOREIGN_PATH " + LIBRARY_BLAME + ":5"], self.problems())

    def test_a_path_that_climbs_out_of_the_checkout_is_foreign(self):
        climbing = os.path.join(self.checkout, "..", "other-checkout")
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(LIBRARY_BLAME, blame_text(climbing, self.gradle_home))

        self.assertEqual(["FOREIGN_PATH " + LIBRARY_BLAME + ":5"], self.problems())

    def test_other_files_in_the_logs_folder_are_not_read(self):
        self.write(APP_REPORT, report_text(self.checkout, self.gradle_home))
        self.write(
            "app/build/outputs/logs/other.txt", f"ADDED from {self.other}/AndroidManifest.xml\n"
        )

        self.assertEqual([], self.problems())


class GradleUserHomeTest(unittest.TestCase):
    def test_the_default_is_dot_gradle_in_the_home_folder(self):
        self.assertEqual(
            os.path.join(os.path.expanduser("~"), ".gradle"), pre_push_gate.gradle_user_home({})
        )

    def test_gradle_user_home_moves_it(self):
        self.assertEqual(
            "/gradle-home", pre_push_gate.gradle_user_home({"GRADLE_USER_HOME": "/gradle-home"})
        )

    def test_the_system_property_in_gradle_opts_comes_first(self):
        environment = {
            "GRADLE_USER_HOME": "/gradle-home",
            "GRADLE_OPTS": "-Xmx1g '-Dgradle.user.home=/from property'",
        }

        self.assertEqual("/from property", pre_push_gate.gradle_user_home(environment))

    def test_the_last_system_property_wins_with_gradle_opts_after_java_opts(self):
        environment = {
            "JAVA_OPTS": "-Dgradle.user.home=/from-java-opts",
            "GRADLE_OPTS": "-Dgradle.user.home=/first -Dgradle.user.home=/last",
        }

        self.assertEqual("/last", pre_push_gate.gradle_user_home(environment))

    def test_a_tilde_stays_as_gradle_reads_it(self):
        self.assertEqual("~/gradle", pre_push_gate.gradle_user_home({"GRADLE_USER_HOME": "~/gradle"}))
        self.assertEqual(
            "~/gradle", pre_push_gate.gradle_user_home({"GRADLE_OPTS": "-Dgradle.user.home=~/gradle"})
        )

    def test_an_empty_gradle_user_home_is_still_set(self):
        self.assertEqual("", pre_push_gate.gradle_user_home({"GRADLE_USER_HOME": ""}))

    def test_the_jvms_own_option_variables_count_in_javas_order(self):
        self.assertEqual(
            "/tool",
            pre_push_gate.gradle_user_home(
                {"GRADLE_USER_HOME": "/gradle-home", "JAVA_TOOL_OPTIONS": "-Dgradle.user.home=/tool"}
            ),
        )
        environment = {
            "JAVA_TOOL_OPTIONS": "-Dgradle.user.home=/tool",
            "JDK_JAVA_OPTIONS": "-Dgradle.user.home=/jdk",
        }
        self.assertEqual("/jdk", pre_push_gate.gradle_user_home(environment))
        environment["JAVA_OPTS"] = "-Dgradle.user.home=/java-opts"
        self.assertEqual("/java-opts", pre_push_gate.gradle_user_home(environment))

    def test_the_system_property_in_java_opts_counts_too(self):
        environment = {
            "GRADLE_USER_HOME": "/gradle-home",
            "JAVA_OPTS": "-Dgradle.user.home=/from-java-opts",
        }

        self.assertEqual("/from-java-opts", pre_push_gate.gradle_user_home(environment))


if __name__ == "__main__":
    unittest.main()
