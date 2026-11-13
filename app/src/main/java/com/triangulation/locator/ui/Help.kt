package com.triangulation.locator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * The in-app help guide: short, phone-sized help for each screen. Works offline.
 * `docs/USAGE.md` stays the full manual; keep the two in step when features change.
 */
enum class HelpTopic(val title: String) {
    GettingStarted("Getting started"),
    TakingBearing("Taking a bearing"),
    Review("Tapping the object on the photo"),
    CarMode("Car mode (passengers only)"),
    History("History and sessions"),
    Session("Inside a session"),
    Map("The map and the result"),
    Terrain("Terrain estimate (one bearing)"),
    ShareExport("Copy, share and export"),
    TypedBearing("Adding a bearing by hand"),
    PointPicker("Picking a point on the map"),
    CompassCheck("Checking the compass against a landmark"),
    Sensors("Sensor check screen"),
    Privacy("Privacy and battery"),
}

private sealed interface Block
private class Para(val text: String) : Block
private class Steps(vararg val items: String) : Block
private class Bullets(vararg val items: String) : Block
private class Tip(val text: String) : Block

private val HELP: Map<HelpTopic, List<Block>> = mapOf(
    HelpTopic.GettingStarted to listOf(
        Para(
            "This app finds a distant object, such as a smoke column, from compass bearings taken at two or " +
                "more places. Where the sightlines cross is the estimate."
        ),
        Steps(
            "Check the sensors: tap Sensors and make sure GPS and the compass are ready.",
            "Take a bearing: aim the crosshair at the object, press Take bearing, then tap the object on the photo and Save.",
            "Move somewhere else, ideally well off to the side, and take another bearing of the same object.",
            "Tap History, open the session, and tap Show … on map to see where the sightlines cross.",
        ),
        Para("With only one bearing, the Terrain estimate on the map can still guess the distance from the shape of the land."),
        Tip("Tap the ? button on any screen for help with that screen. All topics are listed under Contents."),
        Tip("The result is an estimate to guide where to look, not a location to dispatch to. For a real fire, call 911 or your local fire agency."),
    ),
    HelpTopic.TakingBearing to listOf(
        Steps(
            "Stand with a clear view of the object, away from cars, metal fences and power lines.",
            "Wait for the badges: Compass should be High (green), GPS green or yellow.",
            "Hold the phone upright like for a normal photo, level side to side.",
            "Put the crosshair on the object, press Take bearing and hold still for about a second.",
        ),
        Bullets(
            "\"Compass needs calibrating\": wave the phone in a figure-8 a few times.",
            "\"Magnetic interference\": something metal nearby is bending the compass. Walk away from it until the warning goes.",
            "\"Tilted sideways\": hold the phone level.",
            "New bearings go into the session shown at the top. Tap New session when you start on a different object.",
        ),
        Para(
            "Zoom (1× 3× 5× 10×, below the camera view) helps you see and tap a small or faint object. The app " +
                "allows for the zoom, so the bearing stays correct. It doesn't make the compass more accurate."
        ),
        Tip("At 10× every wobble shows. Brace the phone against something and put the object near the centre."),
        Tip("Two people taking bearings of the same smoke at the same time, from different places, give the best result."),
    ),
    HelpTopic.Review to listOf(
        Steps(
            "Tap exactly on the object in the photo. For smoke, tap the base of the column where it meets the ground or treeline, not the drifting top.",
            "Check the bearing shown: the exact direction from where you stand.",
            "Check the error band (±°): how far off the bearing might be. It starts from the compass calibration, or ±15° if there was magnetic interference. Drag it wider if you weren't sure.",
            "Read any orange warnings, then tap Save, or Retake if the phone moved or the compass was poor.",
        ),
        Para("Line of sight shows how far up or down you were looking. The terrain estimate uses it."),
    ),
    HelpTopic.CarMode to listOf(
        Tip("Passengers only. Never use the app while driving."),
        Para(
            "Inside a car the compass is useless: the metal bends it. Car mode uses the gyroscope instead, lined up " +
                "with the direction the car is travelling (from GPS)."
        ),
        Steps(
            "Turn on Car mode on the main screen.",
            "On a straight road above 15 km/h, put the crosshair on a distant point straight ahead and tap Line up.",
            "Take bearings as usual. The error band grows with time since lining up, so line up again every few minutes.",
        ),
        Para("Leaving the screen or the phone sleeping means lining up again."),
    ),
    HelpTopic.History to listOf(
        Para(
            "A session holds all the bearings of one object, such as one smoke column. History lists every session, " +
                "newest first. Tap one to open it."
        ),
        Bullets(
            "The active session is where new bearings go. It's shown at the top of the main screen.",
            "New session starts a fresh one and makes it active.",
        ),
    ),
    HelpTopic.Session to listOf(
        Bullets(
            "Tick the bearings to use. Ticked bearings are used on the map and in exports.",
            "Show … on map opens the map. Two or more ticked bearings give a crossing point.",
            "Move puts a bearing into another session. Delete removes it and its photo.",
            "At the top: Rename, Delete the whole session, Make active, and Export….",
            "Add a bearing by hand (below the list) adds one someone else measured, such as a lookout on the radio.",
        ),
        Para("Bearings are marked when they were taken in car mode, typed in by hand, or had magnetic interference."),
        Tip("Deleted bearings and sessions can't be recovered."),
    ),
    HelpTopic.Map to listOf(
        Bullets(
            "Coloured dots A, B, C… are where each bearing was taken. The coloured wedge from each is its sightline with its error band.",
            "The orange dot is the estimated location. The orange area is where the object probably is.",
            "Pinch to zoom, drag to move.",
        ),
        Para("Below the map:"),
        Bullets(
            "Uncertainty: the object is probably within this distance of the estimate.",
            "Best crossing angle: above 30° is good, near 90° is best.",
            "Lines miss the point by (3 or more bearings): small numbers mean they agree well.",
        ),
        Para(
            "Warnings explain what to do: take a bearing from further to the side, calibrate the compass, or untick a " +
                "bearing that disagrees."
        ),
        Tip(
            "Map background downloads map images from OpenStreetMap, only if you turn it on. When off, a plain grid is " +
                "shown and nothing is downloaded."
        ),
    ),
    HelpTopic.Terrain to listOf(
        Para(
            "With one bearing, the app follows your line of sight down until it meets the ground, using the photo's " +
                "tilt and free elevation data. Turn on Terrain estimate below the map."
        ),
        Bullets(
            "Works best from high ground looking down. From flat ground it's very rough.",
            "The dashed area is the likely range. \"How far off the camera tilt might be\" widens or narrows it.",
            "Elevation data is downloaded only if you allow it, and is kept for use without signal.",
            "Trees and buildings can make the real spot nearer. Bearings typed in by hand have no tilt, so they can't be used.",
        ),
    ),
    HelpTopic.ShareExport to listOf(
        Bullets(
            "Copy: copies the coordinates of the estimate.",
            "Share: sends a short message (location, how sure it is, time and an OpenStreetMap link) with the app you choose. It never includes where you stood.",
            "Open in map app: shows the point in another map app on the phone.",
            "Export…: saves a GeoJSON or GPX file where you choose, for GIS, hiking or GPS apps.",
        ),
        Para("The app never sends anything by itself. It only goes where you send it."),
    ),
    HelpTopic.TypedBearing to listOf(
        Steps(
            "Where it was taken from: type the position (for example 49.2827, -123.1207 or 49°16.9' N 123°07.2' W), or use Use my GPS position or Pick on map.",
            "Bearing in degrees, and whether it's from True north or Magnetic north. Lookouts and maps usually give true; a hand compass gives magnetic.",
            "Error band: about ±2–5° for a lookout's firefinder or a good hand compass, ±10° or more for a rough guess.",
            "How many minutes ago it was taken, an optional note, then Save bearing.",
        ),
        Para("It's used on the map like your own bearings, but has no photo, so the terrain estimate can't place it."),
    ),
    HelpTopic.PointPicker to listOf(
        Bullets(
            "Tap the map to place the point. Tap elsewhere to move it.",
            "Pinch to zoom in close for accuracy, then tap Use this point.",
            "If the map background is off, only a grid shows. Type the coordinates instead, or turn the background on from a session's map.",
        ),
    ),
    HelpTopic.CompassCheck to listOf(
        Para("Shows how far off this phone's compass really is, using a landmark whose exact position you know."),
        Steps(
            "Pick a landmark you can see, ideally more than 1 km away: a radio mast, a summit, a lookout tower.",
            "Type its position or pick it on the map (zoom right in), then tap Aim at it.",
            "Take the bearing as usual, tap the landmark on the photo, then tap Compare.",
        ),
        Para(
            "The result says, for example, \"reads 3.2° clockwise of true\". Each check is listed on the Sensors " +
                "screen with the average. Checks are only recorded: your bearings are never changed."
        ),
        Tip("The landmark's position must be accurate: 50 m off at 2 km changes the answer by about 1.4°."),
    ),
    HelpTopic.Sensors to listOf(
        Bullets(
            "GPS: position and how accurate it is. Go outside with a clear view of the sky.",
            "Heading: the direction the back camera points, true and magnetic, and the declination (the difference) here.",
            "Compass calibration: should be High. To calibrate, step away from metal and wave the phone in a figure-8 3–4 times.",
            "Magnetic field: measured against what Earth's field should be here. More than 15% off means interference.",
            "Check the compass: compare against a landmark (see that topic).",
            "Tilt, and the driving test for car mode (passengers only).",
        ),
    ),
    HelpTopic.Privacy to listOf(
        Bullets(
            "Photos, bearings and positions stay on this phone, inside the app. They're not in your Gallery.",
            "The internet is used only for map images and elevation data, each only if you turn it on.",
            "No ads, no tracking, no accounts.",
            "Sensors and GPS run only while a screen that needs them is open.",
        ),
    ),
)

