#!/usr/bin/env python3
"""The pre-push gate: checks every commit that a push would send, then the build.

Run it from the repository's folder before every push:

    python3 scripts/pre_push_gate.py

It acts on the repository of the folder it runs in, and finds its own files next
to itself, so a copy elsewhere can check another checkout. It fetches origin,
then checks every commit in origin/main..HEAD, not just the last one:

- the branch contains origin/main, and no commit is a merge;
- each commit's author and committer are this clone's local user.name and
  user.email, and never the global email when that differs;
- each subject has a conventional prefix, and the message has no trailer and no
  closing word before an issue;
- no commit adds a local.properties, .env, keystore or key file, or a line that
  holds a private key, a GitHub token, an AWS access key or a Google API key;
- each commit that changes the saved YouTube responses passes
  scan_saved_responses.py, on that commit's own files.

Then it checks that nothing is left uncommitted, runs the pull request
workflow's command, and checks again that the build changed no file. Its own
tests run first, so a broken check fails instead of passing. It prints rule
names, commit hashes and subjects, never an identity or a found value.

--remote-branch origin/<name>, or --remote-branch=origin/<name>, names the
branch on GitHub whose Renovate commits are exempt from the identity rule. It
must start with origin/ and have no .., or the gate stops with exit code 2.
Without it, the gate uses origin/ and the current branch's name, and a
detached HEAD has none, so nothing is exempt. The gate reads the branch under
refs/remotes/, so a local branch or tag with the same name can't stand in for
it.

--no-build skips the build. The gate then ends INCOMPLETE, with exit code 3,
when every other check passed, and FAIL, with exit code 1, when one didn't. It
never ends PASS without the build. It needs Python 3.9 or later.
docs/testing.md#the-pre-push-gate explains each rule.
"""

import os
import re
import subprocess
import sys
import unittest

if sys.version_info < (3, 9):
    sys.exit("The pre-push gate needs Python 3.9 or later.")

# Python's compiled files would land in scripts/ as untracked files, which the
# check for uncommitted files would then refuse.
sys.dont_write_bytecode = True
SCRIPTS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPTS)

import scan_saved_responses  # noqa: E402

SAVED_RESPONSES = "core/youtube/src/test/resources/youtube"
BUILD_COMMAND = [
    "./gradlew",
    "spotlessCheck",
    "detekt",
    "lint",
    "test",
    "assembleDebug",
    "--continue",
    "-Pandroid.builder.sdkDownload=false",
]
# Renovate's commits on its own branches. A fix pushed to Renovate's branch, as
# docs/testing.md says to, sends them again, and they can't carry the clone's
# identity. They are exempt from the identity rule only, and only once they are
# on the remote branch, so the gate never lets a new commit through by name.
RENOVATE = "renovate[bot]"

