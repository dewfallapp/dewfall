# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Search YouTube opens from the inbox`
- TO: `### Requirement: Search YouTube opens from the inbox and the Channels screen`

- FROM: `### Requirement: Search YouTube's back arrow returns to the inbox`
- TO: `### Requirement: Search YouTube's back arrow returns to the screen it opened from`

## MODIFIED Requirements

### Requirement: Search YouTube opens from the inbox and the Channels screen
Search YouTube SHALL open from the search button of the inbox and of the Channels screen, and from the "Search YouTube" button of their "No channels yet" states. It SHALL show its title, the line "Videos to stream and channels to follow", and an empty search field. Tapping a second time while the screen opens SHALL NOT open it twice.

#### Scenario: Opening Search YouTube
- **WHEN** the user taps the inbox's search button
- **THEN** Search YouTube shows its title, the line "Videos to stream and channels to follow", an empty search field and a back arrow

#### Scenario: Opening Search YouTube from the Channels screen
- **WHEN** the user taps the Channels screen's search button
- **THEN** Search YouTube shows an empty search field and a back arrow, and no navigation bar

### Requirement: Search YouTube's back arrow returns to the screen it opened from
Search YouTube's top bar SHALL have a back arrow, which a screen reader SHALL hear as "Back". The back arrow and the back gesture SHALL return to the screen Search YouTube opened from, the inbox or the Channels screen. Tapping the back arrow twice SHALL return to that screen and go no further.

#### Scenario: Going back
- **WHEN** the user opened Search YouTube from the inbox, it shows results, and the user taps the back arrow
- **THEN** the inbox shows, and opening Search YouTube again shows an empty field

#### Scenario: Going back to the Channels screen
- **WHEN** the user opened Search YouTube from the Channels screen and makes the back gesture
- **THEN** the Channels screen shows, with "Channels" selected

#### Scenario: A double tap on the back arrow
- **WHEN** the user opened Search YouTube from the inbox and taps its back arrow twice quickly
- **THEN** the inbox shows, and the app stays open

### Requirement: Search text stays private
The app SHALL never log the search text, or the titles, channel names, descriptions and IDs of results, and SHALL send them nowhere but YouTube. It SHALL keep no search history. The only copies kept outside the screen SHALL be the field's text of each open Search YouTube, at most one under the inbox and one under the Channels screen, and the ID of a video or a channel the user opened from the results, in the state Android saves for a process that it ends in the background. The player spec's "Video details stay private" covers the opened video.

#### Scenario: Logs after a search
- **WHEN** the user searches and results show
- **THEN** the app's log lines name the search and its outcome category and count, and hold no search text, title, channel name, description or ID

#### Scenario: Opening a result
- **WHEN** the user opens a result and the system later ends the app's process in the background
- **THEN** the state Android saved holds the field's text and that video's ID, and no title, channel name or other result

#### Scenario: Opening a channel
- **WHEN** the user opens a channel from the results and the system later ends the app's process in the background
- **THEN** the state Android saved holds the field's text and that channel's ID, and no channel name, description or other result

#### Scenario: Subscribing from a channel row
- **WHEN** the user subscribes from a channel row
- **THEN** no request leaves the phone, nothing about the channel is written to the app's log, and the channel is kept only as the subscriptions spec's "What Dewfall keeps stays on the phone" says

### Requirement: The keyboard opens once
The search field SHALL take focus and the on-screen keyboard SHALL open the first time the screen opens. Each time the user opens Search YouTube from the inbox or the Channels screen counts as a first time, because it opens a new screen with an empty field. It SHALL NOT open again by itself after a configuration change or when the user comes back to the screen. Sending a search with the on-screen keyboard's search key SHALL hide that keyboard, so the results show.

#### Scenario: First open
- **WHEN** the screen opens with no saved state, such as when the user opens it from the inbox or the Channels screen
- **THEN** the field has focus and the on-screen keyboard is open

#### Scenario: After a search
- **WHEN** the user sends a search with the on-screen keyboard's search key
- **THEN** the on-screen keyboard closes, and opening it again takes a tap on the field

#### Scenario: A configuration change
- **WHEN** the screen shows results, the keyboard is closed, and the theme changes
- **THEN** the keyboard stays closed
