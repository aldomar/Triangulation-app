# Using Triangulation Locator

## Phase 1: Sensor check screen

This screen lets you check that your phone's GPS and compass are working well **before**
you rely on them. Go outside, away from cars, metal fences and power lines.

### First launch

The app asks for **location permission**. Choose **"While using the app"** and keep
**"Precise"** turned on. Your position is used only on the phone.

If you chose "Approximate" by mistake, the app shows an **Open settings** button. Tap it,
then **Permissions → Location**, and turn on **Use precise location**.

### GPS position

| Item | Meaning |
| --- | --- |
| Accuracy | How close the position probably is. **Green** = within 10 m (great), **yellow** = within 25 m, **orange** = within 50 m, **red** = worse. |
| Latitude / Longitude | Your position on the globe. |
| Altitude | Height above sea level, in metres. |
| Last fix | When the position was last updated. If it says the position is old, move to open sky. |
| Satellites used / seen | More is better. Fewer than 4 used means no reliable position yet. |

The first position can take up to a minute, and longer indoors. GPS doesn't need mobile data or Wi-Fi.

### Heading

The big number is the direction the **back camera** points, in degrees:
0° = north, 90° = east, 180° = south, 270° = west. Hold the phone **upright**, as if taking a photo.

- **True heading** is measured from true (geographic) north. This is what maps use.
- **Magnetic heading** is what a normal compass needle shows.
- **Declination** is the difference between the two at your location. The app works it out
  from your GPS position, so true heading appears once GPS has a position.

### Compass calibration

| Status | Meaning |
| --- | --- |
| High | Ready to use. |
| Medium | Usable, but calibrating will help. |
| Low / Unreliable | Calibrate before taking bearings. |

**To calibrate:** step away from metal, then slowly wave the phone in a figure-8 shape
3–4 times, turning it as you go.

Phone compasses are often off by 5–15°. At 10 km, being 5° off moves the line by almost 900 m,
so calibration really matters.

### Tilt

- **Pitch**: how far the camera points above (+) or below (−) the horizon.
- **Roll**: how far the phone leans sideways.
- The circle is a level. Turn the phone until the line is green and flat.

### Battery

GPS and the sensors run only while the app is on screen. They stop as soon as you switch away.
