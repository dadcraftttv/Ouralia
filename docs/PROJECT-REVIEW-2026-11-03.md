# Ouralia project and branch review — 2026-11-03

Requested by DadCraft: review the branches and project for performance, housekeeping, improvements, and additions supporting a modern RjDj recreation. Review only: no application changes, merges, installs, commits or pushes.

**Assessment:** the current direction is sound. Found percussion, Singing Room, environmental responses and the scene host are a strong foundation. The next useful work is reliability, removing unnecessary processing, and making the relationship between the surroundings and the music easier to hear. A new composer or a larger classifier is not the next priority.

This is a broad source and architecture review, with focused execution of the existing tests and two targeted reproductions. It is not an exhaustive proof of every line, a new phone benchmark, or a sound-quality verdict. References below are relative to this repository at `sound-kinds` **f02b985**. Working trees were clean before the review. Local remote-tracking refs were inspected; no fetch was performed.

## Branches and release readiness

| Branch | Observed state | Recommendation |
|---|---|---|
| `main` — `5538c40` | Older kit-based app plus current release/privacy documentation. It is an ancestor of `sound-kinds`; 55 commits behind that branch. | Keep as the baseline until DadCraft approves promotion. Do not start new feature work here. |
| `reactive` — `2b7ea4c` | Intermediate kit-removal/reactive work, entirely contained in `sound-kinds`; 40 commits behind it. Local branch is eight commits ahead of `origin/reactive` (`79d6336`). | No independent code to merge into `sound-kinds`. Retire the branch after the accepted work reaches main and its reference value is preserved. |
| `sound-kinds` — `f02b985` | Current development; matches the local `origin/sound-kinds` ref. Contains kit removal, fixed reactive behavior, classification, water/birds/traffic, stereo and quiet tune. | Use as the review/fix target. Promotion needs an explicit release-feature decision, not just a merge. |
| `oboe-fix-test` — `5823ce8` | Old test branch, one unique commit versus 267 on the current line. Its test commit changes dependency selection and the old audio service; current builds already use the documented Oboe fixes through pinned forks. | Archive its purpose; do not merge its old app/dependency setup wholesale. Verify pins before deleting the local reference. |

**Merging will not ship the classifier.** `app/build.gradle.kts:74` declares LiteRT only for Dev; `YamNet.java` and model assets are under `app/src/dev`. `SoundKindsRunner.java:85` detects availability by class presence. A normal release will retain the reactive/audio changes but omit the classifier and its dependent controls. Decide which features graduate, move their build inputs deliberately, then validate the minified release, notices, supported ABIs and model loading. The x86 LiteRT libraries are explicitly excluded at `app/build.gradle.kts:56`; class presence alone does not establish runtime capability on those devices.

The existing pre-merge history-cleanup instruction in Conductor's items remains a separate prerequisite; this review neither rewrites history nor rechecks remote publication. Calm Builds and roadside traffic listening remain open as already recorded. No new approval is implied by this review.

## Findings to fix

All items below are P2 unless indicated: ordinary-priority defects or validation gaps with concrete consequences. Order reflects suggested work sequence, not a claim that a device failure has been observed for every item.

### 1. Music in Stereo can stay wide after it is switched off

**Reproduced in the generated patch.** Width is applied before the side effects (`tools/patchgen/music_stereo.py:60`, `tools/patchgen/build.py:538`). A held Freeze loop retains the earlier side signal; the final mid/side reconstruction has no downstream width gate (`build.py:660`).

Reproduction: play an earned motif note with Music in Stereo enabled, hold Freeze, disable stereo, wait two seconds. Side RMS remained **0.010194**, versus **0.010200** before disabling; releasing Freeze removed it. This is a state-transition bug that an initially-off sample-identity test cannot detect.

Fix: smoothly gate the music side output after its stateful effects when stereo is disabled, preserving the existing width law. Test held Freeze and ordinary effect tails across on/off/on transitions. Keep World in Stereo independent; it is legitimate for that separate mic feature to remain stereo.

### 2. A stopped classifier worker can send stale state back to Pd

**Source-confirmed interleaving; not stress-reproduced on a phone.** `SoundKindsRunner.java:310` checks startup ownership under a lock, then sends `sk_on=1` at line 325 after releasing it. `stop()` can clear ownership and send `sk_on=0` in between. The old startup then re-enables ring capture without a consumer. Similarly, `judge()` checks its generation at line 445 but sends water/birds/traffic state at line 463 onward after releasing the lock; a stop/reset can intervene.

