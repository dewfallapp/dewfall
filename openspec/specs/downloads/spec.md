# downloads Specification

## Purpose
Keeps a video's files on the phone so it plays without internet: a download the user starts from the video screen or the inbox, which runs in a background job that no screen owns, one video at a time, goes on after the process ends, the phone restarts or the connection drops, and says why when it can't finish.

## Requirements

### Requirement: The user can download a video
The app SHALL let the user download a video that isn't a live stream and isn't waiting to start, from the video screen and from the inbox. A download SHALL keep one video format and one audio format of the video in the app's own storage, or one format that has both, chosen as the player chooses the formats on an unmetered network, but with a shorter side of at most 720 pixels, and only formats the phone can decode. A download SHALL start right away on any connection, mobile data included, whether or not the phone charges, because the user asked for it. A video whose formats the phone can't decode SHALL fail with "Download failed". Asking again for a video that is waiting, downloading or downloaded SHALL change nothing.

#### Scenario: Downloading from the video screen
- **WHEN** the user taps "Download" on a video's screen, and the phone stays connected
- **THEN** the video downloads, and the screen says "Downloaded" once every file it keeps is on the phone

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

### Requirement: The Downloads screen lists every download
The Downloads screen SHALL list every download in two sections, each in one card. "Downloading" SHALL hold every download that waits, runs, is paused or failed, in the order the user asked for them. "On this phone" SHALL hold every finished download, the most recently finished first. A section with no download SHALL not show. Showing the screen SHALL send no request to YouTube, apart from the thumbnails, as the list-images spec says.

#### Scenario: Both sections
- **WHEN** one video downloads, another waits, and two finished downloads are on the phone
- **THEN** "Downloading" lists the running one, then the waiting one, and "On this phone" lists the two finished ones, the one that finished last first

#### Scenario: Only finished downloads
- **WHEN** every download has finished
- **THEN** the screen shows "On this phone" and no "Downloading" section

#### Scenario: A download finishes while the screen shows
- **WHEN** the Downloads screen shows and a running download finishes
- **THEN** its row leaves "Downloading" and shows at the top of "On this phone", without the user doing anything

### Requirement: The Downloads screen's top bar
The Downloads screen's top bar SHALL show "Dewfall", "Downloads" under it, and a search button, which a screen reader SHALL hear as "Search YouTube" and which opens Search YouTube. It SHALL have no back arrow and no menu button.

#### Scenario: The top bar
- **WHEN** the user opens the Downloads screen
- **THEN** the top bar says "Dewfall" and "Downloads", and has a search button and no back arrow

### Requirement: A row of an unfinished download
A row in "Downloading" SHALL show the video's thumbnail with its length, its title, its channel name, a progress bar once the download's size is known, a status line and two buttons. A running download SHALL say "42%, 344 of 820 MB", or "Downloading" while its size is unknown, with "Pause" and "Cancel". A waiting one SHALL say "Waiting to download", with "Pause" and "Cancel". A paused one SHALL say "Paused, 42%", with "Resume" and "Cancel".

#### Scenario: A running download
- **WHEN** a 820 MB download has 344 MB on the phone
- **THEN** its row shows a progress bar at 42 percent, "42%, 344 of 820 MB", "Pause" and "Cancel"

#### Scenario: A paused download
- **WHEN** the user paused a download at 42 percent
- **THEN** its row says "Paused, 42%", with "Resume" and "Cancel"

### Requirement: A failed download's row
A failed download's row SHALL show the error glyph and "Download failed", or "Not enough space on this phone" when that is why, with "Try again" and "Cancel". Only the glyph SHALL use the error color. "Try again" SHALL go on from the bytes the download kept, as a tap on the download button does.

#### Scenario: A full phone
- **WHEN** a download failed because the phone's storage was full
- **THEN** its row shows the error glyph, "Not enough space on this phone", "Try again" and "Cancel"

### Requirement: A finished download's row
A row in "On this phone" SHALL show the video's thumbnail with its length, its title, its channel name, the size of its files, such as "940 MB" or "1.2 GB", and a "Remove download" button. Sizes SHALL count 1,000 bytes to a kilobyte, as Android's storage settings do, with one decimal under 10 of a unit.

