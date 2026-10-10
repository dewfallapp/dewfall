# SubscriptionCard

The card near the top of a channel page that says, in words, whether the person is subscribed and what that means, with the one action that changes it.

## Anatomy
- Container: `surface-container-low`, `shape-large`, `space-md` padding, `space-sm` between parts.
- Status row: a 24dp glyph in `on-surface-variant`, then the state in `title-small`, `on-surface`, and one sentence in `body-medium`, `on-surface-variant`.
- Action, right-aligned below the text.

## States
| State | Glyph | Words | Action |
|---|---|---|---|
| Subscribed | `check` | "Subscribed. New videos download overnight while your phone charges." | Text button "Unsubscribe" |
| Not subscribed | `add` | "Not subscribed. Subscribe and this channel's new videos download overnight with the rest." | Filled button "Subscribe" with the `add` glyph |

- The words carry the state. The glyph is a second signal and the color never is.
- "Unsubscribe" asks first, in a dialog: "Unsubscribe from Kettle and Crumb? Videos you've already downloaded stay until cleanup." with "Cancel" and "Unsubscribe".
- Subscribing takes effect at once and needs no confirmation. The first downloads run that night.

## In lists
- Search results and other lists don't use the card. They show a compact button at the end of the channel row: outlined "Subscribed" with a `check` glyph, or tonal "Subscribe" with an `add` glyph. The word and glyph differ, not just the fill.

## Compose
- A `Column` with `Modifier.clip(MaterialTheme.shapes.large).background(surfaceContainerLow).padding(16.dp)`, a `Row` for the status, and a `TextButton` or `Button` aligned to the end.
- Announce the state change as a polite live region: "Subscribed to Kettle and Crumb".

## Don't
- No subscriber counts, notification bells or "join" upsells.
