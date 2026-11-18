#!/usr/bin/env python3
"""The maintainer subagent's guard: lets it read, never write.

.claude/agents/maintainer.md runs this as a PreToolUse hook for Bash, Read,
Grep and Glob. Claude Code passes the tool call as JSON on standard input. The
guard exits with 0 to allow the call, and with 2, and a message on standard
error, to refuse it. An error inside the guard refuses the call too, and the
hook sets onFailure to block, so a guard that can't start, exits with another
code or times out blocks the call as well.

Everything is refused unless a rule below allows it, so a command or an option
that nobody thought of is refused:
- A Bash command is split the way the shell splits it, quotes included. It
  may not use `$`, backticks, backslashes, globs, parentheses, braces, `~`,
  `!`, `#`, `^` or a word that starts with `=` outside single quotes, and no
  redirection other than the whole, unquoted words 2>&1, 2>/dev/null,
  >/dev/null and 1>/dev/null.
- Each subcommand runs a program from a short list, with only the options
  listed for it. Long options must be written in full, because Git accepts
  any unambiguous abbreviation of one.
- Every word of a command other than gh's, taken as a path, must stay inside
  the repository, symbolic links resolved.
- Git and gh may only read. `git fetch` may only fetch from origin.
- There is no `cd`, so every command runs in the repository's root, and a
  failed `cd` can't make the rest of a command run somewhere else. The only
  script is scripts/maintainer_checkout.py, the root's own copy. It runs a
  worktree's pre-push gate and Gradle dependencies task in that worktree,
  after checking that the worktree is one of its own.
- Read, Grep and Glob work only inside the repository. Read may also open a
  file in a scratchpad folder of Claude Code's, where plans are written.

In Claude Code's shell, grep may be a function that runs ugrep. The options
listed for grep mean the same in ugrep and in BSD grep, and none of them runs
a program or writes a file.

So the coordinator's memory notes, in ~/.claude/projects/, are out of reach,
and the maintainer subagent judges the work on its own.
"""

import json
import os
import re
import sys

WORKTREE_FOLDER = os.path.join(".git", "maintainer-check")
SCRATCHPAD = re.compile(r"^(/private)?/tmp/claude-[^/]+/[^/]+/[^/]+/scratchpad/")
ALLOWED_REDIRECTIONS = {"2>&1", "2>/dev/null", ">/dev/null", "1>/dev/null"}
# Characters that the shell gives a meaning to outside quotes, which the guard
# can't follow. zsh adds glob qualifiers, =(...) and, with EXTENDED_GLOB, # and ^.
REFUSED_UNQUOTED = set("$`\\*?[](){}~!#^")
REFUSED_IN_DOUBLE_QUOTES = set("$`\\!")


class Refused(Exception):
    """A tool call that the guard refuses, with the reason."""


def refuse(reason):
    raise Refused("Blocked for the maintainer subagent: " + reason)


# Splitting a command


