# player Specification

## Purpose

Lets a user open one video, see what it is, and watch it: its title, channel, publish time and description, or why they can't be shown, and the video playing above them, or why it can't play, lets the sound play on in the background with a media notification, and lets the user minimize the video into a mini player or watch it in fullscreen.

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
The screen SHALL show the channel's name next to a round emblem with the name's first character, as one user-perceived character, until the channel's picture covers it, as the list-images spec says. The emblem and the picture SHALL be hidden from screen readers, which read the name. Tapping the channel SHALL open the channel's page and minimize the video, as the minimize button does, so playback goes on in the mini player under the channel page. When the screen the video was opened from is a channel page opened with the same channel ID as the video's, tapping the channel SHALL return to it instead of opening a second one. The channel SHALL tell screen readers that the tap opens the channel. A second tap while the channel page opens SHALL NOT open it twice.

#### Scenario: A channel name
- **WHEN** the channel is "Larkspur Lectures", and its picture hasn't loaded
- **THEN** the screen shows "Larkspur Lectures" next to an emblem with "L"

#### Scenario: A name that starts with an emoji
- **WHEN** the channel's name starts with an emoji made of several code points, such as a flag, and no picture covers the emblem
- **THEN** the emblem shows that whole emoji

#### Scenario: Opening the channel
- **WHEN** a video plays, the user opened it from search results, and taps its channel
- **THEN** the channel's page opens, the video plays on in the mini player, and the back gesture on the channel page returns to Search YouTube as it was

#### Scenario: A video opened from its own channel page
- **WHEN** the user opened a video from a channel's page, and taps that video's channel
- **THEN** the same channel page shows as it was, with one channel page on the back stack, and the video plays on in the mini player

#### Scenario: A double tap on the channel
- **WHEN** the user taps the channel twice quickly
- **THEN** one channel page opens, and one back gesture leaves it

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

### Requirement: The video screen survives process death
After the system ends the app's process in the background while the video screen shows, the user SHALL come back to the same video's screen, which loads the details again. Going back from it SHALL show the screen it was opened from.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the screen says that the same video is loading, then shows its details, and the video waits for the user to press play at the position saved for it, or, when none is saved, at the position it had when the user last left the app

### Requirement: The video screen fits any window
On a phone the screen SHALL stay in portrait, except in fullscreen. In any wider window, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, the screen SHALL keep its content in one centered column of readable width, and the background SHALL fill the window. No text or control SHALL sit under the system bars or the display cutout.

#### Scenario: A tablet in landscape
- **WHEN** the screen shows a video's details on a tablet in landscape
- **THEN** the details sit in one centered column no wider than the readable width, and nothing is cut off

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description. It SHALL send them nowhere but YouTube, except the title and channel name, which it SHALL give only to Android's media session, for the media notification, while the video's playback lasts. The only copies kept outside the screen SHALL be the video's ID and its paused position, also user data, in the state Android saves for a process that it ends in the background; the video's watch state, the details of videos saved with a channel's uploads, and, for a video the user downloads, its summary (ID, title, channel ID and name, length, publish time, kind and thumbnail links) and its download's record (state, chosen formats, sizes and progress), in the app's own database on the phone, which no backup or phone transfer copies; the downloaded video's files, in the app's own storage, which no backup or phone transfer copies either; and, in memory while its playback lasts, the playing video's ID, title, channel name and stream links in the playback service, which it SHALL drop when that playback is replaced or stops, once that playback's last save of its position has run. The media session SHALL identify the video only by a random playback ID, and SHALL hold no video ID.

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

#### Scenario: A downloaded video
- **WHEN** the user downloads a video
- **THEN** the app's database holds its summary and its download's record, its files sit in the app's own storage, and the app's log lines hold none of them

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

### Requirement: Playback makes way for other sound
The player SHALL follow Android's audio focus: it SHALL pause or lower its sound when another app takes audio focus, such as for a call or music, and SHALL pause when headphones are unplugged or a Bluetooth headset disconnects.

#### Scenario: Another app plays music
- **WHEN** a video plays and the user starts music in another app
- **THEN** the video pauses

#### Scenario: Unplugging headphones
- **WHEN** a video plays through headphones and they are unplugged
- **THEN** the video pauses, and doesn't play through the speaker

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

### Requirement: The sound plays on in the background
Leaving the app while a video plays, such as with the home gesture or by switching apps, and turning the screen off, SHALL keep the sound playing. Coming back SHALL show the same video playing at its current position, on the video screen or in the mini player. A configuration change, such as a resized window or a theme change, SHALL NOT stop, pause or restart playback.

