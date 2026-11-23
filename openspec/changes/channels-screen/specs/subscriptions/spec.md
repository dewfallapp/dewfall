# Spec Delta

## ADDED Requirements

### Requirement: The Channels screen lists the subscribed channels
The Channels screen SHALL list every subscribed channel in one card, ordered by name as the phone's language sorts it, ignoring case, with an accented letter next to its plain one, and channels with the same name in a fixed order. Above the card it SHALL say how many channels there are, such as "1 channel" or "4 channels". Each row SHALL show the channel's emblem, its name and a chevron.

#### Scenario: Four channels
- **WHEN** the user subscribes to "orbit Hour", "Kettle and Crumb", "Tidewater Woodshop" and "Larkspur Lectures" and opens the Channels screen
- **THEN** it says "4 channels" and lists Kettle and Crumb, Larkspur Lectures, orbit Hour and Tidewater Woodshop, in that order

#### Scenario: An accented name
- **WHEN** the phone's language is English and the user subscribes to "Zebra Hall", "Élodie Bakes" and "Delta Notes"
- **THEN** the Channels screen lists Delta Notes, Élodie Bakes and Zebra Hall, in that order

#### Scenario: One channel
- **WHEN** the user subscribes to one channel and opens the Channels screen
- **THEN** it says "1 channel" above that channel's row

### Requirement: The Channels screen's top bar
The Channels screen's top bar SHALL show "Dewfall", "Channels" under it, and a search button, which a screen reader SHALL hear as "Search YouTube". It SHALL have no back arrow and no menu button.

#### Scenario: The top bar
- **WHEN** the user opens the Channels screen
- **THEN** its top bar shows "Dewfall", "Channels" and the search button, and tapping the search button opens Search YouTube

### Requirement: A row says when the channel last uploaded
A row SHALL say when the channel last uploaded, as a time ago, such as "Last upload 7 hours ago" or "Last upload yesterday", counted as Search YouTube counts publish times. It SHALL take the newest saved video of the channel that has come out, Shorts and live streams included. A premiere or live stream that hasn't started SHALL NOT count. When no saved video of the channel counts, or its time lies ahead, the row SHALL show no such line.

#### Scenario: A video yesterday
- **WHEN** the newest saved video of a subscribed channel came out yesterday
- **THEN** its row says "Last upload yesterday"

#### Scenario: An upcoming premiere
- **WHEN** a channel's newest saved entry is a premiere that hasn't started, and its newest video before it came out 3 days ago
- **THEN** its row says "Last upload 3 days ago"

#### Scenario: Nothing saved yet
- **WHEN** the user subscribed to a channel and none of its videos is saved, such as right after an import
- **THEN** its row shows the emblem, the name and the chevron, and no last upload line

### Requirement: The Channels screen updates on its own
The Channels screen SHALL show a change to the subscriptions or the saved videos while it shows, without the user doing anything.

#### Scenario: Unsubscribing from a channel page
- **WHEN** the user opens a channel from the Channels screen, unsubscribes and goes back
- **THEN** the Channels screen no longer lists that channel, and its count is one lower

#### Scenario: A save
- **WHEN** the Channels screen shows a channel with no last upload line and that channel's uploads are saved
- **THEN** its row gains the last upload line

### Requirement: Tapping a channel opens its page
Tapping a row SHALL open that channel's page, with the Channels screen underneath, so going back shows the Channels screen as it was. Tapping a second time while the page opens SHALL NOT open it twice. A screen reader SHALL hear each row as one item, with the channel's name, its last upload line if it has one, and that tapping opens the channel.

#### Scenario: Opening a channel
- **WHEN** the user taps "Kettle and Crumb" on the Channels screen
- **THEN** the channel page of Kettle and Crumb opens, and one back gesture returns to the Channels screen at the same scroll position

#### Scenario: A double tap
- **WHEN** the user taps a row twice quickly
- **THEN** one channel page opens, and one back gesture returns to the Channels screen

### Requirement: The Channels screen says when there are no channels
When the user subscribes to no channel, the Channels screen SHALL show the channels glyph in a round disc, then say "No channels yet", then "Find channels with Search YouTube. Their new videos show in your inbox.", with a "Search YouTube" button that opens Search YouTube. It SHALL NOT suggest channels.

#### Scenario: No channels yet
- **WHEN** the user subscribes to no channel and opens the Channels screen
- **THEN** it says "No channels yet" and offers "Search YouTube", and tapping it opens Search YouTube

### Requirement: The Channels screen fits any window
On a phone the Channels screen SHALL stay in portrait. In any wider window it SHALL keep its content in one centered column of readable width, and its background SHALL fill the window. Its background SHALL draw under the system bars, and no text or control SHALL sit under the system bars, the display cutout, the mini player or the navigation bar. Text SHALL wrap at large font sizes and never be cut off. It SHALL work in light, dark and dynamic color.

#### Scenario: A tablet
- **WHEN** the Channels screen shows on a tablet in landscape
- **THEN** the card sits in one centered column of readable width, and the background fills the window

#### Scenario: A large font
- **WHEN** the font size is at its largest
- **THEN** every channel name and last upload line wraps, and none is cut off

#### Scenario: The mini player
- **WHEN** a video plays in the mini player and the user scrolls the Channels screen to the end
- **THEN** the last row sits fully above the mini player

### Requirement: The Channels screen keeps its state
A configuration change SHALL keep the Channels screen's scroll position. After the system ends the app's process in the background, the Channels screen SHALL come back with what the phone keeps, and its scroll position. It SHALL keep no copy of the channels outside the screen beyond what "What Dewfall keeps stays on the phone" covers.

#### Scenario: A theme change
- **WHEN** the user scrolled down the Channels screen and the theme changes
- **THEN** it shows the same channels at the same scroll position

#### Scenario: Process death
- **WHEN** the system ends the app's process in the background while the Channels screen shows, and the user comes back
- **THEN** it shows the same channels at the same scroll position, and the state Android saved holds no channel name, and no channel ID or search text beyond the IDs of open channel pages and the text of open Search YouTube screens, in either destination

### Requirement: The Channels screen keeps channels private
The Channels screen SHALL never log a channel's ID or name, and SHALL send nothing to any server. Showing it SHALL send no request to YouTube.

#### Scenario: Opening the Channels screen offline
- **WHEN** the phone has no connection and the user opens the Channels screen
- **THEN** it lists the subscribed channels as the phone keeps them, and no request leaves the phone

## MODIFIED Requirements

### Requirement: The channel page fits any window
On a phone the page SHALL stay in portrait. In any wider window the page SHALL keep its content in one centered column of readable width, its background SHALL fill the window, and no text or button SHALL sit under the system bars, the display cutout, the mini player or the navigation bar.

#### Scenario: A tablet in landscape
- **WHEN** a channel page shows on a tablet in landscape
- **THEN** its content sits in one centered column no wider than the readable width, and nothing is cut off

#### Scenario: The navigation bar
- **WHEN** a channel page opened from the Channels screen shows and the user scrolls to its last upload
- **THEN** the last upload sits fully above the navigation bar, and above the mini player when one shows