/** Small round "?" button that opens the help guide at [topic], over the current screen. */
@Composable
fun HelpButton(topic: HelpTopic, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(36.dp).semantics { contentDescription = "Help" },
    ) {
        Text("?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    if (open) HelpDialog(topic, onClose = { open = false })
}

/**
 * The full guide in a full-screen dialog, scrolled to [topic]. A dialog (not a separate screen)
 * so whatever is underneath, such as a photo being reviewed, is kept.
 */
@Composable
fun HelpDialog(topic: HelpTopic, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val topics = HelpTopic.entries
            // Item 0 is the contents list; topic i is item i + 1.
            LaunchedEffect(Unit) {
                if (topic != HelpTopic.GettingStarted) listState.scrollToItem(topic.ordinal + 1)
            }
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "Help",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose) { Text("Close") }
                }
                HorizontalDivider()
                LazyColumn(state = listState, contentPadding = PaddingValues(16.dp)) {
                    item {
                        Text("Contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        topics.forEach { t ->
                            TextButton(
                                onClick = { scope.launch { listState.animateScrollToItem(t.ordinal + 1) } },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            ) { Text(t.title) }
                        }
                    }
                    itemsIndexed(topics) { _, t ->
                        HelpSection(t, onContents = { scope.launch { listState.animateScrollToItem(0) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpSection(topic: HelpTopic, onContents: () -> Unit) {
    Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
        Text(topic.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        HELP[topic].orEmpty().forEach { block ->
            when (block) {
                is Para -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                is Steps -> block.items.forEachIndexed { i, s -> ListLine("${i + 1}.", s) }
                is Bullets -> block.items.forEach { ListLine("•", it) }
                is Tip -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        TextButton(onClick = onContents, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("↑ Contents") }
    }
}

@Composable
private fun ListLine(marker: String, text: String) {
    Row {
        Text(marker, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(24.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