def split_command(command):
    """The subcommands of [command], each a list of words, split as the shell would.

    Raises Refused for anything the guard can't follow. The allowed redirections
    are dropped from the words.
    """
    parts, words, word = [], [], []
    started = special = quoted = False

    def end_word():
        nonlocal word, started, special, quoted
        if started:
            text = "".join(word)
            if special:
                if quoted or text not in ALLOWED_REDIRECTIONS:
                    refuse("redirection writes files, and & runs commands in the background. "
                           "Only the whole, unquoted words 2>&1, 2>/dev/null, >/dev/null and "
                           "1>/dev/null are allowed.")
            else:
                words.append(text)
        word, started, special, quoted = [], False, False, False

    def end_part():
        end_word()
        if not words:
            refuse("a separator with no command before it.")
        parts.append(list(words))
        words.clear()

    index = 0
    while index < len(command):
        char = command[index]
        if char == "'":
            close = command.find("'", index + 1)
            if close < 0:
                refuse("the command's quotes don't close.")
            word.append(command[index + 1:close])
            started = quoted = True
            index = close + 1
        elif char == '"':
            close = command.find('"', index + 1)
            if close < 0:
                refuse("the command's quotes don't close.")
            inner = command[index + 1:close]
            if REFUSED_IN_DOUBLE_QUOTES & set(inner):
                refuse("$, backticks, backslashes and ! inside double quotes can't be checked. "
                       "Use single quotes.")
            word.append(inner)
            started = quoted = True
            index = close + 1
        elif char in " \t":
            end_word()
            index += 1
        elif char == "\n" and not (words or started):
            # A line break after && or | continues the command, as in the shell.
            index += 1
        elif char in ";\n":
            end_part()
            index += 1
        elif char == "|":
            end_part()
            index += 2 if command.startswith("||", index) else 1
        elif char == "&" and command.startswith("&&", index):
            end_part()
            index += 2
        elif char in "&<>":
            word.append(char)
            started = special = True
            index += 1
        elif char in REFUSED_UNQUOTED:
            refuse(char + " outside single quotes can't be checked. Quote the word, or use Read, "
                   "Grep or Glob.")
        elif char == "=" and not started:
            refuse("zsh expands a word that starts with =. Quote it.")
        else:
            word.append(char)
            started = True
            index += 1
    if words or started or not parts:
        end_part()
    return parts


# Options


class Options:
    """The options that one program, or one of its commands, accepts.

    [flags] take no value. [values] take one, as the next word or, for a long
    option, after =. A next word that starts with - is refused as a value,
    because the program might read it as an option instead. [patterns] match
    whole words, such as -5 or -U3. [bundle] lists the one-letter flags that
    may be written together, such as -rn. [positional] says whether words that
    aren't options are allowed, or is a pattern that each of them must match.
    """

    def __init__(self, flags=(), values=(), patterns=(), bundle="", positional=True):
        self.flags = set(flags)
        self.values = set(values)
        self.patterns = [re.compile(pattern) for pattern in patterns]
        self.bundle = re.compile("-[" + bundle + "]+") if bundle else None
        self.positional = re.compile(positional) if isinstance(positional, str) else positional

    def extended(self, flags=(), values=(), patterns=()):
        result = Options(self.flags | set(flags), self.values | set(values))
        result.patterns = self.patterns + [re.compile(pattern) for pattern in patterns]
        result.bundle = self.bundle
        result.positional = self.positional
        return result

    def check(self, name, arguments):
        """The positional arguments, after refusing any option that isn't listed."""
        positionals = []
        index = 0
        options_end = False
        while index < len(arguments):
            argument = arguments[index]
            index += 1
            if options_end or argument == "-" or not argument.startswith("-"):
                if not self.positional or (
                    self.positional is not True and not self.positional.fullmatch(argument)
                ):
                    refuse(name + " doesn't take the argument " + argument + ".")
                positionals.append(argument)
            elif argument == "--":
                options_end = True
            elif argument in self.flags:
                pass
            elif argument in self.values:
                if index >= len(arguments) or arguments[index].startswith("-"):
                    refuse(name + " " + argument + " needs a value that doesn't start with -. "
                           "Write a long option's value after =.")
                index += 1
            elif (argument.startswith("--") and "=" in argument
                  and argument.split("=", 1)[0] in self.values):
                pass
            elif any(pattern.fullmatch(argument) for pattern in self.patterns):
                pass
            elif self.bundle and self.bundle.fullmatch(argument):
                pass
            else:
                refuse(name + " option " + argument + " isn't on the guard's list. Long options "
                       "must be written in full, and only listed options are allowed.")
        return positionals


