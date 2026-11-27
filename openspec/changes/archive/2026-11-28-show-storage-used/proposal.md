# Proposal

## Why

Phase 6's tasks include a "Storage indicator showing how much space downloads use" ([roadmap](../../../../docs/roadmap.md#phase-6-manual-downloads), [#57](https://github.com/dewfallapp/dewfall/issues/57)). Downloaded videos take hundreds of megabytes each, and today nothing in the app says how much space they take together, so the user can't tell when to remove some. The design puts a storage meter at the top of the Downloads screen, which #55 builds.

## What Changes

- **An "Offline storage" card at the top of the Downloads screen.** Under the top app bar and above the list of downloads, as the "Downloads" frames and the design system's Downloads order show. It says "Offline storage" and the space downloads take, such as "3.6 GB".
- **No bar and no limit yet.** The frames show "3.6 GB / 10 GB" with a bar, where 10 GB is the storage limit. The storage limit arrives in Phase 7, so until then the card shows only the space used, with no bar, as the maintainer chose (answer 1). Phase 7's storage limit change adds "/ 10 GB" and the bar.
- **Every byte counts.** The amount is every byte that downloads keep on the phone: the files of finished downloads, and the parts that waiting, downloading, paused and failed downloads keep, as the maintainer chose (answer 2). So it is the space Dewfall's downloads really take, and it grows while a download runs. It adds up the bytes that the download job records for each download. The job writes them at the end of each range, and about once a second between them while bytes arrive. So a download that stops between two writes can count up to about a second of downloading less than its files hold, until it runs again. That happens when the app ends, the download pauses or the connection drops.
- **It follows the downloads.** The amount changes by itself while a download runs, and when one is removed, with no need to leave and open the screen again. If the phone can't read it, the card shows its heading with no amount, and the rest of the screen stays as it is.
- **The inbox has no meter.** The design keeps storage out of the inbox, so nothing changes there.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `downloads`: a new requirement for the storage card on the Downloads screen.

## Impact

- `:core:database`: DownloadDao gets `bytesInUse()`, the sum of every download row's bytes on disk, with DAO tests. No schema change.
- `:core:data`: DownloadsRepository gets `bytesInUse()`, a Flow of that sum, with tests. DownloadFiles doesn't change.
- #55's Downloads screen modules (`:feature:downloads-logic` and `:feature:downloads-ui`, or the names #55 gives them): the screen state gets the bytes in use, read only while the screen shows, with two intents, their reducer cases and their tests; the screen gets the card, its strings, previews and screenshot tests.
- No new dependency, no schema change, no new network call, nothing new logged.
- docs/architecture.md: the Downloads screen section and DownloadsRepository's list name the storage card and `bytesInUse()`, and the "#57, the storage indicator" line says it counts from the rows. DownloadFiles' KDoc no longer says #57 adds up its files' sizes. docs/testing.md: the card's hand check.
- One pull request, built on top of #55 once #55 merges. It closes #57 and archives this change as its last commit.

## The maintainer's answers

Given on November 27, 2026, in the question dialog of #57's Claude Code session.

1. Question: "#57 storage meter: the design shows "Offline storage 3.6 GB / 10 GB" with a bar, where 10 GB is the storage limit. That limit only arrives in Phase 7. What should the meter show in Phase 6?" Answer: "Used only, no bar (Recommended)", the option that said: ""Offline storage" and "3.6 GB" in the design's card, with no bar. Phase 7 adds "/ 10 GB" and the bar with the limit."
2. Question: "#57 storage meter: which bytes count in the total?" Answer: "Every byte on the phone (Recommended)", the option that said: "Finished downloads plus the parts that waiting, paused and failed downloads keep, so the number is the space Dewfall really takes."
3. Question: "#57: approve the OpenSpec change show-storage-used? An "Offline storage" card at the top of the Downloads screen says the space downloads take, such as "3.6 GB": finished files plus kept parts, no bar until Phase 7. It updates while shown; if unreadable, it shows the heading with no amount. Plan check: Verdict Build. Built after #55 merges." Answer: "Approve (Recommended)", the option that said: "Approve openspec/changes/show-storage-used as written. Building starts once #55's pull request merges."
4. Given on November 28, 2026, in the same question dialog. Question: "#57 storage card: YouTube blocks this network, so I can't check by hand that the amount grows during a real download and stays after a pause. Everything else is checked (tests, and seeded rows on Android 10 and 17). Can #57 close with that one check added to #258's real-video list?" Answer: "Move it to #258 (Recommended)", the option that said: "Close #57 now. I add "the storage amount grows during a real download and stays after a pause" to #258, which runs once YouTube answers this network."
