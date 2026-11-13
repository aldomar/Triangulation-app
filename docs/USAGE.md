# Using Triangulation Locator

## First launch

The app asks for two permissions:

- **Camera:** to photograph the object. Choose **"While using the app"**.
- **Location:** to record where you're standing. Choose **"While using the app"** and keep
  **"Precise"** turned on.

Photos and positions are kept only inside the app on your phone.

If you chose "Approximate" location by mistake, tap **Open settings**, then
**Permissions → Location**, and turn on **Use precise location**.

The first time the app starts, it shows a short **help guide**. You can open it again at any
time with the round **?** button at the top of every screen. It opens at the help for that
screen, and **Contents** lists every topic. The guide is built into the app and works without
signal. This manual has more detail.

## Taking a bearing

The app opens on the **Take a bearing** screen.

1. **Stand somewhere with a clear view** of the object, away from cars and metal.
2. **Wait for the badges to be ready.** At the bottom you'll see:
   - the direction you're facing (for example "247° WSW")
   - **Compass**: should be **High** (green). If not, wave the phone in a figure-8 a few times.
   - **GPS**: how accurate your position is. Green or yellow is good.
   - If an orange **Magnetic interference** warning appears, something nearby (a car, metal
     fence, railing, power line, or even a phone case magnet) is bending the compass. Walk
     away from it until the warning goes.
3. **Hold the phone upright** like you're taking a normal photo, and keep it level side to side.
4. **Put the crosshair on the object** and press **Take bearing**.
5. **Hold still** while it says "Hold still…" (about one second).
6. **Tap exactly on the object** in the photo. For smoke, tap the **base** of the column,
   where it meets the ground or the treeline, not the drifting top. That spot also lets the
   app estimate the distance from this one photo (see **Terrain estimate** below). The
   **Line of sight** row shows how far up or down you were looking. The orange circle shows where you tapped.
7. Check the **bearing** shown (the exact direction to the object from where you stand).
8. Check the **error band** (±°). This is how far off the bearing might be. It starts from how
   well calibrated the compass was. If there was magnetic interference it starts at ±15°. Drag the slider wider if you weren't sure.
9. Read any orange warnings. If one says the phone moved or the compass was poor, consider
   tapping **Retake**. Otherwise tap **Save**.

**Then move.** To locate the object you need **at least two bearings from different places**.
The further apart the places, the better. Ideally the two sightlines should cross at a wide
angle (more than 30°). If two people take bearings at the same time from different places,
moving smoke won't spoil the result.

If you can only take one bearing, the map's **Terrain estimate** can still give a rough
location, especially from high ground.

### Zoom

Below the camera view are zoom buttons: **1×**, **3×**, **5×** and **10×**. Only the steps
your phone can do are shown. Zoom starts at 1× each time you open the screen.

- Zoom helps you **see** a small or faint object and **tap it precisely** on the photo. The
  app knows the zoom and allows for it, so the bearing stays correct.
- Zoom does **not** make the compass more accurate. The compass is still the biggest source
  of error, so the error band doesn't shrink.
- At 3× and above, many phones switch to a separate telephoto camera. It can point a tiny
  bit differently from the main camera, usually well under a degree. That's much less than
  the compass error, but if you want to know, use **Check against a landmark** at 1× and
  then at 5×.
- At 10× the view is very narrow and every wobble shows. Brace the phone (against a post or
  a car roof), put the object near the centre, and hold very still.
- Saved bearings taken with zoom show it in the session list (for example "zoom 5×").

## Car mode (from a moving car, passengers only)

> **Never use the app while driving.** Car mode is for a passenger.

Inside a car the compass is thrown off by the metal and wiring around it, often by 10–30°.
Car mode doesn't use the compass. Instead it uses the phone's **gyroscope**, which measures
how far the phone turns and isn't affected by metal. It is "lined up" with the direction the car
is travelling, which GPS measures accurately once the car is moving.

1. Turn on the **Car mode** switch on the main screen. The first time, the app shows a short
   safety notice. Car mode is always off when the app starts. (Phones without a gyroscope can't
   use it; the switch is greyed out.)
