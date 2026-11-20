## MODIFIED Requirements

### Requirement: The video screen shows the full description
The screen SHALL show the video's whole description as plain text, with its line breaks, below the channel. The user SHALL be able to select and copy it. A link whose text YouTube shortened SHALL show the link's whole address in place of the shortened text, and every other link SHALL keep the text YouTube shows. A video with no description SHALL show nothing in its place.

#### Scenario: A long description
- **WHEN** the description is longer than the screen
- **THEN** the screen scrolls to show all of it

#### Scenario: Copying a link
- **WHEN** the user long-presses a link in the description
- **THEN** they can select it and copy it

#### Scenario: A shortened link
- **WHEN** YouTube shortens the text of a long link in the description and ends it with "...", and the link's address starts with the text before the "..."
- **THEN** the screen shows the link's whole address in place of the shortened text, wrapping onto the next lines when it is wider than the screen, and copying it copies the whole address

#### Scenario: A link that isn't a shortened address
- **WHEN** a link's text is a timestamp, a hashtag, a name, or an address that YouTube shows whole
- **THEN** the screen shows the text that YouTube shows

#### Scenario: A shortened link whose address doesn't match
- **WHEN** YouTube shortens a link's text, but the link's address doesn't start with the text before the "...", or YouTube gives no address
- **THEN** the screen shows the shortened text

#### Scenario: No description
- **WHEN** the video has no description
- **THEN** the screen ends after the channel
