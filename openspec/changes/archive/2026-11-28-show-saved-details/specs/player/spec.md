# Spec Delta

## ADDED Requirements

### Requirement: A downloaded video shows its saved details when YouTube can't be reached
When the video screen's request for the details fails because YouTube can't be reached, and the video has a download in any state, the screen SHALL show the details saved with that download in the failure's place: the title, the publish line counted from the saved publish time, the chip row, and the channel with its emblem and no picture, with no "Live now".

#### Scenario: A downloaded video offline
- **WHEN** the phone has no connection, and the user opens a downloaded video, "How reading rooms were lit before electric lamps" by Larkspur Lectures, published 3 weeks ago, whose files match its download and can all be opened, and whose download state, watch state and saved details can be read
- **THEN** the video plays from its files, the screen shows that title, "Published 3 weeks ago", the "Downloaded" chip, the watched chip and the channel "Larkspur Lectures" with its emblem, and below them that YouTube can't be reached, with "Try again"

#### Scenario: A download that isn't finished
- **WHEN** the phone has no connection, and the user opens a video whose download is paused at 40 percent, and whose download state and saved details can be read
- **THEN** the screen shows the saved details with the "Paused, 40%" chip, and says once, below them, that YouTube can't be reached, with one "Try again" that retries both the details and the video

### Requirement: The saved details show the failure in the description's place
In the description's place, the saved details SHALL show the failure's message, that YouTube can't be reached and to check the connection, with "Try again", which asks YouTube again as after any failure. When YouTube answers, its details SHALL replace the saved ones.

#### Scenario: The connection comes back
- **WHEN** the saved details show, the connection comes back, and the user taps "Try again"
- **THEN** the screen says that the video is loading, then shows YouTube's details with the description

#### Scenario: Still offline
- **WHEN** the saved details show and the user taps "Try again" while the phone still has no connection
- **THEN** the screen says that the video is loading, then shows the saved details again

### Requirement: The saved details count as the details showing
The saved details SHALL count as the details showing for the download chip, the watched chip, and the title and channel name in the media notification, the mini player and fullscreen, as their requirements say. Once a playback has the saved title and channel name, the media notification and the mini player SHALL keep them for the rest of that playback, as they keep YouTube's, until another playback replaces it. That holds even after the download is removed or the saved details stop showing.

#### Scenario: The notification and the mini player offline
- **WHEN** a downloaded video plays offline with its saved details, and the user minimizes it and goes to the home screen
- **THEN** the mini player shows the saved title and channel name, and so does the media notification

#### Scenario: Fullscreen offline
- **WHEN** a downloaded video plays offline with its saved details, and the user enters fullscreen
- **THEN** fullscreen shows the saved title and channel name at the top

### Requirement: Saved details show only for a download when YouTube can't be reached
A failure for any other reason, a video with no download, or a download whose saved details can't be read SHALL show the failure as before. When the download is removed while its saved details show, the screen SHALL show the failure as before.

#### Scenario: A private video
- **WHEN** a downloaded video's details fail because YouTube says the video is private
- **THEN** the screen says that the video is private, as before, and shows no saved details

#### Scenario: A video with no download
- **WHEN** the phone has no connection, and the user opens a video that has no download
- **THEN** the screen says that YouTube can't be reached, with "Try again", as before

#### Scenario: Removing the download
- **WHEN** a video's saved details show, and its download is removed
- **THEN** the screen says that YouTube can't be reached, with "Try again", in the details' place, and the mini player and the notification keep the saved title and channel name while the video plays on

## MODIFIED Requirements

### Requirement: A failure says why
When YouTube can't give the details, the screen SHALL say why in words for each of YouTube's error categories, and SHALL offer "Try again" only when YouTube can't be reached, refuses to answer, or can't be read. "Try again" SHALL show that it is loading and ask once more. When YouTube can't be reached and the video has a download, the screen SHALL also show the details saved with it, as "A downloaded video shows its saved details when YouTube can't be reached" says.

#### Scenario: No connection
- **WHEN** opening a video that has no download fails because YouTube can't be reached
- **THEN** the screen says that YouTube can't be reached and to check the connection, with "Try again"

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a failure
- **THEN** the screen says that the video is loading, and the app asks YouTube once more

#### Scenario: A private video
- **WHEN** opening a video fails because the video is private
- **THEN** the screen says that the video is private, with no "Try again"

#### Scenario: A video that can't be shown
- **WHEN** opening a video fails, and YouTube gives no reason the app can tell apart, such as for a premiere that hasn't started
- **THEN** the screen says that the video isn't available and that YouTube didn't say why, and names removal, age restriction and a start still to come as possible reasons, with no "Try again"

