# Tasks

## 1. The YouTube layer

- [x] 1.1 In core/youtube/src/main/kotlin/io/github/dewfallapp/core/youtube/newpipe/DescriptionText.kt, after jsoup parses the HTML and before it takes the text, give each link that design.md's four rules accept its `href` as its text, and update the KDoc of `htmlDescriptionToText` to say so. Verify with new tests in DescriptionTextTest, one for each case that design.md's "Tests" section lists, and with `./gradlew :core:youtube:test --tests '*DescriptionTextTest'`. The existing tests pass unchanged, and `a link keeps only its text` is renamed `a link that YouTube didn't shorten keeps its text`, as design.md says.
- [x] 1.2 In VideoDetailsReplayTest, check that the saved set VIDEO_BIG_BUCK_BUNNY's description holds `More info:  https://studio.blender.org/projects/big-buck-bunny/` followed by two newlines, and ends with `http://bbb3d.renderfarming.net`, with no `/` after it. Verify with `./gradlew :core:youtube:test`.
- [x] 1.3 In docs/architecture.md's "Descriptions" section, replace "a link keeps only the text it shows" with what the layer now does: a link keeps its text, except one whose text YouTube shortened, which shows the whole address from its `href`, under the four rules in a sentence or two. Link NewPipeExtractor v0.27.1's `getUrlFromNavigationEndpoint` as the source of the address, and say when it was checked. Keep the edit inside this section, because #43's and #138's pull requests change other parts of the file. Verify that the section matches the code.

## 2. Checks

- [x] 2.1 Run every check that the pull request workflow runs: `./gradlew spotlessCheck detekt lint test assembleDebug`. Verify that it passes.
- [ ] 2.2 Check by hand on an emulator with the newest Android installed. Open Big Buck Bunny, aqz-KE-bpKQ, from Search YouTube, and see `https://studio.blender.org/projects/big-buck-bunny/` whole in the description, and `http://bbb3d.renderfarming.net` as before. Select the whole address, copy it, paste it into Search YouTube's field without sending a search, and see the whole address. If a search finds NASA's "We Are NASA", check its images.nasa.gov link the same way. At a font scale of 2, check that the long address wraps inside the screen. Verify that `adb logcat` shows no description text or address. Record the emulator's settings first, and restore them and uninstall the app afterwards.

## Workflow follow-up

- Archive the change with /opsx:archive as the last commit of the pull request, after #43's change in pull request #198 is archived and merged, so the `player` living spec syncs cleanly. Rebase onto main first, then run the reviewer again on the archive commit.
