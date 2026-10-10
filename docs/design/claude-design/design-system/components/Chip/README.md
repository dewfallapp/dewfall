# Chip

Compact controls for filtering a list, toggling a setting on one video, or starting a small action, plus the non-interactive status pill.

## Kinds
- Filter chip: narrows a list, such as Search subtitles results by channel ("All watched (24)", "Larkspur Lectures (6)"). One or several selected.
- Toggle chip: turns a per-video setting on or off on the player. The label changes with the state: "Keep" becomes "Kept", "Mark as watched" becomes "Watched".
- Assist chip: one small action, with a leading glyph in `primary`, such as "Download now".
- Status pill: not a button. Reports when the next downloads run: "Next downloads tonight, on Wi-Fi while charging". `surface-container-high` fill, `on-surface-variant` text and glyph, `shape-full`.

## Anatomy
- 32dp minimum height, `shape-medium` corners, `space-md` side padding (`space-sm` on the side with a glyph), `space-sm` between glyph and label. Label in `label-large`. Glyphs 18dp.
- Unselected: 1dp `outline` border, label `on-surface-variant` (assist: `on-surface`).
- Selected: `secondary-container` fill, no border, label and glyph in `on-secondary-container`, and a leading `check` glyph that replaces any other glyph. The check, not the fill, says it's selected.
- Labels wrap at large font sizes and the chip grows taller. Chip rows wrap onto new lines; they don't scroll sideways out of view.

Borders use `outline`, not `outline-variant`. `outline-variant` is under 3:1 on the light surfaces and can't mark a control by itself.

## Compose
- `FilterChip(selected, onClick, label, leadingIcon = if (selected) checkIcon else null, shape = MaterialTheme.shapes.medium)`, with `FilterChipDefaults.filterChipColors(selectedContainerColor = secondaryContainer, selectedLabelColor = onSecondaryContainer)` and a border in `outline`.
- `AssistChip(onClick, label, leadingIcon, shape = MaterialTheme.shapes.medium)`.
- Put chip rows in a `FlowRow`.
- The status pill is a `Surface(shape = CircleShape)`, not a chip. It has no click handler.

## Don't
- No "Audio mode", quality or playback speed chips.
