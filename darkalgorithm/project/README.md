# DARK ALGORITHM — ported from xai-org/x-algorithm

Source of the port: https://github.com/xai-org/x-algorithm (first released
January 2026, major update May 15 2026, Apache 2.0). That repo is the
*current* X recommendation system, Grok-powered, replacing the old
2023 `twitter/the-algorithm` repo. Its reusable part isn't the trained
Phoenix model — it's the `candidate-pipeline` crate: six generic stage
traits (`Source`, `Hydrator`, `Filter`, `Scorer`, `Selector`, `SideEffect`)
that Home Mixer composes into a pipeline. That's domain-agnostic by design,
which is exactly what makes it portable to an OSINT task engine instead of
a feed-ranking engine.

## File map

| File | Ported from | Role |
|---|---|---|
| `CandidatePipeline.kt` | `candidate-pipeline` crate | the six stage traits, unchanged in shape |
| `Lead.kt` | `Post`/`User` data model | the unit flowing through the pipeline |
| `PhoenixLite.kt` | Phoenix (transformer scorer) | predicts per-lead probabilities, weight-sums into one score, same "candidate isolation" guarantee |
| `SourceCredibility.kt` | base-score account prior | per-Source reliability prior feeding PhoenixLite's corroboration signal |
| `SafetyGate.kt` | Post-Selection Filtering + abuse-enforcement-service | hard compliance boundary, runs pre-score AND post-score like X runs filtering twice |
| `DarkMixer.kt` | Home Mixer | orchestrator: background worker loop, cancellation/recovery, JSON import/export |
| `ExampleBranchSources.kt` | — | shows how an existing FARCAST feature (RDAP, persona lookup) becomes a `LeadSource` |
| `sample-case.json` | — | matches `CaseFile`'s serialized schema |

## What's real here vs. what's a stand-in

- The **pipeline shape, the trait contracts, the scoring pattern, the dual
  filter placement, and the cancellation/recovery model** are a direct,
  accurate port of how x-algorithm is actually built (verified against
  the current repo, not the 2023 one).
- `PhoenixLite`'s four estimators (novelty/corroboration/actionability/
  riskOfMisuse) are heuristic stand-ins for Phoenix's learned transformer —
  there's no training data for "which OSINT lead is worth exploring," so
  this uses declarative rules. Per Section 4 of your spec, a learned model
  could later be swapped in as a plugin without touching `DarkMixer`.
- `ExampleBranchSources.kt`'s two sources have `TODO` bodies — they show the
  integration shape, not finished network calls. Wiring in the rest of
  FARCAST's existing branches (geo, cams, CT logs, DNS, etc.) means writing
  one `LeadSource` per branch following that same pattern.

## Network privacy (Tor) — `privacy/` package

| File | Role |
|---|---|
| `TorGateway.kt` | Orbot broadcast-intent integration (start request + live status flow) |
| `CircuitIsolation.kt` | per-branch SOCKS5 isolation tokens — the real mechanism behind "multiple tunnels": each FARCAST branch gets its own Tor circuit via `IsolateSOCKSAuth`, not a literal VPN tunnel |
| `TorEnforcedHttpClient.kt` | OkHttpClient factory every `LeadSource` should use instead of building its own client |

Design choices, and why:

- **Orbot, not a bundled Tor binary.** Keeps FARCAST's own APK free of a Tor
  binary (Section 4: don't bundle everything into core; Section 6: avoid
  unnecessary bulk/background services). The tradeoff is real: this requires
  the user to separately install Orbot. If you'd rather ship Tor embedded
  (Guardian Project's `tor-android` artifact bundles the Tor binary itself,
  with ARM32 builds available) say so and I'll swap this module for that —
  it's a bigger APK and an always-resident Tor process, which is the thing
  Section 6 is asking us to avoid by default.
- **Fail-closed, always.** `TorEnforcedHttpClient` throws if Orbot isn't
  confirmed `ON` rather than falling back to a direct connection. A privacy
  feature that silently degrades on failure isn't a privacy feature.
- **"Multi-tunnel" = Tor stream isolation**, not multiple VPN tunnels — Tor
  doesn't have a VPN mode in this architecture (Orbot's separate VPN/
  transparent-proxy mode routes the *whole device*, which is a different,
  heavier ask than per-branch isolation and would need its own review if
  that's actually what's wanted instead).
