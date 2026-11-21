# Spec Delta

## ADDED Requirements

### Requirement: The sound plays on in the background
Leaving the app while a video plays, such as with the home gesture or by switching apps, and turning the screen off, SHALL keep the sound playing. Coming back SHALL show the same video playing at its current position. A configuration change, such as a resized window or a theme change, SHALL NOT stop, pause or restart playback.

#### Scenario: Going home
- **WHEN** a video plays and the user goes to the home screen
- **THEN** the sound goes on, and when the user comes back, the video plays on at its current position

#### Scenario: The screen off
- **WHEN** a video plays and the user turns the screen off for 10 minutes
- **THEN** the sound goes on the whole time

#### Scenario: Paused in the background, back much later
- **WHEN** the user paused a video in the background, and Android stopped the player before the user came back
- **THEN** the video waits at the position where it paused, and plays on from it when the user presses play

#### Scenario: Resizing the window
- **WHEN** a video plays in split screen and the user resizes the window
- **THEN** it keeps playing without a pause

### Requirement: The picture turns off while the app can't be seen
While no Dewfall screen can be seen, the player SHALL decode no picture, and SHALL NOT fetch a picture that comes in its own file. When the app shows again, the picture SHALL come back at the current position, after a short load that may pause the sound. A file that holds both sound and picture, and a live stream, whose segments hold both, still load the picture's bytes with the sound.

#### Scenario: Data in the background
- **WHEN** a video whose sound and picture come in separate files plays at 1080p, and the user goes to the home screen
- **THEN** the player fetches only the sound until the user comes back

#### Scenario: A live stream in the background
- **WHEN** a live stream plays and the user goes to the home screen
- **THEN** the sound goes on, and no picture is decoded until the user comes back

#### Scenario: Coming back
- **WHEN** the user comes back to a video that played on in the background
- **THEN** the picture shows again at the current position

### Requirement: A video that hasn't started waits
When the user leaves the app after opening a video and before it has started playing, the video SHALL NOT start in the background. Coming back SHALL show it paused, waiting for the user to press play.

#### Scenario: Leaving while the video loads
- **WHEN** the user opens a video and goes to the home screen before it starts playing
- **THEN** no sound plays, and when the user comes back, the video waits for play

### Requirement: Closing the video screen or removing the app stops playback
Closing the video screen SHALL stop playback and remove the media notification. Removing Dewfall from the recent apps SHALL do the same.

#### Scenario: Closing the video screen
- **WHEN** a video plays and the user closes its screen
- **THEN** the sound stops, the notification goes away, and search shows as it was

#### Scenario: Removing the app from the recent apps
- **WHEN** a video plays in the background and the user removes Dewfall from the recent apps
- **THEN** the sound stops and the notification goes away

### Requirement: A media notification shows while a video plays
While a video plays, and while it is paused until Android stops the player, the app SHALL show a media notification with a play and pause button, and the video's title and channel name once its details have loaded. It SHALL show no picture. Tapping it SHALL open the app as it was. While the video plays, the user SHALL NOT be able to dismiss it.

#### Scenario: Playing in the background
- **WHEN** a video plays and the user goes to the home screen
- **THEN** a notification shows the video's title and channel name with a pause button, and the pause button pauses the video

#### Scenario: Playing in the app
- **WHEN** a video plays on the video screen
- **THEN** the notification shows too

#### Scenario: Tapping the notification
- **WHEN** a video plays in the background and the user taps its notification
- **THEN** Dewfall opens on the video screen as it was, and the video plays on

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
- **THEN** the notification stays with a play button, which tries again from the same position, and once the video plays again the video screen shows it playing, with no failure

### Requirement: Headset and Bluetooth buttons play and pause
While Dewfall's player runs, the play and pause buttons of a headset, a Bluetooth device and the notification SHALL play and pause the video. Next, previous, fast forward and rewind SHALL do nothing. After Android stopped the player, or ended the app's process, a media button SHALL NOT start the app or play anything.

#### Scenario: A headset's button in the background
- **WHEN** a video plays in the background and the user presses a headset's play and pause button
- **THEN** the video pauses, and a second press plays it on

#### Scenario: Next and previous
- **WHEN** a video plays and a Bluetooth device's next, previous, fast forward or rewind button is pressed
- **THEN** the video plays on at its position

