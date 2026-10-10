# ChannelHeader

The row that opens each channel's group in the inbox: emblem, channel name, and how many of its new videos are downloaded.

## Anatomy
- Emblem: a 36dp circle (`shape-full`) with a 1dp `outline-variant` ring. It shows the cached channel image. Until one is cached, and in every public design, it shows the channel's initial in `title-small`, `on-tertiary-container`, on `tertiary-container`. Never a real creator's face.
- Name: `title-small`, `on-surface`. Wraps onto as many lines as it needs.
- Count: `body-small`, `on-surface-variant`, in words: "2 of 3 downloaded".
- Optional trailing overflow icon button for channel actions (open channel page, unsubscribe).
- Layout: a `space-sm` gap between emblem and text, `space-sm` padding, 48dp minimum height. Tapping the name or emblem opens the channel page.

## Channel group
- In the inbox each channel is a group: this header, then its video rows, on `surface-container-low` with `shape-large` corners and `space-sm` padding. Groups stack with `space-lg` between them.

## Compose
- A `Row` with `Arrangement.spacedBy(8.dp)` and `verticalAlignment = CenterVertically`. The emblem is a 36dp `Box` clipped to `CircleShape` with `Modifier.border(1.dp, outlineVariant, CircleShape)`.
- The group is a `Column` with `Modifier.clip(MaterialTheme.shapes.large).background(surfaceContainerLow).padding(8.dp)`.
- Mark the name as a heading (`semantics { heading() }`) so screen readers can jump between channels.

## Don't
- No subscriber counts, view counts or "suggested" channels.
