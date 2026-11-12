# Triangulation Locator

An Android app that helps you work out **where a distant object is**, such as a column of smoke,
by pointing your phone at it from two or more different places.

At each place the app records where you are (GPS) and which direction you are facing (compass).
Where those lines of sight cross is roughly where the object is.

> This app gives an estimate to guide where to look. It is not a location to dispatch
> emergency services to. **For a real fire, call 911 or your local fire agency.**

## What works now (Phase 4 + car mode)

- **Take a bearing:** point the camera's crosshair at the object and press the button. The
  app averages the compass over one second, takes a photo, and records your GPS position.
  You then tap exactly on the object in the photo, and the app works out the precise direction.
- **Car mode (passengers only):** take bearings from a moving car. The compass is unreliable
  inside a car, so this uses the gyroscope, lined up with the GPS direction of travel. A
  **driving test** on the Sensors screen measures how well it works on your phone and car.
  *Not yet tested on the road.*
- **Sessions:** bearings of the same object are kept together in a session (for example
  "Smoke north of town"). New bearings go into the active session; start a new one for each
  new object.
- **History:** every session with its date, number of bearings and estimated location. Open one
  to see its bearings, tick the ones to use, rename it, move bearings between sessions, or delete.
- **Map:** each sightline drawn as a coloured wedge, the estimated location where they cross,
  and a shaded area showing how far off it might be. Works with 2 bearings or more. Warns about
  shallow crossing angles and bearings that don't agree.
- **Export:** save a session as a **GeoJSON** or **GPX** file to open in other map apps
  (QGIS, Google Earth, OsmAnd, caltopo…). You choose where the file goes.
- **Sensor check:** a live readout of GPS accuracy, compass heading, tilt and compass calibration.

Next: sharing sessions between phones over a private network (Tailscale). See `PLAN.md`.

## Privacy

- Photos, bearings and positions stay on your phone, inside the app. They don't appear in
  your Gallery and aren't backed up to the cloud. Uninstalling the app deletes them.
- The **only** internet use is downloading map images from OpenStreetMap, and only if you
  turn on **Map background**. The app asks the first time you open the map. When it's on,
  OpenStreetMap's servers see which area you're looking at and your internet address.
  Your bearings, photos and GPS position are never sent. With it off, the map shows a plain
  grid and the app makes no internet connections at all.
- **Export** writes a file only where you choose. The app never sends it anywhere itself.
- No ads, no tracking, no accounts. Everything used to build it is free.

## Guides

- [Installing the app](docs/INSTALL.md): building it on a computer and putting it on a phone
- [Using the app](docs/USAGE.md): what each part of the screen means
