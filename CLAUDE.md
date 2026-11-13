# CLAUDE.md

Guidance for Claude Code in this repo. The full build plan is in `PLAN.md`.

**Starting a new conversation? Read `docs/STATUS.md` first.** It records the current phase,
branches/PRs, decisions already made, and the owner's working preferences.
Update it at the end of each phase (or whenever something important changes).

## Project
Triangulation Locator: an Android app (Kotlin + Jetpack Compose) that finds a distant object
(e.g. a smoke column) from compass bearings taken at two or more places. Sideloaded APK only.

## Rules (from PLAN.md)
- **Never commit to `main`.** Work on a feature branch and open a pull request.
- **Never approve or merge pull requests.** The repo owner always does that.
- **Plan first** for any major change, and get it agreed before writing code.
- **Ask before resource-heavy work** (full Gradle builds, emulator runs, large downloads) to save usage.
- **Free only.** Every tool, library and service must be free (open source wherever practical).
  If anything would cost money, stop and ask before implementing it.
- **No Google Maps SDK, no Google Play Services.** Use `LocationManager`, `SensorManager`, osmdroid, OSM.
- **Update docs with every major change:** `README.md`, `docs/INSTALL.md`, `docs/USAGE.md`, `docs/STATUS.md`.
  Write them for readers with limited technical experience.
- **Light on the phone.** Minimal battery, CPU and storage use. Sensors run only while a screen is visible.
- **Fully self-contained and private.** No analytics, crash reporting, ads or telemetry.
  Never send usage data anywhere. Network access only for features the user turns on
  (map tiles, optional tailnet sharing) in later phases.

