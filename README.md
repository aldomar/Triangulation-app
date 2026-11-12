# Triangulation Locator

An Android app that helps you work out **where a distant object is**, such as a column of smoke,
by pointing your phone at it from two or more different places.

At each place the app records where you are (GPS) and which direction you are facing (compass).
Where those lines of sight cross is roughly where the object is.

> This app gives an estimate to guide where to look. It is not a location to dispatch
> emergency services to. **For a real fire, call 911 or your local fire agency.**

## What works now (Phase 2)

- **Take a bearing:** point the camera's crosshair at the object and press the button. The
  app averages the compass over one second, takes a photo, and records your GPS position.
  You then tap exactly on the object in the photo, and the app works out the precise direction.
- **Saved bearings:** a list of every bearing taken, with photo, direction, position and time.
- **Sensor check:** a live readout of GPS accuracy, compass heading, tilt and compass calibration.

Next comes the map, which draws each sightline and shows where they cross. See `PLAN.md`.

## Privacy

- The app has **no internet permission**. It cannot send anything anywhere.
- Photos and positions stay on your phone, inside the app. They don't appear in your Gallery
  and aren't backed up to the cloud. Uninstalling the app deletes them.
- No ads, no tracking, no accounts. Everything used to build it is free.

## Guides

- [Installing the app](docs/INSTALL.md): building it on a computer and putting it on a phone
- [Using the app](docs/USAGE.md): what each part of the screen means