#### Scenario: A finished download
- **WHEN** a finished download's files take 940,000,000 bytes
- **THEN** its row says "940 MB", with a "Remove download" button

### Requirement: Pause and Resume on the Downloads screen
"Pause" SHALL pause a running or waiting download, keep its bytes, and let the next one in the queue start, as the download control does. "Resume" SHALL put a paused download back in the queue, at its end, to go on from the bytes it has. A paused download SHALL go on only when the user taps "Resume" or its download control elsewhere.

#### Scenario: Pausing a waiting download
- **WHEN** one video downloads, a second waits, and the user taps the second one's "Pause"
- **THEN** the second says "Paused", and stays paused when the first finishes

#### Scenario: Resuming
- **WHEN** the user taps "Resume" on a download paused at 42 percent
- **THEN** it says "Waiting to download" or "Downloading", and goes on from about 42 percent

### Requirement: Cancelling a download
"Cancel" SHALL end the download, whatever its state, and delete every file it has so far, without asking first. A running download SHALL stop within a few seconds, and the next one in the queue SHALL start. The video SHALL then show the "Download" button wherever it shows a download control.

#### Scenario: Cancelling a running download
- **WHEN** a download is at 42 percent and the user taps its "Cancel"
- **THEN** its row goes, its files are deleted from the phone, the next waiting download starts, and the video's screen shows "Download"

#### Scenario: Cancelling a failed download
- **WHEN** a download failed with its partial files kept and the user taps "Cancel"
- **THEN** its row goes and its partial files are deleted

### Requirement: Removing a download
"Remove download" SHALL delete a finished download's files, without asking first, and show the message "Download removed". The video SHALL then show the "Download" button wherever it shows a download control, and downloading it again SHALL fetch it anew.

#### Scenario: Removing a finished download
- **WHEN** the user taps "Remove download" on a finished download
- **THEN** its row goes, "Download removed" shows, its files are deleted from the phone, and the inbox row of that video shows its download button

### Requirement: No files stay behind
Once a download is cancelled or removed, the phone SHALL keep none of its files, also when the app's process ends in between: files that no download owns any more SHALL be deleted the next time downloads run.

#### Scenario: The process ends during a cancel
- **WHEN** the app's process ends after a download's row went but before its files were deleted, and the user later downloads another video
- **THEN** the first download's files are deleted before the new download starts

### Requirement: Tapping a row opens the video
Tapping a row of the Downloads screen, outside its buttons, SHALL open that video's video screen, with the Downloads screen underneath, so going back shows it as it was. Tapping a second time while the video screen opens SHALL NOT open it twice. A screen reader SHALL hear each row's text as one item, and each of its buttons as a separate one.

#### Scenario: Opening a finished download
- **WHEN** the user taps a row in "On this phone"
- **THEN** that video's screen opens, and one back gesture returns to the Downloads screen at the same scroll position

### Requirement: The Downloads screen says when there are no downloads
When the phone keeps no download, the Downloads screen SHALL show the download glyph in a round disc, then "No downloads yet", then "Download a video from its screen or your inbox to keep it on this phone." It SHALL show no button and SHALL NOT suggest videos.

#### Scenario: No downloads
- **WHEN** the user has never downloaded a video and opens the Downloads screen
- **THEN** it says "No downloads yet" and "Download a video from its screen or your inbox to keep it on this phone."

### Requirement: The Downloads screen says when it can't be read
When the app can't read what it keeps on the phone for the Downloads screen, it SHALL show the error glyph, "Your downloads can't be shown" and "Dewfall couldn't read what it keeps on this phone. Restarting the app may help.", and SHALL NOT crash. Its search button and the navigation bar SHALL still work. When the screen shows again after its read stopped, it SHALL read again.

#### Scenario: A damaged database
- **WHEN** the downloads can't be read and the user opens the Downloads screen
- **THEN** it says "Your downloads can't be shown", the app stays open, and tapping "Inbox" shows the inbox

### Requirement: A button that can't do its work changes nothing
When a tap on "Pause", "Resume", "Try again", "Cancel" or "Remove download" can't change what the phone keeps, the row SHALL stay as it was, so it shows what the phone holds, and the app SHALL NOT crash. "Download removed" SHALL show only once the download is gone.

