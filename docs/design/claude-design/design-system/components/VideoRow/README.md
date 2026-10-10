# VideoRow

One video in a list: placeholder or cached thumbnail with its duration, the full title, and a meta line that ends in the download status.

## Anatomy
- Container: full-width, transparent at rest, `space-sm` padding. Hover, focus and press paint a state layer with `shape-medium` corners. Tapping the row opens the player, which plays the download or streams if there is none.
- Thumbnail: 72dp wide, 16:9, `shape-small`. Shows the cached thumbnail image; the placeholder is a plain `surface-container-highest` fill. Public designs always use the plain placeholder, never YouTube thumbnails.
- Duration tag: lower-right corner of the thumbnail, `label-small` in `on-surface`, on `surface` at `duration-scrim` opacity, `shape-extra-small`. Format: "42m", "1h 14m".
- Title: `title-medium`, `on-surface`. Wraps onto as many lines as it needs, with no hyphenation. Never set maxLines and never ellipsize.
- Meta line, in `body-small` and `on-surface-variant`, wrapping when it runs out of room: the CC badge if subtitles are saved and searchable, the publish date, a status phrase when the video isn't downloaded yet, and the download status control at the end.
- Layout: thumbnail and body in two columns with a `space-md` gap. The status control is right-aligned in the meta line so the title gets the full width.

## Download status
Every state has its own glyph, and the states that need explaining have words too, so the row reads the same under any dynamic palette.

| State | Meta words | Control |
|---|---|---|
| Downloaded | none | 32dp `primary-container` circle with a `check` glyph in `on-primary-container`. Not a button. Announced "Downloaded". |
| Downloading | "Downloading, 42%" | 40dp determinate ring in `primary` on a `surface-container-highest` track, `pause` glyph inside. Tapping pauses. |
| Queued | clock glyph + "Queued for tonight" | none |
| Missed overnight | "Missed last night" | Outlined icon button with the `download` glyph, announced "Download now, 820 MB": downloads now. Tapping the row streams instead. |
| Waiting for space | `pause_circle` glyph + "Waiting for space" | none. Shown while downloads are paused at the storage limit, under the inbox's storage-limit banner. |
| Offline, not downloaded | `cloud_off` glyph + "Can't stream right now" | none. Replaces "Missed last night" and the download button while the phone is offline. Downloaded rows don't change. |

In the dark scheme the downloaded circle is only 1.84:1 against `surface-container-low`. That's fine because the checkmark, at 7.15:1, is what carries the state.

## Compose
- A `Row` with `Modifier.clip(MaterialTheme.shapes.medium).clickable { }.padding(8.dp)`. Merge the row's text into one node with `semantics(mergeDescendants = true)`, and keep the status control a separate focus target with its own `contentDescription`.
- Thumbnail: `Box(Modifier.width(72.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small).background(surfaceContainerHighest))`.
- Title: `Text(title, style = MaterialTheme.typography.titleMedium)` with no `maxLines` or `overflow`.
- Meta line: a `FlowRow` so it wraps at large font sizes.
- Downloading: a 40dp determinate `CircularProgressIndicator` with `color = primary`, `trackColor = surfaceContainerHighest`.
- Missed overnight: `OutlinedIconButton` with border color `outline`.

## Don't
- No "…" anywhere in a title, no view counts, no recommendations between rows, no Shorts.