#### Scenario: Going home
- **WHEN** a video plays and the user goes to the home screen
- **THEN** the sound goes on, and when the user comes back, the video plays on at its current position

#### Scenario: The screen off
- **WHEN** a video plays and the user turns the screen off for 10 minutes
- **THEN** the sound goes on the whole time

#### Scenario: Paused in the background, back much later
- **WHEN** the user paused a video on the video screen in the background, and Android stopped the player before the user came back
- **THEN** the video screen waits at the position where it paused, and plays on from it when the user presses play

#### Scenario: Resizing the window
- **WHEN** a video plays in split screen and the user resizes the window
- **THEN** it keeps playing without a pause

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

### Requirement: A video that hasn't started waits
When the user leaves the app after opening a video and before it has started playing, from the video screen or with the video minimized, the video SHALL NOT start in the background. Coming back SHALL show it paused, waiting for the user to press play.

#### Scenario: Leaving while the video loads
- **WHEN** the user opens a video and goes to the home screen before it starts playing
- **THEN** no sound plays, and when the user comes back, the video waits for play

#### Scenario: Leaving from the mini player while the video loads
- **WHEN** the user opens a video, minimizes it and goes to the home screen before it starts playing
- **THEN** no sound plays, and when the user comes back, the mini player offers to play it

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
- **THEN** Dewfall opens as it was, on the video screen, or on the screen under the video screen with the mini player, such as the inbox, Search YouTube or a channel page, and the video plays on

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
- **THEN** the mini player shows at the bottom of the screen that was under the video screen, such as the inbox, Search YouTube or a channel page

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
Opening, from the inbox, search results, a channel page or the Downloads screen, the video that the mini player plays SHALL open its video screen and go on with that playback, as tapping the mini player does. Opening another video SHALL replace it, and start the new one from its saved position, or from the start when none is saved.

#### Scenario: The same video from search
- **WHEN** a video plays in the mini player and the user opens the same video from search results
- **THEN** the video screen shows it playing on from where it was

#### Scenario: Another video from search
- **WHEN** a video plays in the mini player and the user opens another video from search results, which has no saved position
- **THEN** the first video stops, and the new one starts playing from the start

#### Scenario: The same video from its channel page
- **WHEN** the user opened a video's channel page from the video screen, and taps that video in the channel's uploads
- **THEN** the video screen shows it playing on from where it was

#### Scenario: The same video from the inbox
- **WHEN** a video from the inbox plays in the mini player and the user taps it in the inbox again
- **THEN** the video screen shows it playing on from where it was

#### Scenario: The same video from the Downloads screen
- **WHEN** a video plays in the mini player and the user taps its row on the Downloads screen
- **THEN** the video screen shows it playing on from where it was

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
The video screen's controls SHALL have a fullscreen button, named for screen readers, while the player is connected. Fullscreen SHALL show the video as large as the window allows, keeping its shape, on black. It SHALL use the dark color scheme in light mode too, and show the system bars only while its controls show, or for a moment after a swipe from the screen's edge. The screen SHALL stay on while the video plays.

#### Scenario: Entering fullscreen
- **WHEN** a video plays on the video screen and the user taps the fullscreen button
- **THEN** the video fills the screen in dark colors and plays on without a pause, and the system bars hide when the controls do

#### Scenario: Light mode
- **WHEN** the phone is in light mode and the user enters fullscreen
- **THEN** fullscreen uses the dark color scheme

#### Scenario: A system bar for a moment
- **WHEN** the controls are hidden and the user swipes from the screen's edge in fullscreen
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
- **THEN** fullscreen says that YouTube can't be reached, with "Try again" and the exit button

#### Scenario: A long title at the largest font size in fullscreen
- **WHEN** the title is long and the font size is the largest
- **THEN** the title wraps onto more lines in its box, and nothing is cut off

### Requirement: Fullscreen keeps clear of the system bars and the display cutout
In fullscreen, no text or control SHALL sit under the system bars or the display cutout, whether the bars show or not, so showing or hiding the bars never moves the title or the controls. No picture SHALL sit under the display cutout.

#### Scenario: A display cutout
- **WHEN** fullscreen shows in landscape on a phone with a display cutout
- **THEN** no text, control or picture sits under the cutout

#### Scenario: The system bars over fullscreen
- **WHEN** the system bars show in fullscreen in landscape with three-button navigation, after a tap or a swipe
- **THEN** they cover none of the title and controls, the exit button can be tapped, and nothing moves

