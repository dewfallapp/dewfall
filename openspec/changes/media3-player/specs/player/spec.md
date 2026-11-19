# Spec Delta

## ADDED Requirements

### Requirement: The video plays on the video screen
The video screen SHALL show the video in a 16:9 area above the title, with a play and pause button, a seek bar, and the position and the length. Dragging the seek bar SHALL move playback to that point. While the player waits for data, the area SHALL show that it is loading.

#### Scenario: Playing
- **WHEN** a video plays
- **THEN** its picture fits the 16:9 area, the button offers to pause, the times read like "18:24 / 1:14:02", and the screen stays on

#### Scenario: Watching past the screen timeout
- **WHEN** a video plays longer than the phone's screen timeout without a touch
- **THEN** the screen stays on and the video keeps playing

#### Scenario: Paused
- **WHEN** the video is paused
- **THEN** the screen may turn off after the phone's timeout

#### Scenario: The end of a video
- **WHEN** a video plays to its end
- **THEN** the button offers to play it again from the start, and the screen may turn off after the phone's timeout

#### Scenario: Seeking
- **WHEN** the user drags the seek bar to the middle of a video
- **THEN** playback goes on from the middle

#### Scenario: Pausing
- **WHEN** the user taps the button while the video plays
- **THEN** the video pauses, and the button offers to play

### Requirement: Opening a video starts playing it
Opening a video SHALL start playing it from the start, or a live stream from its live edge, without a tap. A video screen that comes back after the system ended the app's process, or stopped its player in the background, SHALL wait for the user to press play, at the position where it paused.

#### Scenario: Opening a video
- **WHEN** the user opens a video from search
- **THEN** the video starts playing from the start

#### Scenario: Playback after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the video waits at the position where it paused for the user to press play, and no stream links are fetched for it until then

### Requirement: Quality follows the network
On a metered network the player SHALL stream a picture whose shorter side is at most 480 pixels, with the lowest audio bitrate. On an unmetered one, at most 1080 pixels, with the highest. Within the limit it SHALL prefer formats the phone decodes in hardware, then the largest. It SHALL take only formats the phone can decode, the smallest one when none is within the limit, and fail as not decodable when no video or no audio format decodes, and SHALL keep its choice until playback starts again.

#### Scenario: Mobile data
- **WHEN** a video with formats up to 2160p starts playing on mobile data
- **THEN** the player streams a format of 480p or less

#### Scenario: Wi-Fi
- **WHEN** a video with formats up to 2160p starts playing on Wi-Fi, on a phone that decodes them all in hardware
- **THEN** the player streams a 1080p format

#### Scenario: A portrait video
- **WHEN** a portrait video with formats up to 2160x3840 starts playing on Wi-Fi
- **THEN** the player streams a 1080x1920 format

#### Scenario: Audio on mobile data
- **WHEN** a video with dubbed and original audio tracks starts playing on mobile data
- **THEN** the player streams the original track at its lowest bitrate

#### Scenario: Audio on Wi-Fi
- **WHEN** the same video starts playing on Wi-Fi
- **THEN** the player streams the original track at its highest bitrate

#### Scenario: Hardware before size
- **WHEN** on Wi-Fi a video's 1080p format decodes only in software and its 720p format in hardware
- **THEN** the player streams the 720p format

#### Scenario: Nothing within the limit
- **WHEN** a video's only decodable formats are 720p and 1080p, and it starts playing on mobile data
- **THEN** the player streams the 720p format, the smallest it can decode

#### Scenario: A format the phone can't decode
- **WHEN** the best format at the limit uses a codec the phone can't decode
- **THEN** the player streams the best format at or below the limit that it can decode

### Requirement: A live stream plays live
A live stream SHALL play from its live edge. Its controls SHALL show "Live" in place of the seek bar and the times, and the play and pause button SHALL still work.

#### Scenario: Opening a live stream
- **WHEN** the user opens a live stream that is on air
- **THEN** it plays from the live edge, and the controls say "Live"

### Requirement: Expired links are fetched again
When YouTube refuses a stream link, the player SHALL fetch new links once and go on from the same position. If the new links fail too, it SHALL stop and say that playback failed.

#### Scenario: An expired link
- **WHEN** a video paused long enough for its links to expire, and the user presses play
- **THEN** the player fetches new links and plays on from where it paused

### Requirement: A playback failure says why
When a video can't play, the video area SHALL say why in words, and SHALL offer "Try again" when trying again can help: when YouTube can't be reached, refuses to answer, can't be read, the network failed while playing, or playback stopped for another reason. When the phone can decode none of the video's formats, it SHALL say so with no "Try again". The details below SHALL stay.

#### Scenario: No connection while playing
- **WHEN** the network goes away and the player runs out of data
- **THEN** the video area says that YouTube can't be reached, with "Try again", and the details stay

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a playback failure
- **THEN** the player fetches the links again and plays from where it stopped

