# SearchField

The pill-shaped text field on the two search screens. It sits below the top app bar, never inside it, and its glyph and placeholder say which search it is.

## Anatomy
- Container: `surface-container-high`, `shape-full`, 56dp minimum height, `space-md` padding at the start and `space-md` between parts. It grows taller rather than cutting anything off.
- Leading glyph, 24dp, `on-surface-variant`: `search` for Search YouTube, `subtitles` for Search subtitles.
- Input: `body-large`, `on-surface`. Placeholder in `on-surface-variant`: "Search YouTube" or "Search saved subtitles". The placeholder is never the only place the search is named: the top app bar title names it too, so it stays clear once the person has typed.
- Clear button: a standard icon button with the `close` glyph, labelled "Clear search", shown only when there is text. The end padding drops to `space-xs` so the button's 48dp target fits.
- Focus: a 2dp `primary` outline with a 2dp gap. The keyboard opens as the screen opens.

## Placement
- One field per search screen, directly under the top app bar, with 16dp side margins and 8dp below it.
- Results or the empty state follow underneath. Search YouTube results need the internet; Search subtitles works offline.

## Compose
- A `TextField` with `shape = CircleShape`, `leadingIcon`, `trailingIcon` (only when the text isn't empty), `placeholder`, and `TextFieldDefaults.colors(focusedContainerColor = surfaceContainerHigh, unfocusedContainerColor = surfaceContainerHigh, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)`.
- `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)`.
- Don't set `singleLine = true` or a fixed height. At large font sizes the placeholder and the query wrap and the field grows.

## Don't
- No search field in the top app bar or on the inbox.
- No suggestions, trending searches or autocomplete from YouTube.
- Never tell the two searches apart by color.
