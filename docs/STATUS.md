# Project status and handover notes

Read this first when picking the project up in a new conversation. It records where the work
stands, decisions already made, and how the owner likes to work. `PLAN.md` is the original
build plan; `CLAUDE.md` holds the standing rules.

_Last updated: 2026-11-11_

## Where we are

| Phase | State | Branch | PR |
| --- | --- | --- | --- |
| 1. Sensor test screen | Done, built and run on a phone | `phase-1-sensor-test` | #1 → `main` |
| 2. Capture flow | Done, built and run on a phone (indoors only) | `phase-2-capture-flow` | #2 → `phase-1-sensor-test` |
| 3. Solve and map (+ 3+ bearing fit) | Done, built and run on a phone; map displays correctly | `phase-3-solve-and-map` | #3 → `phase-2-capture-flow` |
| 4. History (Room sessions) + GPX/GeoJSON export | Done, built and run on a phone (old bearings migrated, new sessions work) | `phase-4-history-export` | #4 → `phase-3-solve-and-map` |
| 5. Tailnet sharing | Not started | | |
| 6. Single-photo mode | Not started | | |

**Immediate next step:** plan Phase 5 (tailnet sharing) and get it agreed before coding.
Create `phase-5-...` from `phase-4-history-export`; its PR targets that branch.

Phase 4 notes: KSP 2.3.10 + Room 2.8.4 build fine with AGP 9.5.1 built-in Kotlin. The legacy
JSON bearings migrated into "Earlier bearings" with their photos on the owner's phone.

**Not yet verified anywhere:**
- Real-world accuracy outdoors. Nobody has yet checked a bearing against a known landmark,
  tested the tap-offset correction away from the photo centre, or triangulated a known point.
  The owner has so far only tested indoors.
- The owner hasn't confirmed running the unit tests (`app/src/test`: `SolverTest.kt`,
  `SessionExportTest.kt`).
- Export (GeoJSON/GPX) hasn't been tried on the phone, and no exported file has been opened in
  another app yet.

## How the owner works

- **Stacked PRs as save points.** Each phase gets its own branch, made from the previous
  phase's branch, and its PR targets that previous branch. The owner merges the whole stack
  later. Never commit to `main`, and never merge or approve PRs.
- **The owner builds and runs the app** in Android Studio and reports errors back. Don't run
  Gradle builds or emulators without asking (usage cost). Since nothing compiles the code
  before the owner does, re-read new code carefully for compile errors.
- **Plan first** for each phase. Present a short plan with any decisions needed, wait for
  "go", then build. Commit only after the owner confirms the build runs.
- **Standalone APK later.** The owner wants to get further along before making a signed
  release APK. For now they install from Android Studio over USB.
- Docs (`README.md`, `docs/INSTALL.md`, `docs/USAGE.md`) are updated with every phase,
  written for non-technical readers.

## Decisions already made

- **Capture:** shutter → heading averaged over 1 s from raw rotation-vector samples (circular
  mean), photo taken mid-window → user taps the target **on the still photo** (not the live
  preview) → error band slider (default ±5/10/15° from compass calibration) → save.
- **Tap offset** uses the focal length from Camera2 characteristics, with a pitch correction
  (`BearingMath.tapOffsetDeg`). It falls back to an assumed 50° field of view if the phone
  doesn't report its lens.
- **Storage (Phase 4):** Room database `triangulation.db` with `sessions` and `observations`
  tables (`data/Database.kt`), and photos as JPEGs (≤1920 px) in app-private
  `filesDir/observations`. Not in the Gallery, and backups are disabled. Schema version 1;
  any later change needs a real `Migration` (no destructive fallback). Schemas are exported to
  `app/schemas/`. Room 2.8.4 and KSP 2.3.10.
- **Legacy JSON migration:** on first use, `ObservationStore` moves the Phase 2/3 JSON files
  into an "Earlier bearings" session, then deletes the JSON (it retries on failure). This code
  can be removed once no phone has old data.
- **Sessions (owner agreed):** new bearings go into the *active* session
  (`Settings.activeSessionId`). If it isn't set, the most recently used session is used, and a
  new one is created on the first save if none exists. "New session" is on the capture screen.
  Each bearing has an `included` flag (its tick), saved in the database.
- **Export (owner agreed):** "Save to…" through the Storage Access Framework
  (`CreateDocument`), with no share sheet and no permissions. GeoJSON (RFC 7946,
  counter-clockwise ring, extra `properties` on the FeatureCollection) and GPX 1.1
  (waypoints, a route per sightline, and the uncertainty area as a closed track). Only ticked
  bearings are exported. MIME type `application/octet-stream`, so pickers keep the extension.
  Pure Kotlin in `export/SessionExport.kt`, with unit tests.
- **Navigation:** a simple enum in `MainActivity`, with no navigation library.
- **Multi-bearing solve was pulled into Phase 3** at the owner's request: weighted least
  squares for 3+ bearings, with an uncertainty ellipse scaled up when bearings disagree.
- **Map tiles are opt-in (owner's choice).** On first map open the app asks, and the choice
  is stored in `Settings.mapTilesEnabled`. A switch on the map screen changes it. When off,
  the app makes no network use at all and shows a lat/lon grid. Tiles are cached in `cacheDir`,
  capped at 50 MB, and the User-Agent is the app name/version only. `INTERNET` +
  `ACCESS_NETWORK_STATE` were added in Phase 3 for this alone.
- **Map library:** osmdroid 6.1.20, chosen for simplicity. Its development appears to have
  stopped; MapLibre is the fallback named in `PLAN.md` if it ever becomes a problem.
- GPS uses `GPS_PROVIDER` only (no network provider, no Play Services).

## Environment notes (owner's Windows PC)

- Android Studio 2026.2 (installed at `D:\Android\Android Studio`) runs Gradle on **Java 25**,
  so Gradle must be ≥ 9.1. Studio upgraded the project to **AGP 9.5.1 / Gradle 9.6.0** on
  first sync. AGP 9 has built-in Kotlin, so don't apply `kotlin-android`.
- Library versions: Compose BOM 2025.09.00, CameraX 1.4.2, osmdroid 6.1.20, Room 2.8.4
  (KSP 2.3.10), JUnit 4.13.2.
  compileSdk/targetSdk 36, minSdk 26.
- No `gradlew` scripts or wrapper jar are committed. Studio uses `gradle-wrapper.properties` directly.
- **Python is not installed.** Use the editor tools or Git Bash for file edits.
- **Shell heredocs in Git Bash halved backslashes** (`\\.` became `\.`) when writing files once,
  which broke `settings.gradle.kts`. Write files containing backslashes with the editor
  tools, not heredocs.
- The old `.idea/` folder from a plain IntelliJ project was left alone; `.idea/` is git-ignored.

## Known limitations / ideas for later

- Tap offset ignores roll (the app warns when roll > 5°).
- The flat-grid maths is accurate within about 50 km; beyond that the app warns.
- Exports don't include photos. A ZIP option could be added later if wanted.
- No "undo" for deletes; there are confirmation dialogs instead.
