# 0001. Use NewPipeExtractor behind our own YouTube layer

## Status

Accepted

## Context

Dewfall needs five things from YouTube: search, video details, a channel's latest uploads, stream links, and subtitles.

YouTube changes things without warning, and any of these calls can break. When that happens, the fix should stay in one place. If code all over the app depended on the library that talks to YouTube, a single YouTube change could break the whole app instead of one module.

NewPipeExtractor is an open-source library, maintained by the NewPipe team and used by NewPipe and other open-source YouTube clients. It already does everything the first version needs, and it needs no API key and no Google account. When YouTube changes something, a whole team of maintainers works on the fix, instead of one person.

The alternatives were worse for this app:

- YouTube's official Data API needs an API key and has daily usage quotas. It also does not provide the stream links needed for playback and downloads.
- Writing our own client for YouTube's internal API would mean one person keeping up with every YouTube change.
- Going through Piped or Invidious servers would make the app depend on third-party servers that can go offline. Those servers would also see what users watch, which breaks the rule that the app talks directly to YouTube.

## Decision

Dewfall uses NewPipeExtractor to access YouTube.

NewPipeExtractor lives in its own module, the YouTube layer, behind Dewfall's own interfaces for search, video details, a channel's latest uploads, stream links, and subtitles. The module maps every NewPipeExtractor type to Dewfall's own models. No code outside this module may import NewPipeExtractor classes, so nothing else in the app knows which library sits underneath.

## Consequences

- When YouTube breaks something, the fix stays inside the YouTube layer. The steps are in [breakage.md](../breakage.md).
- When YouTube breaks something, we usually wait for an upstream fix or patch the library ourselves.
- The YouTube layer is tested against saved real responses. A daily scheduled workflow also runs it against real YouTube and opens a GitHub issue when something fails. That check never blocks pull requests, because live results are unreliable.
- The library is written in Java, with an API designed for NewPipe. The YouTube layer has to wrap it and map every one of its types to a Dewfall model.
- NewPipeExtractor is licensed under GPL 3.0, so Dewfall has to be GPLv3 too. See [ADR 0003](0003-license-under-gplv3.md).
- It is an unofficial way to reach YouTube, so Dewfall is distributed outside the Play Store, like other apps of this kind.
