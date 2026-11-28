# Tasks

## 1. The swipe check

- [x] 1.1 DownloadsAfterStop doesn't pause after an exit that Android 11 to 13 describe exactly as "remove task" with a background importance, and still pauses for any other description or a foreground importance. Verified by PausesAfterExitTest and SwipeRunTest in `./gradlew :core:download:testDebugUnitTest`, in pull request #287's first commit.
- [x] 1.2 ADR 0010's note for #281, docs/architecture.md and docs/testing.md describe the check, its sources and its gaps, including the maker's-phone case this change puts in the spec. Verified by the reviewer subagent's Ready on pull request #287.
- [x] 1.3 Hand checks on Android 10, 12L, 13, 14 and 17: a swipe after a restart on 12L and 13 goes on without "Paused", and Force stop and the Task Manager's Stop pause. Recorded in ADR 0010's note for #281 and in pull request #287.

## Workflow follow-up

- Archive this change with /opsx:archive as pull request #287's last commit, after the maintainer approves it, then run the reviewer subagent again on that commit.