READING_PROGRAMS = {
    "cat": Options(bundle="benstuv"),
    "head": Options(values={"-n", "-c"}, patterns=[r"-\d+", r"-[nc]\d+"]),
    "tail": Options(values={"-n", "-c"}, patterns=[r"-\d+", r"-[nc]\+?\d+"]),
    "wc": Options(bundle="lwcm"),
    "ls": Options(bundle="1aAlhdtrSFGp"),
    "grep": Options(
        flags={"--color=never"},
        values={"-e", "-A", "-B", "-C", "-m", "--include", "--exclude", "--exclude-dir"},
        patterns=[r"-[ABCm]\d+"],
        bundle="nilcwEFvrhHoqsxIL",
    ),
    "cut": Options(values={"-d", "-f", "-c"}, patterns=[r"-[dfc].+"]),
    "tr": Options(bundle="dsc"),
    "echo": None,
    "test": None,
    "true": None,
    "false": None,
    "date": Options(flags={"-u", "-R"}, positional=r"\+.*"),
    "pwd": Options(positional=False),
    "basename": Options(),
    "dirname": Options(),
    "realpath": Options(),
    "which": Options(),
    "shasum": Options(values={"-a"}),
}

LOG = Options(
    flags={
        "--stat", "--numstat", "--shortstat", "--name-only", "--name-status", "--oneline",
        "--patch", "-p", "-u", "--no-patch", "-s", "--reverse", "--first-parent", "--merges",
        "--no-merges", "--all", "--decorate", "--no-decorate", "--graph", "--follow", "-M",
        "--find-renames", "--no-renames", "--word-diff", "--no-color", "--cached", "--staged",
        "--check", "-w", "--ignore-all-space", "-b", "--ignore-space-change", "--summary",
        "--raw", "-r", "--root", "--no-commit-id", "--exit-code", "--quiet", "-q",
        "--abbrev-commit", "--no-abbrev", "--full-history", "--left-right", "--cherry-pick",
        "--cherry-mark", "--count", "--ancestry-path", "--topo-order", "--date-order", "-z",
        "--boundary", "--source", "--full-index", "--minimal", "--histogram", "--patience",
        "--compact-summary", "--dirstat", "--no-ext-diff", "--no-textconv", "--text", "-a",
        "--remotes", "--branches", "--tags", "--simplify-by-decoration", "--parents",
        "--children", "--no-walk", "-m", "--cc", "-c", "--merge-base",
    },
    values={
        "--format", "--pretty", "--date", "-n", "--max-count", "--skip", "--since", "--after",
        "--until", "--before", "--author", "--committer", "--grep", "--diff-filter", "--unified",
        "-S", "-G", "-L", "--color", "--abbrev", "--word-diff", "--find-renames", "--decorate",
        "--min-parents", "--max-parents", "--diff-merges", "--creation-factor", "--stat",
        "--dirstat",
    },
    patterns=[r"-\d+", r"-n\d+", r"-U\d+"],
)

