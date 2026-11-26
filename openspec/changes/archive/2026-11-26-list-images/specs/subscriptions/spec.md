## MODIFIED Requirements

### Requirement: The channel page shows the channel
The page SHALL have a back arrow, named for screen readers, then a round emblem with the first user-perceived character of the channel's name, which the channel's picture covers once it loads, as the list-images spec says, the name as a heading, and the channel's description. The emblem and the picture SHALL be hidden from screen readers, which read the name. The name SHALL wrap and never be cut off. A channel with no description SHALL show nothing in its place.

#### Scenario: A channel
- **WHEN** the channel is "Kettle and Crumb", with a two-line description, and its picture hasn't loaded
- **THEN** the page shows an emblem with "K", the name, and both lines

#### Scenario: No description
- **WHEN** the channel has no description
- **THEN** the page shows the name, then the subscription card

### Requirement: The Channels screen lists the subscribed channels
The Channels screen SHALL list every subscribed channel in one card, ordered by name as the phone's language sorts it, ignoring case, with an accented letter next to its plain one, and channels with the same name in a fixed order. Above the card it SHALL say how many channels there are, such as "1 channel" or "4 channels". Each row SHALL show the channel's emblem, or its picture once it loads, as the list-images spec says, its name and a chevron.

#### Scenario: Four channels
- **WHEN** the user subscribes to "orbit Hour", "Kettle and Crumb", "Tidewater Woodshop" and "Larkspur Lectures" and opens the Channels screen
- **THEN** it says "4 channels" and lists Kettle and Crumb, Larkspur Lectures, orbit Hour and Tidewater Woodshop, in that order

#### Scenario: An accented name
- **WHEN** the phone's language is English and the user subscribes to "Zebra Hall", "Élodie Bakes" and "Delta Notes"
- **THEN** the Channels screen lists Delta Notes, Élodie Bakes and Zebra Hall, in that order

#### Scenario: One channel
- **WHEN** the user subscribes to one channel and opens the Channels screen
- **THEN** it says "1 channel" above that channel's row