Fix: post immutable, generation-tagged results to the service handler, revalidate there, and apply them on the same executor that handles detach/stop. **Do not move Pd sends back inside the runner lock**: the earlier lock-inversion fix is important. Add controlled stop-during-load, stop-during-inference and immediate-restart tests.

### 3. Imported scene metadata has no memory bound

`SceneLibrary.java:263` reads all of `Info.plist` through `readAll()` at line 347, then copies it into a string. ZIP content may total 512 MB; folder content is unbounded. An oversized metadata file can exhaust heap. `installFolder()` installs the directory before describing it (`SceneLibrary.java:180`), so the problematic scene may remain and be read again on later listing.

Fix: enforce a small metadata byte limit before allocation, bound displayed strings, and validate metadata before committing the staged import. Test a large plist, missing metadata and malformed metadata. Report a useful failure without losing existing scenes. Source finding, not a destructive device reproduction.

### 4. Folder imports need error boundaries and resource budgets

`SceneUi.java:264` starts folder import without an outer exception handler. Provider queries at `TreeImporter.java:58` and line 69 occur outside `copyScene()`'s catch. A revoked permission or unavailable provider can throw out of the worker and terminate the app. The ZIP path already catches these failures.

Separately, `TreeImporter.java:103` recursively copies scene content without depth, file-count or total-byte limits. `MAX_DEPTH=2` limits discovery, not the contents copied after finding a scene. Large folders can fill storage; deep trees can exhaust the stack.

Fix: share import limits across ZIP and folders, catch provider failures at the operation boundary, support cancellation, and clean staging on failure. Keep provider/display-name validation in this shared boundary. Test permission loss, oversized content and excessive nesting with a fake provider.

### 5. The test runner can report success after a native compile failure

`tools/run_tests.py:271` discards the result of `make -s -C dcs`. On a workspace with a previous library, subsequent tests may exercise the stale binary.

**Control flow reproduced:** substituting an exit-2 compiler failure and successful downstream suites made `main(['quick'])` return zero and print `ALL PASSED`. This reproduction used mocks, not a deliberately broken source tree.

Fix: fail immediately on a nonzero make result and print its captured diagnostics, just as patch generation already does. Guard this with a runner-level failure-path test.

### 6. CI misses the active variant and can build a stale generated patch

`.github/workflows/tests.yml:7` runs direct-push CI only for main; it builds `assembleDebug` at line 24. Direct pushes to `sound-kinds` do not trigger it, and the real Dev-only YAMNet implementation is never compiled by this job. Java runner tests use a fake YAMNet. Also, `desktop/test.sh` compiles shared logic and tests, not the production `desktop/src` application.

The APK build occurs **before** the test runner regenerates `test_scene.pd`. If someone changes patchgen but forgets regeneration, CI can build the old patch and test the new one without checking the difference.

Fix: generate first, require a clean generated-file diff, then build Debug and Dev and run tests. Add the active branch to push coverage and a compile check for production desktop sources. At release promotion, build/test the minified release and inspect its actual packaged assets/native libraries. The full timing-sensitive DSP suite can remain a separate controlled check.

## Performance: best opportunities, in order

### Stop processing effects that cannot contribute audio

`tools/patchgen/build.py:73` constructs each Underwater path as filters plus a modulated delay, with a wet crossfade but no `switch~`. Four paths are instantiated at lines 534, 539, 655 and 659. They continue running with Underwater off, including builds without the classifier. The music side FX chain at line 540 also follows the ordinary effect switches even when Music in Stereo supplies no side input.

Put suitable paths in independently gated subpatches. Fade wet output down before suspension; define delay clearing/warming and tail behavior before resuming. Preserve existing audio with the feature off and test transitions. Fix the Freeze correctness issue first or together with its output gate.

**Savings are not yet measured.** On/off CPU comparisons within today's APK do not capture processing paid in both states. Measure old APK versus optimized APK with identical input/settings, and compare effect-off, effect-on and transitions. The earlier desktop stereo build comparison already reported a larger baseline even though the phone switch A/B showed little difference. These results are compatible, not contradictory.

### Measure and reduce the complete classification cost