#### Scenario: The notification's buttons
- **WHEN** a video plays on Android 10, 11 or 12
- **THEN** the notification shows a pause button and no previous or next button

#### Scenario: A headset's button after Android stopped the player
- **WHEN** a video was paused in the background, Android stopped the player while the process lived on, and a headset's play button is pressed
- **THEN** nothing plays

#### Scenario: A headset's button after the process ended
- **WHEN** the system ended the app's process in the background, and a headset's play button is pressed
- **THEN** the app doesn't start, and nothing crashes

### Requirement: Only Android's media controls and trusted apps control playback
Besides Dewfall itself, only Android's own components that may control media, such as the system UI and the Bluetooth service, and apps the user gave notification access SHALL control playback, and only to play, pause, seek within the video and stop. None of them SHALL change which video plays, and no other app SHALL connect to the player.

#### Scenario: The seek bar in the notification shade
- **WHEN** on Android 13 or later the user drags the seek bar of the media player in the notification shade
- **THEN** the video goes on from there

#### Scenario: An app asks for another item
- **WHEN** an app with notification access asks the player to play another item
- **THEN** the player refuses, and the video plays on

## MODIFIED Requirements

### Requirement: Opening a video starts playing it
Opening a video SHALL start playing it from the start, or a live stream from its live edge, without a tap. A video screen that comes back after Android stopped its player in the background SHALL wait for the user to press play, at the position where it paused. One that comes back after the system ended the app's process SHALL wait for play at the position the screen saved when the user last left the app.

#### Scenario: Opening a video
- **WHEN** the user opens a video from search
- **THEN** the video starts playing from the start

#### Scenario: Playback after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the video waits at the position it had when the user last left the app, for the user to press play, and no stream links are fetched for it until then

### Requirement: The video screen survives process death
After the system ends the app's process in the background while the video screen shows, the user SHALL come back to the same video's screen, which loads the details again. Going back from it SHALL show the screen it was opened from.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the screen says that the same video is loading, then shows its details, and the video waits for the user to press play at the position it had when the user last left the app

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background, and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links in the playback service, which it SHALL drop when that playback is replaced or stops. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category

#### Scenario: After closing the video screen
- **WHEN** the user closes the video screen
- **THEN** Android's media session no longer holds the video's title or channel name

### Requirement: Playback stays private
The player SHALL fetch video only through the YouTube layer, which SHALL send its requests only over HTTPS to googlevideo.com and its subdomains, and SHALL never follow a redirect to another host. The app SHALL never log a stream link, a video's ID or its title, and Media3 SHALL write nothing to the log. Android's media session SHALL hold no video ID or link, and no text but the title, the channel name and a constant error. ExoPlayer SHALL report no playback session to Android's media metrics.

#### Scenario: Logs while playing
- **WHEN** the user plays a video, once to the end and once with the network turned off while it plays
- **THEN** the app's log lines hold no link, ID or title, and the failure's line names only its category and Media3's error code

#### Scenario: A redirect to another host
- **WHEN** a request for a stream link is redirected to a host outside googlevideo.com
- **THEN** the redirected request isn't sent, and the video area says that YouTube isn't loading right now

#### Scenario: The media session
- **WHEN** a video plays and another app reads Android's media sessions
- **THEN** the session's item has a random ID, the video's title and channel name, and no video ID or link

#### Scenario: The media session after a failure
- **WHEN** a video failed to play and another app reads Android's media sessions
- **THEN** the session's error message is the same constant text for every failure

## REMOVED Requirements

### Requirement: Leaving stops or pauses playback
**Reason**: Leaving the app no longer pauses playback, because the sound plays on in the background.
**Migration**: See "The sound plays on in the background", "The picture turns off while the app can't be seen", "A video that hasn't started waits", and "Closing the video screen or removing the app stops playback", which keeps the closing rule and its scenario.

### Requirement: Media buttons do nothing yet
**Reason**: Background play exists now, so media buttons play and pause.
**Migration**: See "Headset and Bluetooth buttons play and pause".

### Requirement: The player shows no notification yet
**Reason**: Background play needs a media notification and a foreground service.
**Migration**: See "A media notification shows while a video plays".
