## MODIFIED Requirements

### Requirement: Videos are grouped by channel
The inbox SHALL show one card for each channel that has videos in it. A card SHALL show the channel's emblem with its initial or its picture, its name, and "1 video" or the number of its videos, such as "3 videos", then its videos newest first, in the order the subscriptions spec's "Saved videos keep YouTube's order" gives. Each video row SHALL show what the channel page's rows show: a thumbnail box or its image, with the length, the title, and how long ago the video was published.

#### Scenario: A card
- **WHEN** a channel has new videos published 2 hours and 3 days ago
- **THEN** its card says "2 videos" and lists both, the newer first, without the channel's name on the rows
