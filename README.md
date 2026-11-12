# Triangulation Locator

An Android app that helps you work out **where a distant object is**, such as a column of smoke,
by pointing your phone at it from two or more different places.

At each place the app records where you are (GPS) and which direction you are facing (compass).
Where those lines of sight cross is roughly where the object is.

> This app gives an estimate to guide where to look. It is not a location to dispatch
> emergency services to. **For a real fire, call 911 or your local fire agency.**

## What works now (Phase 1)

The app currently has one screen, a **sensor check**, that shows live:

- your GPS position and how accurate it is
- the direction the back camera points (true north and magnetic north)
- how far the phone is tilted, with a small level
- whether the compass is calibrated, with instructions to fix it

Later phases add the camera, the map and the triangulation itself. See `PLAN.md`.

## Privacy

- The app has **no internet permission**. It cannot send anything anywhere.
- Your location stays on your phone. Nothing is backed up to the cloud.
- No ads, no tracking, no accounts. Everything used to build it is free.

## Guides

- [Installing the app](docs/INSTALL.md): building it on a computer and putting it on a phone
- [Using the app](docs/USAGE.md): what each part of the screen means
