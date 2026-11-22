# Spec Delta

## MODIFIED Requirements

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background; the video's watch state, and the details of videos saved with a channel's uploads, in the app's own database on the phone, which no backup or phone transfer copies; and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links in the playback service, which it SHALL drop when that playback is replaced or stops, once that playback's last save of its position has run. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category

#### Scenario: After closing the video screen
- **WHEN** the user minimizes the video screen, then closes the mini player
- **THEN** Android's media session no longer holds the video's title or channel name

#### Scenario: After minimizing a video that couldn't start
- **WHEN** the user minimizes a video whose stream links failed to load
- **THEN** the playback service holds nothing about that video

#### Scenario: A saved watch state
- **WHEN** a video's position is saved or it is marked watched
- **THEN** the app's database holds the video's ID, position and times, beside the copies this requirement already allows, and the app's log lines hold none of them

### Requirement: The video screen survives process death
After the system ends the app's process in the background while the video screen shows, the user SHALL come back to the same video's screen, which loads the details again. Going back from it SHALL show the screen it was opened from.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the screen says that the same video is loading, then shows its details, and the video waits for the user to press play at the position saved for it, or, when none is saved, at the position it had when the user last left the app

### Requirement: Opening a video starts playing it
Opening a video SHALL start playing it, without a tap, from the position saved for it, or from the start when none is saved, or a live stream from its live edge, unless the mini player already plays that video, which goes on as it is. A video screen that comes back after Android stopped its player in the background SHALL wait for the user to press play, at the position where it paused. One that comes back after the system ended the app's process SHALL wait for play at the position saved for the video, or, when none is saved, at the position the screen saved when the user last left the app.

#### Scenario: Opening a video
- **WHEN** the user opens a video from search that has no saved position
- **THEN** the video starts playing from the start

#### Scenario: Opening a half-watched video
- **WHEN** the user opens a video whose saved position is 12 minutes 30 seconds
- **THEN** the video starts playing from 12 minutes 30 seconds

#### Scenario: Opening a half-watched video after the app restarts
- **WHEN** the user leaves a video at 20 minutes, closes the mini player, removes Dewfall from the recent apps, and opens the same video again
- **THEN** the video starts playing from 20 minutes

#### Scenario: Playback after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the video waits at the position saved for it, for the user to press play, and no stream links are fetched for it until then

#### Scenario: Listening on in the background before process death
- **WHEN** the user left the app at 10 minutes, listened on in the background to 40 minutes and paused there, and the system ended the app's process before the user returned
- **THEN** the video screen waits at 40 minutes, and plays on from 40 minutes when the user presses play

#### Scenario: Paused in the background after resuming, back much later
- **WHEN** the user opened a video that resumed at 12 minutes 30 seconds, it played on to 40 minutes and was paused in the background, and Android stopped the player before the user came back
- **THEN** the video screen waits at 40 minutes, and plays on from 40 minutes when the user presses play

#### Scenario: The saved position can't be read
- **WHEN** the user opens a video and the app can't read the video's saved position
- **THEN** the video starts playing from the start

#### Scenario: The saved position can't be read after process death
- **WHEN** the system ended the app's process while it showed a video at 10 minutes, and when the user returns the app can't read the video's saved position
- **THEN** the video screen waits at 10 minutes, and plays from 10 minutes when the user presses play

### Requirement: Opening the video that already plays goes on with it
Opening, from search results or a channel page, the video that the mini player plays SHALL open its video screen and go on with that playback, as tapping the mini player does. Opening another video SHALL replace it, and start the new one from its saved position, or from the start when none is saved.

#### Scenario: The same video from search
- **WHEN** a video plays in the mini player and the user opens the same video from search results
- **THEN** the video screen shows it playing on from where it was

#### Scenario: Another video from search
- **WHEN** a video plays in the mini player and the user opens another video from search results, which has no saved position
- **THEN** the first video stops, and the new one starts playing from the start

#### Scenario: The same video from its channel page
- **WHEN** the user opened a video's channel page from the video screen, and taps that video in the channel's uploads
- **THEN** the video screen shows it playing on from where it was

### Requirement: The mini player shows while a video is minimized
While the player has a video and no video screen shows, the app SHALL show a mini player at the bottom of the screen. It SHALL show for a video that plays, is paused, waits for data, has ended or failed while playing. It SHALL go away when playback stops, or when Android stops the player.

#### Scenario: A minimized video
- **WHEN** a video plays and the user minimizes it
- **THEN** the mini player shows at the bottom of Search YouTube

#### Scenario: The video screen
- **WHEN** the video screen shows
- **THEN** no mini player shows

#### Scenario: No video
- **WHEN** the app opens and nothing has played
- **THEN** no mini player shows

#### Scenario: A video that couldn't start
- **WHEN** the user minimizes a video whose stream links failed to load, so the player has no video
- **THEN** no mini player shows

