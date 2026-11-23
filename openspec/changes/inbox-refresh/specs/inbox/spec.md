# Spec Delta

## MODIFIED Requirements

### Requirement: The app opens on the inbox
The app SHALL open on the inbox. Its top bar SHALL show "Dewfall", "Inbox" under it, or "Checking for new videos" while a check runs, and a search button, which a screen reader SHALL hear as "Search YouTube". The inbox SHALL have no back arrow, because no screen comes before it, and the back gesture on it SHALL leave the app as Android does.

#### Scenario: Opening the app
- **WHEN** the user opens the app
- **THEN** the inbox shows "Dewfall", "Inbox" and the search button

#### Scenario: Opening Search YouTube
- **WHEN** the user taps the search button
- **THEN** Search YouTube opens over the inbox

### Requirement: The inbox keeps videos private
The inbox SHALL never log a video's ID or title, or a channel's ID or name, and SHALL send nothing to any server, apart from the requests of a check for new videos that the user started, which go only to YouTube, through the YouTube layer. Showing the inbox SHALL send no request to YouTube.

#### Scenario: Opening the inbox offline
- **WHEN** the phone has no connection and the user opens the app
- **THEN** the inbox shows the new videos as the phone keeps them, and no request leaves the phone

#### Scenario: Checking for new videos
- **WHEN** the user taps "Check for new videos"
- **THEN** the only requests that leave the phone go to YouTube, for the subscribed channels' uploads, and the app's log holds no channel or video ID, name or title

## ADDED Requirements

### Requirement: The inbox has a button that checks for new videos
While the user subscribes to at least one channel and the inbox could be read, its top bar SHALL show a button after the search button, which a screen reader SHALL hear as "Check for new videos". A tap SHALL start a check for new videos, as the subscriptions spec's "A check for new videos saves every subscribed channel's uploads" says. Nothing else SHALL start one: not opening the app, and not pulling the list.

#### Scenario: Checking from the inbox
- **WHEN** the user subscribes to a channel whose uploads were never saved, and taps "Check for new videos"
- **THEN** the inbox shows that channel's new videos once the check has saved them, without the user doing anything else

#### Scenario: No channels yet
- **WHEN** the user subscribes to no channel
- **THEN** the top bar has no "Check for new videos" button

#### Scenario: An inbox that can't be read
- **WHEN** the inbox says "Your inbox can't be shown"
- **THEN** the top bar has no "Check for new videos" button, and the search button still works

#### Scenario: Opening the app sends nothing
- **WHEN** the user opens the app and doesn't tap "Check for new videos"
- **THEN** no request leaves the phone

### Requirement: The inbox shows a running check
While a check runs, the line under "Dewfall" SHALL read "Checking for new videos", the button's glyph SHALL turn slowly, and the button SHALL NOT start another check. When the phone's animations are off, the glyph SHALL stay still and the words SHALL still change. The inbox SHALL show a check that is still running whenever it shows again, such as after the user opened a video.

#### Scenario: A tap while checking
- **WHEN** a check runs and the user taps the button again
- **THEN** no second check starts, and the line still reads "Checking for new videos"

#### Scenario: Coming back during a check
- **WHEN** the user starts a check, opens a video while it runs, and goes back to the inbox
- **THEN** the inbox reads "Checking for new videos" until the check ends, then "Inbox"

#### Scenario: Animations off
- **WHEN** the phone's animations are off and a check runs
- **THEN** the glyph doesn't turn, and the line reads "Checking for new videos"

### Requirement: The inbox says when a check can't reach YouTube
When the last check stopped because YouTube couldn't be reached, the inbox SHALL show a banner above its cards with the wifi_off glyph and "Couldn't check for new videos. YouTube can't be reached. Check your connection." When it stopped because YouTube isn't loading, the banner SHALL show the error glyph and "Couldn't check for new videos because YouTube isn't loading." Both SHALL offer "Try again", which starts a check. The banner SHALL go when the next check starts.

#### Scenario: No connection
- **WHEN** the phone has no connection and the user taps "Check for new videos"
- **THEN** the inbox still shows the videos it had, with the banner "Couldn't check for new videos. YouTube can't be reached. Check your connection." and "Try again"

#### Scenario: YouTube isn't loading
- **WHEN** YouTube refuses the check's requests
- **THEN** the inbox shows the banner "Couldn't check for new videos because YouTube isn't loading." and "Try again"

#### Scenario: Trying again
- **WHEN** the banner shows and the user taps "Try again"
- **THEN** the banner goes, and the line reads "Checking for new videos"

#### Scenario: All caught up
- **WHEN** the inbox says "You're all caught up" and a check can't reach YouTube
- **THEN** the banner shows above "You're all caught up"

### Requirement: The inbox says nothing about a channel that fails on its own
When a check skips a channel that YouTube closed or can't show, as the subscriptions spec says, and the check goes on, the inbox SHALL show no banner or message about it. When a check ends with no new video, the inbox SHALL say nothing more than it did before the check.

#### Scenario: One closed channel
- **WHEN** the user subscribes to 3 channels, YouTube closed one of them, and the user taps "Check for new videos"
- **THEN** the check ends with no banner, and the other two channels' new videos show

#### Scenario: Nothing new
- **WHEN** a check finds no new video
- **THEN** the line reads "Inbox" again and the inbox shows what it showed before

### Requirement: A check leaves no trace after the app's process ends
A check SHALL keep its state only while the app's process lives. After the system ends the process, the inbox SHALL show no running check and no banner, and SHALL keep no copy of the check's state in what Android saves for the screen.

#### Scenario: Process death during a check
- **WHEN** a check runs, and the system ends the app's process in the background
- **THEN** the user comes back to an inbox that reads "Inbox", with no banner, and the videos the check saved before the process ended
