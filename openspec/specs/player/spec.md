# player Specification

## Purpose

Lets a user open one video, see what it is, and watch it: its title, channel, publish time and description, or why they can't be shown, and the video playing above them, or why it can't play. Later changes add background play, the media notification, and the mini and fullscreen players.

## Requirements

### Requirement: The video screen loads the video's details
When the video screen opens, it SHALL ask YouTube for that video's details once, and show that it is loading until the answer arrives. A configuration change SHALL NOT ask again.

#### Scenario: Opening a video
- **WHEN** the user opens a video
- **THEN** the screen says that the video is loading, and the app asks YouTube for that video's details once

#### Scenario: Turning a tablet while the details show
- **WHEN** the screen shows a video's details on a tablet and the user turns it
- **THEN** the same details show, and the app asks YouTube for neither the details nor the stream links again

### Requirement: The video screen shows the title and when the video was published
The screen SHALL show the video's title, then a line that says how long ago it was published, counted from the phone's clock and calendar in the same units as Search YouTube's rows, in full words. It SHALL say "Live now" instead for a live stream on air, or one that has just ended while YouTube still serves it as live, and show no line when YouTube gave no time or the time is in the future.

#### Scenario: Published hours ago
- **WHEN** the video was published 7 hours before now
- **THEN** the screen shows the title, then "Published 7 hours ago"

#### Scenario: Published weeks ago
- **WHEN** the video was published 22 days before today
- **THEN** the line says "Published 3 weeks ago"

#### Scenario: Published under a minute ago
- **WHEN** the video was published 30 seconds before now
- **THEN** the line says "Published 1 minute ago"

#### Scenario: A live stream
- **WHEN** the video is a live stream on air
- **THEN** the line says "Live now"

#### Scenario: No publish time
- **WHEN** YouTube gave no publish time
- **THEN** the screen shows the title and no publish line

### Requirement: The video screen shows the channel
The screen SHALL show the channel's name next to a round emblem with the name's first character, as one user-perceived character. The emblem SHALL be hidden from screen readers, which read the name. Until channel pages exist, tapping the channel SHALL do nothing.

#### Scenario: A channel name
- **WHEN** the channel is "Larkspur Lectures"
- **THEN** the screen shows "Larkspur Lectures" next to an emblem with "L"

#### Scenario: A name that starts with an emoji
- **WHEN** the channel's name starts with an emoji made of several code points, such as a flag
- **THEN** the emblem shows that whole emoji

### Requirement: The video screen shows the full description
The screen SHALL show the video's whole description as plain text, with its line breaks, below the channel. The user SHALL be able to select and copy it. A link whose text YouTube shortened to the start of its address, followed by "..." or "…", SHALL show the whole address in its place, except in the cases that this requirement's scenarios name. Every other link SHALL keep the text YouTube shows. A video with no description SHALL show nothing in its place.

#### Scenario: A long description
- **WHEN** the description is longer than the screen
- **THEN** the screen scrolls to show all of it

#### Scenario: Copying a link
- **WHEN** the user long-presses a link in the description
- **THEN** they can select it and copy it

#### Scenario: A shortened link
- **WHEN** YouTube shortens the text of a long link in the description to the start of its address, such as "https://studio.blender.org/projects/b...", and the whole address holds no space, line break, control character or format character
- **THEN** the screen shows the link's whole address in place of the shortened text, wrapping onto the next lines when it is wider than the screen, and copying it copies the whole address

#### Scenario: A link that isn't a shortened address
- **WHEN** a link's text is a timestamp, a hashtag, a name, a title that ends with "...", or an address that YouTube shows whole
- **THEN** the screen shows the text that YouTube shows

#### Scenario: Shortened text that isn't an address
- **WHEN** a link's text ends with "...", but doesn't begin with "http://" or "https://" in lowercase
- **THEN** the screen shows the text that YouTube shows