### Requirement: A downloaded video plays from the phone
A video's files match its download when the download recorded the MIME type of each of them, and each is a regular file in the app's download storage, not empty, at the size recorded for it if any, and all together at the recorded total. When playback of a video starts, its download is done and its files match it, the player SHALL play those files instead of streaming. It SHALL ask for no stream links and fetch no bytes, so it plays without a connection. The rest of playback SHALL work as it does for a stream.

#### Scenario: Opening a downloaded video
- **WHEN** the user opens a video that the player doesn't hold, whose download says "Downloaded", whose files match it, and whose files can all be opened
- **THEN** the video plays from the files on the phone, and the app asks YouTube for neither its stream links nor its bytes

#### Scenario: Opening a downloaded video offline
- **WHEN** the phone has no connection, and the user opens from the inbox a downloaded video that the player doesn't hold, whose files match its download and can all be opened, and whose saved details can be read
- **THEN** the video plays from its files, and the screen shows the details saved with its download, with the message that YouTube can't be reached and "Try again" below them

#### Scenario: Paused for hours
- **WHEN** a downloaded video playing from its files is paused for a day, its files still match its download, and the user presses play
- **THEN** it plays on from where it paused, with no request for its stream links or its bytes

### Requirement: A media notification shows while a video plays
While a video plays, and while it is paused until Android stops the player, the app SHALL show a media notification with a play and pause button, and the video's title and channel name once its details have loaded, or once the details saved with its download show. It SHALL show no picture. Tapping it SHALL open the app as it was, on the video screen or on the screen under the mini player. While the video plays, the user SHALL NOT be able to dismiss it.

#### Scenario: Playing in the background
- **WHEN** a video plays and the user goes to the home screen
- **THEN** a notification shows the video's title and channel name with a pause button, and the pause button pauses the video

#### Scenario: Playing in the app
- **WHEN** a video plays on the video screen, or in the mini player
- **THEN** the notification shows too

#### Scenario: Tapping the notification
- **WHEN** a video plays in the background and the user taps its notification
- **THEN** Dewfall opens as it was, on the video screen, or on the screen under the video screen with the mini player, such as the inbox, Search YouTube or a channel page, and the video plays on

#### Scenario: Details that failed
- **WHEN** a video plays but its details failed to load, and no saved details showed in their place during its playback
- **THEN** the notification shows no title or channel name, and its button still plays and pauses

#### Scenario: Paused for a long time
- **WHEN** a video stays paused in the background for more than 10 minutes
- **THEN** the user can dismiss the notification, which stops playback, and Android may remove it

#### Scenario: A notification left by an ended process
- **WHEN** the system ended the app's process while a paused video's notification still showed, and the user taps its play button
- **THEN** nothing plays, nothing crashes, and the notification goes away

#### Scenario: A failure in the background
- **WHEN** a video plays in the background and the player runs out of data because the network went away
- **THEN** the notification stays with a play button, which tries again from the same position, and once the video plays again the video screen, or the mini player when the video is minimized, shows it playing, with no failure

### Requirement: The mini player shows the video and how far it played
The mini player SHALL show the video's title and channel name once the video's details have loaded, or once the details saved with its download show, and "Now playing" in the title's place until then or when they failed with no saved details. The title SHALL wrap and never be cut off. A line along its top edge SHALL show how much of the video has played, hidden from screen readers. A live stream SHALL have no line.

#### Scenario: A video with its details
- **WHEN** "How reading rooms were lit before electric lamps" by Larkspur Lectures plays in the mini player
- **THEN** the mini player shows that title and the channel name, and its line shows how much has played

#### Scenario: Details that failed
- **WHEN** a video plays in the mini player, but its details failed to load, and no saved details showed in their place during its playback
- **THEN** the mini player shows "Now playing", with no channel name

#### Scenario: A long title at the largest font size
- **WHEN** the title is longer than one line and the font size is the largest
- **THEN** the title wraps onto more lines, and nothing is cut off

#### Scenario: A live stream
- **WHEN** a live stream plays in the mini player
- **THEN** it shows no progress line

### Requirement: Fullscreen has its own controls
Fullscreen SHALL show the title and channel name at the top, once the details have loaded or the details saved with the video's download show, and at the bottom the play and pause button, the position and the length, the seek bar and an exit button, each named for screen readers. A live stream SHALL say "Live" in place of the seek bar and the times. Loading and failures SHALL show as on the video screen. The title SHALL wrap and never be cut off.

