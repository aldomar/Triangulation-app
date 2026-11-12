# Triangulation Locator — Android App Plan

> **For Claude Code:** This is the build plan for this repo. Work through the "Build phases" in order, one phase at a time. Each phase must end with an app that builds and runs on a real phone. Stick to the constraints below: free and open-source tools only, no Google Maps SDK, no Google Play Services.

## Overview

Yes: this app can be built, installed and run for $0, using only free and open-source tools. You need no app store account, no paid map service and no paid server.

The app lets a user photograph a distant object (for example a smoke column) from two or more places. At each shot it records the GPS position and the compass bearing to the object. It then draws the sightlines on a map and marks where they cross, with an uncertainty area.

**Constraints this plan follows**

- Android only, installed as a sideloaded APK. No Google Play listing.
- Every tool, library and service is free. Open source wherever practical.
- No Google Maps API key and no billing account of any kind.
- Devices already share a free Tailscale tailnet. The plan uses it to hand out the APK and, optionally, to share observations between phones.

## Software you need (all free)

The only thing to install on the computer is Android Studio. It bundles the JDK, the Android SDK and Gradle. Everything else is a library pulled in by the build, or something you probably already have.

| Purpose | Tool | License | Cost |
| --- | --- | --- | --- |
| IDE, build, emulator | Android Studio (bundles JDK, Android SDK, Gradle) | Free; Gradle and most components Apache 2.0 | $0 |
| Language | Kotlin, with Jetpack Compose for the UI | Apache 2.0 | $0 |
| Camera preview and capture | CameraX (Jetpack) | Apache 2.0 | $0 |
| GPS position | Android `LocationManager` (built into the OS) | Part of AOSP | $0 |
| Compass heading and tilt | Android `SensorManager` rotation-vector sensor, plus `GeomagneticField` for true-north correction | Part of AOSP | $0 |
| Map view | osmdroid (simplest), or MapLibre Native Android | Apache 2.0 / BSD-2 | $0 |
| Map data | OpenStreetMap | ODbL (attribution required) | $0 |
| Bearing intersection math | Your own Kotlin (about 100 lines), or GeographicLib for Java | MIT | $0 |
| Local storage of sessions | Room on SQLite | Apache 2.0 / public domain | $0 |
| Source control | Git | GPL-2.0 | $0 |
| APK distribution and optional sync | Tailscale (Personal plan), plus a simple file server | Free for personal use | $0 |
| Optional shared-observation server | Python + FastAPI + SQLite on any tailnet machine | MIT / public domain | $0 |
| Optional self-hosted map tiles | Protomaps PMTiles extract, served over the tailnet | BSD-3 tooling, ODbL data | $0 |

**Deliberately avoided:** the Google Maps SDK (needs an API key tied to a billing account) and Google Play Services location. The built-in `LocationManager` works on every Android phone, including ones without Google services.

## How the app works

Everything needed for a location fix runs on the phone, offline if necessary. The tailnet machine only adds installs, shared sessions and self-hosted maps.

```
Android app (sideloaded APK)                      Tailnet machine (optional)
┌───────────────────────────────────────────┐     ┌──────────────────────────┐
│ Camera (CameraX photo + tap point)        │◄────│ APK file server   install│
│ Sensors (GPS, compass, tilt)              │     │                          │
│        ▼                                  │     │                          │
│ Observation: position, true bearing,      │◄───►│ Observation server  sync │
│   tap offset, time, error band            │     │ (FastAPI + SQLite)       │
│        ▼                     ▼            │     │                          │
│ Solver (crossing + error)  Storage (Room) │     │                          │
│        ▼                                  │     │                          │
│ Map view (osmdroid: wedges, fix, area)    │◄────│ Tile server (PMTiles)    │
└───────────────────────────────────────────┘     └──────────────────────────┘
```

Each photo becomes one observation record. The solver needs two or more of them to place a point.

**The math, in three steps**

1. **Bearing to the object.** Start from the compass heading, add the declination from `GeomagneticField` to get true north, then add the tap offset. The tap offset comes from where the user tapped in the photo, using the camera's focal length in pixels:
   `bearing = heading_true + atan((x_tap - x_center) / f_px)`
2. **Intersection.** Convert both positions to a local flat east/north grid (accurate enough under about 50 km), then intersect the two lines. For three or more bearings, use a least-squares fit.
3. **Uncertainty.** Repeat the intersection at bearing ± error for each line. The four corner points outline the area to draw on the map.

## Maps without paying

Start with OpenStreetMap's public tiles. Move to tiles you host yourself if usage grows or you want maps that work offline.