#### Scenario: A shortened link whose address doesn't match
- **WHEN** a link's address doesn't start with the text before its "...", or is no longer than that text
- **THEN** the screen shows the shortened text

#### Scenario: An address with a space, a control character or a format character
- **WHEN** a shortened link's whole address holds a space, a line break, a control character, or a format character, which is invisible or reorders the text, such as a zero-width space or a right-to-left mark
- **THEN** the screen shows the shortened text

#### Scenario: A link that bold text splits
- **WHEN** bold, italic or struck-through text starts before a shortened link and ends inside it
- **THEN** the screen shows the shortened text

#### Scenario: A shortened address that isn't a link
- **WHEN** the description holds a shortened address that YouTube doesn't make a link
- **THEN** the screen shows the text that YouTube shows

#### Scenario: No description
- **WHEN** the video has no description
- **THEN** the screen ends after the channel

### Requirement: A failure says why
When YouTube can't give the details, the screen SHALL say why in words for each of YouTube's error categories, and SHALL offer "Try again" only when YouTube can't be reached, refuses to answer, or can't be read. "Try again" SHALL show that it is loading and ask once more.

#### Scenario: No connection
- **WHEN** opening a video fails because YouTube can't be reached
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

### Requirement: Closing goes back
The screen SHALL have a close button at the top, named for screen readers. The close button and the system's back gesture SHALL return to the screen the video was opened from, as that screen was.

#### Scenario: Closing after a search
- **WHEN** the user opened a video from search results and taps the close button
- **THEN** Search YouTube shows the same text and results, and sends no new search

#### Scenario: The back gesture
- **WHEN** the user makes the system's back gesture on the video screen
- **THEN** the app goes back as the close button does

#### Scenario: A double tap on close
- **WHEN** the user taps the close button twice quickly, or taps it during the back gesture
- **THEN** the app goes back once, to the screen the video was opened from, and doesn't close

### Requirement: The video screen survives process death
After the system ends the app's process in the background while the video screen shows, the user SHALL come back to the same video's screen, which loads the details again. Going back from it SHALL show the screen it was opened from.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the screen says that the same video is loading, then shows its details, and the video waits at the position where it paused for the user to press play

### Requirement: The video screen fits any window
On a phone the screen SHALL stay in portrait. In any wider window, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, the screen SHALL keep its content in one centered column of readable width, and the background SHALL fill the window. No text or control SHALL sit under the system bars or the display cutout.

#### Scenario: A tablet in landscape
- **WHEN** the screen shows a video's details on a tablet in landscape
- **THEN** the details sit in one centered column no wider than the readable width, and nothing is cut off

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description, and SHALL send them nowhere but YouTube. The only copies kept outside the screen SHALL be the video's ID in the state Android saves for a process that it ends in the background, the video's paused position, also user data, in that same saved state, and, in memory while its playback lasts, the playing video's ID and stream links in the playback service, which it SHALL drop when that playback is replaced or stops. The media session SHALL name the video only by a random playback ID.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category

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
When a video can't play, the video area SHALL say why in words, and SHALL offer "Try again" when trying again can help: when YouTube can't be reached, refuses to answer, can't be read, the network failed while playing, or playback stopped for another reason. When the phone can decode none of the video's formats, or its decoder fails while decoding, it SHALL say so with no "Try again". The details below SHALL stay.

#### Scenario: No connection while playing
- **WHEN** the network goes away and the player runs out of data
- **THEN** the video area says that YouTube can't be reached, with "Try again", and the details stay

#### Scenario: Trying again
- **WHEN** the user taps "Try again" after a playback failure
- **THEN** the player fetches the links again and plays from where it stopped

#### Scenario: No format the phone can decode
- **WHEN** the phone can decode none of the video's formats
- **THEN** the video area says that this video can't play on this phone, with no "Try again"

#### Scenario: A decoder that fails to start
- **WHEN** the phone's decoder for the chosen format fails to start, such as while another app holds it
- **THEN** the video area says that the video stopped playing, with "Try again"

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
