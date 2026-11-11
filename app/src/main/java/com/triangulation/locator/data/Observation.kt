package com.triangulation.locator.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONObject

/**
 * One sighting of the target: where the phone was, which way it pointed, and the photo.
 * The solver needs two or more of these. Stored as one row of the `observations` table;
 * the photo is a separate JPEG file (see [ObservationStore]).
 */
@Entity(
    tableName = "observations",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class Observation(
    @PrimaryKey val id: String,
    /** The session (group of bearings of one object) this belongs to. Set by [ObservationStore.save]. */
    val sessionId: Long = 0,
    /** Ticked = used for the fix on the map and in exports. */
    val included: Boolean = true,
    /** Wall-clock time of the photo, milliseconds since 1970 (UTC). */
    val timeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val gpsAccuracyM: Double?,
    /** Magnetic heading of the camera's centre line, averaged over about 1 second. */
    val magneticHeadingDeg: Double,
    /** How much the heading wandered during that second (circular standard deviation). */
    val headingSpreadDeg: Double,
    val declinationDeg: Double,
    val pitchDeg: Double,
    val rollDeg: Double,
    /** SensorManager.SENSOR_STATUS_* at capture time. */
    val compassAccuracy: Int,
    /** Where the user tapped on the photo, as fractions of width/height (0.5, 0.5 = centre). */
    val tapX: Double,
    val tapY: Double,
    /** Camera focal length divided by photo width. Null if the phone didn't report it. */
    val focalLengthFraction: Double?,
    /** Angle between the photo centre and the tapped point, + = to the right. */
    val tapOffsetDeg: Double,
    /** Final true bearing from the observer to the target, 0..360, clockwise from true north. */
    val bearingDeg: Double,
    /** Uncertainty of the bearing, ± degrees. Shown as the width of the sightline on the map. */
    val errorDeg: Double,
    val photoFileName: String,
    val photoWidth: Int,
    val photoHeight: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("timeMillis", timeMillis)
        put("latitude", latitude)
        put("longitude", longitude)
        putOpt("altitudeM", altitudeM)
        putOpt("gpsAccuracyM", gpsAccuracyM)
        put("magneticHeadingDeg", magneticHeadingDeg)
        put("headingSpreadDeg", headingSpreadDeg)
        put("declinationDeg", declinationDeg)
        put("pitchDeg", pitchDeg)
        put("rollDeg", rollDeg)
        put("compassAccuracy", compassAccuracy)
        put("tapX", tapX)
        put("tapY", tapY)
        putOpt("focalLengthFraction", focalLengthFraction)
        put("tapOffsetDeg", tapOffsetDeg)
        put("bearingDeg", bearingDeg)
        put("errorDeg", errorDeg)
        put("photoFileName", photoFileName)
        put("photoWidth", photoWidth)
        put("photoHeight", photoHeight)
    }

    companion object {
        fun fromJson(o: JSONObject) = Observation(
            id = o.getString("id"),
            timeMillis = o.getLong("timeMillis"),
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            altitudeM = o.optDoubleOrNull("altitudeM"),
            gpsAccuracyM = o.optDoubleOrNull("gpsAccuracyM"),
            magneticHeadingDeg = o.getDouble("magneticHeadingDeg"),
            headingSpreadDeg = o.getDouble("headingSpreadDeg"),
            declinationDeg = o.getDouble("declinationDeg"),
            pitchDeg = o.getDouble("pitchDeg"),
            rollDeg = o.getDouble("rollDeg"),
            compassAccuracy = o.getInt("compassAccuracy"),
            tapX = o.getDouble("tapX"),
            tapY = o.getDouble("tapY"),
            focalLengthFraction = o.optDoubleOrNull("focalLengthFraction"),
            tapOffsetDeg = o.getDouble("tapOffsetDeg"),
            bearingDeg = o.getDouble("bearingDeg"),
            errorDeg = o.getDouble("errorDeg"),
            photoFileName = o.getString("photoFileName"),
            photoWidth = o.getInt("photoWidth"),
            photoHeight = o.getInt("photoHeight"),
        )

        private fun JSONObject.optDoubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) getDouble(key) else null
    }
}