2. **Line up.** On a **straight** stretch of road, above 15 km/h, put the crosshair on a distant
   point **straight ahead down the road** and tap **Line up**. Hold still for a second. If the
   car is turning, too slow, or GPS isn't ready, the app tells you what to wait for.
3. **Take bearings** as usual: crosshair on the object, **Take bearing**, keep it on the object
   for a second, tap the object in the photo, **Save**. You can turn the phone towards the object
   freely after lining up, and the car can turn too.
4. **Line up again every few minutes.** The gyroscope slowly drifts. The text next to the switch
   shows how long ago you lined up and turns orange after 3 minutes. The error band starts at
   about ±2° and grows by about 1° per minute since lining up.

Good to know:

- **Choose the line-up point carefully.** It is the main source of error. Pick something far
  away where the road runs dead straight. Don't line up during a lane change or on a bend.
- If you leave the main screen, or the phone sleeps, you'll need to line up again.
- The position is the car's position at the moment of the photo (at 100 km/h the car moves
  28 m every second, and the app allows for this).
- Bearings taken along one straight road all look at the object from a similar direction, so
  their lines cross at a shallow angle. Take more after the road bends or from a crossing road.
- Car-mode bearings are labelled "Car mode" in the session list and in exports.
- An open side window gives the clearest view. Glass doesn't noticeably bend the sightline.

## Sessions

A **session** holds all the bearings of **one object**, for example one smoke column. The
line under the title on the main screen shows which session new bearings go into (the
**active** session).

- **Start a new session** for each new object: tap **New session**, type a name you'll
  recognise (such as "Smoke north of town"), and tap **Start**. If you just take a bearing
  without one, the app starts a session for you, named after the date and time.
- **Tap the session's name** on the main screen to open it.
- When you open the app again, it carries on with the session you used last.

### History

Tap **History** at the top of the main screen to see every session, newest first, with its
date, how many bearings it has, and its estimated location (once it has two ticked bearings).
The **Active** label marks the session new bearings go into. Tap a session to open it.

Bearings saved with an older version of the app are in a session called **Earlier bearings**.

### Inside a session

You'll see each bearing: its photo, direction, error band, time, position, how far up or down
you were looking, and compass quality.

- **Tick** the bearings to use. Ticked bearings are used on the map and in exports. New
  bearings are ticked automatically. Ticks are remembered, even if you close the app.
- **Show … on map** opens the map. Two or more ticked bearings give a crossing point; one
  ticked bearing can be placed with the **Terrain estimate**.
- **Move** puts a bearing into another session, for example if you forgot to start a new one.
- **Delete** (on a bearing) removes that bearing and its photo.
- At the top: **Rename** the session, **Delete** it with all its bearings and photos,
  **Make active** to send new bearings to it, and **Export…** (see below).

Deleted bearings and sessions can't be recovered.

### Adding a bearing by hand

Use this for a bearing someone else measured: a fire lookout on the radio, a hand compass, or
another person's phone. Open the session and tap **Add a bearing by hand** (below the list).

1. **Where it was taken from.** Type the position, for example `49.2827, -123.1207`, or
   `49°16.9' N 123°07.2' W`. Or tap **Use my GPS position** if you took it yourself where you
   stand, or **Pick on map** to tap the spot on a map.
2. **Bearing** in degrees (0–360). Choose **True north** or **Magnetic north**. Fire lookouts
   and maps usually give true bearings. A hand compass gives magnetic, unless it has been set
   for declination. For magnetic, the app adds the declination at that place for you.
3. **Error band:** how far off it might be. A lookout's firefinder or a good hand compass is
   about ±2–5°. Use ±10° or more for a rough guess.
4. **Taken how many minutes ago** (0 = now), and an optional **note**, such as who reported it.
5. Tap **Save bearing**.

It appears in the list marked **Typed in**, with no photo, and is used on the map and in exports
like any other bearing. It has no camera tilt, so the **Terrain estimate** can't place it.

## Exporting a session

**Export…** is at the top of a session and next to the result below the map. It saves the
ticked bearings, their sightlines, the estimated location and its uncertainty area as a file
that other map apps can open. When **Terrain estimate** is on, export from the map to include
the terrain estimates too. Photos aren't included.

1. Tap **Export…** and choose a file type:
   - **GeoJSON**: for QGIS, geojson.io, Google Earth and most mapping tools.
   - **GPX**: for hiking and GPS apps such as OsmAnd, Gaia GPS or caltopo.
