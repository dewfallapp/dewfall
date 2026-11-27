## MODIFIED Requirements

### Requirement: Downloads run one at a time without any screen
Downloads SHALL run in a background job that no screen owns, one video at a time, in the order the user asked for them. A download SHALL go on after the user leaves the screen it started from, leaves the app or turns the screen off. A download that stopped because the app's process ended, the phone restarted or the connection dropped SHALL go on by itself from the bytes it already has, once the phone is connected again. A download that Android refused to schedule, such as one asked for just as the app left the screen, SHALL go on the next time the app shows. When the user stops Dewfall while a download runs, such as with the Stop button in Android's Task Manager or Force stop in its settings, every download that was waiting or running SHALL count as paused, and SHALL go on only when the user taps its download control; on Android 10, which can't tell the app such a stop apart, it SHALL go on the next time the app shows. Updating the app SHALL NOT pause a download, and on stock Android neither SHALL swiping Dewfall away from Android's recent apps; on a phone whose maker ends a running download's process on that swipe, the download SHALL count as paused, as after a Stop. While no download can run, its video SHALL say "Waiting to download".

#### Scenario: Leaving the screen
- **WHEN** the user taps "Download", goes back to the inbox and opens another video
- **THEN** the first video goes on downloading

#### Scenario: Two downloads
- **WHEN** the user asks for two videos to download, one after the other
- **THEN** the first downloads while the second says "Waiting to download", and the second starts when the first is done

#### Scenario: The connection drops
- **WHEN** a download is at 40 percent and the phone loses its connection, then gets it back
- **THEN** the video says "Waiting to download" while the phone has no connection, and goes on from about 40 percent once it is back, without fetching the first 40 percent again

#### Scenario: The app's process ends
- **WHEN** a download is at 40 percent, the app isn't showing, and Android ends the app's process
- **THEN** the download goes on from about 40 percent by itself, within about a minute while the phone is awake, without the user opening the app or turning the screen on

#### Scenario: Stopped from the Task Manager
- **WHEN** a download is at 40 percent and the user stops Dewfall from Android's Task Manager, then opens the app again
- **THEN** the video says "Paused, 40%", and goes on from about 40 percent only when the user taps its download button

#### Scenario: Swiped away from the recent apps
- **WHEN** a download waits for a connection and the user swipes Dewfall away from the recent apps, then the connection comes back
- **THEN** the download goes on by itself, and the video never says "Paused"

#### Scenario: The phone restarts
- **WHEN** a download is at 40 percent and the phone restarts
- **THEN** the download goes on from about 40 percent once the phone is connected, without the user opening the app

### Requirement: A notification shows while a download runs
While a download runs in a job that Android gave a notification to, the app SHALL give it Dewfall's download notification, in its own notification channel named "Downloads", with the words "Downloading a video" and its progress. Android SHALL show it in the notification shade when the user allows Dewfall's notifications. The app SHALL NOT ask for that permission, so on Android 13 and later, where a new install starts without it, Android shows the download only in its Task Manager. A download that WorkManager starts while the app is in the background on Android 12 or 13, such as after a reboot, may run with no notification, because Android may refuse it a foreground service there. On Android 14 and later, a download that goes on by itself after the app's process ended may run in a backup job, because Android doesn't always start its user-initiated job again by itself, and Android gives a backup job no notification and doesn't list it in its Task Manager. While a backup job downloads, the app SHALL show the same notification itself when the user allows Dewfall's notifications, SHALL NOT show it again during that run once the user swipes it away, and SHALL take it away when the run ends or the user-initiated job takes the download back. If the process ends during a backup run, its notification may stay until the next run replaces it or takes it away. Apart from that, the notification SHALL go when no download runs. It SHALL hold no video title, channel name or ID, and tapping it SHALL open the app.

#### Scenario: While downloading, notifications allowed
- **WHEN** the user allowed Dewfall's notifications, a video downloads, and the user pulls down the notification shade
- **THEN** a notification says "Downloading a video" with a progress bar, and shows no title

#### Scenario: While downloading, notifications not allowed
- **WHEN** Dewfall's notifications aren't allowed on Android 14 or later, and a video downloads
- **THEN** the download runs, the notification shade shows nothing for it, and Android's Task Manager lists Dewfall as running

#### Scenario: Going on after the process ended
- **WHEN** on Android 14 or later, the user allowed Dewfall's notifications, the app's process ended during a download, and the download went on by itself in a backup job without the app showing
- **THEN** a notification says "Downloading a video" with a progress bar, and Android's Task Manager doesn't list the backup job

#### Scenario: Swiping away the backup job's notification
- **WHEN** a download goes on in a backup job on Android 14 or later, and the user swipes its notification away
- **THEN** the notification doesn't come back while that run goes on

#### Scenario: Nothing left to download
- **WHEN** the last download finishes
- **THEN** the download notification goes away
