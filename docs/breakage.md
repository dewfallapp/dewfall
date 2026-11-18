# Breakage

This file is the playbook for when YouTube changes something and Dewfall stops working. It lists the steps to take, in order, says what to do about each result of the daily check, and records known problems with specific phone brands and past breakages.

The YouTube layer is the only code that reaches YouTube, so a fix almost always lands in `:core:youtube` or in NewPipeExtractor, the library under it ([ADR 0001](adr/0001-newpipeextractor-behind-youtube-layer.md)). [testing.md](testing.md#the-daily-youtube-check) describes the daily check and the saved YouTube responses, and what each result means. This file says what to do about them.

## Playbook

When the daily check fails or users report that videos stop loading:

1. **Confirm the failure.** Run the live check by hand from a home network, `./gradlew :core:youtube:liveYouTubeCheck`, because YouTube blocks some calls from GitHub's runners but not from homes ([testing.md](testing.md#running-it-by-hand)). Then try the same call on a real phone or an emulator with the YouTube layer's debug screen ([testing.md](testing.md#checking-the-youtube-layer-on-a-phone)). A failure that only the runners see is usually about the runners, not a breakage, but read the reason first: Google's block page once reached only the runners and still needed a fix in the layer ([Past breakages](#past-breakages)).
2. **Check whether NewPipeExtractor already has a fix.** Look at its [releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases), its [issues](https://github.com/TeamNewPipe/NewPipeExtractor/issues) and [pull requests](https://github.com/TeamNewPipe/NewPipeExtractor/pulls), open and closed, and the latest commits on its dev branch. If nobody has reported it, the maintainer reports it, as [Reports to TeamNewPipe](#reports-to-teamnewpipe) says.
3. **Update the dependency.** A release that fixes it lands as [How a NewPipeExtractor update lands](#how-a-newpipeextractor-update-lands) describes. Until there is one, the app waits for the upstream fix, or the maintainer decides to patch the library, as ADR 0001's consequences allow.
4. **Run the tests.** Record the saved responses again after every NewPipeExtractor update, as [Recording the saved responses again](#recording-the-saved-responses-again) describes, run every check in AGENTS.md's Commands, and run the live check by hand again. Every kind must pass.
5. **Ship a hotfix release.** [release.md](release.md) will describe how, once Phase 9's release tasks fill it in. Until the first public release, there is nothing to ship.
6. **Pin a GitHub issue so users know a fix is coming.** On the issue's page, click Pin issue in the right sidebar. A repository can pin up to three issues, and only people with write access can pin one ([Pinning an issue to your repository](https://docs.github.com/en/issues/tracking-your-work-with-issues/administering-issues/pinning-an-issue-to-your-repository)). Unpin it once the fixed release is out.

## When the daily check opens an issue

The daily workflow keeps one issue for each kind that fails, titled "Daily YouTube check fails: KIND", with the label daily-youtube-check ([testing.md](testing.md#issues)). The issue holds the kind's result line, such as `VIDEO_BIG_BUCK_BUNNY FAILED UNEXPECTED_RESULT expected=SUCCESS got=BROKEN`: the kind, the reason, and fields that say more. [testing.md](testing.md#results) explains each reason. The issue also links to the run, whose summary shows every kind's result, and whose log shows the class names of each error.

Start every time with step 1 of the playbook: run the check by hand, and see whether the same kind fails from a home network. Then find the reason below.

The check reports the first problem it finds for each kind, in this order: how the call ended, its requests, its warnings, then its content. So fixing one reason can reveal the next, such as READ_IN_PART after REQUESTS_CHANGED.

Never fix a failing kind by loosening its check. When the content of a target really changed, replace the target, as [WRONG_CONTENT](#wrong_content) says.

### UNEXPECTED_RESULT

The call succeeded when its set expects it to fail, or failed when it expects success, or failed with another category. `expected` and `got` name both.

**got=BROKEN.** The layer got an answer from YouTube that it couldn't read, or refused to send a request to a host that it doesn't allow. Only an update fixes either one ([architecture.md](architecture.md#errors-from-youtube)).

1. Look at the run's log, or the log of a run by hand. The error's class names tell the causes apart. HostNotAllowedException means that NewPipeExtractor now asks for a host that [Network access](architecture.md#network-access) doesn't list. A ParsingException or another of NewPipeExtractor's exceptions means that its code couldn't read YouTube's answer. Two are the layer's own: MissingFieldException means that an answer lacks a field that the layer requires, such as a video's channel ID, and NoStreamLinksException means that a video's page gave no link that plays. The log never holds the answer itself.
2. If the run by hand passes, YouTube may have answered the runner with a page that the layer doesn't know yet, as Google's block page was until November 18, 2026 ([Past breakages](#past-breakages)).
3. If it fails by hand too, run the same call on the debug screen, and look in `adb logcat -d | grep ' Dewfall'` for the ERROR line "YouTube call failed", whose stack frames show which part of NewPipeExtractor threw.
4. Follow the playbook from step 2. A new host also needs [ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md)'s allow list changed, with the host checked and documented in Network access first, not only a new version.

**Other values.** Check the video or channel in a browser first:

- A set that expects an error got another one, or SUCCESS, such as VIDEO_PRIVATE got SUCCESS. If the browser shows that the target changed, replace it, as for WRONG_CONTENT. If it hasn't changed, NewPipeExtractor may no longer recognise YouTube's English wording of the reason, as happened with the age check in v0.27 ([ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md)), so follow the playbook from step 2.
- VIDEO_AGE_RESTRICTED got AGE_RESTRICTED: NewPipeExtractor now recognises the visionOS client's age check, which it didn't in v0.27.1 ([ADR 0008](adr/0008-youtube-layer-contract.md)). Change the set's expected error in SavedSet.kt, record it again, and update testing.md and architecture.md.
- Big Buck Bunny's video details got UNAVAILABLE or COUNTRY_BLOCKED: check that the film still plays in a browser. If it does, YouTube changed how it reports playability, so follow the playbook from step 2.

### READ_IN_PART

The call worked, but the layer logged warnings while it ran, counted in `warnings`: it left out items that lacked a required field, or NewPipeExtractor couldn't parse part of the answer. It is often the first sign that YouTube changed its answers, before anything fails outright.

1. Run the check by hand, and look at the call's "YouTube answer read in part" line in the log. Its fields `dropped` and `parseErrors` say whether items were left out or parts failed to parse.
2. Run the same call on the debug screen, which shows what came back, so you can see which items or fields are missing.
3. If it stays, follow the playbook from step 2. A part that NewPipeExtractor can't parse is its bug to fix. An item that the layer leaves out follows the layer's rule in [The models](architecture.md#the-models).

### WRONG_CONTENT

The call worked, but returned content that the check doesn't expect. `what` names it, and [testing.md](testing.md#what-it-checks) lists what each kind expects. It means one of three things:

- **The target changed.** Someone edited or removed something on YouTube, such as a video that a channel took down. Check it in a browser. If it really changed, pick another target that should last, change SavedSet.kt and the kind's check in LiveCheck.kt, record the set again, and update testing.md's tables. Say in the pull request how you checked that the old target changed.
- **The layer reads it wrongly.** The content is still there, but the layer returns something else, such as a Short among a channel's uploads. YouTube changed its answer in a way that the layer misreads, so follow the playbook from step 2.
- **YouTube changed how its stream links work.** NO_AUDIO_ONLY_MP4, NO_VIDEO_ONLY_MP4, NO_HLS_MANIFEST, NOT_GOOGLEVIDEO and NO_EXPIRY, for Big Buck Bunny's stream links, mean that the formats or links themselves changed. Treat them like got=BROKEN.

### REQUESTS_CHANGED

The call sent other requests than its set lists. The log names each request, in order. After a NewPipeExtractor update, the new version usually asks differently: change SavedSet.kt, record the set again, and check the counts in [What each function sends](architecture.md#what-each-function-sends). On main, find out why first. NewPipeExtractor falls back to another request when one fails, such as YouTube's search page when sw.js_data fails ([Network access](architecture.md#network-access)), and recording that fallback into SavedSet would hide a breakage.

### LINKS_EXPIRE_SOON, LINK_NOT_PLAYABLE and LENGTH_MISMATCH

Big Buck Bunny's stream links expire within the hour, don't answer a ranged GET as a player needs, or give another total length than their format. YouTube changed how its stream links work, so follow the playbook from step 2. LINK_NOT_PLAYABLE's `answer` and `status` say how the link answered.

### EVERY_CALL_SKIPPED

Only NOTHING_CHECKED fails this way, when the workflow skipped every other kind, so the run checked nothing. `blocked` and `network` count the skipped calls. It means that the runners lost search and channel pages too, which they reach on other days. Run the check by hand, and see whether the next run gets through.

### NO_RESULTS

Only DID_NOT_FINISH fails this way: the workflow got no results, because the build failed, the check stopped, or the job ran out of time. Open the run's log. It also happens when simulate_failure doesn't name a kind, and when the results file has a line that isn't a result. The workflow refuses such a line, because only names and numbers may reach an issue ([testing.md](testing.md#permissions-cache-and-public-logs)). Then find out what wrote it before you change anything else.

### SIMULATED

Someone ran the workflow with simulate_failure, to test its issues ([testing.md](testing.md#testing-the-issues)). The next normal run closes the issue, unless the runners skip that kind. Then close it by hand.

### When an issue closes

A kind's issue closes by itself when a later run passes that kind. A run that skips the kind leaves the issue open, so an issue for a kind that the runners now skip, such as a video call, stays open until someone closes it. Close it by hand once a run by hand passes that kind, with a comment that says so. If the kind still fails, the next failure reopens an issue closed in the last 14 days.

### What to share

Comments on a daily check issue may hold result lines, class names and stack frames, and words such as "home network" or "mobile data". Never add anything that says who ran the check or where, such as an IP address, an internet provider, a city, a country or a VPN. From logcat, share only the lines that `adb logcat -d | grep ' Dewfall'` shows, which ADR 0007 keeps free of user data.

## When a run by hand fails

A run by hand writes its results to core/youtube/build/live-youtube-check/results.txt and opens no issue. It skips nothing, so two results appear only there:

- **RUNNER_BLOCKED.** YouTube blocked the call from your network, with a captcha, its "confirm you're not a bot" answer or Google's block page. The kind fails with `KIND FAILED RUNNER_BLOCKED`, the reason named after the runners, where this is normal. Wait an hour and run it again, then try another network, such as a phone's mobile data. If one network passes, the block is about that network. If every network stays blocked for a day, phones are likely blocked too: check NewPipeExtractor's issues for the same block, as in step 2 of the playbook. A new bot check usually needs a NewPipeExtractor change.
- **NETWORK.** The call failed to reach YouTube twice, 30 seconds apart. Check the network, and run it again.

## The runners' skips

From GitHub's runners, YouTube blocks some or all of the calls that send a video's player request, depending on the runner, while search and channel pages pass ([testing.md](testing.md#youtube-blocks-the-runners-video-calls)). The workflow runs the check with `-PliveCheckSkipUnreachable=true`, so a blocked kind ends `SKIPPED RUNNER_BLOCKED` instead of failed, and a call that fails with NETWORK twice is skipped too. A skipped kind opens no issue and doesn't touch an open one. This is normal, not a breakage.

The cost is that the runners don't always check those kinds. A run by hand does, so run one before merging a NewPipeExtractor update, before each release, and whenever the daily check reports a change ([testing.md](testing.md#running-it-by-hand)).

## When the schedule stops

In a public repository, GitHub turns off scheduled workflows when no repository activity has occurred in 60 days ([Disabling and enabling a workflow](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/disable-and-enable-workflows)). A turned-off workflow opens no issues, so a quiet issue list doesn't mean that the check passes. Look at the date of the last scheduled run, especially before a release ([gh run list](https://cli.github.com/manual/gh_run_list)):

```bash
gh run list --workflow daily-youtube-check.yml --event schedule --limit 1
```

If it is older than a day or two, turn the workflow back on ([gh workflow enable](https://cli.github.com/manual/gh_workflow_enable)), then start a run and read its results:

```bash
gh workflow enable daily-youtube-check.yml
```

```bash
gh workflow run daily-youtube-check.yml
```

## Recording the saved responses again

The YouTube layer's tests replay saved answers, so a NewPipeExtractor update that changes its requests fails them until the sets are recorded again. [testing.md](testing.md#recording-the-sets) explains the recorder and when to run it. In short:

1. If YouTube's answers hold something new, such as a new kind of link or a new place for the machine's address, check that the masking finds it before you record. A value that no rule knows can pass every check ([testing.md](testing.md#privacy)).
2. Record every set from a home network, or only some with `-PyoutubeSets=SET_A,SET_B`:

   ```bash
   ./gradlew :core:youtube:recordYouTubeResponses
   ```

   The recorder writes nothing if a call ends otherwise than SavedSet expects, if it sends other requests, or if a real value is still there after masking. It logs why, never the value.
3. Run the layer's tests, SavedResponsesPrivacyTest included, then review what changed with `git diff --stat`:

   ```bash
   ./gradlew :core:youtube:test
   ```

4. Run the live check by hand. Every kind must pass.

## How a NewPipeExtractor update lands

- **Where it comes from.** NewPipeExtractor's releases are only on JitPack, which builds a Git tag the first time someone asks for that version ([JitPack's README](https://github.com/jitpack/jitpack.io/blob/master/README.md)). settings.gradle.kts takes only TeamNewPipe's group from JitPack ([ADR 0009](adr/0009-how-the-youtube-layer-reaches-youtube.md)).
- **Who proposes it.** Renovate opens the pull request, on a Friday in the Asia/Dhaka time zone, with at most five of its pull requests open at once ([testing.md](testing.md#dependency-updates)).
- **When.** AGENTS.md says to wait 14 days after a release before adopting it, unless the release fixes a security issue. Renovate counts the 14 days from JitPack's build, not from the Git tag ([testing.md](testing.md#dependency-updates)). A release that fixes a YouTube breakage waits too, because AGENTS.md has no other exception. The maintainer made a one-time exception for v0.27.1, in #167.
- **A fix with no release.** If the fix is only on NewPipeExtractor's dev branch, it has no release, so Renovate opens nothing. Pointing the version catalog at a commit is a dependency change, so the maintainer decides it.
- **Before it merges.** Record the saved responses again, as above, after every update, so that the version the app ships reads them, and the tests show whether its requests changed. Run the live check by hand, and check the debug screen on Android 10 and on the newest Android ([testing.md](testing.md#checking-the-youtube-layer-on-a-phone)). Nothing merges on its own ([testing.md](testing.md#dependency-updates)).

## Reports to TeamNewPipe

When a breakage is NewPipeExtractor's to fix and nobody has reported it, the maintainer writes the report and posts it, in their own words. An agent never writes or posts it. TeamNewPipe's contribution guidelines forbid using generative AI to write or fill in the templates of issues and pull requests, and say that issues and pull requests that break the policy can be closed without further explanation ([AI policy](https://github.com/TeamNewPipe/NewPipe/blob/6f6b5fb9961c525c046f197636eaf587110e93ab/.github/CONTRIBUTING.md#ai-policy)). NewPipeExtractor's bug report form asks the reporter to confirm that they have read those guidelines ([bug_report.yml](https://github.com/TeamNewPipe/NewPipeExtractor/blob/6963385176be024a9bce585352a492d7839f23b8/.github/ISSUE_TEMPLATE/bug_report.yml)).

An agent may collect what the maintainer needs: which calls fail and since when, the NewPipeExtractor version, the class names of the exceptions and the lines of NewPipeExtractor's code that throw them, with links. A report never holds user data, such as an IP address, the network's country, or what anyone watched.

## Phone brands and background limits

TODO: Phase 7 adds the overnight downloads and fills this in. The roadmap asks to test on as many phone brands as possible, and to note here which settings each one needs so that background jobs run.

## Past breakages

- **November 2026, NewPipeExtractor v0.27.** v0.27.0 dropped the Android and iOS clients, so formats, the HLS manifest and subtitle tracks come only from the visionOS client, and the DASH manifest is gone. Dewfall updated to v0.27.1 in #161 ([architecture.md](architecture.md#what-newpipeextractor-v027-changed)). An age-restricted video has read as UNAVAILABLE since then, because NewPipeExtractor recognises the restriction only by the Android client's wording.
- **November 16, 2026, a video that never existed.** With v0.26.5, YouTube answered the Android client's player request for a video that never existed with an empty object, so the layer reported BROKEN instead of UNAVAILABLE. v0.27.1 fixed it (#155).
- **November 17 and 18, 2026, the runners' video calls.** YouTube blocked video details and stream links from GitHub's runners. Some runners got its "confirm you're not a bot" answer, others HTTP 403 with Google's block page, which the layer reported as BROKEN until NewPipeDownloader learned to report it as BLOCKED. The daily workflow on the update's branch opened #162 to #166 for it ([testing.md](testing.md#youtube-blocks-the-runners-video-calls)).