**Stage 1: public OSM tiles.** OSM data is free, but its tile servers are volunteer-run and have a [usage policy](https://operations.osmfoundation.org/policies/tiles/). A handful of personal devices browsing normally is fine. Follow these rules:

- Set a unique User-Agent (osmdroid requires this anyway).
- Show the "© OpenStreetMap contributors" credit on the map.
- Never bulk-download areas for offline use from these servers.
- Respect the cache headers.

**Stage 2: self-hosted tiles over Tailscale.** Download a regional extract once as a single PMTiles file (Protomaps publishes free OSM builds). Serve it from any always-on machine on your tailnet.

**Stage 3 (optional): fully offline.** Copy the regional tile file onto each phone. The map then works with no signal at all. GPS and the compass never need a connection.

## How Tailscale fits in

The app works on its own, with no Tailscale at all. The tailnet adds three optional extras at no cost.

1. **Handing out the APK.** Serve the built APK from any tailnet machine, for example with `python3 -m http.server 8080`. Each phone opens `http://<machine-name>:8080/` and taps the file. Taildrop also works.
2. **Sharing observations between phones.** A small FastAPI + SQLite service on an always-on tailnet machine. Each phone posts its bearings (position, true bearing, time, estimated error). Two people at different spots build one fix together.
3. **Serving map tiles.** The same machine can serve the PMTiles file.

Use Tailscale's MagicDNS name in the app settings rather than an IP address.

## Build phases

Each phase ends with something usable on a phone. Phases 1 to 3 are the core app. Phases 4 to 6 are optional.

1. **Sensor test screen.** Live readout of GPS position and accuracy, true heading, pitch, and compass accuracy status.
2. **Capture flow.** Camera preview with a crosshair. The user frames the object, taps it, and the app saves the photo, position, heading, tap offset and timestamp. Heading is averaged over about 1 second.
3. **Solve and map.** Draw each sightline as a wedge on the osmdroid map. Compute the crossing point and its uncertainty area. Warn when the angle between sightlines is under about 30°.
4. **Multi-bearing and history.** Three or more observations with a best-fit solution. Save sessions in Room; export as GPX or GeoJSON.
5. **Tailnet sharing.** The FastAPI observation server, plus a "shared session" mode in the app. Use `tailscale serve` for HTTPS; plain HTTP needs an Android network-security exception.
6. **Single-photo mode.** Use phone tilt plus free elevation data (for example Copernicus 30 m DEM) to project one sightline onto the terrain.

## Building, signing and installing the APK

1. Create one signing keystore (Build → Generate Signed App Bundle or APK → create new). Back it up. Every update must use the same key.
2. Build a release APK and put it on the tailnet file server.
3. On each phone, allow "Install unknown apps" for the browser or file manager, then open the APK.

**Developer verification (2027):** Google plans to require registered developers for normal installs on certified phones worldwide in 2027. Free routes remain: a free limited distribution account for small groups, ADB installs (exempt; wireless debugging works over the tailnet), or the advanced sideloading flow (one-time 24-hour wait).

## Accuracy, risks and limits

| Risk | Effect | Mitigation in the app |
| --- | --- | --- |
| Compass error of 5–15°, worse near cars, metal or power lines | At 10 km, 5° shifts a sightline by about 870 m | Show sensor accuracy status; prompt figure-8 calibration; average heading over ~1 s; user-set error band |
| Sightlines crossing at a shallow angle | Fix smears along the line, often by kilometres | Show crossing angle; warn under ~30°; suggest where to move |
| Phone not held level or upright | Heading drifts | Rotation-vector sensor in camera orientation; level indicator |
| Smoke drifts between shots | Photos aim at different points | Tap the plume base; timestamp shots; favour two people shooting at once |
| Cleartext HTTP blocked by Android | App can't reach tailnet server | `tailscale serve` with HTTPS, or allow cleartext for the tailnet hostname only |
| Developer verification in 2027 | Normal installs blocked for unregistered apps | Free limited distribution account, or ADB |

The app gives an estimate to guide where to look, not a location to dispatch from. For a real fire, report it to 911 or the local fire agency.

## Rules
Never make commits directly to main, never approve and merge pull requests that will always be done by the repo owner.
Create a Claude.md file from the rules as needed in this plan. Any major changes always make plans first. Any resource heavy processes should be approved before beginning to save on usage. As mentioned above everything used should be a free tier if anything is going to cost money stop before implementing it.
Update usage, installation, read.me etc documentation with every major change, assume that those reading the usage and installation documentation have limited technical experience.
Nothing about this app should have minimal usage of a phones resources and should be entirely self-contained never sharing any usage info out to anyone or thing.