2. Android's **Save** screen opens. Pick a folder (for example **Downloads**) or a cloud
   drive app, change the file name if you like, and tap **Save**.

The app never sends the file anywhere itself. It only goes to a cloud service if you pick one
on the Save screen.

**What's in the file:** a point for each place you stood (A, B, C…) with its bearing, error
band, time and GPS accuracy, a line for each sightline, a point for the estimated location,
and its uncertainty area. GeoJSON stores the area as a shape. GPX can't store shapes, so the
area is drawn as a closed track around it. Terrain estimates (if included) add a point and an
area for each bearing, labelled "A terrain estimate" and so on.

## The map

### Map background

The first time you open the map, the app asks whether to show a map background.

- **Show map** downloads map images from OpenStreetMap. It needs mobile data or Wi-Fi.
  OpenStreetMap sees which area you're viewing, but none of your bearings, photos or position.
  Areas you've already viewed are kept on the phone (up to about 50 MB), so they load again
  without data.
- **No thanks** shows a plain grid with latitude/longitude lines instead. Everything else works
  the same, and the app uses no internet at all.

Change your mind any time with the **Map background** switch at the top of the map.

### Reading the map

- **Coloured wedges** are the sightlines, one per bearing, labelled **A**, **B**, **C**… at the
  spot where you stood. The solid line is the bearing. The shaded wedge is its error band:
  the object should be somewhere inside it.
- The **orange dot** marked **Target** is the estimated location of the object.
- The **orange shaded area** around it is how far off the estimate might be. Smaller is better.
- Pinch to zoom, and drag to move around.

### The results panel (below the map)

| Item | Meaning |
| --- | --- |
| Estimated location | Latitude and longitude of the object. Tap **Copy** to copy just the numbers, **Share** to send a short message (location, how sure it is, time and an OpenStreetMap link) with any app you choose, **Open in map app** to show it in another map app, or **Export…** to save a file. Shared text never includes where you stood. |
| Uncertainty | The object is probably within this distance of the estimated location. |
| Best crossing angle | The widest angle between any two sightlines. Above 30° is good, and close to 90° is best. |
| Lines miss the point by | With 3 or more bearings: how closely they agree. Small numbers mean they agree well. |
| A, B, C… | Each bearing, its error band, and how far you were from the object. |

### Warnings, and what to do

| Warning | What to do |
| --- | --- |
| Shallow angle | The lines are close to parallel, so the point could be anywhere along them. Take another bearing from a place well off to the side. |
| Error bands too wide | Calibrate the compass (figure-8), retake, or add a bearing from another direction. |
| Bearings disagree | One bearing may be wrong, or the smoke moved. Untick bearings one at a time in the session to find the odd one out. |
| Points away / no location | A bearing points away from where the others cross. Check it, or untick it. |
| Over 50 km away | The estimate is less accurate this far out. |

**More bearings help.** With three or more from different directions, the app finds the
best-fit point and shows how well they agree.

The result is an estimate to guide where to look, not a location to dispatch to.
**For a real fire, call 911 or your local fire agency.**

## Terrain estimate (single-photo mode)

Each photo records not just the direction but also **how far down you were looking**. If you
tapped where the smoke meets the ground, the app can follow that line of sight from your eye
until it reaches the land, using free elevation data. That gives a distance, and so a
location, from a **single** bearing.

**Turning it on:** below the map, switch on **Terrain estimate**. The first time, the app asks
whether it may download elevation data (see Privacy in the README). Areas already downloaded
are kept on the phone, so they work later without signal. You can change this with the
**Download elevation data** switch.

**What you'll see:**

- With **one** bearing: an orange dot marked **Target (terrain)** and a dashed orange area
  along the sightline. The results panel shows the coordinates (with **Copy** and **Share**), the
  distance, and the likely range (for example "2.1 km to 5.5 km").
- With **several** bearings: a dashed area and a dot ("A ground", "B ground"…) on each
  sightline, plus a line under each bearing in the list. If these land near the crossing
  point, the bearings and the terrain agree. If not, be careful.

