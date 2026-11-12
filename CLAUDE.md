# CLAUDE.md

Guidance for Claude Code in this repo. The full build plan is in `PLAN.md`.

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
- **Update docs with every major change:** `README.md`, `docs/INSTALL.md`, `docs/USAGE.md`.
  Write them for readers with limited technical experience.
- **Light on the phone.** Minimal battery, CPU and storage use. Sensors run only while a screen is visible.
- **Fully self-contained and private.** No analytics, crash reporting, ads or telemetry.
  Never send usage data anywhere. Network access only for features the user turns on
  (map tiles, optional tailnet sharing) in later phases.

## Build phases
Work one phase at a time; each must build and run on a real phone. Status:
- [x] Phase 1: Sensor test screen
- [x] Phase 2: Capture flow
- [ ] Phase 3: Solve and map
- [ ] Phase 4: Multi-bearing and history
- [ ] Phase 5: Tailnet sharing
- [ ] Phase 6: Single-photo mode

## Layout
- `app/src/main/java/com/triangulation/locator/`
  - `MainActivity.kt`: entry point and screen switching (simple enum, no navigation library)
  - `sensors/`: GPS (`GpsTracker.kt`) and compass/tilt (`OrientationTracker.kt`) as Kotlin Flows; `AngleMath.kt`
  - `camera/`: CameraX photo capture and lens optics (focal length for tap offsets)
  - `data/`: `Observation` model, `ObservationStore` (JPEG + JSON in app-private `filesDir/observations`), `BearingMath`
  - `ui/`: Compose screens (`CaptureScreen`, `ObservationsScreen`, `SensorTestScreen`), shared bits in `Common.kt`; `ui/theme/` colours
- Versions are in `gradle/libs.versions.toml` (AGP 9 with built-in Kotlin, Gradle 9).
  Android Studio 2026.2 runs Gradle on Java 25, so Gradle must stay at 9.1 or newer.

## Conventions
- Heading = direction the back camera points (phone upright, portrait), from the rotation-vector sensor.
- True heading = magnetic heading + `GeomagneticField` declination.
- Bearing = averaged (1 s, raw samples, circular mean) magnetic heading + declination + tap offset.
  Tap offset uses focal length from Camera2 characteristics, pitch-corrected (`BearingMath.tapOffsetDeg`).
- Photos stay in app-private storage, never the Gallery. Saved at most 1920 px on the long side.
- Room arrives in Phase 4; until then observations are JSON files.
- The user builds and runs the app in Android Studio; don't run Gradle builds without asking.
- GPS uses `GPS_PROVIDER` only. The network provider can send Wi-Fi/cell data to outside services.
- No `INTERNET` permission until a phase actually needs it.
