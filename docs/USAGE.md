# Using Triangulation Locator

## First launch

The app asks for two permissions:

- **Camera:** to photograph the object. Choose **"While using the app"**.
- **Location:** to record where you're standing. Choose **"While using the app"** and keep
  **"Precise"** turned on.

Photos and positions are kept only inside the app on your phone.

If you chose "Approximate" location by mistake, tap **Open settings**, then
**Permissions → Location**, and turn on **Use precise location**.

## Taking a bearing

The app opens on the **Take a bearing** screen.

1. **Stand somewhere with a clear view** of the object, away from cars and metal.
2. **Wait for the badges to be ready.** At the bottom you'll see:
   - the direction you're facing (for example "247° WSW")
   - **Compass**: should be **High** (green). If not, wave the phone in a figure-8 a few times.
   - **GPS**: how accurate your position is. Green or yellow is good.
3. **Hold the phone upright** like you're taking a normal photo, and keep it level side to side.
4. **Put the crosshair on the object** and press **Take bearing**.
5. **Hold still** while it says "Hold still…" (about one second).
6. **Tap exactly on the object** in the photo. For smoke, tap the **base** of the column,
   where it leaves the ground, not the drifting top. The orange circle shows where you tapped.
7. Check the **bearing** shown (the exact direction to the object from where you stand).
8. Check the **error band** (±°). This is how far off the bearing might be. It starts from how
   well calibrated the compass was. Drag the slider wider if you weren't sure.
9. Read any orange warnings. If one says the phone moved or the compass was poor, consider
   tapping **Retake**. Otherwise tap **Save**.

**Then move.** To locate the object you need **at least two bearings from different places**.
The further apart the places, the better. Ideally the two sightlines should cross at a wide
angle (more than 30°). If two people take bearings at the same time from different places,
moving smoke won't spoil the result.

## Saved bearings

Tap **Saved** at the top of the main screen to see every bearing: its photo, direction,
error band, time, position and compass quality. Tap **Delete** to remove one, or
**Delete all** to start fresh. Deleted bearings can't be recovered.

The map that shows where the sightlines cross arrives in the next version.

## Sensor check screen

Tap **Sensors** at the top of the main screen. This screen lets you check that your phone's
GPS and compass are working well **before** you rely on them.

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

## Battery and storage

The camera, GPS and sensors run only while the app is on screen. They stop as soon as you
switch away. Each saved photo is small (a few hundred KB).
