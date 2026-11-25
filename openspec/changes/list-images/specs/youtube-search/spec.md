## MODIFIED Requirements

### Requirement: Results list the matching channels first
A successful search SHALL list the channels that YouTube returned under a "Channels" heading, above the videos under a "Videos" heading, in YouTube's order, showing a channel that YouTube lists twice only once, at its first place. Each row SHALL show a round emblem with the first character of the channel's name, which the channel's picture covers once it loads, as the list-images spec says, both hidden from screen readers, the name, and the excerpt of its description that YouTube gave, if any. A heading SHALL show only above a list that has rows.

#### Scenario: Channels and videos found
- **WHEN** a search for "sourdough" returns the channels "Kettle and Crumb" and "The Starter Jar", and three videos
- **THEN** the screen shows "Channels" with both channels in that order, then "Videos" with the three videos

#### Scenario: Only channels found
- **WHEN** a search returns two channels and no videos, or only Shorts
- **THEN** the screen shows "Channels" with both channels, and no "Videos" heading

#### Scenario: Only videos found
- **WHEN** a search returns videos and no channels
- **THEN** the screen shows "Videos" with the videos, and no "Channels" heading

#### Scenario: A channel listed twice
- **WHEN** a search returns the same channel twice
- **THEN** the screen lists it once, where it first appears

#### Scenario: A long name or a large font
- **WHEN** a channel's name is longer than one line, or the font size is large
- **THEN** the name wraps onto more lines and is never cut off, and the row's button shows whole

#### Scenario: A long excerpt
- **WHEN** the excerpt of a channel's description takes more than two lines
- **THEN** the row shows its first two lines, ending with an ellipsis

#### Scenario: No excerpt
- **WHEN** YouTube gave no description for a channel
- **THEN** its row shows the emblem, the name and the button
