# Design

## Context

See proposal.md for why.

**How the layer reads a description today.** NewPipeExtractor's `StreamExtractor.getDescription()` gives a video's description as HTML. `htmlDescriptionToText` in core/youtube/src/main/kotlin/io/github/dewfallapp/core/youtube/newpipe/DescriptionText.kt escapes each `<` that doesn't start one of NewPipeExtractor's own tags, parses the HTML with jsoup, and takes the body's `wholeText()`. A `<br>` becomes a newline, and a link keeps only its text. docs/architecture.md's "Descriptions" section says the same.

**What NewPipeExtractor v0.27.1 writes for a link.** Read from its sources jar, the version in gradle/libs.versions.toml:

- `YoutubeStreamExtractor.getDescription()` ([lines 260-285](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java#L260-L285)) reads the page's older `description` runs first, then its `attributedDescription`, and only then the player's `shortDescription`, as plain text.
- For the attributed description, `YoutubeDescriptionHelper` ([lines 225-263](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeDescriptionHelper.java#L225-L263)) writes each command run as `<a href="` + `Entities.escape(url)` + `">`, around the text that YouTube shows. For the older runs, `getTextFromObject` ([lines 819-826](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L819-L826)) does the same.
- The `url` comes from `getUrlFromNavigationEndpoint` ([lines 692-717](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java#L692-L717)). For a link through YouTube's redirect, `https://www.youtube.com/redirect?...&q=...`, it is the `q` parameter, decoded with `URLDecoder` as UTF-8. So the `href` holds the page's whole address, not the redirect link. For a link inside YouTube, such as to a video, a channel or a playlist, it is a youtube.com address that NewPipeExtractor builds from the link's endpoint.
- For a link with an accessibility label that isn't YouTube's, such as a social media account, NewPipeExtractor writes the label as the link's text, such as "Instagram: name" ([lines 265-294](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeDescriptionHelper.java#L265-L294)).

**What the saved responses show.** The saved set VIDEO_BIG_BUCK_BUNNY's 06-next.json has an attributed description with two links through YouTube's redirect, and no older `description` runs. Running NewPipeExtractor v0.27.1's `attributedDescriptionToHtml` on it, offline with jshell on November 21, 2026, gave:

```
More info: &nbsp;<a href="https://studio.blender.org/projects/big-buck-bunny/">https://studio.blender.org/projects/b...</a><br><br>
Learn more about this UHD high frame-rate version at: <a href="http://bbb3d.renderfarming.net/">http://bbb3d.renderfarming.net</a>
```

The first link is a shortened one: its text is the start of its address, followed by three full stops. The second is shown whole, and its address has a `/` that its text lacks. The player response's `shortDescription` in 02-player.json has both addresses whole, but NewPipeExtractor reads it only when the page has no description of its own.

**No new recording.** The masking keeps the `q` parameter of redirect links, as docs/testing.md's "Links" table says, so the saved set already holds the whole address. docs/testing.md asks for a new recording when a NewPipeExtractor update changes its requests, and this change sends no new request and updates nothing.

## Goals / Non-Goals

**Goals:**

- A shortened link's whole address is in the description text that VideoDetails holds, so the video screen shows it and the user can copy it.
- No other link's text changes.

**Non-Goals:**

- Tappable links, and links as their own data in VideoDetails. The video screen left tappable links for later. When a change brings them, it can carry each link's address and place next to the text, without undoing this one.
- A channel's description, which NewPipeExtractor already gives as plain text.
- Any change to the video screen's code, or to the requests the layer sends.

## Decisions

### The layer replaces the text, in the step that turns HTML into text

The YouTube layer's `htmlDescriptionToText` is the one place that sees each link's address, so it does the work: after jsoup parses the HTML, and before it takes the text, each `<a>` whose text is a shortened form of its `href` gets the `href` as its text. jsoup's `attr("href")` gives the address with NewPipeExtractor's escaping undone, so `&amp;` in the HTML is `&` in the address.

The text stays the description that YouTube shows, with only the shortening undone, so this doesn't reshape data that a later phase needs. The links' places and addresses are still lost, as they are today, and the non-goals say how a later change can keep them.

Alternatives:

- **The whole address next to the shortened text**, such as `https://studio.blender.org/projects/b... (https://studio.blender.org/projects/big-buck-bunny/)`. It repeats the start of the address, and a user who copies the shown link still copies the shortened one. The questions below ask the maintainer.
- **The player's `shortDescription`**, which has every address whole. NewPipeExtractor exposes no way to read it when the page has a description, and it would lose NewPipeExtractor's labels for social media links.
- **Links as data in VideoDetails, with the screen building the text.** It changes the model, the repository and the video screen, which #43's pull request #198 is changing, for a result that the user sees the same way. It belongs with tappable links.

### Which links change

A link's text becomes its address only when all of these hold:

1. Its text ends with three full stops, `...`, or with the ellipsis character, `…`. The saved set and the NASA video in the issue show three full stops. The ellipsis character costs nothing to accept, because the next rules still apply.
2. Its text before them starts with `http://` or `https://`, in lowercase. A name or a title that ends with "...", such as a video's title in a link to it, isn't an address.
3. The address starts with the text before them, exactly, and is longer. So the text is the start of this very address, and the address adds to it. When YouTube or NewPipeExtractor writes the address differently, such as another host, `youtu.be` against `www.youtube.com`, or a character decoded differently, the link keeps its shortened text, as it does today.
4. The address has no space of any kind, no line break, no control character and no format character, Unicode's categories Zs, Zl, Zp, Cc and Cf. NewPipeExtractor decodes `q` with `URLDecoder`, which turns a `+` into a space, so such an address would not open the page, and a line break in it would break the description's lines. A format character, such as the right-to-left override U+202E or a zero-width space, is invisible or reorders the text, so the address on screen could read differently from the one the user copies.

A link that fails any of them keeps its text. In the saved set, the first link changes and the second, shown whole, keeps its text, without the `/` that its address adds.

Alternatives:

- **Every link shows its address.** Timestamps, hashtags and channel names would become long youtube.com addresses.
- **Every link whose text looks like an address shows its address.** Addresses shown whole would change too, such as the second link above, which would gain a `/` that the creator didn't write. The questions below ask the maintainer.

### A link split into pieces keeps its shortened text

NewPipeExtractor's `runsToHtml` ([lines 145-223](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.27.1/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeDescriptionHelper.java#L145-L223)) keeps its tags nested. When a style run, such as bold text, opens before a link and ends inside it, it closes the link, closes the style, and opens the link again, so one link becomes two `<a>` elements with the same `href`:

```
<b>See <a href="https://example.com/a/long">https:</a></b><a href="https://example.com/a/long">//example.com/a/lo...</a>
```

A style run that starts inside a link and ends after it splits the style instead, and the link stays whole. Both shapes come from running v0.27.1's `attributedDescriptionToHtml` on made-up attributed descriptions with jshell on November 21, 2026. In the split shape, the first piece doesn't end with "..." and the second doesn't start with `http`, so both keep their text. Joining them would need NewPipeExtractor's run logic in the layer. The saved set's style runs give a link its color without a tag, so bold or italic text across a link's start is rare.

### Tests

- Unit tests of `htmlDescriptionToText` in DescriptionTextTest cover each rule above, from HTML strings written like NewPipeExtractor's: a shortened link with `...` and with `…`; a link shown whole, which keeps its text; a timestamp and a hashtag, whose addresses don't start with their text; a title that ends with "..."; an address that doesn't start with the text; an address with a space; text that is only `https://...` with a longer address, which becomes the address; text that starts with `HTTPS://`, which keeps its text; an address that is the text without its "...", which keeps its text; an address with a space, a no-break space, a line break, a control character, or the format characters U+202E, U+200B and U+E0041, each of which keeps its text, where U+E0041 lies above U+FFFF, so the check must read code points, not Kotlin's `Char`s; an `&amp;` in the address, which becomes `&`; a link that holds a bold part, and a link whose style starts inside it, from the second shape above, which both become the address; a link split into two pieces, from the first shape above, which keeps its text; and a shortened address that isn't a link, which stays as it is. The existing tests stay as they are, and `a link keeps only its text` is renamed `a link that YouTube didn't shorten keeps its text`, because it no longer holds for every link.
- VideoDetailsReplayTest checks that the saved set's description holds `https://studio.blender.org/projects/big-buck-bunny/` whole, and still holds `http://bbb3d.renderfarming.net` without a `/` after it.
- The live YouTube check doesn't read the description, and the debug screen shows only its length and line count, so neither changes.

## Risks / Trade-offs

- [YouTube changes how it shortens a link, such as dropping `https://` from the text] → The rules don't match, and the link keeps its shortened text, as today. Nothing breaks.
- [An address with a long query, such as tracking parameters, takes several lines of the description] → That is the address the creator linked. The screen wraps it, and the user can copy it whole, which is what the issue asks.
- [The address comes from YouTube's answer, and the app shows it to the user] → The app already shows it in shortened form. It never opens the address, logs it or sends it anywhere. The description stays user data, as ADR 0007 defines it.

## Migration Plan

Nothing is stored yet, so there is nothing to migrate. Reverting the commit brings back the shortened text.

## The maintainer's answers

The maintainer answered both questions on November 21, 2026, after the maintainer subagent's first plan check, and chose the recommendation each time: "In place" and "Only shortened ones". They also approved this change as it stood at commit f48d81d. The pull request's description quotes the answers. The questions were:

1. **Where does the whole address go?** In place of the shortened text, or next to it? Recommendation: in place. YouTube's text is only the start of the same address, so nothing is lost, and copying the link copies an address that opens the page. A very long address takes several lines.
2. **Which links change?** Only a link whose text is the start of its address followed by "...", or every link whose text looks like an address? Recommendation: only the shortened ones. A link shown whole stays exactly as YouTube shows it, and the rules can't turn a timestamp, a hashtag or a name into an address.