`SoundKindsRunner.java:430` allocates and copies roughly one second of audio each judgement. `SoundKinds.to16k()` (`SoundKinds.java:115`) allocates coefficients/output and computes two 65-tap filters for each of 15,600 output samples. At four judgements per second this is about **8.1 million filter multiply-accumulates per second**, before inference. At 48 kHz, the large input/output arrays alone allocate roughly **1 MB per second**. These are source-derived operation/allocation counts, not measured CPU savings.

Start with reusable worker-owned buffers and coefficients cached per sample rate. A stateful resampler could subsequently process only newly arrived samples instead of recomputing the overlapping second, but must preserve anti-aliasing, alignment and classification behavior. Do not simply decimate samples. TensorFlow's [YAMNet documentation](https://www.tensorflow.org/hub/tutorials/yamnet) specifies 16 kHz input; conversion quality remains necessary.

The timer at `SoundKindsRunner.java:438` starts **after** copying/resampling. Keep its useful inference metric, but add end-to-end judgement duration, queue age, skipped windows and allocation/GC measurements. Observe p95/p99 and audio underruns, not just means. Existing process-CPU A/B measurements include more than this timer and remain useful evidence.

### Avoid inference without a consumer

Enabled sound-kind music switches keep the worker alive even when `music_on=0`; output state is gated, demand is not (`SoundKindsRunner.settingsTick()`). Suspend when neither an audible feature nor explicit Diagnostics needs results. Define how context warms up on resume; preserving immediate context may justify some intentional background cost. Cache immutable build capability instead of repeating seven reflective availability checks every 33 ms. Prioritize DSP/resampling work above this smaller cleanup.

### Establish a repeatable device budget

Retain the existing Health/xrun logs. Add reproducible profiles for dry room, DadCraft's normal setup, all accepted reactive options, and an imported scene. Compare screen-off sessions at the same routing, rate and temperature; record latency/xruns, process CPU, memory/GC and battery drain. Repeat on a slower supported phone before claiming broad efficiency. The earlier battery estimate was accepted for the experiment; it is not a measured fleet-wide result.

Keep allocations, blocking work and long locks out of the real-time callback. Android's [Oboe guidance](https://developer.android.com/games/sdk/oboe/low-latency-audio) explicitly identifies these as glitch risks. No fork rewrite is recommended from this review; inspect callback timing before changing the audio backend.

## Housekeeping and architecture

- **Keep the existing technology.** Java Views, libpd, the native objects and patch generation fit this app. A UI framework migration or engine rewrite would consume effort without addressing the findings above.
- **Give reactive responses one coordination boundary.** `SoundKindsRunner` now repeats feature-specific enabled/current/sent state and Pd messages. Introduce an immutable context snapshot and one response-policy/application step, initially preserving behavior exactly. Explicitly encode talk vetoes, water/birds/traffic coexistence, reset and priority. This also makes the lifecycle fix testable.
- **Keep fast events separate from scene context.** Onsets, pitch and envelope belong to the fast audio path; classification supplies slower context. Do not route drum timing through YAMNet. Found percussion's timing and identity are central strengths.
- **Split by responsibility when touching large files.** `AudioService` is 1,466 lines, `music.py` 1,747, `build.py` 935 and `test_patch.py` 3,469. Extract lifecycle/output routing, feature policy and cohesive patch/test sections incrementally. File length alone is not a defect. Preserve current test names and avoid another broad rewrite.
- **Make feature availability explicit.** One capability description should drive Android/desktop UI, presets, packaging and tests. Distinguish a saved preference from whether the runtime can fulfill it. Preserve preferences across upgrades.
- **Do not blindly remove dormant native voices.** `dcs_voice~` remains compiled although patchgen no longer uses it; the design notes explicitly retain the frozen engine. Check imported-scene compatibility before trimming registered objects. Kit UI removal does not authorize breaking the scene ecosystem.
- **Tighten documentation status.** ROADMAP's Open-now heading still says checked Oct 22 despite November entries, and built/pending-listen/proposed work is interleaved. Keep one short current queue with those statuses and link to history. Stale R.5/dev comments in build configuration and classifier “only listens” comments should match current behavior.
- **Test transitions and combinations.** Startup, teardown, restart, switch-off with a tail held, scene changes, unsupported runtime and mixed sound contexts deserve targeted tests. The strong isolated DSP suite cannot establish these properties by itself.

