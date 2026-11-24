## ADDED Requirements

### Requirement: The user picks the Takeout file
The import screen SHALL let the user pick one file with Android's file picker: either the .zip file that Google Takeout makes, or a subscriptions .csv file from it. Closing the picker without a file SHALL change nothing.

#### Scenario: A Takeout zip file
- **WHEN** the user picks a Takeout .zip file whose subscriptions file lists three channels
- **THEN** the same three channels are subscribed as when the user picks that subscriptions file itself

#### Scenario: Closing the picker
- **WHEN** the user opens the file picker and closes it without picking a file
- **THEN** no channel is subscribed, and the import screen looks as it did before

### Requirement: Importing subscribes to every channel in the file
Importing a file that holds subscriptions SHALL subscribe to every channel in it, with the channel's ID and the name the file gives, trimmed of white space at both ends, and with no avatars and no description. The time of subscribing SHALL be the time of the import. A channel that is already subscribed SHALL keep its details and its time of subscribing. A channel ID listed more than once SHALL count once, with the name from its first line.

#### Scenario: A subscriptions file
- **WHEN** the user picks a subscriptions .csv file that lists three channels
- **THEN** the three channels are subscribed, each with its ID and name from the file, no avatars, no description, and the time of the import as the time of subscribing

#### Scenario: A channel the user already follows
- **WHEN** the user subscribed to a channel last week, and imports a file that lists it under another name
- **THEN** the channel keeps last week's time of subscribing and the name it had

#### Scenario: Importing the same file again
- **WHEN** the user imports a file, then imports the same file again
- **THEN** the second import subscribes to nothing new, and every channel keeps its first time of subscribing

#### Scenario: A channel listed twice
- **WHEN** a file lists the same channel ID on two lines, with different names
- **THEN** the channel is subscribed once, with the name from the first of those lines

### Requirement: The import doesn't rely on Takeout's names
Takeout translates the names of its folders, files and columns into the account's language, so the import SHALL NOT rely on them. A subscriptions table SHALL be a table of comma-separated values whose lines hold three fields: a channel ID, the channel's link and the channel's name.

#### Scenario: A file in another language
- **WHEN** the user picks a .zip file whose subscriptions file, its folder and its header have names in another language
- **THEN** its channels are subscribed as they would be with English names

### Requirement: In a zip file, the table with the most channels counts
In a .zip file, the import SHALL read every file whose name ends in .csv, and use the one that holds the most channels, the first of them in the .zip file's order on a tie. It SHALL NOT import a table whose lines hold more or fewer than three fields, such as the table of the user's own channel.

#### Scenario: Other tables before the subscriptions
- **WHEN** a .zip file holds, before its subscriptions file, a .csv file with the user's own channel ID in a table with more than three fields, and a .csv file with no channel IDs
- **THEN** only the channels in the subscriptions file are subscribed, and the lines of the other files aren't counted

#### Scenario: A smaller table with channel IDs
- **WHEN** a .zip file holds, before its subscriptions file with ten channels, another three-field table with two channel IDs
- **THEN** only the ten channels of the subscriptions file are subscribed

### Requirement: The import reads comma-separated values
The import SHALL read fields as comma-separated values do: a field in double quotes may hold commas, line breaks and doubled double quotes, which stand for one double quote. It SHALL accept lines that end in a line feed or in a carriage return and a line feed, and a file that starts with a UTF-8 byte order mark.

#### Scenario: A name with a comma and quotes
- **WHEN** a channel's name in the file is in double quotes and holds a comma and a doubled double quote
- **THEN** the channel's name holds the comma and one double quote, and no line is counted as unreadable

#### Scenario: Line endings and a byte order mark
- **WHEN** a file starts with a UTF-8 byte order mark and its lines end in a carriage return and a line feed
- **THEN** its channels are subscribed as they would be without them

### Requirement: The import skips lines it can't read
A line that doesn't hold exactly three fields, whose first field isn't a channel ID, or whose name is empty after trimming white space, SHALL be skipped and counted as a line the import couldn't read. A blank line SHALL be skipped and not counted. The first line that isn't blank SHALL count as the header, neither imported nor counted, unless its first field is a channel ID. Skipped lines SHALL NOT stop the import of the other lines.

#### Scenario: Blank lines at the end
- **WHEN** a file ends with several blank lines
- **THEN** the blank lines aren't counted as lines it couldn't read

#### Scenario: Lines that aren't channels
- **WHEN** a file holds ten channels, a line whose first field is a handle such as `@name`, a line with two fields, and a line whose name is empty
- **THEN** the ten channels are subscribed, and three lines are counted as lines it couldn't read