### Requirement: Fullscreen controls hide while the video plays
While a video plays in fullscreen, the title, the controls and the system bars SHALL hide 3 seconds after the user last touched the screen or pressed a key, or later when the accessibility settings ask for more time. A tap on the video SHALL show them, and a tap while they show SHALL hide them. Whenever the video isn't playing, and while a screen reader is on, they SHALL show, even if they were hidden. When it plays again they SHALL show, and hide 3 seconds later.

#### Scenario: Watching
- **WHEN** a video plays in fullscreen and the user doesn't touch the screen for 3 seconds
- **THEN** the title, the controls and the system bars hide, and the video plays on

#### Scenario: Showing them again
- **WHEN** the controls are hidden and the user taps the video
- **THEN** the title, the controls and the system bars show again

#### Scenario: Hiding them with a tap
- **WHEN** a video plays in fullscreen with the controls showing and the user taps the video
- **THEN** the title, the controls and the system bars hide

#### Scenario: Paused
- **WHEN** the user pauses a video in fullscreen
- **THEN** the title and the controls stay, and a tap on the video doesn't hide them

#### Scenario: Paused from elsewhere while hidden
- **WHEN** the controls are hidden and the video pauses from a headset, the notification or a phone call
- **THEN** the title, the controls and the system bars show

#### Scenario: Waiting for data while hidden
- **WHEN** the controls are hidden and the player runs out of data
- **THEN** the title and the controls show, with the loading sign, until it plays again

#### Scenario: The end while hidden
- **WHEN** the controls are hidden and the video plays to its end
- **THEN** the title and the controls show, and the button offers to play it again

#### Scenario: A failure while hidden
- **WHEN** the controls are hidden and the video fails
- **THEN** the failure shows with the exit button

#### Scenario: Playing again
- **WHEN** the user presses play in fullscreen
- **THEN** the controls stay, and hide 3 seconds after the last touch

#### Scenario: A key press
- **WHEN** a video plays in fullscreen and the user presses a key on a hardware keyboard
- **THEN** the controls show, and the 3 seconds start again

#### Scenario: More time to act
- **WHEN** the phone's accessibility setting for time to take action is 1 minute
- **THEN** the controls hide only after 1 minute without a touch

#### Scenario: A screen reader
- **WHEN** TalkBack is on, or turns on, while a video plays in fullscreen
- **THEN** the title and the controls show and stay

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

### Requirement: Dewfall remembers where each video was left
Saving a video's position SHALL keep it on the phone with the time it was saved, for that video's ID, whether or not the video's details or its channel are saved. The latest save SHALL replace the earlier one. A video that never had a position saved and was never marked SHALL have no watch state.

#### Scenario: Saving a position
- **WHEN** the position 12 minutes 30 seconds is saved for a video that isn't marked watched
- **THEN** that video's watch state holds 12 minutes 30 seconds and the time of the save, and it isn't marked watched

#### Scenario: A later position
- **WHEN** a video's position was saved at 12 minutes, and later at 20 minutes
- **THEN** its watch state holds 20 minutes and the time of the later save

#### Scenario: A video from search
- **WHEN** a position is saved for a video whose channel isn't subscribed and whose details aren't saved
- **THEN** that video's watch state holds the position

#### Scenario: A video never played
- **WHEN** no position was saved and nothing was marked for a video
- **THEN** that video has no watch state

#### Scenario: After the app closes
- **WHEN** a position was saved and the app's process ends and starts again
- **THEN** the video's watch state still holds that position

### Requirement: Dewfall remembers when a video was marked watched
Marking a video watched SHALL keep the time of marking on the phone. Marking it watched again SHALL keep the first time, so a rule that counts from when a video was watched stays the same. Marking it unwatched SHALL clear that time. Neither SHALL change the saved position.

#### Scenario: Marking watched
- **WHEN** a video with a saved position of 40 minutes is marked watched
- **THEN** its watch state is marked watched with the time of marking, and still holds 40 minutes

#### Scenario: Marking watched with no position
- **WHEN** a video with no watch state is marked watched
- **THEN** its watch state is marked watched, with a position of zero

#### Scenario: Marking watched again
- **WHEN** a video was marked watched on Monday and is marked watched again on Wednesday
- **THEN** it stays marked watched since Monday

#### Scenario: Marking unwatched
- **WHEN** a video marked watched is marked unwatched
- **THEN** it is no longer marked watched, and its position stays as it was

#### Scenario: A new position after watching
- **WHEN** a video marked watched gets a new saved position
- **THEN** it stays marked watched since the first time, and holds the new position

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

### Requirement: The video screen downloads the video
Once the details show, unless the video is live or waiting to start, the video screen SHALL show a download chip in the chip row under the publish line, before the watched chip, following the video's download as the downloads spec describes:

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