#### Scenario: No format the phone can decode
- **WHEN** the phone can decode none of the video's formats
- **THEN** the video area says that this video can't play on this phone, with no "Try again"

#### Scenario: Details and playback both fail
- **WHEN** the network is off as the user opens a video
- **THEN** the screen says once that YouTube can't be reached, with one "Try again" that retries both the details and the video

### Requirement: Leaving stops or pauses playback
Closing the video screen SHALL stop playback. Leaving the app, such as with the home gesture, SHALL pause it, and coming back SHALL leave it paused at the same position. A configuration change, such as a resized window or a theme change, SHALL NOT stop, pause or restart playback.

#### Scenario: Closing the video screen
- **WHEN** a video plays and the user closes its screen
- **THEN** the sound stops, and search shows as it was

#### Scenario: Leaving the app
- **WHEN** a video plays and the user goes to the home screen
- **THEN** the sound stops, and when the user comes back the video is paused at the same position

#### Scenario: Coming back much later
- **WHEN** a video plays, the user goes to the home screen, and comes back after Android has stopped the player in the background
- **THEN** the video is paused at the same position, and plays on from it when the user presses play

#### Scenario: Resizing the window
- **WHEN** a video plays in split screen and the user resizes the window
- **THEN** it keeps playing without a pause

### Requirement: Playback makes way for other sound
The player SHALL follow Android's audio focus: it SHALL pause or lower its sound when another app takes audio focus, such as for a call or music, and SHALL pause when headphones are unplugged or a Bluetooth headset disconnects.

#### Scenario: Another app plays music
- **WHEN** a video plays and the user starts music in another app
- **THEN** the video pauses

#### Scenario: Unplugging headphones
- **WHEN** a video plays through headphones and they are unplugged
- **THEN** the video pauses, and doesn't play through the speaker

### Requirement: Media buttons do nothing yet
Until background play exists, headset and Bluetooth media buttons SHALL NOT control playback, and SHALL NOT start the app.

#### Scenario: A headset's play button while the app is in the background
- **WHEN** a video was playing, the user went home, and a headset's play button is pressed
- **THEN** no sound plays

#### Scenario: A headset's play button after the process ended
- **WHEN** the system ended the app's process in the background, and a headset's play button is pressed
- **THEN** the app doesn't start, and nothing crashes

### Requirement: The player shows no notification yet
Until background play exists, the app SHALL NOT show a media notification, and SHALL NOT run a foreground service.

#### Scenario: Playing with the app open
- **WHEN** a video plays
- **THEN** no notification about it shows

### Requirement: Playback stays private
The player SHALL fetch video only through the YouTube layer, which SHALL send its requests only over HTTPS to googlevideo.com and its subdomains, and SHALL never follow a redirect to another host. The app SHALL never log a stream link, a video's ID or its title, and Media3 SHALL write nothing to the log. Android's media session SHALL hold no video ID, title or link, and ExoPlayer SHALL report no playback session to Android's media metrics.

#### Scenario: Logs while playing
- **WHEN** the user plays a video, once to the end and once with the network turned off while it plays
- **THEN** the app's log lines hold no link, ID or title, and the failure's line names only its category and Media3's error code

#### Scenario: A redirect to another host
- **WHEN** a request for a stream link is redirected to a host outside googlevideo.com
- **THEN** the redirected request isn't sent, and the video area says that YouTube isn't loading right now

#### Scenario: The media session
- **WHEN** a video plays and another app reads Android's media sessions
- **THEN** the session's item has a random ID, and no video ID, title or link

## MODIFIED Requirements

### Requirement: The video screen survives process death
After the system ends the app's process in the background while the video screen shows, the user SHALL come back to the same video's screen, which loads the details again. Going back from it SHALL show the screen it was opened from.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the screen says that the same video is loading, then shows its details, and the video waits at the position where it paused for the user to press play

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description, and SHALL send them nowhere but YouTube. The only copies kept outside the screen SHALL be the video's ID in the state Android saves for a process that it ends in the background, the video's paused position, also user data, in that same saved state, and, in memory while its playback lasts, the playing video's ID and stream links in the playback service, which it SHALL drop when that playback is replaced or stops. The media session SHALL name the video only by a random playback ID.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category

### Requirement: The video screen loads the video's details
When the video screen opens, it SHALL ask YouTube for that video's details once, and show that it is loading until the answer arrives. A configuration change SHALL NOT ask again.

#### Scenario: Opening a video
- **WHEN** the user opens a video
- **THEN** the screen says that the video is loading, and the app asks YouTube for that video's details once

#### Scenario: Turning a tablet while the details show
- **WHEN** the screen shows a video's details on a tablet and the user turns it
- **THEN** the same details show, and the app asks YouTube for neither the details nor the stream links again