GIT_COMMANDS = {
    "log": LOG,
    "show": LOG,
    "diff": LOG,
    "diff-tree": LOG,
    "rev-list": LOG,
    "range-diff": LOG,
    "shortlog": LOG.extended(flags={"--summary", "--numbered", "--email", "-e"}),
    "status": Options(
        flags={"--short", "-s", "--branch", "-b", "--porcelain", "-z", "--long"},
        values={"--porcelain", "--untracked-files"},
        patterns=[r"-u(no|normal|all)?"],
    ),
    "rev-parse": Options(
        flags={"--abbrev-ref", "--short", "--verify", "--show-toplevel", "--git-common-dir",
               "--git-dir", "--absolute-git-dir", "--symbolic-full-name", "--symbolic", "-q",
               "--quiet", "--is-inside-work-tree", "--show-prefix"},
        values={"--short", "--path-format", "--abbrev-ref"},
    ),
    "ls-tree": Options(
        flags={"-r", "--name-only", "--name-status", "-l", "--long", "-t", "-d", "--full-tree",
               "--full-name", "-z", "--object-only"},
        values={"--format", "--abbrev"},
    ),
    "ls-files": Options(
        flags={"--stage", "-s", "--cached", "-c", "--modified", "-m", "--others", "-o",
               "--exclude-standard", "-z", "--error-unmatch", "--deleted", "-d", "--full-name"},
    ),
    "cat-file": Options(flags={"-p", "-t", "-s", "-e"}),
    "grep": Options(
        flags={
            "-n", "--line-number", "-i", "--ignore-case", "-l", "--files-with-matches",
            "--name-only", "-L", "--files-without-match", "-c", "--count", "-w",
            "--word-regexp", "-E", "--extended-regexp", "-F", "--fixed-strings", "-P",
            "--perl-regexp", "-G", "--basic-regexp", "-h", "-H", "--full-name", "-I", "-v",
            "--invert-match", "-o", "--only-matching", "--heading", "--break", "-q", "--quiet",
            "--all-match", "--and", "--or", "--not", "-z", "--null", "--cached", "-W",
            "--function-context", "-p", "--show-function", "--column", "-r", "--recursive",
            "--no-recursive", "-a", "--text", "--no-color",
        },
        values={"-e", "-A", "-B", "-C", "--after-context", "--before-context", "--context",
                "--max-depth", "-m", "--max-count"},
        patterns=[r"-[ABCm]\d+"],
    ),
    "for-each-ref": Options(
        values={"--format", "--sort", "--count", "--contains", "--no-contains", "--merged",
                "--no-merged", "--points-at"},
    ),
    "merge-base": Options(
        flags={"--is-ancestor", "--all", "--fork-point", "--octopus", "--independent"},
    ),
    "blame": Options(
        flags={"-w", "-M", "-C", "--porcelain", "--line-porcelain", "-s", "-e", "--show-email",
               "-l", "-t", "-f", "--show-name", "-n", "--show-number", "-c", "--root"},
        values={"-L", "--date"},
        patterns=[r"-L.+"],
    ),
    "describe": Options(
        flags={"--tags", "--all", "--always", "--exact-match", "--long", "--contains"},
        values={"--abbrev", "--match", "--exclude", "--candidates"},
    ),
    "name-rev": Options(flags={"--name-only", "--tags", "--always"},
                        values={"--refs", "--exclude"}),
    "show-ref": Options(
        flags={"--heads", "--tags", "--branches", "--verify", "-q", "--quiet", "--hash", "-s",
               "-d", "--dereference", "--head", "--exists"},
        values={"--hash", "--abbrev"},
    ),
    "fetch": Options(flags={"--quiet", "-q"}),
}
BRANCH_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._/-]*")