#### Scenario: A file with no header
- **WHEN** a subscriptions file's first line is already a channel
- **THEN** that channel is subscribed with the others

### Requirement: The import reports what it did
After an import, the import screen SHALL show how many channels the file holds, each counted once, and list them in the file's order with their names. It SHALL say how many of them were newly subscribed, how many were already subscribed, and how many lines it couldn't read, leaving out each count that is zero. While the file is being read, the screen SHALL show that it is reading and SHALL NOT offer to choose another file.

#### Scenario: A first import
- **WHEN** the user imports a file with 40 channels into a Dewfall with none
- **THEN** the screen says 40 channels were found, lists the 40, and says all 40 are new

#### Scenario: Some already subscribed and some lines unreadable
- **WHEN** the user imports a file with 40 channels, of which 5 are already subscribed, and 2 lines it couldn't read
- **THEN** the screen says 40 channels were found, 35 are new, 5 were already subscribed, and 2 lines couldn't be read

#### Scenario: While reading
- **WHEN** the user picked a file and the import hasn't finished
- **THEN** the screen shows that it is reading the file, and has no button to choose a file

### Requirement: A failed import changes nothing
The import SHALL subscribe to nothing, and the screen SHALL say why and offer to choose another file, when the file holds no subscriptions table with at least one channel, when it can't be opened or read, such as a damaged .zip file, when it is a .csv file larger than 8 MiB, far more than any list of subscriptions, or when it is a .zip file larger than 256 MiB. In a .zip file, a .csv file larger than 8 MiB SHALL be passed over as not the subscriptions table.

#### Scenario: A file with no channels
- **WHEN** the user picks a .zip file from Takeout that holds no subscriptions, or a file that isn't from Takeout
- **THEN** no channel is subscribed, and the screen says the file holds no channels and suggests the .zip file from Takeout or the subscriptions file in it

#### Scenario: A damaged file
- **WHEN** the user picks a .zip file that ends before its first file does
- **THEN** no channel is subscribed, and the screen says Dewfall couldn't read the file

#### Scenario: A file that is too large
- **WHEN** the user picks a .csv file larger than 8 MiB
- **THEN** no channel is subscribed, and the screen says the file is too large and to export only the subscriptions from Takeout

#### Scenario: A zip file that is too large
- **WHEN** the user picks a .zip file larger than 256 MiB, such as a Takeout export with videos
- **THEN** no channel is subscribed, and the screen says the file is too large and to export only the subscriptions from Takeout

#### Scenario: A zip file with a name that isn't UTF-8
- **WHEN** the user picks a .zip file in which a file's name isn't valid UTF-8
- **THEN** no channel is subscribed, and the screen says Dewfall couldn't read the file

#### Scenario: Choosing another file
- **WHEN** an import failed and the user chooses another file that holds channels
- **THEN** that file's channels are subscribed and reported

### Requirement: An import subscribes to all of its channels or none
An import SHALL subscribe to every channel it found in one step, so a list of subscriptions never holds only part of a file. Leaving the import screen while the file is still being read SHALL subscribe to nothing. Once subscribing has started, it SHALL finish, even if the user leaves the screen.

#### Scenario: Leaving while reading
- **WHEN** the user leaves the import screen before the file has been read
- **THEN** no channel from the file is subscribed

#### Scenario: Leaving while subscribing
- **WHEN** the user leaves the import screen after the file was read, while its channels are being subscribed
- **THEN** every channel the file holds is subscribed

### Requirement: The Takeout file stays on the phone
The import SHALL read the picked file once, when the user picks it, and SHALL keep no access to it afterwards and no copy of it. It SHALL keep only the channels' IDs and names, as subscriptions. It SHALL NOT log the file's name, link or contents, or the channels in it, and SHALL send nothing anywhere: an import makes no network request.

#### Scenario: Importing offline
- **WHEN** the phone has no internet and the user imports a file
- **THEN** the import works as it does online

#### Scenario: What an import logs
- **WHEN** an import succeeds or fails
- **THEN** the app's log holds no channel ID, channel name, file name or file link from it

### Requirement: The empty Channels screen offers the import
When the Channels screen says "No channels yet", it SHALL show an "Import from Google Takeout" button under "Search YouTube", which opens the Takeout import screen. The import's back button, the back gesture and Done SHALL return to the Channels screen.

#### Scenario: Opening the import from the Channels screen
- **WHEN** the user subscribes to no channel, opens the Channels screen and taps "Import from Google Takeout"
- **THEN** the Takeout import screen opens, without the navigation bar

#### Scenario: Back to the Channels screen
- **WHEN** the user opened the import from the Channels screen and taps the back button
- **THEN** the Channels screen shows again, with "Channels" selected in the navigation bar
