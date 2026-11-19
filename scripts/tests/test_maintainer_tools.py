"""Tests of maintainer_guard.py and maintainer_checkout.py, the maintainer subagent's tools."""

import contextlib
import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.dont_write_bytecode = True
SCRIPTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
sys.path.insert(0, SCRIPTS)

import maintainer_checkout  # noqa: E402
import maintainer_guard  # noqa: E402


class GuardTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = os.path.realpath(os.path.join(self.folder.name, "dewfall"))
        self.worktree = os.path.join(self.root, ".git", "maintainer-check", "pr-7-abcdef0")
        self.notes = os.path.join(self.folder.name, "home", ".claude", "projects", "p", "memory")
        os.makedirs(self.worktree)
        os.makedirs(self.notes)
        os.makedirs(os.path.join(self.root, "docs"))
        os.symlink(self.notes, os.path.join(self.worktree, "notes"))
        environment = mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": self.root})
        environment.start()
        self.addCleanup(environment.stop)
        self.addCleanup(self.folder.cleanup)

    def call(self, tool, tool_input, cwd=None):
        return {"tool_name": tool, "tool_input": tool_input, "cwd": cwd or self.root}

    def refusal(self, call):
        """The guard's reason to refuse [call], or None if it allows it."""
        try:
            maintainer_guard.check(call)
        except maintainer_guard.Refused as refusal:
            return str(refusal)
        return None

    def bash(self, command, cwd=None):
        return self.refusal(self.call("Bash", {"command": command}, cwd))

    def assert_allowed(self, *commands):
        for command in commands:
            with self.subTest(command=command):
                self.assertIsNone(self.bash(command))

    def assert_refused(self, *commands):
        for command in commands:
            with self.subTest(command=command):
                self.assertIsNotNone(self.bash(command))

    def test_reading_commands_are_allowed(self):
        self.assert_allowed(
            "git status",
            "git diff origin/main...origin/feat/x -- docs",
            "git log --oneline -5 2>&1",
            "git log --format='%h %s' -n 3 origin/main",
            "git show origin/main:AGENTS.md | head -40",
            "git fetch --quiet origin",
            "git fetch --quiet origin feat/debug-subtitles",
            "git grep -n -e timedtext origin/main -- 'docs/*.md'",
            "git grep -n 'TODO$' -- docs",
            "gh pr view 7 --json title,body,closingIssuesReferences",
            "gh pr view 7 --json headRefOid,statusCheckRollup --jq '.headRefOid'",
            "gh pr diff 7",
            "gh pr checks 7",
            "gh issue view 39 --comments",
            "gh run list --workflow daily-youtube-check.yml --limit 1",
            "gh api repos/dewfallapp/dewfall/pulls/7/files --paginate --jq '.[].filename'",
            "gh api 'advisories?ecosystem=maven&affects=com.squareup.okhttp3:okhttp@5.1.0'",
            "grep -rn 'Wait 14 days' AGENTS.md CONTRIBUTING.md",
            "grep -n -- '->' docs/architecture.md",
            "date -u",
            "ls -la docs",
            "python3 -I scripts/maintainer_checkout.py add 7",
            "python3 -I scripts/maintainer_checkout.py remove " + self.worktree,
            "python3 -I scripts/maintainer_checkout.py gate " + self.worktree
            + " --remote-branch origin/feat/x",
            "python3 -I scripts/maintainer_checkout.py dependencies " + self.worktree
            + " :app:dependencies --configuration releaseRuntimeClasspath",
            "git diff origin/main...abcdef0 --stat",
            "git show abcdef0:docs/testing.md",
            "cat " + self.worktree + "/docs/testing.md",
            "git status &&\n  git log -1",
        )

    def test_writing_commands_are_refused(self):
        self.assert_refused(
            "git checkout main",
            "git switch -c x",
            "git commit -m x",
            "git push origin x",
            "git config user.email x@example.com",
            "git worktree prune",
            "git -C /tmp/other status",
            "git --git-dir=/tmp/x status",
            "git fetch origin main:main",
            "git fetch --prune origin",
            "git update-ref refs/heads/x HEAD",
            "git diff --output=x.patch",
            "git interpret-trailers --in-place --trailer x AGENTS.md",
            "gh pr merge 7 --merge",
            "gh pr comment 7 --body x",
            "gh pr checkout 7",
            "gh issue close 39",
            "gh workflow run daily-youtube-check.yml",
            "gh api -X POST repos/x/y/issues",
            "gh api repos/x/y/issues -f title=x",
            "gh project item-add 1",
            "rm -rf build",
            "touch x",
            "sed -n 'w AGENTS.md' docs/vision.md",
            "echo x > AGENTS.md",
            "echo x >> AGENTS.md",
            "cat AGENTS.md | tee copy.md",
            "find . -name x -delete",
            "sort -o out.txt in.txt",
            "awk 'BEGIN { system(1) }'",
            "./gradlew assembleDebug",
            "python3 scripts/pre_push_gate.py",
            "python3 -c 'print(1)'",
            "echo $HOME",
            "echo `date`",
            "cat <(ls)",
            "curl https://example.com",
        )

    def test_abbreviated_and_bundled_options_are_refused(self):
        self.assert_refused(
            "git grep --op=echo x",
            "git grep -nOecho x",
            "git grep -O x",
            "git fetch --upl=evil .",
            "git fetch --depth=1 origin",
            "git fetch --set-upstream origin main",
            "git fetch origin tag v1",
            "git log --outp=x",
            "git diff --no-index a b",
            "git diff --ext-diff",
            "git diff --color --output=x",
            "git log --format --output=x",
        )

    def test_nothing_reads_files_or_the_environment_through_a_filter(self):
        self.assert_refused(
            "jq -n 'import \"renovate\" as $r {search: \"/Users/x\"}; $r'",
            "jq . renovate.json",
            "gh api repos/x/y --jq 'env'",
            "gh api repos/x/y --jq '$ENV.GH_TOKEN'",
            "gh pr view 7 --json title --jq '$__loc__'",
            "gh api repos/x/y --jq 'input_filename'",
        )
        self.assert_allowed("gh api repos/x/y --jq '.environment_url'")

    def test_gh_writes_and_browsers_are_refused(self):
        self.assert_refused(
            "gh api -XPOST repos/x/y/issues",
            "gh api repos/x/y/issues -ftitle=x",
            "gh api repos/x/y/issues --raw-field=x",
            "gh api repos/x/y/issues --input=f",
            "gh api repos/x/y -H 'X-HTTP-Method-Override: POST'",
            "gh pr view 7 --web",
            "gh pr view 7 -w",
            "gh issue list --web",
            "gh pr view 7 --json --web",
        )

    def test_quoted_separators_are_words_as_in_the_shell(self):
        self.assertEqual(
            maintainer_guard.split_command("echo '|' ';' \"\" && ls | wc -l"),
            [["echo", "|", ";", ""], ["ls"], ["wc", "-l"]],
        )
        self.assert_refused("cat . '' find -delete", "ls '' rm -rf x")

    def test_zsh_expansions_are_refused(self):
        self.assert_refused(
            "ls x(e:'touch y':)",
            "cat =(ls)",
            "ls =ls",
            "ls docs/*",
            "cat ~/x",
            "ls {a,b}",
            "ls x^y",
            "echo hi # comment",
            "echo \"$HOME\"",
            "echo \\$HOME",
        )

    def test_only_whole_redirection_words_are_allowed(self):
        self.assert_allowed("git status 2>&1", "git status 2>/dev/null", "git status >/dev/null")
        self.assert_refused(
            "echo hi 2>&1x",
            "echo hi > /dev/null",
            "git status>x",
            "ls &",
            "ls &> x",
            "echo hi 2>'&'1",
            'echo hi 2>"&"1',
            "echo hi '2>'&1",
            "echo hi 2'>'&1",
        )

    def test_there_is_no_cd(self):
        self.assert_refused(
            "cd " + self.worktree + " && git status",
            "cd " + self.worktree + " && 2>&1 \npython3 -I scripts/maintainer_checkout.py add 7",
            "git status && cd " + self.worktree,
            "cd /tmp",
            "pushd " + self.worktree,
        )

    def test_date_takes_only_a_format(self):
        self.assert_allowed("date -u", "date -u '+%Y-%m-%d'")
        self.assert_refused("date 1119000026", "date -f x y")

    def test_only_the_roots_checkout_script_runs_and_only_on_a_worktree(self):
        self.assertIsNotNone(
            self.bash("python3 -I scripts/maintainer_checkout.py add 7", cwd=self.worktree)
        )
        self.assert_refused(
            "python3 -I " + self.worktree + "/scripts/maintainer_checkout.py add 7",
            "python3 -I scripts/pre_push_gate.py",
            "python3 scripts/maintainer_checkout.py add 7",
            "python3 -I scripts/maintainer_checkout.py gate " + self.root,
            "python3 -I scripts/maintainer_checkout.py gate " + self.worktree + "/..",
            "python3 -I scripts/maintainer_checkout.py dependencies /tmp :app:dependencies",
            "python3 -I scripts/maintainer_checkout.py remove /tmp/other",
            "python3 -I scripts/maintainer_checkout.py prune",
            "python3 -I scripts/maintainer_checkout.py add x",
            "./gradlew -q :app:dependencies",
        )

    def test_paths_outside_the_repository_are_refused(self):
        self.assert_refused(
            "cat " + os.path.join(self.notes, "MEMORY.md"),
            "cat /etc/hosts",
            "cat ../x",
            "grep -rn x " + self.folder.name,
            "cat " + self.worktree + "/notes/MEMORY.md",
            "ls -R docs",
            "grep -R x docs",
        )

    def test_file_tools_stay_inside_the_repository(self):
        memory = os.path.join(self.notes, "MEMORY.md")
        for call in (
            self.call("Read", {"file_path": memory}),
            self.call("Read", {"file_path": os.path.join(self.worktree, "notes", "MEMORY.md")}),
            self.call("Read", {"file_path": self.notes.replace("/.claude/", "/.claude//")}),
            self.call("Grep", {"pattern": "x", "path": os.path.dirname(self.notes)}),
            self.call("Grep", {"pattern": "x", "path": self.folder.name}),
            self.call("Glob", {"pattern": "projects/**", "path": self.folder.name}),
            self.call("Glob", {"pattern": "/Users/**"}),
            self.call("Glob", {"pattern": "../**"}),
            self.call("Glob", {"pattern": "{..,docs}/**"}),
            self.call("Glob", {"pattern": "{/Users,x}/**"}),
        ):
            with self.subTest(call=call):
                self.assertIsNotNone(self.refusal(call))
        for call in (
            self.call("Read", {"file_path": os.path.join(self.root, "AGENTS.md")}),
            self.call("Read", {"file_path": "/private/tmp/claude-501/p/s/scratchpad/plan.md"}),
            self.call("Grep", {"pattern": "x"}),
            self.call("Grep", {"pattern": "x", "path": os.path.join(self.root, "docs")}),
            self.call("Glob", {"pattern": "docs/**/*.md"}),
        ):
            with self.subTest(call=call):
                self.assertIsNone(self.refusal(call))

    def test_other_tools_are_left_alone(self):
        self.assertIsNone(self.refusal(self.call("WebFetch", {"url": "https://example.com"})))

    def test_the_guard_fails_closed(self):
        # Text that isn't JSON, and a tool input that makes the check itself fail.
        for text in ("not json", json.dumps({"tool_name": "Bash", "tool_input": ["x"]})):
            with self.subTest(text=text):
                result = subprocess.run(
                    [sys.executable, "-I", os.path.join(SCRIPTS, "maintainer_guard.py")],
                    input=text, capture_output=True, text=True,
                )
                self.assertEqual(2, result.returncode)
                self.assertIn("the guard failed", result.stderr)