# jq's ways to read the environment and its own location, which --jq would print.
GH_ENVIRONMENT = re.compile(r"\benv\b|\$ENV|\$__loc__|\binput_filename\b")
GH_COMMON = {"--repo", "-R", "--json", "--jq", "-q", "--template", "-t", "--limit", "-L"}
GH_COMMANDS = {
    ("pr", "view"): Options(flags={"--comments", "-c"}, values=GH_COMMON),
    ("pr", "diff"): Options(flags={"--name-only", "--patch", "--color=never"},
                            values={"--repo", "-R"}),
    ("pr", "checks"): Options(flags={"--required"}, values=GH_COMMON),
    ("pr", "list"): Options(
        flags={"--draft", "-d"},
        values=GH_COMMON | {"--state", "-s", "--base", "-B", "--head", "-H", "--author", "-A",
                            "--label", "-l", "--search", "-S", "--assignee", "-a"},
    ),
    ("pr", "status"): Options(values=GH_COMMON),
    ("issue", "view"): Options(flags={"--comments", "-c"}, values=GH_COMMON),
    ("issue", "list"): Options(
        values=GH_COMMON | {"--state", "-s", "--label", "-l", "--milestone", "-m", "--search",
                            "-S", "--author", "-A", "--assignee", "-a", "--mention"},
    ),
    ("issue", "status"): Options(values=GH_COMMON),
    ("run", "view"): Options(
        flags={"--log", "--log-failed", "--verbose", "-v", "--exit-status"},
        values=GH_COMMON | {"--job", "-j", "--attempt", "-a"},
    ),
    ("run", "list"): Options(
        flags={"--all", "-a"},
        values=GH_COMMON | {"--workflow", "-w", "--branch", "-b", "--event", "-e", "--status",
                            "-s", "--user", "-u", "--commit", "-c", "--created"},
    ),
    ("release", "view"): Options(values=GH_COMMON),
    ("release", "list"): Options(flags={"--exclude-drafts", "--exclude-pre-releases"},
                                 values=GH_COMMON),
    ("repo", "view"): Options(values=GH_COMMON | {"--branch", "-b"}),
    ("workflow", "view"): Options(flags={"--yaml", "-y"}, values={"--repo", "-R", "--ref", "-r"}),
    ("workflow", "list"): Options(flags={"--all", "-a"}, values=GH_COMMON),
    ("api", None): Options(
        flags={"--paginate", "--slurp", "--include", "-i", "--silent", "--verbose"},
        values={"--jq", "-q", "--template", "-t", "--cache"},
    ),
}
GH_SEARCH = Options(
    values=GH_COMMON | {"--owner", "--state", "--sort", "--order", "--author", "--label",
                        "--language", "--filename", "--extension", "--match", "--created",
                        "--updated"},
)
for kind in ("issues", "prs", "code", "commits", "repos"):
    GH_COMMANDS[("search", kind)] = GH_SEARCH

CHECKOUT_COMMANDS = {"add", "remove", "gate", "dependencies"}


# Paths


def is_inside(path, folder):
    return path == folder or path.startswith(folder + os.sep)


def resolved(path, base):
    return os.path.realpath(os.path.join(base, os.path.expanduser(path)))


def worktree_root(path, root):
    """Whether [path], resolved, is one of the maintainer's worktrees."""
    folder = os.path.join(root, WORKTREE_FOLDER)
    return os.path.dirname(path) == folder and os.path.basename(path).startswith("pr-")


def check_paths(words, base, root):
    for word in words:
        if not is_inside(resolved(word, base), root):
            refuse(word + " is outside the repository. Read only the repository, and use Read for "
                   "a plan in a scratchpad folder.")


# Programs


def check_git(arguments):
    if not arguments or arguments[0].startswith("-"):
        refuse("git options before the command, such as -C, -c or --git-dir, are not allowed. "
               "Read your worktree's commit from the root, with git show <commit>:<file>.")
    command = arguments[0]
    if command == "worktree":
        refuse("make and remove your worktree with python3 -I scripts/maintainer_checkout.py.")
    options = GIT_COMMANDS.get(command)
    if options is None:
        refuse("git " + command + " isn't one of the reading commands: "
               + ", ".join(sorted(GIT_COMMANDS)) + ".")
    positionals = options.check("git " + command, arguments[1:])
    if command == "fetch":
        if not positionals or positionals[0] != "origin" or len(positionals) > 2:
            refuse("git fetch may only fetch from origin, and at most one branch.")
        if len(positionals) == 2:
            branch = positionals[1]
            if not BRANCH_NAME.fullmatch(branch) or ".." in branch or branch == "tag":
                refuse("git fetch may only name a branch, with no refspec or tag.")


def check_gh(arguments):
    if not arguments:
        refuse("gh needs a command.")
    if any(GH_ENVIRONMENT.search(argument) for argument in arguments):
        refuse("gh's --jq and --template may not read the environment, with env, $ENV or "
               "$__loc__.")
    if arguments[0] == "api":
        key, rest = ("api", None), arguments[1:]
    else:
        key, rest = tuple(arguments[:2]), arguments[2:]
    options = GH_COMMANDS.get(key)
    if options is None:
        refuse("gh may only read: pr view, diff, checks, list and status, issue view, list and "
               "status, run view and list, release view and list, repo view, search, workflow "
               "view and list, and api without write options.")
    options.check("gh " + " ".join(word for word in key if word), rest)


