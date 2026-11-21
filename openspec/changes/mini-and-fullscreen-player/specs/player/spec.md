# Spec Delta

## ADDED Requirements

### Requirement: Minimizing goes back
The video screen SHALL have a minimize button at the top, named for screen readers. The minimize button and the system's back gesture SHALL return to the screen the video was opened from, as that screen was, and SHALL leave playback as it is: a playing video plays on, and a paused one stays paused.

#### Scenario: Minimizing after a search
- **WHEN** a video plays, the user opened it from search results, and taps the minimize button
- **THEN** Search YouTube shows the same text and results, sends no new search, and the video plays on in the mini player

#### Scenario: The back gesture
- **WHEN** the user makes the system's back gesture on the video screen
- **THEN** the app goes back as the minimize button does

#### Scenario: A double tap on minimize
- **WHEN** the user taps the minimize button twice quickly, or taps it during the back gesture
- **THEN** the app goes back once, to the screen the video was opened from, and doesn't close

#### Scenario: Minimizing a paused video
- **WHEN** the user pauses a video and taps the minimize button
- **THEN** the video stays paused, and the mini player offers to play it

#### Scenario: Minimizing while the video loads
- **WHEN** the user opens a video and taps the minimize button before it starts playing
- **THEN** it starts playing, and the mini player shows it

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
- **THEN** no mini player shows, and opening the video again starts it from the start

### Requirement: The mini player shows the video and how far it played
The mini player SHALL show the video's title and channel name once the video's details have loaded, and "Now playing" in the title's place until then or when they failed. The title SHALL wrap and never be cut off. A line along its top edge SHALL show how much of the video has played, hidden from screen readers. A live stream SHALL have no line.

#### Scenario: A video with its details
- **WHEN** "How reading rooms were lit before electric lamps" by Larkspur Lectures plays in the mini player
- **THEN** the mini player shows that title and the channel name, and its line shows how much has played

#### Scenario: Details that failed
- **WHEN** a video plays in the mini player, but its details failed to load
- **THEN** the mini player shows "Now playing", with no channel name

#### Scenario: A long title at the largest font size
- **WHEN** the title is longer than one line and the font size is the largest
- **THEN** the title wraps onto more lines, and nothing is cut off

#### Scenario: A live stream
- **WHEN** a live stream plays in the mini player
- **THEN** it shows no progress line

### Requirement: The mini player plays and pauses
The mini player SHALL have a play and pause button, named for screen readers, that plays and pauses the video. At the end of a video it SHALL offer to play it again from the start. After a failure while playing, play SHALL try again from the same position.

#### Scenario: Pausing from the mini player
- **WHEN** a video plays in the mini player and the user taps its button
- **THEN** the video pauses, and the button offers to play

#### Scenario: The end
- **WHEN** a video in the mini player plays to its end and the user taps its button
- **THEN** it plays again from the start

#### Scenario: After a failure
- **WHEN** the network went away while a video played in the mini player, and the user taps play with the network back
- **THEN** the video plays on from where it stopped

#### Scenario: A failure says nothing in the mini player
- **WHEN** a video fails while it plays in the mini player
- **THEN** the mini player offers play and gives no reason, and the video screen says why once the user opens it

### Requirement: Tapping the mini player opens the video
Tapping the mini player's title SHALL open the video screen for its video, which SHALL go on with the playback as it is, without starting it again, and SHALL load the video's details. The mini player SHALL tell screen readers that the tap opens the video.

#### Scenario: Opening a playing video
- **WHEN** a video plays in the mini player at 18:24 and the user taps its title
- **THEN** the video screen opens and the video plays on from 18:24, and the app asks YouTube for its details once and for no new stream links

#### Scenario: Opening a paused video
- **WHEN** the mini player's video is paused and the user taps its title
- **THEN** the video screen shows it paused at the same position

### Requirement: Opening the video that already plays goes on with it
Opening, from search, the video that the mini player plays SHALL open its video screen and go on with that playback, as tapping the mini player does. Opening another video SHALL replace it, and start the new one from the start.

#### Scenario: The same video from search
- **WHEN** a video plays in the mini player and the user opens the same video from search results
- **THEN** the video screen shows it playing on from where it was