class CheckoutTest(unittest.TestCase):
    def setUp(self):
        # Each change is undone by a cleanup registered right after it, so a failed git
        # command below can't leave later tests in the temporary repository.
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        environment = mock.patch.dict(
            os.environ,
            {
                "GIT_CONFIG_GLOBAL": os.path.join(self.folder.name, "no-global-config"),
                "GIT_CONFIG_NOSYSTEM": "1",
            },
        )
        environment.start()
        self.addCleanup(environment.stop)
        origin = os.path.join(self.folder.name, "origin.git")
        subprocess.run(["git", "init", "--quiet", "--bare", origin], check=True)
        self.repository = os.path.join(self.folder.name, "work")
        os.makedirs(self.repository)
        self.addCleanup(os.chdir, os.getcwd())
        os.chdir(self.repository)
        for command in (
            ["git", "init", "--quiet", "-b", "main"],
            ["git", "config", "--local", "user.name", "A Maintainer"],
            ["git", "config", "--local", "user.email", "maintainer@example.com"],
            ["git", "commit", "--quiet", "--allow-empty", "-m", "docs: start"],
            ["git", "remote", "add", "origin", origin],
            ["git", "push", "--quiet", "origin", "main:feat/x"],
        ):
            subprocess.run(command, check=True, capture_output=True)
        self.sha = subprocess.run(
            ["git", "rev-parse", "HEAD"], check=True, capture_output=True, text=True
        ).stdout.strip()

    def run_main(self, head):
        output = io.StringIO()
        with mock.patch.object(maintainer_checkout, "pull_request_head", return_value=head):
            with contextlib.redirect_stdout(output):
                code = maintainer_checkout.main(["add", "7"])
        return code, output.getvalue()

    def test_add_checks_out_the_head_that_github_names(self):
        code, output = self.run_main(("feat/x", self.sha, False))

        self.assertEqual(0, code)
        lines = output.splitlines()
        path = lines[0][len("path="):]
        self.assertTrue(path.endswith(os.path.join(".git", "maintainer-check", "pr-7-" + self.sha[:7])))
        self.assertEqual(["sha=" + self.sha, "remote-branch=origin/feat/x"], lines[1:])
        self.assertTrue(os.path.isdir(path))

    def test_add_refuses_a_head_that_origin_doesnt_have(self):
        code, output = self.run_main(("feat/x", "0" * 40, False))

        self.assertEqual(3, code)
        self.assertIn("INCOMPLETE", output)

    def test_add_refuses_a_fork(self):
        code, output = self.run_main(("feat/x", self.sha, True))

        self.assertEqual(3, code)
        self.assertIn("fork", output)

    def test_a_worktree_is_made_inside_the_git_folder_and_removed_again(self):
        path = maintainer_checkout.add_worktree(7, self.sha)

        self.assertTrue(path.endswith(os.path.join(".git", "maintainer-check", "pr-7-" + self.sha[:7])))
        self.assertTrue(os.path.isdir(path))
        maintainer_checkout.remove_worktree(path)
        self.assertFalse(os.path.exists(path))

    def test_making_the_same_worktree_again_replaces_it(self):
        first = maintainer_checkout.add_worktree(7, self.sha)

        second = maintainer_checkout.add_worktree(7, self.sha)

        self.assertEqual(first, second)
        self.assertTrue(os.path.isdir(second))

    def test_gate_and_dependencies_take_only_their_own_options(self):
        self.assertEqual(
            ["--no-build", "--remote-branch", "origin/x", "--remote-branch=origin/y"],
            maintainer_checkout.gate_arguments(
                ["--no-build", "--remote-branch", "origin/x", "--remote-branch=origin/y"]
            ),
        )
        self.assertEqual(
            ["./gradlew", "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
            maintainer_checkout.dependencies_command(
                [":app:dependencies", "--configuration", "releaseRuntimeClasspath"]
            ),
        )
        for bad in (["--remote"], ["-x"], ["--remote-branch"]):
            with self.subTest(gate=bad):
                with self.assertRaises(RuntimeError):
                    maintainer_checkout.gate_arguments(bad)
        for bad in (["build"], [":app:dependencies", "build"], [":app:dependencies", "-I", "x"],
                    [":app:dependencies", "--configuration", "-Dx"]):
            with self.subTest(dependencies=bad):
                with self.assertRaises(RuntimeError):
                    maintainer_checkout.dependencies_command(bad)

    def test_gate_runs_only_in_its_own_worktree(self):
        path = maintainer_checkout.add_worktree(7, self.sha)
        other = os.path.join(self.folder.name, "other")
        subprocess.run(["git", "worktree", "add", "--detach", other], check=True, capture_output=True)

        self.assertEqual(0, maintainer_checkout.run_in(path, ["git", "status", "--short"]))
        with self.assertRaises(RuntimeError):
            maintainer_checkout.run_in(other, ["git", "status", "--short"])

    def test_another_worktree_is_never_removed(self):
        other = os.path.join(self.folder.name, "other")
        subprocess.run(["git", "worktree", "add", "--detach", other], check=True, capture_output=True)

        with self.assertRaises(RuntimeError):
            maintainer_checkout.remove_worktree(other)
        self.assertTrue(os.path.isdir(other))


if __name__ == "__main__":
    unittest.main()
