#!/usr/bin/env python3
"""Makes, uses and removes the maintainer subagent's own worktree of a pull request.

The maintainer subagent reviews exactly what is on GitHub, never another
checkout, so it checks a pull request out fresh:

    python3 -I scripts/maintainer_checkout.py add <pull request number>
    python3 -I scripts/maintainer_checkout.py gate <path> [--no-build]
        [--remote-branch origin/<branch> | --remote-branch=origin/<branch>]
    python3 -I scripts/maintainer_checkout.py dependencies <path> <task>
        [--configuration <name>]
    python3 -I scripts/maintainer_checkout.py remove <path>

`add` reads the pull request's head with gh, fetches it from origin, and fails
when origin's head differs from GitHub's or the pull request comes from a fork.
It then makes a detached worktree at .git/maintainer-check/pr-<number>-<sha>,
inside the repository's .git folder, and prints its path and the commit. It
replaces only a worktree of its own at that same path.

`gate` runs the worktree's own pre-push gate in the worktree, and
`dependencies` runs a Gradle dependencies task there, such as
:app:dependencies. The subagent can't change folders, so these run the
worktree's code in the worktree and nowhere else.

`remove` removes a worktree. Each command takes only a worktree under
.git/maintainer-check/, so no other checkout can be touched. None of them
prunes, moves or locks worktrees.
"""

import json
import os
import re
import subprocess
import sys

if sys.version_info < (3, 9):
    sys.exit("The maintainer checkout needs Python 3.9 or later.")

FOLDER = "maintainer-check"
GRADLE_TASK = re.compile(r"(:[A-Za-z0-9_-]+)*:dependencies|dependencies")
CONFIGURATION = re.compile(r"[A-Za-z][A-Za-z0-9]*")


def run(*command, check=True):
    result = subprocess.run(list(command), capture_output=True, text=True)
    if check and result.returncode != 0:
        raise RuntimeError(command[0] + " " + command[1] + " failed: " + result.stderr.strip())
    return result.stdout.strip()


def checks_folder():
    """The folder that holds the maintainer's worktrees, in the repository's own .git folder."""
    common = run("git", "rev-parse", "--path-format=absolute", "--git-common-dir")
    return os.path.join(common, FOLDER)


def is_own(path):
    """Whether [path] is a worktree folder that this script may remove."""
    folder = os.path.realpath(checks_folder())
    target = os.path.realpath(path)
    return os.path.dirname(target) == folder and os.path.basename(target).startswith("pr-")


def add_worktree(number, sha):
    """Makes the detached worktree of [sha] for pull request [number], and returns its path."""
    path = os.path.join(checks_folder(), "pr-" + str(number) + "-" + sha[:7])
    if os.path.exists(path):
        run("git", "worktree", "remove", "--force", path)
    run("git", "worktree", "add", "--detach", path, sha)
    return path


def remove_worktree(path):
    if not is_own(path):
        raise RuntimeError("only a worktree under .git/" + FOLDER + "/ may be removed")
    run("git", "worktree", "remove", "--force", path)


def gate_arguments(arguments):
    """[arguments] for the gate, after refusing any but --no-build and --remote-branch."""
    index = 0
    while index < len(arguments):
        argument = arguments[index]
        if argument == "--remote-branch" and index + 1 < len(arguments):
            index += 1
        elif argument != "--no-build" and not argument.startswith("--remote-branch="):
            raise RuntimeError("the gate takes only --no-build and --remote-branch origin/<branch>")
        index += 1
    return list(arguments)


def dependencies_command(arguments):
    """The Gradle command for a dependencies task and an optional --configuration."""
    if len(arguments) not in (1, 3) or not GRADLE_TASK.fullmatch(arguments[0]):
        raise RuntimeError("dependencies takes a task such as :app:dependencies, and optionally "
                           "--configuration <name>")
    command = ["./gradlew", "-q", arguments[0]]
    if len(arguments) == 3:
        if arguments[1] != "--configuration" or not CONFIGURATION.fullmatch(arguments[2]):
            raise RuntimeError("dependencies takes --configuration <name> after its task")
        command += arguments[1:]
    return command


def run_in(path, command):
    """Runs [command] in the worktree at [path], showing its output, and returns its exit code."""
    if not is_own(path):
        raise RuntimeError("only a worktree under .git/" + FOLDER + "/ may be used")
    sys.stdout.flush()
    return subprocess.run(command, cwd=os.path.realpath(path)).returncode


def pull_request_head(number):
    """(branch, sha, from a fork) of pull request [number], as GitHub has it."""
    fields = json.loads(
        run("gh", "pr", "view", str(number), "--json", "headRefName,headRefOid,isCrossRepository")
    )
    return fields["headRefName"], fields["headRefOid"], fields["isCrossRepository"]


def main(arguments):
    if len(arguments) == 2 and arguments[0] == "add" and arguments[1].isdigit():
        number = int(arguments[1])
        branch, sha, from_fork = pull_request_head(number)
        if from_fork:
            print("INCOMPLETE: the pull request comes from a fork, which origin doesn't hold.")
            return 3
        run("git", "fetch", "--quiet", "origin", branch)
        # The full ref, because a local branch or tag of the same name would come first.
        fetched = run("git", "rev-parse", "refs/remotes/origin/" + branch)
        if fetched != sha:
            print("INCOMPLETE: origin's head of the branch differs from GitHub's. Fetch again.")
            return 3
        path = add_worktree(number, sha)
        print("path=" + path)
        print("sha=" + sha)
        print("remote-branch=origin/" + branch)
        return 0
    if len(arguments) == 2 and arguments[0] == "remove":
        remove_worktree(arguments[1])
        print("removed " + arguments[1])
        return 0
    if len(arguments) >= 2 and arguments[0] == "gate":
        gate = [sys.executable, "-I", os.path.join("scripts", "pre_push_gate.py")]
        return run_in(arguments[1], gate + gate_arguments(arguments[2:]))
    if len(arguments) >= 3 and arguments[0] == "dependencies":
        return run_in(arguments[1], dependencies_command(arguments[2:]))
    print("Usage: maintainer_checkout.py add <pull request number> | gate <path> [options] | "
          "dependencies <path> <task> [--configuration <name>] | remove <path>")
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