#### Scenario: After the process ended
- **WHEN** the system ended the app's process while the mini player showed, and the user returns to the app
- **THEN** no mini player shows, and nothing plays

#### Scenario: Paused and minimized in the background, back much later
- **WHEN** the user paused a minimized video, went to the home screen, and Android stopped the player before the user came back
- **THEN** no mini player shows, and opening the video again starts it from where it was paused

## ADDED Requirements

### Requirement: Playback saves where the video was left
While a video that isn't live plays, the player SHALL save its position when it pauses, when the user seeks while it is paused, when it stops, when another video takes its place, and when the playback service ends, and at least every 30 seconds while it plays. It SHALL NOT save on every change of the position. A live stream and a player that failed SHALL save nothing. A save that fails SHALL NOT stop playback.

#### Scenario: Pausing
- **WHEN** the user pauses a video at 12 minutes 30 seconds
- **THEN** that video's saved position is 12 minutes 30 seconds

#### Scenario: Playing on
- **WHEN** a video plays without a pause from 0 to 5 minutes
- **THEN** its saved position is never more than 30 seconds behind the position that plays

#### Scenario: Another video takes its place
- **WHEN** a video plays at 8 minutes and the user opens another video
- **THEN** the first video's saved position is 8 minutes

#### Scenario: Closing the mini player
- **WHEN** a 60-minute video plays in the mini player at 3 minutes and the user taps its close button
- **THEN** that video's saved position is 3 minutes, and it isn't marked watched

#### Scenario: A live stream
- **WHEN** the user watches a live stream and pauses it
- **THEN** the live stream has no saved position

#### Scenario: A failure
- **WHEN** a video at 6 minutes fails to play because the network went away
- **THEN** its saved position stays as the last save before the failure

#### Scenario: A save that fails
- **WHEN** the phone's storage is full and a save fails
- **THEN** the video plays on, the app logs one line that names no video and no position, and the next save tries again

### Requirement: A video counts as watched near its end
A position SHALL count as watched when it is at least 90 percent of the video's length. Playback SHALL mark the video watched when its position goes from below 90 percent to 90 percent or more during one playback, or when it plays to its end. A video whose length isn't known SHALL count as watched only at its end. Playback SHALL NOT mark a live stream.

#### Scenario: Reaching 90 percent
- **WHEN** a 60-minute video plays from 50 to 54 minutes and is paused there
- **THEN** it is marked watched, and its saved position is 54 minutes

#### Scenario: Opening a video marked at 90 percent
- **WHEN** a 3-hour video was marked watched by playback and paused at 2 hours 45 minutes, and the user opens it again
- **THEN** it starts playing from 2 hours 45 minutes

#### Scenario: Before 90 percent
- **WHEN** a 60-minute video is paused at 53 minutes
- **THEN** it isn't marked watched by playback, and its saved position is 53 minutes

#### Scenario: The end
- **WHEN** a video plays to its end
- **THEN** it is marked watched

#### Scenario: A live stream that ends
- **WHEN** a live stream plays until it ends
- **THEN** playback doesn't mark it watched, and it has no saved position

#### Scenario: Unmarked by hand past 90 percent
- **WHEN** the user marks a 60-minute video unwatched while it plays at 57 minutes, and it plays on and is paused at 58 minutes
- **THEN** it stays unwatched, and its saved position is 58 minutes

#### Scenario: Unmarked by hand, then played to the end
- **WHEN** the user marks a video unwatched while it plays past 90 percent, and it plays on to its end
- **THEN** it is marked watched again

#### Scenario: Watching again
- **WHEN** a video marked watched on Monday plays again on Wednesday and is paused at 10 minutes of 60
- **THEN** it stays marked watched since Monday, and its saved position is 10 minutes

### Requirement: A video played to its end starts over next time
Reaching a video's end SHALL save its position as zero, as SHALL every save after it until the video plays again or the user seeks, so the video plays from the start next time. Every other save SHALL keep the position, even past 90 percent.

#### Scenario: Opening a video played to its end
- **WHEN** a video played to its end, and the user opens it again
- **THEN** it starts playing from the start

#### Scenario: The end, then closing the mini player
- **WHEN** a minimized video plays to its end, and the user then taps the mini player's close button
- **THEN** its saved position is zero

#### Scenario: Playing again after the end
- **WHEN** a video plays to its end, the user plays it again from the start, and pauses at 2 minutes
- **THEN** its saved position is 2 minutes

### Requirement: The video screen marks a video watched or unwatched
Once the details show and the video's watch state has been read, the video screen SHALL show a toggle chip under the publish line: "Mark as watched", unselected, while the video isn't watched, and "Watched", selected, with a check, while it is. It SHALL show no chip when the watch state can't be read. Tapping it SHALL mark the video watched or unwatched and keep its saved position. It SHALL follow the video's watch state, so it changes when playback marks the video too.

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