def check_python(arguments, cwd, root):
    """Allows only the root's own scripts/maintainer_checkout.py, which checks its arguments."""
    if arguments[:2] != ["-I", "scripts/maintainer_checkout.py"] or cwd != root:
        refuse("python3 may only run python3 -I scripts/maintainer_checkout.py, from the "
               "repository's root.")
    arguments = arguments[1:]
    rest = arguments[1:]
    if not rest or rest[0] not in CHECKOUT_COMMANDS:
        refuse("maintainer_checkout.py takes add, gate, dependencies or remove.")
    if rest[0] == "add":
        if len(rest) != 2 or not rest[1].isdigit():
            refuse("maintainer_checkout.py add takes a pull request number.")
    elif len(rest) < 2 or not worktree_root(resolved(rest[1], cwd), root):
        refuse("maintainer_checkout.py " + rest[0] + " takes your worktree's path first.")


def check_subcommand(words, cwd, root):
    program, arguments = words[0], words[1:]
    if program == "git":
        check_paths(arguments, cwd, root)
        check_git(arguments)
    elif program == "gh":
        check_gh(arguments)
    elif program == "python3":
        check_paths(arguments, cwd, root)
        check_python(arguments, cwd, root)
    elif program in READING_PROGRAMS:
        check_paths(arguments, cwd, root)
        options = READING_PROGRAMS[program]
        if options is not None:
            options.check(program, arguments)
    else:
        refuse(program + " isn't on the list of reading commands: git, gh, python3, "
               + ", ".join(sorted(READING_PROGRAMS)) + ". There is no cd: name your worktree's "
               "files by their paths, or read them with git show <commit>:<file>.")


def check_command(command, cwd, root):
    """Refuses a Bash command that the rules above don't allow."""
    parts = split_command(command)
    for words in parts:
        check_subcommand(words, cwd, root)


def check_file_tool(tool, tool_input, cwd, root):
    """Refuses a Read, Grep or Glob outside the repository."""
    if tool == "Read":
        path = resolved(str(tool_input.get("file_path") or ""), cwd)
        if not is_inside(path, root) and not SCRATCHPAD.match(path):
            refuse("Read may open only files in the repository, or a plan in a scratchpad folder.")
        return
    path = resolved(str(tool_input.get("path") or "."), cwd)
    if not is_inside(path, root):
        refuse(tool + " may search only inside the repository.")
    pattern = str(tool_input.get("pattern") or "") if tool == "Glob" else ""
    if pattern.startswith(("/", "~")) or ".." in pattern.split("/") or "{" in pattern:
        refuse("Glob's pattern must stay inside its folder, with no .. or braces.")


def check(call):
    """Raises Refused for [call], a hook's tool call, unless the guard allows it."""
    tool = call.get("tool_name")
    tool_input = call.get("tool_input") or {}
    cwd = os.path.realpath(call.get("cwd") or os.getcwd())
    root = os.path.realpath(os.environ.get("CLAUDE_PROJECT_DIR") or cwd)
    if tool == "Bash":
        check_command(str(tool_input.get("command") or ""), cwd, root)
    elif tool in ("Read", "Grep", "Glob"):
        check_file_tool(tool, tool_input, cwd, root)


def main():
    try:
        if sys.version_info < (3, 9):
            refuse("the guard needs Python 3.9 or later.")
        check(json.load(sys.stdin))
    except Refused as refusal:
        print(refusal, file=sys.stderr)
        return 2
    except BaseException as error:
        # The guard fails closed: a call that it couldn't check is refused.
        print("Blocked for the maintainer subagent: the guard failed, "
              + type(error).__name__ + ".", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
