# Button

Pill-shaped text buttons for actions that need a word, in four emphasis levels.

## Variants
- Filled: `primary` fill, `on-primary` label. The single most important action on a screen, at most one per view: "Download now" on a missed video, "Continue" in onboarding.
- Tonal: `secondary-container` fill, `on-secondary-container` label. Secondary actions that still deserve weight: "Manage downloads".
- Outlined: 1dp `outline` border, `primary` label. Alternatives next to a filled button: "Stream".
- Text: `primary` label, no container. The quietest action: "Raise limit", "Not now", dialog actions.

## Anatomy
- `shape-full`, 40dp minimum height, `space-lg` side padding (`space-md` on the side with a glyph, 12dp for text buttons), `space-sm` between glyph and label. Label in `label-large`, sentence case, verb first. Glyphs 18dp.
- Long labels wrap and the button grows taller. A button label is never cut off.
- Disabled: label and glyph in `on-surface` at `disabled-content` opacity, fill in `on-surface` at `disabled-container` opacity, no state layer. Prefer explaining why something can't happen over showing a disabled button.
- Touch target 48dp tall even though the button is 40dp.

## Compose
- `Button`, `FilledTonalButton`, `OutlinedButton` and `TextButton`. Shape and colors come from the theme; set the outlined border to `outline` explicitly.
- Leading glyph: `Icon(modifier = Modifier.size(18.dp))` followed by `Spacer(Modifier.width(8.dp))`.
- Don't set a fixed height on any button.

## Words
- Name what happens: "Download now", "Remove download", "Mark as watched". Never "OK", "Submit" or "Get it now!".
- Keep the same word through a flow: the button "Remove download" leads to the message "Download removed".
