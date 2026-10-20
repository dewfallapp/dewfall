# 0003. License the app under GPLv3

## Status

Accepted

## Context

Dewfall uses NewPipeExtractor to access YouTube (see [ADR 0001](0001-newpipeextractor-behind-youtube-layer.md)). NewPipeExtractor is licensed under GPL 3.0, so an app that uses it has to be GPLv3 too.

## Decision

Dewfall is licensed under the GNU General Public License v3.0. The full text is in [LICENSE](../../LICENSE).

## Consequences

- Anything that ships in the app must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. Bundled fonts may use the SIL Open Font License 1.1 and must ship with their copyright notice and license text. Build tools that never ship, such as Gradle plugins and the tools they download, may use any open source license or be in the public domain. Anything else needs a check before it is added. This line was clarified after #122, to separate what ships in the app from build tools. The sentence about fonts was added for #26, when the app first bundled a font. The decision itself is unchanged.