- Every `LeadSource` gets its client from `TorEnforcedHttpClient.forBranch(...)`
  instead of constructing its own — that's the single integration point, so
  adding Tor to a new branch later is a one-line change, not a per-branch one.

Add to the plugin manifest catalogue (Section 4) as a declared capability
rather than a downloadable plugin, since it's infra, not a model:
`"requiredRuntime": "orbot-socks-proxy"`, `"permissions": ["INTERNET", "QUERY_ALL_PACKAGES or <queries> entry for org.torproject.android on API 30+"]`.

## Gradle deps this module needs

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("info.guardianproject.netcipher:netcipher:2.1.0") // Orbot/SOCKS5 wiring
}
```
and the Kotlin serialization plugin applied in the module's `build.gradle.kts`.

Also add to `AndroidManifest.xml` (Android 11+ package visibility, needed to
detect whether Orbot is installed):
```xml
<queries>
    <package android:name="org.torproject.android" />
</queries>
```

## Where this sits in your proposed architecture

Drop these files into `app/agent/darkalgorithm/` as suggested in Section 7.
`DarkMixer` is the thing a ViewModel calls; it has no Android dependencies
itself (no Context, no Compose), so it's trivially unit-testable and doesn't
care whether it's running on ARM32 or ARM64.

## Whole-device routing (`privacy/TorVpnGateway.kt`, `privacy/FarcastVpnService.kt`)

Two paths, both built, pick one per deployment:

- **Path A — `TorVpnGateway`**: Orbot already has a VPN mode. There's no
  documented Orbot intent to flip it on remotely (verified against the
  Orbot Intent API docs — only `ACTION_START`/`ACTION_STATUS` are public),
  so this deep-links into Orbot and lets the user enable it there. Zero new
  permission prompts for FARCAST, Orbot stays the thing holding the tunnel.
- **Path B — `FarcastVpnService`**: FARCAST holds its own `VpnService`
  tunnel instead. Real Android lifecycle (permission flow, foreground
  notification, tun fd setup); the packet-forwarding core is an explicit,
  labeled integration point for a real tun2socks native lib (not faked —
  see the file's header comment for why).

## AI access over MCP (`mcp/McpServer.kt`, `McpAuth.kt`, `McpTools.kt`)

Lets an MCP client (Claude Desktop, Claude Code, or anything else that
speaks MCP) connect to FARCAST directly and call `list_cases`, `get_case`,
`run_dark_algorithm_pass`, `export_case`.

- **Off by default.** `McpAuth.isEnabled()` is false until the user taps
  "Enable AI access" in Settings and a pairing token is issued.
- **Loopback-only by default** (`McpBindScope.LOOPBACK_ONLY`); a desktop
  client reaches it via `adb forward tcp:8765 tcp:8765`. LAN binding is a
  separate, explicitly-labeled opt-in — never implied by turning the
  feature on.
- **Pairing-token auth** on every call (`Authorization: Bearer <token>`),
  constant-time compared.
- **Read/execute-only tool surface** — no delete/edit tools, and every tool
  reads through the same SafetyGate-governed `CaseFile` the UI uses, not a
  separate looser path to case data.
- Targets MCP spec revision **2025-11-25** (the current final spec — stateful,
  `initialize` → `Mcp-Session-Id` → `tools/list`/`tools/call`). There's a
  **2026-07-28 release candidate** that drops sessions for a stateless
  header-based convention; worth revisiting once it's finalized and MCP
  clients broadly support it, but not worth building against an RC now.

Gradle deps to add for this piece:
```kotlin
implementation("org.nanohttpd:nanohttpd:2.3.1")
```

## Website feature migration (`features/` package)

See `FEATURE_INVENTORY.md` for the full Stage-1 table (16 panels, what each
actually calls, where it landed). Summary:

| File | Ports | Upgrade over the website version |
|---|---|---|
| `FarcastHttp.kt` | — | shared GET/POST/probe helper every service below uses, always via `TorEnforcedHttpClient` |
| `LiveFeeds.kt` | Aviation, CelesTrak, USGS Quakes, ISS | — |
| `PublicCamsService.kt` | TfL JamCams, curated list, Windy | — |
| `IpThreatService.kt` | GeoJS, Shodan InternetDB, URLhaus, GreyNoise, ThreatFox, AbuseIPDB | — |
| `DomainIntelService.kt` | RDAP, DoH DNS, crt.sh, urlscan, Wayback, typosquat probing | now backs `DomainRdapSource` for real |
| `PersonaIntelService.kt` | quick-7, live Sherlock (400+), live Maigret (500+), email MX+Gravatar | now backs `PersonaUsernameSource`; Sherlock/Maigret deliberately kept as separate on-demand calls, not run every DarkMixer pass (same reason the website has them as separate buttons — a 400+/500+ site scan is seconds-to-minutes of work) |
| `OpsecMirrorService.kt` | DNS-resolver leak test (ports for real) | WebRTC leak + browser fingerprint **do not port** — no browser, nothing to leak that way; replaced with an honest device-exposure inventory instead of a fake browser fingerprint |
| `CaseDatabase.kt` | sql.js case DB | **upgraded** to native Room/SQLite — no reason to WASM-compile SQLite when the platform already has it |
| `SecureKeyVault.kt` | API key storage | **upgraded** from plaintext `localStorage` to `EncryptedSharedPreferences` (Keystore-backed) |
| `PqCryptoDemo.kt` | AES-GCM sealing (real), ML-KEM (left as a verified integration point) | kept the website's own "demo, not production" labeling rather than overclaiming |
| `SqliTestingNotice.kt` | the disabled panel + its warning text | preserved verbatim, on purpose — see the file |

Gradle deps this pass adds:
```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")           // already added for Tor
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") // already added
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")
implementation("androidx.security:security-crypto:1.1.0")
```

Not ported this pass (see `FEATURE_INVENTORY.md` for why): RE/EMU/MEDIA LAB
(needs verified current Android artifact coordinates for Rizin/WABT/
Capstone+Keystone+Unicorn/FFmpeg — not guessing those), and the Location
Picker's map/globe UI (Section 5 territory, not a service to port).

## DarkMixer now runs every branch (`AllBranchSources.kt`, `SearchBranchSource.kt`)

Previously only domain/persona were wired as real `LeadSource`s; the rest of
Section 3's feature services sat unconnected. `allBranchSources()` now wires
aviation, orbital, quakes, ISS, cams, IP/threat, and OPSEC-mirror the same
way — DarkMixer itself needed zero changes, since `LeadSource` was already
generic. OPSEC-mirror leads are always tagged `SELF_EXPOSURE_AUDIT` (never
`INFRASTRUCTURE_RECON`), which both `SafetyGate` and `PhoenixLite` already
treat differently from leads about a target.

## Search engines (`AhmiaOnionSearchService.kt`, `DuckDuckGoSearchService.kt`, `SearchBranchSource.kt`)

- **Ahmia** (.onion search): no public API — this screen-scrapes
  `ahmia.fi/search/`, which third-party tooling confirms uses a rotating
  session token. Treat parse failures as "the page changed," not a bug.
  Results are onion URLs; querying Ahmia itself needs no Tor (ahmia.fi is
  clearnet), but *opening* a result does — route that through
  `TorGateway`/`TorEnforcedHttpClient` same as everything else.
- **DuckDuckGo**: `instantAnswer()` is their real documented API (free, no
  key) but only returns infobox-style answers, not general web results.
  `htmlSearchScrape()` gets real web results by scraping
  `html.duckduckgo.com` — **not an official API**, sits outside DDG's
  documented terms for automated querying, and is hard-gated behind an
  `enableHtmlScrape: Boolean` the caller must explicitly pass `true`, so a
  UI toggle for it should show the ToS caveat, not default it on.
- `SearchBranchSource` wires both into DarkMixer as the `"search"` branch.

## YOLO + ONNX Runtime + Moment Finder (`vision/`, `MomentFinderService.kt`, `YoloHydrator.kt`)

Section 3.B/3.C, plus the Moment Finder tool: run YOLO once per image,
auto-label it, then search labels across the whole case afterward instead
of re-running detection per search.

| File | Role |
|---|---|
| `vision/OnnxYoloEngine.kt` | ONNX Runtime session: load, validate input shape, NNAPI-if-available/CPU-fallback, explicit release() |
| `vision/YoloDetector.kt` | letterbox preprocessing, anchor-free decode (the common `[1,84,8400]`-style export layout), NMS |
| `vision/YoloLabels.kt` | COCO 80-class list — document the model's actual limits, don't let a UI imply broader detection than a stock COCO model has |
| `MomentFinderService.kt` | runs detection on an image, indexes every detection, `findMoments(caseId, label)` search |
| `YoloHydrator.kt` | plugs Moment Finder into DarkMixer as a `LeadHydrator` — any Lead with `rawData["imageUrl"]` (cam snapshots, e.g.) gets auto-labeled as part of a normal pass; fails soft, never blocks the Lead |
| `CaseDatabase.kt` (extended) | new `detections` table + DAO backing the label search; **bumped to Room schema version 2** — needs a real `Migration(1,2)` before shipping to anyone with an existing DB, `fallbackToDestructiveMigration()` is dev-only |

**Model licensing — read before picking a `.onnx` file to ship.** ONNX
Runtime itself (`com.microsoft.onnxruntime:onnxruntime-android:1.27.0`,
confirmed current on Maven Central, MIT) is unencumbered. The model weights
are a separate question: Ultralytics' YOLOv8/YOLO11 — the easiest "general-
purpose YOLO" to reach for — ship under AGPL-3.0, which by Ultralytics' own
stated terms means either open-sourcing FARCAST under AGPL or buying their
Enterprise License for closed-source use. If FARCAST isn't AGPL, resolve
that before bundling one of their exports; a permissively-licensed COCO
detector (e.g. YOLOX, Apache-2.0) avoids the question entirely and decodes
with the same `YoloDetector` shape, different tensor names.

Gradle dep to add:
```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
```

## Tor DNS fix (`TorEnforcedHttpClient.kt`)

Caught before it bit anyone: OkHttp resolves hostnames itself, locally,
before a request ever reaches the SOCKS proxy — which silently breaks on
`.onion` addresses (not real DNS names; resolution has to happen inside
Tor). Added `OnionAwareDns`, the same placeholder-IP trick NetCipher uses,
so `.onion` hosts get remote-resolved through the SOCKS layer like Tor
Browser does, instead of throwing `UnknownHostException` before the request
even starts. This matters now because both Ahmia results and a self-hosted
SearXNG-over-onion instance are genuine `.onion` URLs this app will fetch.

## SearXNG — the real fix for "DuckDuckGo integration" + image/video/audio search (`SearxngSearchService.kt`)

Rather than have FARCAST scrape DuckDuckGo (or anyone) directly, SearXNG
(AGPL-3.0, fine now that FARCAST is open source) aggregates dozens of
backend engines — DuckDuckGo included — behind one official JSON API, with
`images`/`videos`/`music`/`news`/`science` as real categories, not something
bolted on. `searchAllMedia()` returns general + image + video + music
results from one call.

- **No default instance** — same as SearXNG's own docs recommend: pick a
  public one from searx.space, or self-host (their repo ships a Docker
  image).
- **"Embed Tor search," literally**: put a Tor hidden service in front of a
  self-hosted instance (a few lines of torrc) and point `instanceUrl` at its
  `.onion` address. Every call already routes through
  `TorEnforcedHttpClient`, now `.onion`-capable per the fix above.
- **Requires JSON output enabled** on whichever instance is used
  (`search.formats` in `settings.yml`) — off by default on fresh installs
  and most public instances as an anti-abuse measure; a non-JSON response is
  caught and reported clearly rather than crashing on a parse error.
- Image/video results' thumbnail URLs land in a Lead's `rawData["imageUrl"]`
  automatically — which means `YoloHydrator` auto-labels search-result
  thumbnails too, for free, with zero code changes to either file.
- `SearchBranchSource` now takes `searxngInstanceUrl` and prefers it; the
  old DDG-instant-answer path is the fallback when no instance is configured
  yet, not removed.

## Search by image (`ImageQueryService.kt`)

Two layers, stacked:
1. **Local YOLO labeling** — always on, free, no third-party call at all.
   Detects objects in the query image and turns them into a ready-to-use
   text query for SearXNG/Ahmia. This alone is a real "search by image"
   path, not a stub.
2. **Google Cloud Vision Web Detection** — optional, official, documented,
   keyed (user-supplied via `SecureKeyVault.GOOGLE_VISION`, same pattern as
   urlscan/AbuseIPDB). Paid per use, but it's the actual sanctioned way to
   ask "what pages have this image" — there's no free equivalent worth
   pretending exists.

Deliberately NOT added: TinEye (real API, but its request-signing needs
verifying against current docs before trusting a from-memory implementation)
and Yandex reverse-image search (no public API, HTML-scrape only — same
ToS-gray territory as the DDG scrape, not worth duplicating that problem a
second time without the user explicitly asking for it the way they did for
DuckDuckGo).

## Plugin system (`plugins/` package) — one-tap install/delete, data not code

**Read this before building the marketplace UI**: this is NOT a VS-Code-style
extension host. VS Code extensions run arbitrary JS in a sandboxed process;
Android has no equivalent sandbox, and Section 4 of your own spec explicitly
says "do not execute arbitrary downloaded code... without a secure,
explicitly designed extension mechanism." So every plugin here is **data**
interpreted by fixed, already-reviewed Kotlin — never a downloaded `.dex`/
`.jar` being loaded and run. Three plugin kinds, all data:

| File | Role |
|---|---|
| `PluginManifest.kt` | Section 4's manifest fields + `kind` (MODEL / DECLARATIVE_SOURCE / SCORING_WEIGHTS) + `extensionPoint` |
| `PluginCatalogClient.kt` | fetches `catalogue.json` from a GitHub repo, checks each entry against this device's API level + CPU ABI |
| `PluginDownloader.kt` | downloads a release asset, verifies SHA-256 **before** it's ever treated as installed |
| `PluginRegistry.kt` | Room-backed installed-plugins table — list/enable/disable/remove |
| `PluginInstaller.kt` | the entire one-tap contract: `install(entry)` / `uninstall(id)`, nothing else for a UI button to call |
| `DeclarativeLeadSource.kt` | interprets a DECLARATIVE_SOURCE plugin's rule file (URL template + dotted-path field extraction) — the "interpreter" is a fixed loop with no branching or function calls a plugin author controls, not a scripting language |
| `PluginAwarePipeline.kt` | assembles DarkMixer's actual source list + PhoenixLite weights from built-ins + whatever's installed — this is the concrete "DarkMixer adapts to plugins" behavior |

What a plugin can and can't do:
- **MODEL** (`extensionPoint: VISION_MODEL`) — a `.onnx` file, loaded by the
  same generic `OnnxYoloEngine.load()` the built-in YOLO model uses. Swaps
  in a different detector; doesn't add new capabilities beyond detection.
- **DECLARATIVE_SOURCE** — a JSON rule file (URL template + field paths)
  describing a new OSINT branch. Produces Leads through the same SafetyGate/
  PhoenixLite pipeline as every built-in branch — a plugin can't create a
  Lead that skips the compliance gate.
- **SCORING_WEIGHTS** — a JSON map re-tuning PhoenixLite's existing
  novelty/corroboration/actionability weights per branch. Changes
  prioritization, never adds a new probability signal.

## SCRIPT plugins (`PluginSandbox.kt`, `ScriptedLeadSource.kt`)

Added after pushback that data-only plugins weren't enough — real scripted
logic now exists, but not "the same as VS Code," because VS Code doesn't
actually sandbox extensions (full Node access, same process; its real
safety net is marketplace review, not isolation — which is exactly why
malicious VS Code extensions have been a repeated, real problem). What's
built here aims higher than that:

- **`DenyAllClassShutter`** blocks every Java class from being reachable by
  script code (Rhino's own documented hook for this), and the `Packages`/
  `java`/`org`/`com`/`edu`/`net` globals are deleted from scope on top of
  that — no LiveConnect bridge out of the interpreter at all.
- **No network/filesystem API is ever injected into script scope.** The
  host (`ScriptedLeadSource`) fetches the one URL the plugin's manifest
  declared (`declaredFetchUrlTemplate`, shown to the user before install)
  and hands the script already-fetched JSON to transform. A script cannot
  make its own network call, period.
- **Two independent resource limits** — instruction-count ceiling and
  wall-clock deadline, both checked at Rhino's own `observeInstructionCount`
  hook — either one kills a runaway script.
- Dependency: `org.mozilla:rhino:1.9.1` (confirmed current on Maven Central).

**Stated honestly, not overclaimed**: no from-memory sandbox implementation
should be called bulletproof — JS engines have had real VM-escape bugs even
in heavily-audited ones. This is genuine defense in depth (denied Java
access, no network capability, hard limits), not a guarantee against every
possible interpreter bug. `PluginSandbox.kt`'s own header comment says this
too — a SCRIPT plugin catalogue should still have a manual review step
before anything goes live, the same real safety net VS Code's marketplace
actually relies on, just layered on top of a sandbox VS Code doesn't have,
rather than instead of one.

`PluginAwarePipeline.assembleSources()` now includes SCRIPT-kind plugins
alongside DECLARATIVE_SOURCE ones — same "takes effect on the next pass,
no restart" behavior as everything else plugin-related.

## Generate JSON / Feed JSON (`CaseFileBuilder.kt`, `DarkAlgorithmController.kt`)

- **Generate JSON** — `CaseFileBuilder.generate(caseId, title,
  ownerAuthorizedScope, TargetProfile(domain=..., usernames=[...], ips=[...],
  freeTextQuery=...))` builds a complete `CaseFile` from whatever the
  operator actually knows about a target, pre-populating the right `Source`
  list (RDAP+crt.sh if a domain was given, Sherlock if a username was given,
  etc.). `DarkMixer.exportCase(caseFile)` turns it into the JSON text a
  "Generate JSON" button hands back — this already existed as a function,
  what's new is `TargetProfile` giving it something self-describing to work
  from instead of an empty shell.
- **Feed JSON** — `DarkAlgorithmController.feedJsonAndRun(jsonText, scope)`
  is the whole autonomous pipeline: imports the JSON, reads `targetProfile`
  to decide which built-in branches actually apply (no point running
  `DomainRdapSource` on a case with no domain), merges in every enabled
  plugin via `PluginAwarePipeline`, wires `YoloHydrator` so any lead with an
  image gets auto-labeled, and calls `DarkMixer.startWorker()` — a continuous
  background loop from there, same `ProgressEvent` callback as before.
  Returns the running `DarkMixer` so a "pause case" button can still
  `stop()` it; autonomous doesn't mean uncontrollable. SafetyGate and the
  authorized-scope check still run on every single pass — "autonomous"
  describes the re-run loop, not a bypass of either.
- `feedJsonAndRunWithLiveFeeds()` is the same thing plus the always-on
  live-data branches (aviation/orbital/quakes/ISS/cams/OPSEC-mirror) layered
  on top, for a caller that wants everything running rather than just the
  target-derived subset.

Import/export itself (`DarkMixer.importCase`/`.exportCase`) was already
built in the first pass — `TargetProfile` and `CaseFileBuilder` are what
make the exported JSON worth generating from a form instead of writing by
hand, and worth feeding back in to actually kick off work.

## The real project tree + UI + tests (this pass)

Everything built across this whole conversation now lives under one real
`project/` directory — not scattered flat files:

```
project/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
  app/build.gradle.kts         — every dependency this build actually used, pinned
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/farcast/
    FarcastApplication.kt      — the DI container (plain lazy singletons, no Hilt)
    MainActivity.kt            — wires state + every screen (see its own scoping note)
    agent/darkalgorithm/...    — all 50+ files from every prior pass, now correctly packaged
    ui/theme/                  — Color/Type/Shape/Theme from the design pass
    ui/screens/                — CaseManifest, Pipeline, CaseGenerator, PluginMarketplace,
                                  Settings, MomentFinder, LocationPicker, Navigation
  app/src/main/cpp/            — RE/EMU lab's final resolution (see below)
  app/src/test/java/...        — real JUnit tests: SafetyGate, PhoenixLite, CaseFileBuilder
