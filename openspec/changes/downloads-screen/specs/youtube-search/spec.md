# Spec Delta

## RENAMED Requirements

- FROM: `### Requirement: Search YouTube opens from the inbox and the Channels screen`
- TO: `### Requirement: Search YouTube opens from the inbox, the Channels screen and the Downloads screen`

## MODIFIED Requirements

### Requirement: The keyboard opens once
The search field SHALL take focus and the on-screen keyboard SHALL open the first time the screen opens. Each time the user opens Search YouTube from the inbox, the Channels screen or the Downloads screen counts as a first time, because it opens a new screen with an empty field. It SHALL NOT open again by itself after a configuration change or when the user comes back to the screen. Sending a search with the on-screen keyboard's search key SHALL hide that keyboard, so the results show.

#### Scenario: First open
- **WHEN** the screen opens with no saved state, such as when the user opens it from the inbox, the Channels screen or the Downloads screen
- **THEN** the field has focus and the on-screen keyboard is open

#### Scenario: After a search
- **WHEN** the user sends a search with the on-screen keyboard's search key
- **THEN** the on-screen keyboard closes, and opening it again takes a tap on the field

#### Scenario: A configuration change
- **WHEN** the screen shows results, the keyboard is closed, and the theme changes
- **THEN** the keyboard stays closed

### Requirement: Search YouTube's back arrow returns to the screen it opened from
Search YouTube's top bar SHALL have a back arrow, which a screen reader SHALL hear as "Back". The back arrow and the back gesture SHALL return to the screen Search YouTube opened from, the inbox, the Channels screen or the Downloads screen. Tapping the back arrow twice SHALL return to that screen and go no further.

#### Scenario: Going back
- **WHEN** the user opened Search YouTube from the inbox, it shows results, and the user taps the back arrow
- **THEN** the inbox shows, and opening Search YouTube again shows an empty field

#### Scenario: Going back to the Channels screen
- **WHEN** the user opened Search YouTube from the Channels screen and makes the back gesture
- **THEN** the Channels screen shows, with "Channels" selected

#### Scenario: Going back to the Downloads screen
- **WHEN** the user opened Search YouTube from the Downloads screen and makes the back gesture
- **THEN** the Downloads screen shows, with "Downloads" selected

#### Scenario: A double tap on the back arrow
- **WHEN** the user opened Search YouTube from the inbox and taps its back arrow twice quickly
- **THEN** the inbox shows, and the app stays open

### Requirement: Search YouTube opens from the inbox, the Channels screen and the Downloads screen
Search YouTube SHALL open from the search button of the inbox, of the Channels screen and of the Downloads screen, and from the "Search YouTube" button of the inbox's and the Channels screen's "No channels yet" states. It SHALL show its title, the line "Videos to stream and channels to follow", and an empty search field. Tapping a second time while the screen opens SHALL NOT open it twice.

#### Scenario: Opening Search YouTube
- **WHEN** the user taps the inbox's search button
- **THEN** Search YouTube shows its title, the line "Videos to stream and channels to follow", an empty search field and a back arrow

#### Scenario: Opening Search YouTube from the Channels screen
- **WHEN** the user taps the Channels screen's search button
- **THEN** Search YouTube shows an empty search field and a back arrow, and no navigation bar

#### Scenario: Opening Search YouTube from the Downloads screen
- **WHEN** the user taps the Downloads screen's search button
- **THEN** Search YouTube shows an empty search field and a back arrow, and no navigation bar
