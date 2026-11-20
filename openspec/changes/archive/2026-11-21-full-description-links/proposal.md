# Proposal

## Why

The video screen shows a video's description as text that the user can select and copy, but YouTube shortens the text of a long link and ends it with "...". Copying such a link copies the shortened text, which doesn't open the page. On November 19, 2026, the description of NASA's "We Are NASA" showed `https://images.nasa.gov/details/NHQ_2...`. This is issue [#179](https://github.com/dewfallapp/dewfall/issues/179), which the check of the video screen, [#41](https://github.com/dewfallapp/dewfall/issues/41), found.

YouTube's answer holds the whole address, as the saved set VIDEO_BIG_BUCK_BUNNY, recorded on November 18, 2026, shows, and NewPipeExtractor v0.27.1 already gives it to the YouTube layer in each link's `href`, as design.md shows from its sources. The layer keeps only a link's text, so it drops the address.

## What Changes

- **A shortened link shows its whole address.** When a link's text ends with "..." and the text before it is the start of the link's address, the YouTube layer puts the whole address in place of the shortened text. Copying the link then copies an address that opens the page.
- **Every other link keeps its text.** A link whose text isn't a shortened address keeps the text YouTube shows, such as a timestamp, a hashtag, a channel's name, the name of a social media account, or an address shown whole. So does a shortened link in the few cases that design.md lists, such as an address that doesn't start with the link's text, because then it isn't clear that the two are the same address, or an address with a space, or a control or format character, which wouldn't open the page or could read differently from what the user copies.
- **No new requests, and no change to the video screen's code.** The address comes from the answer that the layer already reads. The screen already shows the description as selectable text, and a long address wraps onto the next lines.

Out of scope: tappable links. The video screen left them for a later change, if users miss them.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `player`: the requirement "The video screen shows the full description" says that a link YouTube shortened shows its whole address, and that every other link keeps its text.

## Impact

- `:core:youtube`: the function that turns NewPipeExtractor's description HTML into plain text, with its unit tests, and the replay test of the saved set VIDEO_BIG_BUCK_BUNNY, whose description has a shortened link. The saved responses stay as they are. They already hold the whole address, because the masking keeps the `q` parameter of YouTube's redirect links, as docs/testing.md says.
- docs/architecture.md: the "Descriptions" section says how the layer treats links.
- No new dependency, request, permission or log line. The description stays user data, as ADR 0007 defines it, and is never logged.
- Other work in flight: [#43](https://github.com/dewfallapp/dewfall/issues/43)'s change, in pull request #198, also changes the `player` spec and docs/architecture.md, but neither the description requirement nor the "Descriptions" section. This change was planned to archive after #43's, so the living spec would sync cleanly. It archived first instead, as tasks.md's dated note under the workflow follow-up says.
