# Triangulation Locator

An Android app that helps you work out **where a distant object is**, such as a column of smoke,
by pointing your phone at it from two or more different places.

At each place the app records where you are (GPS) and which direction you are facing (compass).
Where those lines of sight cross is roughly where the object is.

> This app gives an estimate to guide where to look. It is not a location to dispatch
> emergency services to. **For a real fire, call 911 or your local fire agency.**

## What works now (Phase 6: single-photo mode)

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
- **Terrain estimate (single-photo mode):** even with **one** bearing, the app can estimate
  how far away the object is. It uses the photo's tilt to follow your line of sight down to
  the ground, using free elevation data. Best from high ground looking down; rough from flat
  ground. With several bearings it's a useful cross-check.
- **Export:** save a session as a **GeoJSON** or **GPX** file to open in other map apps
  (QGIS, Google Earth, OsmAnd, caltopo…). You choose where the file goes.
- **Share the result:** send the estimated location, how sure it is, the time and an
  OpenStreetMap link to any app you choose (messages, email…), or open it in another map app.
- **Sensor check:** a live readout of GPS accuracy, compass heading, tilt and compass calibration.
- **Compass check against a landmark:** take a bearing of something whose position you know
  and see how far off the compass is. Results are kept in a list; bearings aren't changed.
- **Bearings typed in by hand:** add a bearing someone else measured (a fire lookout on the
  radio, a hand compass) by typing or picking the position and the bearing.
- **Magnetic interference warning:** the app notices when nearby metal (a car, a fence, a
  railing) is bending the compass, warns you, and widens the error band of that bearing.

Sharing sessions between phones over a private network (Tailscale) is planned but on hold;
see `docs/PHASE5-PLAN.md`.

## Privacy

- Photos, bearings and positions stay on your phone, inside the app. They don't appear in
  your Gallery and aren't backed up to the cloud. Uninstalling the app deletes them.
- The app uses the internet for only two things, each only if you turn it on:
  - **Map background**: map images from OpenStreetMap. The app asks the first time you open
    the map. OpenStreetMap's servers see which area you're looking at and your internet address.
  - **Elevation data** for terrain estimates, from the free "Terrain Tiles" collection on
    Amazon's open data service. The app asks the first time you turn on **Terrain estimate**.
    Amazon sees which area the sightlines cross and your internet address.

  Your bearings, photos and GPS position are never sent. With both off, the app makes no
  internet connections at all.
- **Export** writes a file only where you choose. The app never sends it anywhere itself.
- **Share** hands a short text to the app you pick. It contains the estimated location only,
  not where you stood. The app never sends it anywhere by itself.
- No ads, no tracking, no accounts. Everything used to build it is free.

## Guides

- [Installing the app](docs/INSTALL.md): building it on a computer and putting it on a phone
- [Using the app](docs/USAGE.md): what each part of the screen means
