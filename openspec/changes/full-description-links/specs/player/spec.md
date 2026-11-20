## MODIFIED Requirements

### Requirement: The video screen shows the full description
The screen SHALL show the video's whole description as plain text, with its line breaks, below the channel. The user SHALL be able to select and copy it. A link whose text YouTube shortened to the start of its address, followed by "..." or "…", SHALL show the whole address in its place, except in the cases that this requirement's scenarios name. Every other link SHALL keep the text YouTube shows. A video with no description SHALL show nothing in its place.

#### Scenario: A long description
- **WHEN** the description is longer than the screen
- **THEN** the screen scrolls to show all of it

#### Scenario: Copying a link
- **WHEN** the user long-presses a link in the description
- **THEN** they can select it and copy it

#### Scenario: A shortened link
- **WHEN** YouTube shortens the text of a long link in the description to the start of its address, such as "https://studio.blender.org/projects/b...", and the whole address holds no space, line break or invisible character
- **THEN** the screen shows the link's whole address in place of the shortened text, wrapping onto the next lines when it is wider than the screen, and copying it copies the whole address

#### Scenario: A link that isn't a shortened address
- **WHEN** a link's text is a timestamp, a hashtag, a name, a title that ends with "...", or an address that YouTube shows whole
- **THEN** the screen shows the text that YouTube shows

#### Scenario: Shortened text that isn't an address
- **WHEN** a link's text ends with "...", but doesn't begin with "http://" or "https://" in lowercase
- **THEN** the screen shows the text that YouTube shows

#### Scenario: A shortened link whose address doesn't match
- **WHEN** a link's address doesn't start with the text before its "...", or is no longer than that text
- **THEN** the screen shows the shortened text

#### Scenario: An address with a space or an invisible character
- **WHEN** a shortened link's whole address holds a space, a line break or an invisible character, such as a zero-width space or a right-to-left mark
- **THEN** the screen shows the shortened text

#### Scenario: A link that bold text splits
- **WHEN** bold, italic or struck-through text starts before a shortened link and ends inside it
- **THEN** the screen shows the shortened text

#### Scenario: A shortened address that isn't a link
- **WHEN** the description holds a shortened address that YouTube doesn't make a link
- **THEN** the screen shows the text that YouTube shows

#### Scenario: No description
- **WHEN** the video has no description
- **THEN** the screen ends after the channel