## Build phases
Work one phase at a time; each must build and run on a real phone. Status:
- [x] Phase 1: Sensor test screen
- [x] Phase 2: Capture flow
- [x] Phase 3: Solve and map (includes the 3+ bearing least-squares fit, pulled forward from Phase 4)
- [x] Phase 4: History (Room sessions) and GPX/GeoJSON export
- [ ] Phase 4b: Car mode + driving test (built before Phase 5 at the owner's request)
- [ ] Phase 5: Tailnet sharing (on hold; plan in `docs/PHASE5-PLAN.md`)
- [ ] Phase 6: Single-photo mode

## Layout
- `app/src/main/java/com/triangulation/locator/`
  - `MainActivity.kt`: entry point and screen switching (simple enum, no navigation library)
  - `sensors/`: GPS (`GpsTracker.kt`), compass/tilt (`OrientationTracker.kt`) and gyroscope-only heading for car mode (`GyroTracker.kt`) as Kotlin Flows; `AngleMath.kt` (pure)
  - `car/`: `CarMath.kt` car-mode line-up, error band, position extrapolation, driving-test stats; pure Kotlin (unit-tested)
  - `camera/`: CameraX photo capture and lens optics (focal length for tap offsets)
  - `data/`: `Observation` + `Session` Room entities, `Database.kt` (AppDatabase + DAO), `ObservationStore` (DB + JPEGs in app-private `filesDir/observations`), `BearingMath`, `Settings`
  - `export/`: `SessionExport.kt` GeoJSON/GPX writers and `ShareText.kt` share message, pure Kotlin (unit-tested)
  - `solver/`: pure Kotlin (no Android imports) so it unit-tests on the JVM. `Geo.kt` local east/north
    projection; `Solver.kt` 2-line intersection + quadrilateral uncertainty, 3+ weighted least squares + covariance ellipse
  - `map/`: osmdroid setup (`MapSetup.kt`) and the custom dot overlay
  - `terrain/`: single-photo mode. `TerrainMath.kt` (line of sight to the ground) and `Terrarium.kt`
    (elevation tile maths) are pure Kotlin; `TerrainTiles.kt` downloads/caches tiles and runs estimates
- Tests: `app/src/test/.../solver/SolverTest.kt`, `export/SessionExportTest.kt`, `export/ShareTextTest.kt`, `solver/CoordinateParserTest.kt`, `solver/LandmarkCheckTest.kt`, `sensors/MagneticCheckTest.kt`, `car/CarMathTest.kt`, `terrain/TerrainMathTest.kt`, `camera/CameraOpticsTest.kt` (JUnit 4).
  - `ui/`: Compose screens (`CaptureScreen`, `SessionsScreen` = History, `SessionScreen`, `MapScreen`, `SensorTestScreen` incl. driving test, `TypedBearingScreen`, `LandmarkCheckScreen`, `PointPicker` tap-to-choose map), `ExportUi.kt`, `CarModeUi.kt`, shared bits in `Common.kt`; `ui/theme/` colours
- Versions are in `gradle/libs.versions.toml` (AGP 9 with built-in Kotlin, Gradle 9).
  Android Studio 2026.2 runs Gradle on Java 25, so Gradle must stay at 9.1 or newer.

## Conventions
- Heading = direction the back camera points (phone upright, portrait), from the rotation-vector sensor.
- True heading = magnetic heading + `GeomagneticField` declination.
- Bearing = averaged (1 s, raw samples, circular mean) magnetic heading + declination + tap offset.
  Tap offset uses focal length from Camera2 characteristics, pitch-corrected (`BearingMath.tapOffsetDeg`).
- Zoom presets (1×/3×/5×/10×, capture screen): the saved `focalLengthFraction` already includes the zoom
  (read back from CameraX at the photo). `Observation.zoomRatio` (DB v6) is for display only; null = 1×.
- Photos stay in app-private storage, never the Gallery. Saved at most 1920 px on the long side.
- Storage is Room (sessions + observations). Schema changes need a proper `Migration`; never use destructive fallback. Room/KSP via KSP plugin (AGP 9 built-in Kotlin, no kapt).
- Magnetic interference: `MagneticCheck` compares the magnetometer field strength with `GeomagneticField.fieldStrength`;
  more than 15% off = disturbed (warn, default error band ±15°, saved as `Observation.magneticFieldRatio`, DB v3).
- Help: `ui/Help.kt` holds the in-app guide (`HelpTopic` sections, `HelpButton` "?" on every screen, shown as a
  dialog so the screen underneath keeps its state). When a feature changes, update both `Help.kt` and `docs/USAGE.md`.
- Compass checks (`CompassCheck` table, DB v5; `ui/LandmarkCheckScreen.kt`, maths in `solver/LandmarkCheck.kt`):
  measure and record only, never change bearings (owner's choice). `CaptureScreen` runs in check mode via `check: CheckTarget`.
- Typed-in bearings (`Observation.typedIn`, DB v4): no photo (`photoFileName` blank; always check `hasPhoto` before touching files),
  no tilt (`TerrainResult.NoTilt`), `compassAccuracy` -1. Positions parsed by pure `solver/CoordinateParser.kt`.
- Car mode: never use the compass inside a car; heading = gyroscope (game rotation vector) + line-up offset from GPS course. Passenger-only warnings must stay.
- Export uses SAF `CreateDocument` only (user picks the location); the app itself never uploads exports.
- Share (map screen) hands plain text to `ACTION_SEND` via the chooser, and "Open in map app" uses a `geo:` intent.
  Both start only from a user tap. Shared text has the estimate only, never observer positions (`export/ShareText.kt`, unit-tested).
- The user builds and runs the app in Android Studio; don't run Gradle builds without asking.
- GPS uses `GPS_PROVIDER` only. The network provider can send Wi-Fi/cell data to outside services.
- `INTERNET` is used only for OpenStreetMap tiles and elevation tiles (terrain estimate), each opt-in.
  Elevation: `Settings.elevationDownloadsEnabled`, cache `cacheDir/terrain` capped at 100 MB.
- Map tiles only after the user opts in
  (`Settings.mapTilesEnabled`, asked on first map open, switch on the map screen). When off:
  `setUseDataConnection(false)`, tiles hidden, grid shown. Tile cache in `cacheDir`, capped at 50 MB.
  User-Agent is the app name/version only. Never bulk-download tiles (OSM policy).
- Keep "© OpenStreetMap contributors" visible whenever tiles are shown.