```

**UI**: all seven screens from the design pass are real Compose, wired
through one `NavHost` with the left branch-rail from DESIGN_NOTES. Lead-
detail, key-entry dialogs, and an image-picker Activity Result contract
are the honest gaps — `MainActivity.kt`'s comments mark exactly where each
goes rather than silently omitting them.

**RE/EMU/MEDIA LAB, finally resolved** (not deferred again): there's no
verified Maven AAR for Capstone/Keystone/Unicorn on Android — checked
again this pass. The real pattern is building them from source via NDK/
CMake, which is what `app/src/main/cpp/` now scaffolds — `CMakeLists.txt`
wires git submodules + `externalNativeBuild` (commented out until the
submodules are actually checked out), `jni_bridge.cpp` has the real
function signatures stubbed with an honest "not yet linked" rather than
fake logic. ffmpeg-kit (the obvious reach for the media part) was retired
by its maintainer — don't use it; `RE_EMU_LAB.md` has the real options.

**Tests**: `SafetyGateTest` (the actual compliance boundary — tested harder
than anything else), `PhoenixLiteTest` (verifies the weighted-sum scoring
pattern really does what the X-algorithm-port README section claims),
`CaseFileBuilderTest` (generate→export→import round-trips correctly). Real
JUnit, runs with `./gradlew test`, no device needed — this is genuine Stage
7 validation for the pure-Kotlin logic, though it doesn't cover anything
requiring an actual Android runtime (Compose screens, Room, ONNX inference
on-device) — that tier still needs instrumented tests on a real device/
emulator, not something to fake a passing result for here.

## Project status — what's built vs. what's still ahead

Built so far, all under `app/agent/darkalgorithm/`:
- [x] DARK ALGORITHM core engine (Candidate Pipeline port, PhoenixLite, SafetyGate, SourceCredibility, DarkMixer)
- [x] Tor SOCKS proxying + per-branch circuit isolation
- [x] Whole-device routing (both paths)
- [x] MCP server for AI-assistant access
- [x] Stage 1 feature inventory (all 16 website panels, `FEATURE_INVENTORY.md`)
- [x] Stage 3 native feature migration — 13 of 16 panels, real APIs wired (see table above)
- [x] DarkMixer wired to every branch, not just domain/persona
- [x] Ahmia .onion search + DuckDuckGo integration (both honestly labeled on their real limitations)
- [x] ONNX Runtime + YOLO detector + Moment Finder label search
- [x] Fixed a real Tor/.onion DNS bug before it caused silent failures
- [x] SearXNG integration (image/video/audio search, sanctioned multi-engine path)
- [x] Search by image (local YOLO + optional Google Vision)
- [x] Plugin system — catalogue/download/checksum/install/uninstall
- [x] SCRIPT plugins — real scripted logic in a locked-down Rhino sandbox (no Java/network/filesystem access from scripts, hard resource limits)
- [x] DarkMixer behavior now genuinely adapts per installed plugin, live (no restart needed — see DarkMixer's sourcesProvider)
- [x] TargetProfile + CaseFileBuilder (generate JSON) + DarkAlgorithmController (feed JSON, autonomous)
- [x] Real Gradle project — every file correctly packaged under `project/`, dependencies pinned to the versions verified during this build
- [x] Full Compose UI — 7 screens (Case Manifest, Pipeline Status, Case Generator, Plugin Marketplace, Settings, Moment Finder, Location Picker), one NavHost, DESIGN_NOTES' design system actually applied rather than left as tokens nobody used
- [x] Location Picker (OSMDroid — open source, no Google Maps API key)
- [x] RE/EMU/MEDIA LAB resolved honestly — NDK/CMake scaffold + a scoped follow-up task, not a fabricated dependency coordinate
- [x] Unit tests for the core logic — SafetyGate, PhoenixLite, CaseFileBuilder — real and runnable with no device needed

What's genuinely still ahead, now a short list instead of a long one:
- [ ] The RE/EMU lab's actual native builds — needs an NDK toolchain and real device/emulator testing (`RE_EMU_LAB.md` has the scoped task)
- [ ] Instrumented (on-device) tests for Compose screens, Room, and ONNX inference — JVM unit tests don't reach these
- [ ] The gaps `MainActivity.kt`'s own comments mark explicitly: a lead-detail screen, a key-entry dialog, and the image-picker Activity Result contract — each small and well-scoped, not a redesign
- [ ] A first real compile — nothing here has gone through an actual Gradle build; the project is structured and internally consistent, but "compiles cleanly" is still an assumption until someone runs it

Tell me which of these to do next.

## Scope note

This is the DARK ALGORITHM engine only — one piece of the eleven-part spec
you uploaded. It doesn't touch ONNX/YOLO, the plugin downloader/catalogue,
or the UI; those are separate, similarly-sized pieces of work. Given how
large the full spec is, it's worth tackling the rest as separate asks
(e.g. "wire up the plugin manifest + GitHub Releases downloader next") so
each piece gets the same depth rather than everything getting rushed at once.
