# Spec Delta

## MODIFIED Requirements

### Requirement: Subscriptions are kept on the phone
Subscribing to a channel SHALL keep its ID, name, avatars, its description when one is given, and the time of subscribing on the phone, until the user unsubscribes. They SHALL still be there after the app closes, the phone restarts or the app is updated, until a schema change before the first public release asks for the app's data to be cleared.

#### Scenario: Subscribing
- **WHEN** the user subscribes to a channel
- **THEN** the list of subscribed channels holds it, reading that channel gives its name, avatars, description and the time of subscribing, and the channel counts as subscribed

#### Scenario: After the app closes
- **WHEN** the user subscribed to a channel and the app's process ends and starts again
- **THEN** the channel is still subscribed, with the same details

#### Scenario: A channel never subscribed to
- **WHEN** the user has not subscribed to a channel
- **THEN** the channel does not count as subscribed, reading it gives nothing, and the list of subscribed channels doesn't hold it

#### Scenario: A subscription with no description
- **WHEN** the user subscribes from Search YouTube's channel results, which keep no description
- **THEN** reading that channel gives its name, avatars, no description and the time of subscribing, until a save of its uploads brings one, as "Saving a channel's uploads refreshes its details" says

### Requirement: Going back
The back arrow and the system's back gesture SHALL return to the screen under the channel page, as that screen was.

#### Scenario: Back from a channel opened from a video
- **WHEN** the user opened a video from search results, opened its channel page, and taps the back arrow
- **THEN** Search YouTube shows the same text and results, and the video plays on in the mini player

#### Scenario: Back from a channel opened from search
- **WHEN** the user opened a channel from Search YouTube's channel results, subscribed on its page, and taps the back arrow
- **THEN** Search YouTube shows the same text and results, and that channel's row says "Subscribed"
