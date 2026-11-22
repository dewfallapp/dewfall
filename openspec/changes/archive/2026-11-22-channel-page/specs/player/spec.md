## MODIFIED Requirements

### Requirement: The video screen shows the channel
The screen SHALL show the channel's name next to a round emblem with the name's first character, as one user-perceived character. The emblem SHALL be hidden from screen readers, which read the name. Tapping the channel SHALL open the channel's page and minimize the video, as the minimize button does, so playback goes on in the mini player under the channel page. When the screen the video was opened from is a channel page opened with the same channel ID as the video's, tapping the channel SHALL return to it instead of opening a second one. The channel SHALL tell screen readers that the tap opens the channel. A second tap while the channel page opens SHALL NOT open it twice.

#### Scenario: A channel name
- **WHEN** the channel is "Larkspur Lectures"
- **THEN** the screen shows "Larkspur Lectures" next to an emblem with "L"

#### Scenario: A name that starts with an emoji
- **WHEN** the channel's name starts with an emoji made of several code points, such as a flag
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

### Requirement: Opening the video that already plays goes on with it
Opening, from search results or a channel page, the video that the mini player plays SHALL open its video screen and go on with that playback, as tapping the mini player does. Opening another video SHALL replace it, and start the new one from the start.

#### Scenario: The same video from search
- **WHEN** a video plays in the mini player and the user opens the same video from search results
- **THEN** the video screen shows it playing on from where it was

#### Scenario: Another video from search
- **WHEN** a video plays in the mini player and the user opens another video from search results
- **THEN** the first video stops, and the new one starts playing from the start

#### Scenario: The same video from its channel page
- **WHEN** the user opened a video's channel page from the video screen, and taps that video in the channel's uploads
- **THEN** the video screen shows it playing on from where it was
