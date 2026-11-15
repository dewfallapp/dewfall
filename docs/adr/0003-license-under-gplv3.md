# 0003. License the app under GPLv3

## Status

Accepted

## Context

Dewfall uses NewPipeExtractor to access YouTube (see [ADR 0001](0001-newpipeextractor-behind-youtube-layer.md)). NewPipeExtractor is licensed under GPL 3.0, so an app that uses it has to be GPLv3 too.

## Decision

Dewfall is licensed under the GNU General Public License v3.0. The full text is in [LICENSE](../../LICENSE).

## Consequences

- Anything that ships in the app must be compatible with GPLv3. Apache 2.0, MIT, BSD and LGPL are fine. So is MPL 2.0, unless the code carries the "Incompatible With Secondary Licenses" notice from MPL 2.0's Exhibit B. Bundled fonts may use the SIL Open Font License 1.1 and must ship with their copyright notice and license text. Build tools that never ship, such as Gradle plugins and the tools they download, may use any open source license or be in the public domain. Anything else needs a check before it is added. This line was clarified after #122, to separate what ships in the app from build tools. The sentence about fonts was added for #26, when the app first bundled a font. The sentence about MPL 2.0 was added for #35, when NewPipeExtractor brought in Rhino, its JavaScript engine. Section 3.3 of [MPL 2.0](https://www.mozilla.org/en-US/MPL/2.0/) lets its code be combined with code under the GPL, unless that notice is attached, and no source file of Rhino 1.8.1 attaches it. The decision itself is unchanged.
