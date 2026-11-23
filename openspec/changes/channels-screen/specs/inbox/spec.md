# Spec Delta

## ADDED Requirements

### Requirement: A navigation bar switches between the inbox and the channels
The app SHALL show a navigation bar at the bottom with two destinations, "Inbox" and "Channels", each with its icon and label, and no count badges. The destination that shows SHALL be marked as selected, by a filled icon and an indicator behind it, and a screen reader SHALL hear it as selected. Tapping the other destination SHALL show it.

#### Scenario: Opening Channels
- **WHEN** the inbox shows and the user taps "Channels" in the navigation bar
- **THEN** the Channels screen shows, and "Channels" is selected

#### Scenario: Opening the app
- **WHEN** the user opens the app
- **THEN** the inbox shows, and "Inbox" is selected

### Requirement: The navigation bar shows only where it leads
The navigation bar SHALL show on the inbox, the Channels screen and a channel page. It SHALL hide on Search YouTube and the video screen. While a channel page shows, the destination it was opened from SHALL be selected.

#### Scenario: A channel page from the Channels screen
- **WHEN** the user opens a channel from the Channels screen
- **THEN** the navigation bar shows under the channel page, with "Channels" selected

#### Scenario: Search YouTube
- **WHEN** the user opens Search YouTube from the inbox
- **THEN** no navigation bar shows

### Requirement: Each destination keeps its own history
Each destination SHALL keep the screens opened from it and their state, such as a channel page opened from the Channels screen, while the other destination shows, and across configuration changes and process death. Showing a destination again SHALL show its last screen as it was.

#### Scenario: Switching and coming back
- **WHEN** the user opens a channel from the Channels screen, taps "Inbox", then taps "Channels"
- **THEN** the same channel page shows, and one back gesture returns to the Channels screen

#### Scenario: Search YouTube in both destinations
- **WHEN** the user searches "bread" from the inbox and opens a video, then its channel, taps "Channels", searches "tea" there and opens a video, then its channel, taps "Inbox" and makes the back gesture
- **THEN** Search YouTube shows "bread" and its results; after one more back gesture to the inbox, tapping "Channels" and making the back gesture, Search YouTube shows "tea" and its results

#### Scenario: Process death
- **WHEN** the system ends the app's process in the background while a channel page opened from the Channels screen shows, and the user comes back
- **THEN** that channel page shows with "Channels" selected, and one back gesture returns to the Channels screen

### Requirement: The user leaves the app through the inbox
The back gesture on the Channels screen SHALL show the Inbox destination as it was, its last screen included, with "Inbox" selected. The back gesture on the inbox SHALL leave the app as Android does.

#### Scenario: Back from the Channels screen
- **WHEN** the Channels screen shows and the user makes the back gesture
- **THEN** the Inbox destination shows as it was, and when that is the inbox itself, a second back gesture leaves the app

### Requirement: Tapping the selected destination returns to its first screen
Tapping the destination that is already selected SHALL close the screens opened from it and show its first screen, the inbox or the Channels screen. On its first screen, tapping it SHALL change nothing.

#### Scenario: Reselecting Channels
- **WHEN** a channel page opened from the Channels screen shows and the user taps "Channels"
- **THEN** the Channels screen shows

### Requirement: The mini player sits above the navigation bar
While both show, the mini player SHALL sit directly above the navigation bar, and no screen's text or control SHALL sit under either. Tapping a destination SHALL leave playback as it is.

#### Scenario: Switching while a video plays
- **WHEN** a video plays in the mini player and the user taps "Channels"
- **THEN** the video plays on in the mini player, above the navigation bar, over the Channels screen

## MODIFIED Requirements

### Requirement: The inbox keeps its state
A configuration change, such as a theme change, a fold or a resized window, SHALL keep the inbox's scroll position. After the system ends the app's process in the background, the inbox SHALL come back with what the phone keeps, and its scroll position. The inbox SHALL keep no copy of the videos or channels outside the screen beyond what the subscriptions spec's "What Dewfall keeps stays on the phone" covers.

#### Scenario: A theme change
- **WHEN** the user scrolled down the inbox and the theme changes
- **THEN** the inbox shows the same videos at the same scroll position

#### Scenario: Process death
- **WHEN** the system ends the app's process in the background while the inbox shows, and the user comes back
- **THEN** the inbox shows the same videos, and the state Android saved holds no video title or channel name, and no video ID, channel ID or search text beyond those of open video screens, open channel pages and open Search YouTube screens, in either destination, as the player, subscriptions and youtube-search specs allow

#### Scenario: Process death on a page opened from the inbox
- **WHEN** the system ends the app's process while a video screen or channel page opened from the inbox shows
- **THEN** the state Android saved holds that page's own video or channel ID, as the player spec and the subscriptions spec allow, nothing from the inbox, and from the Channels destination only the IDs of its open channel pages and the text of its open Search YouTube screen

### Requirement: The inbox fits any window
On a phone the inbox SHALL stay in portrait. In any window wider than a phone in portrait, such as a tablet, an unfolded foldable, landscape on a large screen or split screen, it SHALL keep its content in one column of readable width, centered, and its background SHALL fill the window. No text or control SHALL sit under the navigation bar.

#### Scenario: A tablet
- **WHEN** the inbox shows on a tablet in landscape
- **THEN** the cards sit in one centered column of readable width, and the background fills the window

#### Scenario: The navigation bar
- **WHEN** the user scrolls the inbox to the end
- **THEN** "That's everything for now." sits fully above the navigation bar

