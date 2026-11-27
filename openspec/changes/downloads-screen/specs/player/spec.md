# Spec Delta

## MODIFIED Requirements

### Requirement: Opening the video that already plays goes on with it
Opening, from the inbox, search results, a channel page or the Downloads screen, the video that the mini player plays SHALL open its video screen and go on with that playback, as tapping the mini player does. Opening another video SHALL replace it, and start the new one from its saved position, or from the start when none is saved.

#### Scenario: The same video from search
- **WHEN** a video plays in the mini player and the user opens the same video from search results
- **THEN** the video screen shows it playing on from where it was

#### Scenario: Another video from search
- **WHEN** a video plays in the mini player and the user opens another video from search results, which has no saved position
- **THEN** the first video stops, and the new one starts playing from the start

#### Scenario: The same video from its channel page
- **WHEN** the user opened a video's channel page from the video screen, and taps that video in the channel's uploads
- **THEN** the video screen shows it playing on from where it was

#### Scenario: The same video from the inbox
- **WHEN** a video from the inbox plays in the mini player and the user taps it in the inbox again
- **THEN** the video screen shows it playing on from where it was

#### Scenario: The same video from the Downloads screen
- **WHEN** a video plays in the mini player and the user taps its row on the Downloads screen
- **THEN** the video screen shows it playing on from where it was
