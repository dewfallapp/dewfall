# Spec Delta

## ADDED Requirements

### Requirement: The channel page saves the uploads of a subscribed channel
The channel page SHALL save the uploads it loaded, whole and as YouTube gave them, as "Saving a channel's uploads keeps every video" describes, while its card says "Subscribed": after the user taps "Subscribe" and the subscription is kept, and whenever the page finishes loading them. It SHALL send no request to YouTube for this beyond the page's own load. A page whose card doesn't say "Subscribed" SHALL save nothing.

#### Scenario: Subscribing from a channel page
- **WHEN** the user subscribes on the page of a channel whose uploads show, and goes back to the inbox
- **THEN** the inbox shows that channel's 2 latest videos, with no new request to YouTube

#### Scenario: Opening a subscribed channel's page
- **WHEN** the user subscribed to a channel last week, under the ID YouTube answers for, and opens its page today, which loads a video published yesterday
- **THEN** that video shows in the inbox

#### Scenario: A channel that moved, subscribed under its old ID
- **WHEN** the user subscribed to a channel under an ID that YouTube now sends on to another, and opens its page, which loads
- **THEN** the page saves the uploads under the ID YouTube answered for, and they show in the inbox only once the subscription is under that ID, which the manual refresh (#52) decides how to do

#### Scenario: A channel not subscribed to
- **WHEN** the user opens the page of a channel they don't subscribe to, and its uploads load
- **THEN** none of its videos is saved
