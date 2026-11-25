# Spec Delta

## ADDED Requirements

### Requirement: The user can download a video
The app SHALL let the user download a video that isn't a live stream and isn't waiting to start, from the video screen and from the inbox. A download SHALL keep one video format and one audio format of the video in the app's own storage, or one format that has both, chosen as the player chooses the formats on an unmetered network, but with a shorter side of at most 720 pixels, and only formats the phone can decode. A download SHALL start right away on any connection, mobile data included, whether or not the phone charges, because the user asked for it. A video whose formats the phone can't decode SHALL fail with "Download failed". Asking again for a video that is waiting, downloading or downloaded SHALL change nothing.

#### Scenario: Downloading from the video screen
- **WHEN** the user taps "Download" on a video's screen, and the phone stays connected
- **THEN** the video downloads, and the screen says "Downloaded" once both files are on the phone

#### Scenario: Asking twice
- **WHEN** the user taps "Download" for a video in the inbox, then opens the video and taps the chip while it downloads
- **THEN** one download runs, and the tap on the chip pauses it

#### Scenario: Quality
- **WHEN** a video has video formats with a shorter side of 360, 720 and 1080 pixels, and the phone decodes all of them
- **THEN** the download keeps the 720 pixel one

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

#### Scenario: Stopped from the Task Manager
- **WHEN** a download is at 40 percent and the user stops Dewfall from Android's Task Manager, then opens the app again
- **THEN** the video says "Paused, 40%", and goes on from about 40 percent only when the user taps its download button

#### Scenario: Swiped away from the recent apps
- **WHEN** a download waits for a connection and the user swipes Dewfall away from the recent apps, then the connection comes back
- **THEN** the download goes on by itself, and the video never says "Paused"

#### Scenario: The phone restarts
- **WHEN** a download is at 40 percent and the phone restarts
- **THEN** the download goes on from about 40 percent once the phone is connected, without the user opening the app

### Requirement: A download can be paused and go on
Tapping a running download's control SHALL pause it, and the files it has so far SHALL stay. A paused download SHALL say "Paused" with how far it got, and SHALL go on only when the user taps the download control again, from the bytes it already has. Pausing a download SHALL let the next one in the queue start.

#### Scenario: Pausing
- **WHEN** a download is at 42 percent and the user taps its progress ring
- **THEN** it stops within a few seconds, says "Paused, 42%", and stays paused after the app is closed and opened again

#### Scenario: Going on
- **WHEN** the user taps the download button of a video paused at 42 percent
- **THEN** it says "Waiting to download" or "Downloading, 42%", and goes on from there

### Requirement: A download that can't finish says why
A download SHALL fail, keep no partial files, and say "Download failed" when YouTube says the video can't be watched, or when the phone decodes none of its formats. It SHALL say "Download failed" and keep its partial files when YouTube can't be read for it after three tries in a row, because that may pass. It SHALL say "Not enough space on this phone" and keep its partial files when the phone's free storage can't hold the rest of the files. A failed download SHALL show the download button, and a tap SHALL try again, from the kept bytes when there are any. A link that YouTube refuses because it expired SHALL be fetched again without failing the download.

#### Scenario: A private video
- **WHEN** the user downloads a video that becomes private before its download starts
- **THEN** the video says "Download failed" and the phone keeps none of its files

#### Scenario: A full phone
- **WHEN** the phone has less free space than the rest of the video's files
- **THEN** the video says "Not enough space on this phone", and a tap on the download button after space was freed goes on from the bytes it had

#### Scenario: An expired link
- **WHEN** a paused download goes on the next day, after its links expired
- **THEN** the app fetches new links and goes on from where it stopped

### Requirement: A notification shows while a download runs
While a download runs in a job that Android gave a notification to, the app SHALL give it Dewfall's download notification, in its own notification channel named "Downloads", with the words "Downloading a video" and its progress. Android SHALL show it in the notification shade when the user allows Dewfall's notifications. The app SHALL NOT ask for that permission, so on Android 13 and later, where a new install starts without it, Android shows the download only in its Task Manager. A download that WorkManager starts while the app is in the background on Android 12 or 13, such as after a reboot, may run with no notification, because Android may refuse it a foreground service there. The notification SHALL hold no video title, channel name or ID, SHALL go when no download runs, and tapping it SHALL open the app.

#### Scenario: While downloading, notifications allowed
- **WHEN** the user allowed Dewfall's notifications, a video downloads, and the user pulls down the notification shade
- **THEN** a notification says "Downloading a video" with a progress bar, and shows no title

#### Scenario: While downloading, notifications not allowed
- **WHEN** Dewfall's notifications aren't allowed on Android 14 or later, and a video downloads
- **THEN** the download runs, the notification shade shows nothing for it, and Android's Task Manager lists Dewfall as running

#### Scenario: Nothing left to download
- **WHEN** the last download finishes
- **THEN** the download notification goes away

### Requirement: Downloads stay private
A download SHALL send requests only to YouTube, through the YouTube layer: its stream links call, and requests for the files' bytes, which SHALL follow the same rules as playback's: only HTTPS to googlevideo.com and its subdomains, with every redirect's host checked. The files SHALL live in the app's own storage, where no other app can read them and backups and phone transfers leave them out, under names that hold no video ID. The app SHALL never log a video's ID, title, link or file name for a download. WorkManager writes its own log lines, which the app can't turn off, so the work the app gives it SHALL carry no video ID, title, link or file name.

#### Scenario: Logs while downloading
- **WHEN** the user downloads a video, pauses it, goes on, and it finishes
- **THEN** the app's log lines, WorkManager's included, hold no ID, title, link or file name

#### Scenario: Where the files are
- **WHEN** a video is downloaded
- **THEN** its files are inside the app's own storage, and their folder's name is not the video's ID
