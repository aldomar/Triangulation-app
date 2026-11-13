# Project status and handover notes

Read this first when picking the project up in a new conversation. It records where the work
stands, decisions already made, and how the owner likes to work. `PLAN.md` is the original
build plan; `CLAUDE.md` holds the standing rules.

_Last updated: 2026-11-13_

## Where we are

| Phase | State | Branch | PR |
| --- | --- | --- | --- |
| 1. Sensor test screen | Done, built and run on a phone | `phase-1-sensor-test` | #1 → `main` |
| 2. Capture flow | Done, built and run on a phone (indoors only) | `phase-2-capture-flow` | #2 → `phase-1-sensor-test` |
| 3. Solve and map (+ 3+ bearing fit) | Done, built and run on a phone; map displays correctly | `phase-3-solve-and-map` | #3 → `phase-2-capture-flow` |
| 4. History (Room sessions) + GPX/GeoJSON export | Done, built and run on a phone (old bearings migrated, new sessions work) | `phase-4-history-export` | #4 → `phase-3-solve-and-map` |
| 4b. Car mode + driving test (added by owner before Phase 5) | Built and run on a phone; DB upgrade v1→v2 kept all bearings; car-mode UI, line-up checks and driving-test card behave correctly when stationary; all tests pass. **Not yet road-tested.** | `phase-4b-car-mode` | #5 → `phase-4-history-export` |
| 5. Tailnet sharing | **On hold** (owner's choice). Plan saved in `docs/PHASE5-PLAN.md`; not built. | | |
| 6. Single-photo mode (terrain estimate) | Code complete; pushed and PR opened at the owner's request. **Outdoor testing still to do** (checklist below). | `phase-6-single-photo` | #6 → `phase-4b-car-mode` |
| Map layout fix | Done, checked on the phone | `map-layout-fix` | #7 → `phase-6-single-photo` |

**On 2026-11-12 the owner merged PRs #1–#8 into `main`.** New work branches off `main`.

**Improvements chosen by the owner (2026-11-12),** built in this order, one branch and PR each
(full plan agreed in conversation; summary here):
1. **Share result** (`share-result`): Share button (text: location, uncertainty, time, OSM link;
   never observer positions) and Open in map app on the map. No database change.
   Built and checked on the phone. PR #9 → `main`.
2. **Magnetic interference warning** (`compass-interference`): compare measured field strength
   with `GeomagneticField`; warn when off by more than 15%; save the ratio with each bearing (DB v3).
   Built and checked on the phone. PR #10 → `share-result`.
3. **Type in a bearing** (`typed-bearing`): add a bearing by hand (position, true/magnetic
   bearing, error, time, note), e.g. from a lookout. No photo. DB v4.
   Built and checked on the phone. PR #11 → `compass-interference`.
4. **Landmark check** (`landmark-check`): take a bearing of a known point and record the compass
   error. **Measure and record only** (owner's choice); no automatic correction yet. DB v5.
   Built and flow-tested on the phone (indoors); unit tests pass. PR #12 → `typed-bearing`.
   Starts from the Sensors screen; reuses `CaptureScreen` in check mode (`CheckTarget`) and
   `PointPicker`. Pure maths in `solver/LandmarkCheck.kt` and great-circle helpers in `Geo.kt`.
   **Next step after this:** use landmark checks outdoors to decide whether automatic correction
   is worth building, and to tune the interference limit (15%) and default error bands.
5. **In-app help guide** (`help-guide`, stacked on `landmark-check`; owner's request): a round
   "?" on every screen opens a full-screen help dialog at that screen's topic, with Contents.
   Short phone-sized text in `ui/Help.kt` (owner chose this over showing USAGE.md); opens once
   by itself on first launch (`Settings.helpShown`). Built and checked on the phone. PR #13 → `landmark-check`.
6. **Zoom presets** (`zoom-presets`, off `main`; owner's request, 2026-11-13): 1×/3×/5×/10×
   buttons under the camera view (only those the phone supports; starts at 1× each time).
   Zoom is applied by CameraX; the zoom actually in use at the photo is read back and multiplied
   into the saved `focalLengthFraction` (`CameraOptics.focalFraction(..., zoomRatio)`), so the
   tap offset, terrain estimate and solver need no other change. `Observation.zoomRatio` is
   display only (DB v6, AutoMigration; null = 1×). Unit test `camera/CameraOpticsTest.kt`.
   **Not yet built or run on the phone.** To check: presets change the preview; a landmark
   check at 1× and 5× (target off centre) agrees within about 0.5°; old sessions still open.

**Immediate next step:** outdoor testing of Phases 6 and 4b (checklists below), then tune the
guesses from the results. Phase 5 is on hold (plan kept in `docs/PHASE5-PLAN.md`).

**Phase 6 test checklist (still to do):**
- [ ] Unit tests pass (`TerrainMathTest`, new export test).
- [ ] Switching on **Terrain estimate** asks about downloads; elevation tiles download (the AWS
  Terrain Tiles address hasn't been checked from a phone yet) and work again offline.
- [ ] With downloads off and nothing saved, the "no elevation data" message shows; no network use.
- [ ] From high ground, one bearing of a known landmark: how far is the terrain estimate off,
  and is the landmark inside the dashed area? Try tilt error ±1° and ±2°.
- [ ] From flat ground: confirm the "very uncertain" warning and open-ended far range appear.
- [ ] With 2+ bearings: terrain points land near the crossing point.
- [ ] Export from the map with terrain on: GeoJSON/GPX open correctly with the terrain points/areas.
- [ ] Tap-offset correction away from the photo centre (still unverified from Phase 2).
**Pending whenever the owner has time:** road-test the driving test, then car-mode bearings.
Use the results to tune the guesses listed below.

**Single-photo mode (6), agreed design.** The owner accepted all recommendations.
- Elevation from the free AWS "Terrain Tiles" (Terrarium PNG, zoom 12, about 30 m), decoded with
  `BitmapFactory`; no new libraries. Opt-in like map tiles: `Settings.elevationDownloadsEnabled`
  (asked the first time **Terrain estimate** is switched on), a switch below the map. Saved tiles
  in `cacheDir/terrain`, capped at 100 MB, used offline even with downloads off.
- Line-of-sight angle from the saved pitch + vertical tap + focal length
  (`TerrainMath.elevationAngleDeg`), so **old bearings work too and the database is unchanged**.
  Roll is ignored, as for the tap offset.
- Eye = elevation-data ground at the observer + 1.5 m (GPS height not used). March every 15 m
  from 30 m out to 40 km, with Earth curvature and refraction (k = 0.13).
- Near/far from camera tilt ± error (default ±1°; choices 0.5/1/2°). **Deviation from the plan:**
  the tilt error is one setting on the map (`Settings.tiltErrorDeg`), not per bearing, to avoid
  a database change. The area is a ring segment: near–far along the bearing, ± its error band.
- Shown via a **Terrain estimate** switch on the map (`Settings.showTerrain`), for every ticked
  bearing. The map now opens with one ticked bearing. Exports from the map include terrain
  estimates when the switch is on.
- **Untested guesses:** default tilt error ±1°, "very uncertain" when far > 3 × near or open-ended.

**Car mode (4b), agreed design.** The owner chose to build the driving test and car mode
together, then road-test later.
- In a car the compass is unusable (metal distorts it). Car mode uses `TYPE_GAME_ROTATION_VECTOR`
  (gyroscope + accelerometer, no magnetometer), "lined up" by the user against the GPS
  direction of travel (`Location.bearing`). The line-up rule is: crosshair on a distant point
  straight down the road, at ≥15 km/h, GPS direction accuracy ≤3°, no turn >2° in the last 3 s.
- A line-up is valid only for the gyroscope run it was made in (`runId`). The gyroscope flow is
  shared (`shareIn`) so the readout, line-up and capture use one sensor run. Leaving the capture
  screen or sleeping means lining up again.
- Car mode is off at every app start (`CarSession`, in memory only). The "passenger only"
  notice is shown the first time it's turned on (`Settings.carSafetyAcknowledged`), and a
  banner is always visible while it's on.
- Car bearings: `magneticHeadingDeg` holds the TRUE heading, `declinationDeg` = 0,
  `compassAccuracy` = -1, `headingFromCar` = true, plus `speedMps` and `lineUpAgeSeconds`.
  The position is moved on from the last GPS fix to the photo time (capped at 3 s).
- Default error band = max(2°, GPS direction accuracy) + 1°/min since line-up
  (`CarMath.DRIFT_DEG_PER_MIN`, to be tuned from the driving test).
- The driving test is on the Sensors screen; the gyroscope runs only after "Start driving
  test". It counts samples only on straight stretches above 20 km/h.
- Database v2 via Room `AutoMigration(1, 2)` (adds the three columns).
- **Untuned guesses to revisit after road tests:** drift 1°/min, line-up wobble limit 1.5°,
  stale after 3 min, turn threshold 2°.

Phase 4 notes: all unit tests pass, and an exported file opened correctly in Google Earth.
KSP 2.3.10 + Room 2.8.4 build fine with AGP 9.5.1 built-in Kotlin. The legacy
JSON bearings migrated into "Earlier bearings" with their photos on the owner's phone.

**Not yet verified anywhere:**
- Real-world accuracy outdoors. Nobody has yet checked a bearing against a known landmark,
  tested the tap-offset correction away from the photo centre, or triangulated a known point.
  The owner has so far only tested indoors.
- Car mode and the driving test haven't been used in a car yet.
- Terrain (single-photo) estimates haven't been checked outdoors or against a known point.

## How the owner works

- **PRs as save points.** Each feature gets its own branch and PR. Since 2026-11-12 (stack
  merged) branches come off `main`; if the previous PR isn't merged yet, branch off it instead.
  The owner merges. Never commit to `main`, and never merge or approve PRs.
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
