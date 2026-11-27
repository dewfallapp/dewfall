# Spec Delta

## ADDED Requirements

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
