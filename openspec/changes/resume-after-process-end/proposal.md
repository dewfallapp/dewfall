# Proposal

## Why

On Android 14, when the app's process ends during a download, the download doesn't go on by itself ([#269](https://github.com/dewfallapp/dewfall/issues/269)). It waits until the screen turns off and on or the app opens, 4 to 7 minutes in #258's hand checks. The requirement "Downloads run one at a time without any screen" says it SHALL go on by itself.

The cause is in Android's JobScheduler, not in Dewfall's job. Android counts the end of the process as a system stop. For the first two system stops it gives the rescheduled job no backoff, so the job has no timing delay, and nothing makes JobScheduler look at it again until some other event does. Android 13 always gave a backoff, which is why WorkManager's job there goes on 20 to 45 seconds later. A user-initiated data transfer (UIDT) job can only be scheduled while the app is visible, so the app can't schedule it again from the background. design.md has the sources.

## What Changes

- **A backup job.** While the UIDT job downloads on Android 14 and later, the app keeps an ordinary JobScheduler job armed about 30 seconds ahead. When the process ends, that job starts about 30 seconds later and goes on with the download from its bytes, with the same runner. When the UIDT job starts again, or the app shows, the UIDT job takes the download back.
- **The notification during a backup run.** Android gives an ordinary job no notification and doesn't list it in its Task Manager. So while a backup job downloads, the app posts the same "Downloading a video" notification itself, on the "Downloads" channel, when the user allows Dewfall's notifications, as the maintainer chose (answers 1 and 2). Android doesn't tie it to the job, so the app takes it away itself when the run ends, and when the UIDT job takes the download back. If the process ends during a backup run, the notification can stay up until the next run, about 30 seconds later, replaces it or takes it away. The user can swipe it away, and then it doesn't come back during that run. Without the permission, nothing shows, and the Task Manager never lists a backup job.
- **Shorter runs.** Android 14 lets an ordinary job run for 10 to 30 minutes, depending on charging and the app's standby bucket. A long download in a backup job may stop partway through, and go on from its bytes in the next run, as expedited work already does on Android 10 to 13.
- Android 10 to 13 keep WorkManager's worker, unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `downloads`: "Downloads run one at a time without any screen" gets a scenario for the end of the process. "A notification shows while a download runs" says how the backup job shows the notification itself, on Android 14 and later.

## Impact

- `:core:download`: DownloadNotifications posts and takes away the backup job's notification. DownloadJobService handles two more job IDs, 55 and 56, for the backup jobs, and keeps one armed during each run. QueueScheduler hands a backup run back to the UIDT job when the app shows or the user taps a download control. DownloadRunState gets a flag for a backup run. New JVM tests, and a check in `:app`'s DownloadTest that no backup job stays after a download.
- No new dependency, permission, network call or schema change. No new log line holds user data.
- ADR 0010 gets a dated note. docs/architecture.md and docs/testing.md describe the backup jobs and the hand check.
- One pull request that closes #269 and archives this change as its last commit.

## The maintainer's answers

1. Given on November 28, 2026, in #269's Claude Code session's chat. Question, sent through the coordinating session: "#269: after the process ends mid-download on Android 14+, should the download go on by itself in a background job that shows no notification or Task Manager entry until the app shows again?" with the options "(1) Yes, go on without the notification (Recommended); (2) No, keep waiting for the app or screen, and change the spec." Answer: "go with your recommendation"
2. Given on November 28, 2026, in the coordinating session's Claude Code chat, correcting answer 1. The coordinating session had put the question to the maintainer with a third option, its own recommendation: the backup job shows the usual progress notification on the "Downloads" channel, when Dewfall's notifications are allowed. The maintainer's words: "I meant your recommendation. Go with yours. Tell it to stop for option 1."
3. Given on November 28, 2026, in the question dialog of #269's Claude Code session. Question: "#269: approve the revised OpenSpec change resume-after-process-end? On Android 14+, a download whose process ended goes on by itself in a backup job within about a minute. While it runs, the app posts the usual "Downloading a video" notification itself when notifications are allowed; it won't come back that run once swiped away; it goes when the run ends or the UIDT job takes over; a kill mid-backup-run can leave it up about 30 s; the Task Manager doesn't list the backup job." Answer: "Approve (Recommended)", the option that said: "Approve openspec/changes/resume-after-process-end as now written, replacing the earlier approval of the no-notification version. Building starts after the maintainer subagent's Verdict: Build."

An earlier approval in the same dialog, of the version with no notification, is replaced by answer 3.
