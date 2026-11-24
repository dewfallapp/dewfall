## ADDED Requirements

### Requirement: The empty inbox offers the import
When the inbox says "No channels yet", it SHALL show an "Import from Google Takeout" button under "Search YouTube", which opens the Takeout import screen. The "You're all caught up" state SHALL NOT show it.

#### Scenario: Opening the import
- **WHEN** the user subscribes to no channel and taps "Import from Google Takeout" on the inbox
- **THEN** the Takeout import screen opens

#### Scenario: Subscribed to a channel
- **WHEN** the user subscribes to one channel and the inbox says "You're all caught up"
- **THEN** the inbox shows no "Import from Google Takeout" button

### Requirement: The import returns to where it opened from
The Takeout import screen SHALL have a back button that returns to where it opened from, and after an import, a Done button that does the same. The system back gesture SHALL do the same as the back button.

#### Scenario: Going back without importing
- **WHEN** the user opened the import from the inbox and taps the back button before choosing a file
- **THEN** the inbox shows again, still saying "No channels yet"

#### Scenario: After the import
- **WHEN** an import of 12 channels finished, and the user taps Done
- **THEN** the inbox shows again, and says "You're all caught up" and "Nothing new from your 12 channels."
