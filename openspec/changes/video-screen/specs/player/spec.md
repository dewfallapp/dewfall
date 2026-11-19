# Spec Delta

## Purpose

Lets a user open one video and see what it is: its title, channel, publish time and description, or why they can't be shown. Later changes add playback to the same screen.

## ADDED Requirements

### Requirement: The video screen loads the video's details
When the video screen opens, it SHALL ask YouTube for that video's details once, and show that it is loading until the answer arrives. A configuration change SHALL NOT ask again.

#### Scenario: Opening a video
- **WHEN** the user opens a video
- **THEN** the screen says that the video is loading, and the app asks YouTube for that video's details once

#### Scenario: Turning a tablet while the details show
- **WHEN** the screen shows a video's details on a tablet and the user turns it
- **THEN** the same details show, and nothing is sent to YouTube again

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
The screen SHALL show the video's whole description as plain text, with its line breaks, below the channel. The user SHALL be able to select and copy it. A video with no description SHALL show nothing in its place.

#### Scenario: A long description
- **WHEN** the description is longer than the screen
- **THEN** the screen scrolls to show all of it

#### Scenario: Copying a link
- **WHEN** the user long-presses a link in the description
- **THEN** they can select it and copy it

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
- **THEN** the screen says that the video isn't available, and that it may have been removed or not have started yet, with no "Try again"

### Requirement: Closing goes back
The screen SHALL have a close button at the top, named for screen readers. The close button and the system's back gesture SHALL return to the screen the video was opened from, as that screen was.

#### Scenario: Closing after a search
- **WHEN** the user opened a video from search results and taps the close button
- **THEN** Search YouTube shows the same text and results, and sends no new search

#### Scenario: The back gesture
- **WHEN** the user makes the system's back gesture on the video screen
- **THEN** the app goes back as the close button does

### Requirement: The video screen survives process death
After the system ends the app's process in the background while the video screen shows, the user SHALL come back to the same video's screen, which loads the details again. Going back from it SHALL show the screen it was opened from.

#### Scenario: Coming back after process death
- **WHEN** the system ended the app's process while it showed a video, and the user returns to it
- **THEN** the screen says that the same video is loading, then shows its details

### Requirement: The video screen fits any window
On a phone the screen SHALL stay in portrait. In any wider window, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, the screen SHALL keep its content in one centered column of readable width, and the background SHALL fill the window. No text or control SHALL sit under the system bars or the display cutout.

#### Scenario: A tablet in landscape
- **WHEN** the screen shows a video's details on a tablet in landscape
- **THEN** the details sit in one centered column no wider than the readable width, and nothing is cut off

### Requirement: Video details stay private
The app SHALL never log the video's ID, title, channel name or description, and SHALL send them nowhere but YouTube. The only copy kept outside the screen SHALL be the video's ID in the state Android saves for a process that it ends in the background.

#### Scenario: Logs after opening a video
- **WHEN** the user opens a video, once with details showing and once with the network off
- **THEN** the app's log lines hold no ID, title, channel name or description, and the failure's line names only the call and its category