#### Scenario: Another video from search
- **WHEN** a video plays in the mini player and the user opens another video from search results
- **THEN** the first video stops, and the new one starts playing from the start

### Requirement: The mini player fits any window
The mini player's background SHALL reach the window's bottom edge, behind the navigation bar, and its text and buttons SHALL sit above the system bars and clear of the display cutout. In a wider window its content SHALL sit in one centered column of readable width. The screen above it SHALL end at its top edge, so nothing is hidden behind it.

#### Scenario: The end of the search results
- **WHEN** the mini player shows and the user scrolls Search YouTube's results to the end
- **THEN** the last result sits fully above the mini player

#### Scenario: A tablet in landscape
- **WHEN** the mini player shows on a tablet in landscape
- **THEN** its background fills the window's width, and its title and buttons sit in one centered column no wider than the readable width

#### Scenario: The keyboard
- **WHEN** the mini player shows and the user types in Search YouTube's field
- **THEN** the keyboard covers the mini player, and the search results above it end above the keyboard

### Requirement: Closing the mini player or removing the app stops playback
The mini player SHALL have a close button, named for screen readers. Closing it SHALL stop playback, remove the media notification, and hide the mini player. Removing Dewfall from the recent apps SHALL do the same. Closing the video screen by minimizing it SHALL NOT stop playback.

#### Scenario: Closing the mini player
- **WHEN** a video plays in the mini player and the user taps its close button
- **THEN** the sound stops, the notification goes away, the mini player goes away, and the screen under it shows as it was

#### Scenario: Removing the app from the recent apps
- **WHEN** a video plays in the background and the user removes Dewfall from the recent apps
- **THEN** the sound stops and the notification goes away

### Requirement: The picture turns off while minimized
While a video is minimized, the player SHALL decode no picture, and SHALL NOT fetch a picture that comes in its own file, as while the app can't be seen. Opening the video screen again SHALL bring the picture back at the current position, after a short load that may pause the sound.

#### Scenario: Data while minimized
- **WHEN** a video whose sound and picture come in separate files plays at 1080p, and the user minimizes it
- **THEN** the player fetches only the sound until the video screen opens again

#### Scenario: Opening it again
- **WHEN** the user opens the minimized video from the mini player
- **THEN** the picture shows again at the current position

### Requirement: The video can play in fullscreen
The video screen's controls SHALL have a fullscreen button, named for screen readers, while the player is connected. Fullscreen SHALL show the video as large as the window allows, keeping its shape, on black. It SHALL use the dark color scheme in light mode too, and hide the system bars, which a swipe from the screen's edge shows for a moment. The screen SHALL stay on while the video plays.

#### Scenario: Entering fullscreen
- **WHEN** a video plays on the video screen and the user taps the fullscreen button
- **THEN** the video fills the screen in dark colors, without the system bars, and plays on without a pause

#### Scenario: Light mode
- **WHEN** the phone is in light mode and the user enters fullscreen
- **THEN** fullscreen uses the dark color scheme

#### Scenario: A system bar for a moment
- **WHEN** the user swipes from the screen's edge in fullscreen
- **THEN** the system bars show for a moment over the video, and hide again

### Requirement: A phone turns to landscape in fullscreen
On a phone, entering fullscreen SHALL turn the screen to landscape, either way round as the phone's rotation setting allows, and leaving it SHALL turn the screen back to portrait. Where Android ignores an app's orientation, such as on a tablet, an unfolded foldable or in split screen, fullscreen SHALL keep the window's shape. Turning the phone SHALL NOT enter or leave fullscreen.

#### Scenario: A phone
- **WHEN** the user enters fullscreen on a phone held in portrait
- **THEN** the screen turns to landscape, and the video plays on without a pause

#### Scenario: A tablet in portrait
- **WHEN** the user enters fullscreen on a tablet in portrait
- **THEN** fullscreen fills the window in portrait, with black above and below the video

#### Scenario: Turning the phone on the video screen
- **WHEN** the user turns a phone to landscape on the video screen
- **THEN** the video screen stays in portrait, and doesn't enter fullscreen

