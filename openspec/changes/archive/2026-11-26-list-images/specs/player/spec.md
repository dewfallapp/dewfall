## MODIFIED Requirements

### Requirement: The video screen shows the channel
The screen SHALL show the channel's name next to a round emblem with the name's first character, as one user-perceived character, until the channel's picture covers it, as the list-images spec says. The emblem and the picture SHALL be hidden from screen readers, which read the name. Tapping the channel SHALL open the channel's page and minimize the video, as the minimize button does, so playback goes on in the mini player under the channel page. When the screen the video was opened from is a channel page opened with the same channel ID as the video's, tapping the channel SHALL return to it instead of opening a second one. The channel SHALL tell screen readers that the tap opens the channel. A second tap while the channel page opens SHALL NOT open it twice.

#### Scenario: A channel name
- **WHEN** the channel is "Larkspur Lectures", and its picture hasn't loaded
- **THEN** the screen shows "Larkspur Lectures" next to an emblem with "L"

#### Scenario: A name that starts with an emoji
- **WHEN** the channel's name starts with an emoji made of several code points, such as a flag, and no picture covers the emblem
- **THEN** the emblem shows that whole emoji

#### Scenario: Opening the channel
- **WHEN** a video plays, the user opened it from search results, and taps its channel
- **THEN** the channel's page opens, the video plays on in the mini player, and the back gesture on the channel page returns to Search YouTube as it was

#### Scenario: A video opened from its own channel page
- **WHEN** the user opened a video from a channel's page, and taps that video's channel
- **THEN** the same channel page shows as it was, with one channel page on the back stack, and the video plays on in the mini player

#### Scenario: A double tap on the channel
- **WHEN** the user taps the channel twice quickly
- **THEN** one channel page opens, and one back gesture leaves it
