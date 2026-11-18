"""Tests of pre_push_gate.py against real commits, in a repository of its own.

Each test builds a small repository, with a bare one as its origin, in a
temporary folder, and runs the gate's functions there. The user's own Git
configuration is left out, so a setting such as signing or a colour can't
change the result.
"""

import contextlib
import io
import os
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))

import pre_push_gate  # noqa: E402

LOCAL = ("A Maintainer", "maintainer@example.com")
SAVED = pre_push_gate.SAVED_RESPONSES


class GitRepository:
    """A repository with a bare origin, whose main has one commit."""

    def __init__(self, folder):
        self.path = os.path.join(folder, "work")
        origin = os.path.join(folder, "origin.git")
        self.run_git("init", "--quiet", "--bare", origin, cwd=folder)
        self.run_git("init", "--quiet", "-b", "main", self.path, cwd=folder)
        self.run_git("config", "--local", "user.name", LOCAL[0])
        self.run_git("config", "--local", "user.email", LOCAL[1])
        self.run_git("remote", "add", "origin", origin)
        self.write(SAVED + "/set/01-a.json", '{"gl":"GB"}')
        self.commit("test: add a saved set")
        self.run_git("push", "--quiet", "origin", "main")
        self.run_git("checkout", "--quiet", "-b", "work")

    def run_git(self, *arguments, cwd=None, name=None, email=None):
        environment = dict(os.environ)
        if name:
            environment.update(
                GIT_AUTHOR_NAME=name,
                GIT_AUTHOR_EMAIL=email,
                GIT_COMMITTER_NAME=name,
                GIT_COMMITTER_EMAIL=email,
            )
        return subprocess.run(
            ["git", *arguments],
            cwd=cwd or self.path,
            env=environment,
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()

    def write(self, path, text):
        full = os.path.join(self.path, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "w") as file:
            file.write(text)

    def commit(self, message, name=None, email=None):
        self.run_git("add", "-A")
        self.run_git("commit", "--quiet", "-m", message, name=name, email=email)
        return self.run_git("rev-parse", "HEAD")


class GateWithGitTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.saved_directory = os.getcwd()
        self.saved_environment = dict(os.environ)
        # Leave the user's global and system Git configuration out.
        os.environ["GIT_CONFIG_GLOBAL"] = os.path.join(self.folder.name, "no-global-config")
        os.environ["GIT_CONFIG_NOSYSTEM"] = "1"
        self.repository = GitRepository(self.folder.name)
        os.chdir(self.repository.path)

    def tearDown(self):
        os.chdir(self.saved_directory)
        os.environ.clear()
        os.environ.update(self.saved_environment)
        self.folder.cleanup()

    def problems_of(self, commit, on_remote_branch=frozenset()):
        return pre_push_gate.check_commit(commit, LOCAL, None, set(on_remote_branch))

    def run_gate(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = pre_push_gate.main(["--no-build"], own_tests=False)
        return code, output.getvalue()

    def test_a_good_commit_has_no_problems(self):
        self.repository.write("docs/notes.md", "Notes\n")
        commit = self.repository.commit("docs: add notes")

        self.assertEqual([], self.problems_of(commit))

    def test_a_merge_commit_is_refused(self):
        self.repository.run_git("checkout", "--quiet", "-b", "side")
        self.repository.write("docs/side.md", "Side\n")
        self.repository.commit("docs: add a side note")
        self.repository.run_git("checkout", "--quiet", "work")
        self.repository.write("docs/work.md", "Work\n")
        self.repository.commit("docs: add a work note")
        self.repository.run_git("merge", "--quiet", "--no-ff", "-m", "Merge side", "side")
        merge = self.repository.run_git("rev-parse", "HEAD")

        problems = self.problems_of(merge)

        self.assertEqual(1, len(problems))
        self.assertTrue(problems[0].startswith("MERGE_COMMIT"))

    def test_renovate_is_exempt_from_the_identity_rule_only_once_on_the_remote_branch(self):
        self.repository.write("gradle/libs.versions.toml", "[versions]\n")
        commit = self.repository.commit(
            "build: update a library", name="renovate[bot]", email="bot@example.com"
        )

        self.assertEqual([], self.problems_of(commit, on_remote_branch={commit}))
        self.assertIn("AUTHOR_NOT_LOCAL_IDENTITY", self.problems_of(commit))

    def test_a_commit_that_changes_saved_responses_is_scanned(self):
        self.repository.write(SAVED + "/set/02-b.json", '{"a":"8.8.8.8"}')
        commit = self.repository.commit("test: save another answer")

        problems = self.problems_of(commit)

        self.assertEqual(["SAVED_RESPONSE set/02-b.json:1 depth=0 IPV4_ADDRESS -"], problems)

    def test_a_commit_that_leaves_saved_responses_alone_isnt_scanned(self):
        # Main's own saved set would fail the scan, so a scan here would show.
        self.repository.write(SAVED + "/set/01-a.json", '{"a":"8.8.8.8"}')
        self.repository.commit("test: plant an address")
        self.repository.write("docs/notes.md", "Notes\n")
        commit = self.repository.commit("docs: add notes")

        self.assertEqual([], self.problems_of(commit))

    def test_a_commit_that_deletes_the_saved_responses_passes(self):
        self.repository.run_git("rm", "--quiet", "-r", SAVED)
        commit = self.repository.commit("test: remove the saved sets")

        self.assertEqual([], self.problems_of(commit))

    def test_a_secret_file_is_refused(self):
        self.repository.write("local.properties", "sdk.dir=/somewhere\n")
        commit = self.repository.commit("chore: add a file")

        self.assertEqual(["SECRET_FILE local.properties"], self.problems_of(commit))

    def test_the_gate_ends_incomplete_without_the_build_when_every_commit_passes(self):
        self.repository.write("docs/notes.md", "Notes\n")
        self.repository.commit("docs: add notes")

        code, output = self.run_gate()

        self.assertEqual(3, code)
        self.assertIn("INCOMPLETE", output)

    def test_the_gate_fails_without_the_build_when_a_commit_fails(self):
        self.repository.write("docs/notes.md", "Notes\n")
        self.repository.commit("Add notes")

        code, output = self.run_gate()

        self.assertEqual(1, code)
        self.assertIn("SUBJECT_NOT_CONVENTIONAL", output)
        self.assertNotIn("INCOMPLETE", output)

    def test_the_gate_fails_when_a_file_isnt_committed(self):
        self.repository.write("docs/notes.md", "Notes\n")
        self.repository.commit("docs: add notes")
        self.repository.write("docs/draft.md", "Draft\n")

        code, output = self.run_gate()

        self.assertEqual(1, code)
        self.assertIn("FAIL nothing uncommitted", output)

    def test_the_gate_has_nothing_to_check_without_new_commits(self):
        self.repository.run_git("checkout", "--quiet", "main")

        code, output = self.run_gate()

        self.assertEqual(0, code)
        self.assertIn("Nothing to push", output)


if __name__ == "__main__":
    unittest.main()