### Requirement: Fullscreen has its own controls
Fullscreen SHALL show the title and channel name at the top, once the details have loaded, and at the bottom the play and pause button, the position and the length, the seek bar and an exit button, each named for screen readers. A live stream SHALL say "Live" in place of the seek bar and the times. Loading and failures SHALL show as on the video screen. The title SHALL wrap and never be cut off.

#### Scenario: Controls in fullscreen
- **WHEN** a video plays in fullscreen
- **THEN** the title and channel name show at the top, and the button, "18:24 / 1:14:02", the seek bar and the exit button at the bottom

#### Scenario: Seeking in fullscreen
- **WHEN** the user drags the fullscreen seek bar to the middle of a video
- **THEN** playback goes on from the middle

#### Scenario: A failure in fullscreen
- **WHEN** the network goes away in fullscreen and the player runs out of data
- **THEN** fullscreen says that YouTube can't be reached, with "Try again", and the exit button stays

#### Scenario: A long title at the largest font size in fullscreen
- **WHEN** the title is long and the font size is the largest
- **THEN** the title wraps onto more lines in its box, and nothing is cut off

### Requirement: Fullscreen keeps clear of the display cutout
In fullscreen, no text, control or picture SHALL sit under the display cutout. The system bars stay hidden, and while a swipe shows them for a moment they may cover the controls.

#### Scenario: A display cutout
- **WHEN** fullscreen shows in landscape on a phone with a display cutout
- **THEN** no text, control or picture sits under the cutout

#### Scenario: A system bar over the controls
- **WHEN** the user swipes the navigation bar in over fullscreen
- **THEN** it may cover the controls for a moment, and the controls are whole again once it hides

### Requirement: Fullscreen controls hide while the video plays
While a video plays in fullscreen, the title and the controls SHALL hide 3 seconds after the user last touched the screen, or later when the phone's accessibility settings ask for more time. A tap on the video SHALL show them again. They SHALL stay while the video is paused, waits for data, has ended or failed, and while a screen reader such as TalkBack is on.

#### Scenario: Watching
- **WHEN** a video plays in fullscreen and the user doesn't touch the screen for 3 seconds
- **THEN** the title and the controls hide, and the video plays on

#### Scenario: Showing them again
- **WHEN** the controls are hidden and the user taps the video
- **THEN** the title and the controls show again

#### Scenario: Paused
- **WHEN** the user pauses a video in fullscreen
- **THEN** the title and the controls stay

#### Scenario: More time to act
- **WHEN** the phone's accessibility setting for time to take action is 1 minute
- **THEN** the controls hide only after 1 minute without a touch

#### Scenario: A screen reader
- **WHEN** TalkBack is on and a video plays in fullscreen
- **THEN** the title and the controls stay

### Requirement: Leaving fullscreen returns to the video screen
The exit button and the system's back gesture SHALL leave fullscreen and return to the video screen, with the system bars and the screen's own colors, and playback SHALL go on without a pause.

#### Scenario: The exit button
- **WHEN** a video plays in fullscreen and the user taps the exit button
- **THEN** the video screen shows in portrait, with the system bars, and the video plays on

#### Scenario: The back gesture in fullscreen
- **WHEN** the user makes the system's back gesture in fullscreen
- **THEN** fullscreen ends as with the exit button, and the video screen stays open

#### Scenario: Fullscreen after the process ended
- **WHEN** the system ended the app's process while a video showed in fullscreen on a phone, and the user returns to it
- **THEN** fullscreen shows again, waiting for play at the saved position, and leaving it turns the phone back to portrait

## MODIFIED Requirements

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background, and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links in the playback service, which it SHALL drop when that playback is replaced or stops. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category

#### Scenario: After closing the video screen
- **WHEN** the user minimizes the video screen, then closes the mini player
- **THEN** Android's media session no longer holds the video's title or channel name

#### Scenario: After minimizing a video that couldn't start
- **WHEN** the user minimizes a video whose stream links failed to load
- **THEN** the playback service holds nothing about that video

### Requirement: The video screen fits any window
On a phone the screen SHALL stay in portrait, except in fullscreen. In any wider window, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, the screen SHALL keep its content in one centered column of readable width, and the background SHALL fill the window. No text or control SHALL sit under the system bars or the display cutout.

