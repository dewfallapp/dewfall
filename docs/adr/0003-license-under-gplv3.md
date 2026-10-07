# 0003. License the app under GPLv3

## Status

Accepted

## Context

Dewfall uses NewPipeExtractor to access YouTube (see [ADR 0001](0001-newpipeextractor-behind-youtube-layer.md)). NewPipeExtractor is licensed under GPL 3.0, so an app that uses it has to be GPLv3 too.

## Decision

Dewfall is licensed under the GNU General Public License v3.0. The full text is in [LICENSE](../../LICENSE).

## Consequences

- Every dependency must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. Anything else needs a check before it is added.
