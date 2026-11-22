## ADDED Requirements

### Requirement: The import opens from the empty inbox
The inbox's "No channels yet" state SHALL have an "Import from Google Takeout" button that opens the import screen. The import screen SHALL have a back button that returns to where it opened from, and after an import, a Done button that does the same.

#### Scenario: Opening the import
- **WHEN** the user has no channels and taps "Import from Google Takeout" on the inbox
- **THEN** the import screen opens

#### Scenario: After the import
- **WHEN** an import finished and the user taps Done
- **THEN** the inbox shows again, with the imported channels subscribed