#### Scenario: A tablet in landscape
- **WHEN** the screen shows a video's details on a tablet in landscape
- **THEN** the details sit in one centered column no wider than the readable width, and nothing is cut off

### Requirement: The picture turns off while the app can't be seen
While no Dewfall screen can be seen, the player SHALL decode no picture, and SHALL NOT fetch a picture that comes in its own file. When the app shows the video screen again, the picture SHALL come back at the current position, after a short load that may pause the sound. When the app shows again with the video minimized, the picture SHALL stay off until the video screen opens. A file that holds both sound and picture, and a live stream, whose segments hold both, still load the picture's bytes with the sound.

#### Scenario: Data in the background
- **WHEN** a video whose sound and picture come in separate files plays at 1080p, and the user goes to the home screen
- **THEN** the player fetches only the sound until the user comes back

#### Scenario: A live stream in the background
- **WHEN** a live stream plays and the user goes to the home screen
- **THEN** the sound goes on, and no picture is decoded until the user comes back

#### Scenario: Coming back
- **WHEN** the user comes back to the video screen of a video that played on in the background
- **THEN** the picture shows again at the current position

#### Scenario: Coming back to the mini player
- **WHEN** the user minimized a playing video, went to the home screen, and comes back
- **THEN** the mini player shows the video playing on, and the player fetches only the sound until the video screen opens

### Requirement: A media notification shows while a video plays
While a video plays, and while it is paused until Android stops the player, the app SHALL show a media notification with a play and pause button, and the video's title and channel name once its details have loaded. It SHALL show no picture. Tapping it SHALL open the app as it was, on the video screen or on the screen under the mini player. While the video plays, the user SHALL NOT be able to dismiss it.

#### Scenario: Playing in the background
- **WHEN** a video plays and the user goes to the home screen
- **THEN** a notification shows the video's title and channel name with a pause button, and the pause button pauses the video

#### Scenario: Playing in the app
- **WHEN** a video plays on the video screen, or in the mini player
- **THEN** the notification shows too

#### Scenario: Tapping the notification
- **WHEN** a video plays in the background and the user taps its notification
- **THEN** Dewfall opens as it was, on the video screen or on Search YouTube with the mini player, and the video plays on

#### Scenario: Details that failed
- **WHEN** a video plays but its details failed to load
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

### Requirement: Opening a video starts playing it
Opening a video SHALL start playing it from the start, or a live stream from its live edge, without a tap, unless the mini player already plays that video, which goes on as it is. A video screen that comes back after Android stopped its player in the background SHALL wait for the user to press play, at the position where it paused. One that comes back after the system ended the app's process SHALL wait for play at the position the screen saved when the user last left the app.

#### Scenario: Opening a video
- **WHEN** the user opens a video from search
- **THEN** the video starts playing from the start

#### Scenario: Playback after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the video waits at the position it had when the user last left the app, for the user to press play, and no stream links are fetched for it until then

### Requirement: A video that hasn't started waits
When the user leaves the app after opening a video and before it has started playing, from the video screen or with the video minimized, the video SHALL NOT start in the background. Coming back SHALL show it paused, waiting for the user to press play.

#### Scenario: Leaving while the video loads
- **WHEN** the user opens a video and goes to the home screen before it starts playing
- **THEN** no sound plays, and when the user comes back, the video waits for play

#### Scenario: Leaving from the mini player while the video loads
- **WHEN** the user opens a video, minimizes it and goes to the home screen before it starts playing
- **THEN** no sound plays, and when the user comes back, the mini player offers to play it

## REMOVED Requirements

### Requirement: Closing goes back
**Reason**: The video screen's top button minimizes the player instead of closing it, and minimizing leaves playback as it is.
**Migration**: See "Minimizing goes back", which keeps the return to the screen the video was opened from, the back gesture and the double tap.

### Requirement: Closing the video screen or removing the app stops playback
**Reason**: Closing the video screen no longer stops playback, because the mini player goes on with it.
**Migration**: See "Closing the mini player or removing the app stops playback".
