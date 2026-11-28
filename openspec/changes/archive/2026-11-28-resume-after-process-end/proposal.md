# Proposal

## Why

On Android 14, when the app's process ends during a download, the download doesn't go on by itself ([#269](https://github.com/dewfallapp/dewfall/issues/269)). It waits until the screen turns off and on or the app opens, 4 to 7 minutes in #258's hand checks. The requirement "Downloads run one at a time without any screen" says it SHALL go on by itself.

The cause is in Android's JobScheduler, not in Dewfall's job. Android counts the end of the process as a system stop. For the first two system stops it gives the rescheduled job no backoff, so the job has no timing delay, and nothing makes JobScheduler look at it again until some other event does. Android 13 always gave a backoff, which is why WorkManager's job there goes on 20 to 45 seconds later. A user-initiated data transfer (UIDT) job can only be scheduled while the app is visible, so the app can't schedule it again from the background. design.md has the sources.

## What Changes

- **A backup job.** While the UIDT job downloads on Android 14 and later, the app keeps an ordinary JobScheduler job armed about 30 seconds ahead. When the process ends, that job starts about 30 seconds later and goes on with the download from its bytes, with the same runner. When the UIDT job starts again, or the app shows, the UIDT job takes the download back.
- **The notification during a backup run.** While a backup job downloads, it gives Android the same "Downloading a video" notification as the UIDT job, which Android shows when the user allows Dewfall's notifications, as the maintainer chose (answers 2 and 3). Android takes it away when the run ends or its process dies, and doesn't list a backup job in its Task Manager. Where Android lets the user swipe it away, it doesn't come back during that run; on the Android 14 emulator Android kept it as a user-initiated job's, so a swipe didn't dismiss it. Without the permission, nothing shows.
- **Shorter runs.** Android 14 lets an ordinary job run for 10 to 30 minutes, depending on charging and the app's standby bucket. A long download in a backup job may stop partway through, and go on from its bytes in the next run, as expedited work already does on Android 10 to 13.
- Android 10 to 13 keep WorkManager's worker, unchanged.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `downloads`: "Downloads run one at a time without any screen" gets a scenario for the end of the process. "A notification shows while a download runs" says how the backup job shows the notification itself, on Android 14 and later.

## Impact

- `:core:download`: DownloadJobService handles two more job IDs, 55 and 56, for the backup jobs, and keeps one armed during each run. QueueScheduler hands a backup run back to the UIDT job when the app shows or the user taps a download control. DownloadRunState gets a flag for a backup run. DownloadsAfterStop tells a swipe from the recent apps during a backup run, which Android 14 answers by ending the process, from Force stop, with a marker file that a backup run writes and a check for a scheduled backup job, so the swipe doesn't pause the download, as the spec already asks. New JVM tests, and a check in `:app`'s DownloadTest that no backup job stays after a download.
- No new dependency, permission, network call or schema change. No new log line holds user data.
- ADR 0010 gets a dated note. docs/architecture.md and docs/testing.md describe the backup jobs and the hand check.
- One pull request that closes #269 and archives this change as its last commit.

## The maintainer's answers

1. Given on November 28, 2026, in #269's Claude Code session's chat. Question, sent through the coordinating session: "#269: after the process ends mid-download on Android 14+, should the download go on by itself in a background job that shows no notification or Task Manager entry until the app shows again?" with the options "(1) Yes, go on without the notification (Recommended); (2) No, keep waiting for the app or screen, and change the spec." Answer: "go with your recommendation"
2. Given on November 28, 2026, in the coordinating session's Claude Code chat, correcting answer 1. The coordinating session first asked, word for word:

   > #269 checked the third option, and it works with some catches. Here are your three choices, for when the app is killed during a download on Android 14 or later:
   >
   > 1. **Go on in the background with no notification.** It stays hidden until the app opens or Android restarts the main download job.
   > 2. **Go on and show the usual progress notification, when you've allowed notifications.** This is my recommendation: the download is never hidden. The catches:
   >    - After the app is killed, the old notification can stay up for about 30 seconds until the backup job replaces or clears it. That bends the spec's rule that the notification goes when no download runs.
   >    - You can swipe it away, since it's an ordinary notification.
   >    - It doesn't show if notifications aren't allowed, which is the default on a new install.
   >    - It never appears in Task Manager.
   > 3. **Don't go on.** Wait until the app opens or the screen turns on, and change the spec to say so.
   >
   > Options 1 and 2 share one limit: on battery, Android may stop the backup job after about 10 minutes. It then goes on from the bytes it has, after a short wait. When the app opens, the main download job takes over again with its full notification. That matches how Android 10 to 13 already behave, as ADR 0010 says.
   >
   > Which one do you want: 1, 2 or 3?

   The maintainer gave no reply there before answer 1. After answer 1, the coordinating session asked, word for word:

   > You answered #269 in its own session with "go with your recommendation". #269's own recommendation was **option 1**: the download goes on in a backup job, with no notification and no Task Manager entry until the app shows again. #269 has gone ahead with that.
   >
   > My recommendation here was **option 2**, the same with the usual progress notification when notifications are allowed. If you meant option 2, tell me and I'll pass it on before #269 builds much. Otherwise option 1 stands.

   Answer: "I meant your recommendation. Go with yours. Tell it to stop for option 1."
3. Given on November 28, 2026, in the question dialog of #269's Claude Code session. Question: "#269: approve the revised OpenSpec change resume-after-process-end? On Android 14+, a download whose process ended goes on by itself in a backup job within about a minute. While it runs, the app posts the usual "Downloading a video" notification itself when notifications are allowed; it won't come back that run once swiped away; it goes when the run ends or the UIDT job takes over; a kill mid-backup-run can leave it up about 30 s; the Task Manager doesn't list the backup job." Answer: "Approve (Recommended)", the option that said: "Approve openspec/changes/resume-after-process-end as now written, replacing the earlier approval of the no-notification version. Building starts after the maintainer subagent's Verdict: Build."

An earlier approval in the same dialog, of the version with no notification, is replaced by answer 3.

Note, November 28, 2026: after answer 3, CodeRabbit's review of #271 led to two narrower wordings in the spec, which change no behavior the maintainer chose. "The app's process ends" now says the download goes on within about a minute "while the phone is awake and connected", because it can't go on without a connection, as the requirement already says. "While downloading, notifications not allowed" now covers a download in the user-initiated job only, and the requirement says that without the permission Android shows a backup job's download nowhere, because Android doesn't list a backup job in its Task Manager. The maintainer confirmed them on November 28, 2026, in the coordinating session's question dialog, with "Yes, approved (Recommended)", as pull request #271 records.
