# Spec Delta

## ADDED Requirements

### Requirement: Search YouTube opens from the inbox
Search YouTube SHALL open from the inbox's search button, and from the "Search YouTube" button of the inbox's "No channels yet" state. It SHALL show its title, a line saying it finds videos to stream, and an empty search field. Tapping a second time while the screen opens SHALL NOT open it twice.

#### Scenario: Opening Search YouTube
- **WHEN** the user taps the inbox's search button
- **THEN** Search YouTube shows its title, the line saying it finds videos to stream, an empty search field and a back arrow

### Requirement: Search YouTube's back arrow returns to the inbox
Search YouTube's top bar SHALL have a back arrow, which a screen reader SHALL hear as "Back". The back arrow and the back gesture SHALL return to the inbox. Tapping the back arrow twice SHALL return to the inbox and go no further.

#### Scenario: Going back
- **WHEN** Search YouTube shows results and the user taps the back arrow
- **THEN** the inbox shows, and opening Search YouTube again shows an empty field

#### Scenario: A double tap on the back arrow
- **WHEN** the user taps Search YouTube's back arrow twice quickly
- **THEN** the inbox shows, and the app stays open

## MODIFIED Requirements

### Requirement: The keyboard opens once
The search field SHALL take focus and the on-screen keyboard SHALL open the first time the screen opens. Each time the user opens Search YouTube from the inbox counts as a first time, because it opens a new screen with an empty field. It SHALL NOT open again by itself after a configuration change or when the user comes back to the screen. Sending a search with the on-screen keyboard's search key SHALL hide that keyboard, so the results show.

#### Scenario: First open
- **WHEN** the screen opens with no saved state, such as when the user opens it from the inbox
- **THEN** the field has focus and the on-screen keyboard is open

#### Scenario: After a search
- **WHEN** the user sends a search with the on-screen keyboard's search key
- **THEN** the on-screen keyboard closes, and opening it again takes a tap on the field

#### Scenario: A configuration change
- **WHEN** the screen shows results, the keyboard is closed, and the theme changes
- **THEN** the keyboard stays closed

## REMOVED Requirements

### Requirement: The app opens on Search YouTube
**Reason**: The inbox is the app's home screen now, as the requirement foresaw ("until a later change makes another screen the home screen"), so Search YouTube has a screen before it and a back arrow.
**Migration**: See the inbox spec's "The app opens on the inbox", and "Search YouTube opens from the inbox" and "Search YouTube's back arrow returns to the inbox" here. The first keeps what the screen shows when it opens.