**Camera tilt error (±0.5°, ±1°, ±2°):** how far off the phone's tilt reading might be. It sets
the near and far ends of the dashed area. ±1° is a sensible start. Pick ±2° if the phone was
moving or you weren't sure where the smoke met the ground.

**When to trust it:**

- **Good:** looking down from a hill, lookout or ridge at smoke in a valley or on lower ground.
- **Rough:** from flat ground looking at something on flat ground far away. The line of sight
  meets the land at a very shallow angle, so a tiny tilt error moves the point kilometres.
  The app warns you when this happens, and the far end may say "beyond 40 km".
- It uses the bare shape of the land. **Trees and buildings** aren't in the data, so if the
  smoke rises from behind a forest, the real spot may be nearer than shown.
- The data is about 30 m detailed in most places, and it goes out to 40 km.
- Your eye height is taken as the ground height where you stood plus 1.5 m. If you were
  up a tower or on a roof, the estimate will be too close.

| Message | Meaning |
| --- | --- |
| Doesn't meet the ground within 40 km | The line of sight points level or upward. Maybe the tap was above the horizon, or the object is very far away. |
| No elevation data saved for this area | Turn on **Download elevation data** (needs signal once). |
| Couldn't get elevation data | No connection, or the download failed. Tap **Try the download again**. |

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

**Magnetic field.** The card also compares the magnetic field the phone measures with the
strength Earth's field should have where you are (worked out on the phone, no internet). If
they differ by more than 15%, something nearby is disturbing the compass, and the app shows
**Magnetic interference**. Calibrating doesn't fix this: move away from the metal instead.
Bearings taken with interference are marked in the session list and in exports.

### Check against a landmark

This shows how far off your phone's compass really is. You need a landmark whose exact
position you know: a radio mast, a summit, a lookout tower. Ideally it's more than 1 km away.

1. On the Sensors screen, tap **Check the compass**.
2. Give the landmark a name if you like. Then type its position, or tap **Pick on map** and tap
   it (zoom right in). The position must be accurate: 50 m off at 2 km moves the answer by
   about 1.4°.
3. Tap **Aim at it**. Take a bearing exactly as usual, and tap the landmark on the photo.
4. Tap **Compare**. The app shows, for example, "Your compass reads 3.2° clockwise of true",
   with the true bearing, the distance, and how much GPS error alone could explain.

Each check is kept in a list on the Sensors screen, with the average over all checks.
Checks are only recorded: they never change your bearings, and the photo isn't kept.
Delete a check if you know it was wrong (for example, the wrong landmark).

**Tip:** tap the landmark away from the centre of the photo now and then. That also checks
the app's correction for where you tap.

### Tilt

- **Pitch**: how far the camera points above (+) or below (−) the horizon.
- **Roll**: how far the phone leans sideways.
- The circle is a level. Turn the phone until the line is green and flat.

### Driving test (passengers only)

This checks how well your phone can take bearings in your car. It compares the compass and the
lined-up gyroscope with the direction the car is travelling.

1. A passenger opens **Sensors** and taps **Start driving test** at the bottom.
2. Hold the phone upright and steady, **facing straight ahead along the car** for the whole
   test. A dashboard mount is best.
3. On a straight road above 20 km/h, tap **Line up**.
4. Drive normally for 10–15 minutes, with some turns. Only straight stretches above 20 km/h
   are counted, because GPS direction lags in turns.
5. Note the **gyroscope error** after about 2, 5 and 10 minutes, and the **compass error**
   (average, spread and worst).

What the numbers mean:

- **Average**: a steady offset, for example if the phone isn't quite facing forward.
- **Spread**: how much the error wanders. This is what matters most.
- **Worst**: the largest error seen.

If the gyroscope error stays within about 2° for 3 minutes or more, car mode will work well on
this phone. Tap **Reset** to clear the numbers and **Stop** to turn the gyroscope off.
Leaving the Sensors screen stops the test.

## Battery and storage

The camera, GPS, sensors and map run only while the app is on screen. They stop as soon as you
switch away. In car mode the gyroscope runs instead of the compass, not alongside it. Each saved photo is small (a few hundred KB). Deleting a session or bearing
frees its photo's space straight away. Map images are kept to about 50 MB and
elevation data to about 100 MB; Android can clear either if the phone runs low on space.