#### Scenario: A write that fails
- **WHEN** the database refuses the write of a tap on "Remove download"
- **THEN** the row stays, no "Download removed" shows, and the app stays open

### Requirement: The Downloads screen fits any window
On a phone the Downloads screen SHALL stay in portrait. In any wider window it SHALL keep its content in one centered column of readable width, and its background SHALL fill the window and draw under the system bars. No text or control SHALL sit under the system bars, the display cutout, the mini player or the navigation bar. Text SHALL wrap at large font sizes and never be cut off. It SHALL work in light, dark and dynamic color.

#### Scenario: A large font
- **WHEN** the font size is at its largest
- **THEN** every title, channel name, status line and button label wraps or moves to its own line, and none is cut off

#### Scenario: The mini player
- **WHEN** a video plays in the mini player and the user scrolls the Downloads screen to the end
- **THEN** the last row's button sits fully above the mini player and the navigation bar

### Requirement: The Downloads screen keeps its state
A configuration change SHALL keep the Downloads screen's scroll position. After the system ends the app's process in the background, the Downloads screen SHALL come back with what the phone keeps, and its scroll position. The state Android saves for it SHALL hold no video ID, title or channel name.

#### Scenario: A theme change
- **WHEN** the user scrolled down the Downloads screen and the theme changes
- **THEN** the screen shows the same downloads at the same scroll position

### Requirement: The Downloads screen keeps downloads private
The Downloads screen SHALL never log a video's ID, title or file name, or a channel's name, and SHALL send nothing to any server, apart from the requests of a download that the user started or resumed, which go only to YouTube, as "Downloads stay private" says, and the thumbnails, as the list-images spec says.

#### Scenario: Opening the Downloads screen offline
- **WHEN** the phone has no connection and the user opens the Downloads screen
- **THEN** it lists the downloads as the phone keeps them, with the thumbnails that are in the app's cache, and no request goes to YouTube

### Requirement: The Downloads screen shows the space downloads take
The Downloads screen SHALL show a card at its top, under the top app bar and above the list of downloads, that says "Offline storage" and the space that downloads take on the phone, such as "3.6 GB", in the phone's own units and number format. Until the app has a storage limit, the card SHALL show no bar and no limit. A screen reader SHALL read the card as one item, with its heading and the space. The inbox SHALL NOT show the space.

#### Scenario: Opening the Downloads screen
- **WHEN** the user opens the Downloads screen with 3.6 GB of downloads on the phone
- **THEN** the first thing under the top app bar is a card that says "Offline storage" and "3.6 GB", with no bar

#### Scenario: With a screen reader
- **WHEN** TalkBack moves to the card
- **THEN** it reads "Offline storage" and the space as one item

### Requirement: The space counts every byte downloads keep
The space on the storage card SHALL count every byte that downloads keep, as the download job last recorded them: finished downloads' files, and the partial files that waiting, downloading, paused and failed downloads keep. It SHALL change by itself while the screen shows, as downloads grow or go. With nothing downloaded it SHALL say zero. When the phone can't read the space, the card SHALL show its heading with no amount.

#### Scenario: Finished and partial downloads
- **WHEN** one video is downloaded with 2.5 GB of files, and another is paused with 1.1 GB of its files on the phone
- **THEN** the card says "3.6 GB"

#### Scenario: While a download runs
- **WHEN** the Downloads screen is open and a download runs
- **THEN** the space on the card grows as the download's files grow, without leaving the screen

#### Scenario: A download stops between two records of its bytes
- **WHEN** a download stops between two of the download job's records of its bytes, such as when the app ends, the download pauses or the connection drops
- **THEN** the card counts that download's last recorded bytes, which can be up to about a second of downloading less than its files hold, until the download runs again

#### Scenario: A failed download that keeps nothing
- **WHEN** a download fails because the video became private, and its partial files are deleted
- **THEN** the space on the card no longer counts them

#### Scenario: Nothing downloaded
- **WHEN** the user opens the Downloads screen before downloading any video
- **THEN** the card says "Offline storage" and a space of zero

#### Scenario: The space can't be read
- **WHEN** the phone can't read the space downloads take
- **THEN** the card shows "Offline storage" with no amount, and the rest of the Downloads screen stays as it is