SUBJECT = re.compile(r"^(feat|fix|docs|test|refactor|build|ci|chore)(\([a-z0-9-]+\))?!?: \S")
TRAILER_NAME = re.compile(
    r"^(co-authored-by|signed-off-by|reviewed-by|acked-by|tested-by|reported-by|"
    r"suggested-by|helped-by|change-id|generated-by)\s*:",
    re.IGNORECASE | re.MULTILINE,
)
CLOSING_WORD = r"\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\b[\s:]*"
CLOSING = re.compile(
    CLOSING_WORD
    + r"(?:(?:[\w.-]+/[\w.-]+)?#\d+|https?://github\.com/[\w.-]+/[\w.-]+/(?:issues|pull)/\d+)",
    re.IGNORECASE,
)
SECRET_FILE = re.compile(
    r"(?:^|/)(?:local\.properties|\.env(?:\..*)?|[^/]*\.(?:jks|keystore|p12|pfx|pem|key))$",
    re.IGNORECASE,
)
SECRET_CONTENT = [
    ("PRIVATE_KEY", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("GITHUB_TOKEN", re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,})")),
    ("AWS_ACCESS_KEY", re.compile(r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("GOOGLE_API_KEY", re.compile(r"AIza[0-9A-Za-z_\-]{35}")),
]


def subject_problems(message):
    subject = message.split("\n", 1)[0]
    return [] if SUBJECT.match(subject) else ["SUBJECT_NOT_CONVENTIONAL"]


def trailer_problems(message, parsed_trailers):
    """[parsed_trailers] is what git interpret-trailers --parse printed for [message]."""
    body = message.split("\n", 1)[1] if "\n" in message else ""
    if parsed_trailers.strip() or TRAILER_NAME.search(body):
        return ["TRAILER"]
    return []


def closing_problems(message):
    return ["CLOSING_WORD"] if CLOSING.search(message) else []


def identity_problems(author, committer, local, global_email):
    """Each of [author], [committer] and [local] is (name, email). [global_email] may be None."""
    if not local[0] or not local[1]:
        return ["NO_LOCAL_IDENTITY"]
    problems = []
    for role, (name, email) in (("AUTHOR", author), ("COMMITTER", committer)):
        if (name, email) != local:
            problems.append(role + "_NOT_LOCAL_IDENTITY")
        if global_email and global_email != local[1] and email == global_email:
            problems.append(role + "_IS_GLOBAL_IDENTITY")
    return problems


def secret_file_problems(paths):
    return ["SECRET_FILE " + path for path in paths if SECRET_FILE.search(path)]


def secret_content_problems(added_lines):
    found = []
    for name, pattern in SECRET_CONTENT:
        if any(pattern.search(line) for line in added_lines):
            found.append(name)
    return found


def git(*arguments, check=True):
    result = subprocess.run(
        ["git", *arguments], capture_output=True, text=True, errors="replace"
    )
    if check and result.returncode != 0:
        raise RuntimeError("git " + arguments[0] + " failed")
    return result.stdout


def git_config(scope, key):
    result = subprocess.run(["git", "config", scope, key], capture_output=True, text=True)
    return result.stdout.strip() or None


def tree_id(commit, path):
    result = subprocess.run(
        ["git", "rev-parse", "--verify", "--quiet", commit + ":" + path],
        capture_output=True,
        text=True,
    )
    return result.stdout.strip() or None


def saved_files(commit):
    """Each (path in the folder, bytes) of [commit]'s saved responses, read from Git itself.

    Reading the blobs, instead of an archive, ignores export-ignore and any filter that the
    commit's .gitattributes names, so every file is scanned as the commit holds it.
    """
    listing = subprocess.run(
        ["git", "ls-tree", "-r", "-z", commit, "--", SAVED_RESPONSES],
        capture_output=True,
        check=True,
    ).stdout
    for entry in listing.split(b"\0"):
        if not entry:
            continue
        meta, path = entry.split(b"\t", 1)
        _, kind, blob = meta.split(b" ")
        if kind != b"blob":
            continue
        data = subprocess.run(
            ["git", "cat-file", "blob", blob.decode()], capture_output=True, check=True
        ).stdout
        relative = path.decode("utf-8", errors="replace")[len(SAVED_RESPONSES) + 1 :]
        yield relative, data


def scan_commit(commit):
    """The findings of the saved-response scan on [commit]'s own files, and how many it read."""
    return scan_saved_responses.scan_files(saved_files(commit))


def check_commit(commit, local, global_email, on_remote_branch):
    """The problems of one commit, as rule names."""
    parents = git("rev-list", "--parents", "-n", "1", commit).split()[1:]
    if len(parents) > 1:
        return ["MERGE_COMMIT: rebase onto origin/main instead"]
    fields = git("log", "-1", "--no-mailmap", "--format=%an%x00%ae%x00%cn%x00%ce", commit)
    author_name, author_email, committer_name, committer_email = fields.rstrip("\n").split("\0")
    message = git("log", "-1", "--format=%B", commit)
    problems = []
    is_renovate = RENOVATE in (author_name, committer_name)
    if not (is_renovate and commit in on_remote_branch):
        problems += identity_problems(
            (author_name, author_email), (committer_name, committer_email), local, global_email
        )
    problems += subject_problems(message)
    parsed = subprocess.run(
        ["git", "interpret-trailers", "--parse"], input=message, capture_output=True, text=True
    ).stdout
    problems += trailer_problems(message, parsed)
    problems += closing_problems(message)
    paths = git(
        "diff-tree", "--no-commit-id", "--name-only", "-r", "--no-renames", "--diff-filter=d",
        commit,
    ).split("\n")
    problems += secret_file_problems([path for path in paths if path])
    # Colours, external diff tools and text conversions from the user's configuration would
    # change the lines, so a secret could hide from the check.
    diff = git(
        "show", "--format=", "--text", "--no-color", "--no-ext-diff", "--no-textconv",
        "--no-renames", "--diff-filter=d", "-U0", commit,
    )
    added = [line[1:] for line in diff.split("\n") if line.startswith("+") and not line.startswith("+++")]
    problems += secret_content_problems(added)
    after = tree_id(commit, SAVED_RESPONSES)
    # A commit that deletes the folder leaves nothing to scan.
    if after is not None and after != tree_id(commit + "^", SAVED_RESPONSES):
        findings, count = scan_commit(commit)
        if count == 0:
            problems.append("SAVED_RESPONSE no file was read")
        for relative, line, depth, kind, key in findings:
            problems.append(f"SAVED_RESPONSE {relative}:{line} depth={depth} {kind} {key}")
    return problems


def run_own_tests():
    suite = unittest.defaultTestLoader.discover(os.path.join(SCRIPTS, "tests"))
    result = unittest.TextTestRunner(stream=open(os.devnull, "w")).run(suite)
    return result.wasSuccessful(), result.testsRun


def uncommitted():
    return git("status", "--porcelain", "--untracked-files=all").strip()


def named_remote_branch(arguments):
    """The branch that --remote-branch names, or None without it.

    Raises ValueError unless it names a branch under origin/, because any other
    ref, such as HEAD, would exempt Renovate commits that aren't on GitHub.
    """
    named = None
    for index, argument in enumerate(arguments):
        if argument == "--remote-branch":
            named = arguments[index + 1] if index + 1 < len(arguments) else ""
        elif argument.startswith("--remote-branch="):
            named = argument.split("=", 1)[1]
    if named is not None and (
        not named.startswith("origin/") or named == "origin/" or ".." in named
    ):
        raise ValueError("--remote-branch must name a branch under origin/, such as origin/feat/x.")
    return named


def remote_branch_of(named, branch):
    """The [named] remote branch, else origin/[branch], as a full ref, or None if detached."""
    if named is not None:
        return "refs/remotes/" + named
    return None if branch == "HEAD" else "refs/remotes/origin/" + branch


def main(arguments, own_tests=True):
    """Runs the gate and returns its exit code. [own_tests] is false only in the gate's tests."""
    build = "--no-build" not in arguments
    failed = False
    try:
        named = named_remote_branch(arguments)
    except ValueError as error:
        print("FAIL " + str(error))
        return 2

    def verdict(name, passed, detail=""):
        nonlocal failed
        failed = failed or not passed
        print(("PASS " if passed else "FAIL ") + name + (" " + detail if detail else ""))

    if own_tests:
        passed, count = run_own_tests()
        verdict("gate's own tests", passed, f"tests={count}")
        if not passed:
            return 1

    root = git("rev-parse", "--show-toplevel").strip()
    os.chdir(root)
    git("fetch", "--quiet", "origin")
    verdict(
        "branch contains origin/main",
        subprocess.run(["git", "merge-base", "--is-ancestor", "origin/main", "HEAD"]).returncode
        == 0,
    )
    commits = git("rev-list", "--reverse", "origin/main..HEAD").split()
    if not commits:
        print("Nothing to push: HEAD has no commit that origin/main lacks.")
        return 1 if failed else 0

    local = (git_config("--local", "user.name"), git_config("--local", "user.email"))
    global_email = git_config("--global", "user.email")
    branch = git("rev-parse", "--abbrev-ref", "HEAD").strip()
    remote_branch = remote_branch_of(named, branch)
    on_remote_branch = set()
    if remote_branch is not None and tree_id(remote_branch, "") is not None:
        on_remote_branch = set(git("rev-list", remote_branch).split())

    for commit in commits:
        subject = git("log", "-1", "--format=%s", commit).strip()
        problems = check_commit(commit, local, global_email, on_remote_branch)
        verdict("commit " + commit[:7] + " " + subject, not problems)
        for problem in problems:
            print("    " + problem)

    left = uncommitted()
    verdict("nothing uncommitted", not left, "" if not left else "commit or stash every change")
    if not build:
        if failed:
            print("FAIL: the gate failed.")
            return 1
        print("INCOMPLETE: the build didn't run, so the gate didn't pass.")
        return 3
    if left:
        print("FAIL: the build would check files that the push doesn't send.")
        return 1
    built = subprocess.run(BUILD_COMMAND).returncode == 0
    verdict("full build and every test", built)
    left = uncommitted()
    verdict("the build changed no file", not left)
    print("PASS: the gate passed." if not failed else "FAIL: the gate failed.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
