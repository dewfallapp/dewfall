## ADDED Requirements

### Requirement: The Downloads screen shows the space downloads take
The Downloads screen SHALL show a card at its top, under the top app bar and above the list of downloads, that says "Offline storage" and the space that downloads take on the phone, such as "3.6 GB", in the phone's own units and number format. Until the app has a storage limit, the card SHALL show no bar and no limit. A screen reader SHALL read the card as one item, with its heading and the space. The inbox SHALL NOT show the space.

#### Scenario: Opening the Downloads screen
- **WHEN** the user opens the Downloads screen with 3.6 GB of downloads on the phone
- **THEN** the first thing under the top app bar is a card that says "Offline storage" and "3.6 GB", with no bar

#### Scenario: With a screen reader
- **WHEN** TalkBack moves to the card
- **THEN** it reads "Offline storage" and the space as one item

### Requirement: The space counts every byte downloads keep
The space on the storage card SHALL count every byte that downloads keep, as the download job last recorded them: finished downloads' files, and the partial files that waiting, downloading, paused and failed downloads keep. It SHALL change by itself while the screen shows, as downloads grow or go. With nothing downloaded it SHALL say zero. When the phone can't read the space, the card SHALL show its heading with no amount.

#### Scenario: Finished and partial downloads
- **WHEN** one video is downloaded with 2.5 GB of files, and another is paused with 1.1 GB of its files on the phone
- **THEN** the card says "3.6 GB"

#### Scenario: While a download runs
- **WHEN** the Downloads screen is open and a download runs
- **THEN** the space on the card grows as the download's files grow, without leaving the screen

#### Scenario: A download stops between two records of its bytes
- **WHEN** a download stops between two of the download job's records of its bytes, such as when the app ends, the download pauses or the connection drops
- **THEN** the card counts that download's last recorded bytes, which can be up to about a second of downloading less than its files hold, until the download runs again

#### Scenario: A failed download that keeps nothing
- **WHEN** a download fails because the video became private, and its partial files are deleted
- **THEN** the space on the card no longer counts them

#### Scenario: Nothing downloaded
- **WHEN** the user opens the Downloads screen before downloading any video
- **THEN** the card says "Offline storage" and a space of zero

#### Scenario: The space can't be read
- **WHEN** the phone can't read the space downloads take
- **THEN** the card shows "Offline storage" with no amount, and the rest of the Downloads screen stays as it is
