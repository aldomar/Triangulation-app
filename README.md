# Triangulation Locator

An Android app that helps you work out **where a distant object is**, such as a column of smoke,
by pointing your phone at it from two or more different places.

At each place the app records where you are (GPS) and which direction you are facing (compass).
Where those lines of sight cross is roughly where the object is.

> This app gives an estimate to guide where to look. It is not a location to dispatch
> emergency services to. **For a real fire, call 911 or your local fire agency.**

## What works now (Phase 3)

- **Take a bearing:** point the camera's crosshair at the object and press the button. The
  app averages the compass over one second, takes a photo, and records your GPS position.
  You then tap exactly on the object in the photo, and the app works out the precise direction.
- **Saved bearings:** a list of every bearing taken, with photo, direction, position and time.
  Tick two or more and show them on the map.
- **Map:** each sightline drawn as a coloured wedge, the estimated location where they cross,
  and a shaded area showing how far off it might be. Works with 2 bearings or more. Warns about
  shallow crossing angles and bearings that don't agree.
- **Sensor check:** a live readout of GPS accuracy, compass heading, tilt and compass calibration.

Next: saving sessions and exporting results (GPX/GeoJSON). See `PLAN.md`.

## Privacy

- Photos, bearings and positions stay on your phone, inside the app. They don't appear in
  your Gallery and aren't backed up to the cloud. Uninstalling the app deletes them.
- The **only** internet use is downloading map images from OpenStreetMap, and only if you
  turn on **Map background**. The app asks the first time you open the map. When it's on,
  OpenStreetMap's servers see which area you're looking at and your internet address.
  Your bearings, photos and GPS position are never sent. With it off, the map shows a plain
  grid and the app makes no internet connections at all.
- No ads, no tracking, no accounts. Everything used to build it is free.

## Guides

- [Installing the app](docs/INSTALL.md): building it on a computer and putting it on a phone
- [Using the app](docs/USAGE.md): what each part of the screen means