#### Scenario: Controls in fullscreen
- **WHEN** a video plays in fullscreen
- **THEN** the title and channel name show at the top, and the button, "18:24 / 1:14:02", the seek bar and the exit button at the bottom

#### Scenario: Seeking in fullscreen
- **WHEN** the user drags the fullscreen seek bar to the middle of a video
- **THEN** playback goes on from the middle

#### Scenario: A failure in fullscreen
- **WHEN** the network goes away in fullscreen and the player runs out of data
- **THEN** fullscreen says that YouTube can't be reached, with "Try again" and the exit button

#### Scenario: A long title at the largest font size in fullscreen
- **WHEN** the title is long and the font size is the largest
- **THEN** the title wraps onto more lines in its box, and nothing is cut off

### Requirement: The video screen marks a video watched or unwatched
Once the details show, YouTube's or the ones saved with the video's download, and the video's watch state has been read, the video screen SHALL show a toggle chip under the publish line: "Mark as watched", unselected, while the video isn't watched, and "Watched", selected, with a check, while it is. It SHALL show no chip when the watch state can't be read. Tapping it SHALL mark the video watched or unwatched and keep its saved position. It SHALL follow the video's watch state, so it changes when playback marks the video too.

#### Scenario: Fullscreen and the mini player
- **WHEN** the video shows in fullscreen or in the mini player
- **THEN** no watched chip shows

#### Scenario: Marking by hand
- **WHEN** the user taps "Mark as watched" while a video plays at 20 minutes
- **THEN** the video is marked watched, the chip says "Watched" with a check, playback goes on, and the saved position isn't changed by the tap

#### Scenario: Unmarking by hand
- **WHEN** the user taps "Watched" on a watched video
- **THEN** the video is no longer marked watched, and the chip says "Mark as watched"

#### Scenario: Playback marks it
- **WHEN** the video screen shows a video that plays from below to past 90 percent of its length and pauses
- **THEN** the chip changes to "Watched" without the user touching it

#### Scenario: A screen reader
- **WHEN** a screen reader reads the chip
- **THEN** it reads the chip's text and whether it is selected, and its glyph adds nothing

#### Scenario: Large font
- **WHEN** the font size is at its largest
- **THEN** the chip's text shows in full, wrapping if it needs to

#### Scenario: The watch state can't be read
- **WHEN** the video screen shows a video's details and the app can't read the video's watch state
- **THEN** no chip shows, and the rest of the screen works as before

### Requirement: The video screen downloads the video
Once the details show, YouTube's or the ones saved with the video's download, unless the video is live or waiting to start, the video screen SHALL show a download chip in the chip row under the publish line, before the watched chip, following the video's download as the downloads spec describes:

- Not downloaded: "Download", with the `download` glyph. A tap starts the download.
- Waiting: "Waiting to download", with the `schedule` glyph. The chip isn't clickable, and a screen reader announces no action, as the inbox's waiting row has no control, after the design system's Queued state.
- Downloading: "Downloading, 42%", with a small progress ring, or "Downloading" while the size is unknown. A tap pauses it.
- Paused: "Paused, 42%", with the `download` glyph. A tap goes on.
- Failed: "Download failed", or "Not enough space on this phone", with the `error` glyph in the error color. A tap tries again.
- Downloaded: "Downloaded", selected, with a check. The chip isn't clickable, and a screen reader announces no action, because removing a download comes with the Downloads screen (#55).

A screen reader SHALL read the chip's words, and the glyph or ring SHALL add nothing. The chip SHALL show in the row even while the watch state can't be read. When the video's download state can't be read, no download chip SHALL show, and the rest of the screen SHALL work.

#### Scenario: Downloading from the video screen
- **WHEN** the user taps "Download" on a video's screen
- **THEN** the chip says "Waiting to download" or "Downloading, 0%", then counts up, and says "Downloaded", selected, with a check, when the download is done, even if the user left the screen and came back

#### Scenario: A live stream
- **WHEN** the video screen shows a live stream
- **THEN** no download chip shows

#### Scenario: Pausing from the video screen
- **WHEN** the user taps "Downloading, 42%"
- **THEN** the chip says "Paused, 42%", and a second tap goes on

#### Scenario: Fullscreen and the mini player
- **WHEN** the video shows in fullscreen or in the mini player
- **THEN** no download chip shows

#### Scenario: Large font
- **WHEN** the font size is at its largest
- **THEN** the chip's words show in full, wrapping if they need to, and the chips wrap onto a new line