## Additions that advance the modern RjDj goal

These are proposals for DadCraft to choose from, not a newly authorized implementation backlog. Existing features were checked first: do not rebuild Found percussion, Singing Room, Harmony, Call and Response, trailing notes, scenes, Worlds, session marks or the water/bird/traffic features under new names.

### First: make existing cause and response easier to understand

Extend the current Now/Why display into a small optional explanation: “that knock joined the beat,” “voices are holding the chimes,” or “the quiet tune is growing.” Show the **actual reason the engine acted**, including suppression, rather than only the classifier's winning label. Surface the existing response switch beside the explanation for immediate A/B without resetting settings.

Acceptance: a tester can identify what caused a change by listening, and the explanation agrees with the engine. This is also useful labeled feedback through the existing Like/Flat/Note workflow; start with analysis before training anything.

### Next: strengthen the identity of captured sounds

Extend existing Found capture with a small, bounded, RAM-only palette of distinct recent transients. Preserve the favorite quantized percussion behavior; explore whether a knock and a rustle can retain separate rhythmic roles instead of replacing each other. Reuse existing capture/cleanliness mechanisms and expose little or no new configuration at first.

Acceptance: an A/B listener can recognize their own recent sounds in the groove, without extra clutter, stale loops or unbounded CPU/memory. Clear the palette on stop/scene change and never persist mic audio. First audit the existing Found slots so this extends their selection policy rather than duplicating the capture engine.

### Then: handle mixed environments gracefully

`SoundKinds.java` reduces most scene windows to one winning kind. Real surroundings contain combinations: voices over water, birds beside traffic. Retain independent supported scores over time and define a small response priority/budget: conversation has room, percussion stays grounded, only one strong decorative gesture competes at once. Include “uncertain/other”; the current fallback-to-quiet label is not evidence that an unrecognized sound is acoustically quiet.

Start with deterministic replay of scores and two mixed contexts, not a replacement model. If inference cadence changes, use elapsed time for context windows: the current judgement-count windows stretch when work is skipped. Keep acoustic quiet measured separately, as Calm Builds already does. More simultaneous classifications are only useful if the resulting music becomes clearer.

### Finally: make a few authored reactive experiences

Build on the **existing scene host and bundled scenes** with two or three new experiences centered on interactions, such as found rhythm, answering a voice, or a quiet room gradually coming alive. Offer a simple entry point, with the detailed controls still available. This differs from resurrecting genre kits: the scene's defining idea is what the listener and environment do.

The original [RjDj rjlib repository](https://github.com/rjdj/rjlib) is explicitly a toolkit for creating scenes. A modern continuation can add dependable Android lifecycle, clearer feedback and richer environmental context while preserving that authored, interactive character. Later, provide a small scene-author template and compatibility checks before considering a community catalogue.

**Calm Builds belongs as an intentional, opt-in exception:** sustained quiet is its cause and the existing drums provide its setting. Keep its fade/re-entry understandable. Finish DadCraft's chime listen before adding bass. Do not undo the restored composer behavior DadCraft explicitly chose; constrain and explain how it supports reactive events instead of reopening that decision by implication.

## Suggested execution order

1. Fix test-runner failure handling, generated-artifact checks and active-variant CI. Fix the reproduced stereo transition and classifier generation race.
2. Bound scene import/metadata and handle provider failures. Add focused regression tests for those paths.
3. Benchmark baseline DSP, gate inactive Underwater/side processing, then optimize classifier allocations/resampling if the measured profile supports it. Keep changes independently comparable.
4. Complete outstanding listens and decide release feature promotion. Test the resulting release artifact and supported-device matrix before merging/publishing on DadCraft's approval.
5. Prototype one causal explanation or one Found-palette improvement; obtain a short A/B verdict before expanding. Mixed context and new authored scenes follow evidence from that experiment.

## Verification

No code changed in this review: the tree is `sound-kinds` f02b985, which passed the full tier on Nov 3 before the review (Claude Code, for the YAMNet notice). Claude Code spot-checked findings 2, 5 and 6 and the Underwater gating against the source; they hold. The stereo/Freeze reproduction exercised the current generated patch; the test-runner failure reproduction used isolated mocked subprocess results. Other findings are source-verified and explicitly distinguished above. No phone install, real microphone capture, branch checkout or native-fork edits were performed.
