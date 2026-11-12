package com.triangulation.locator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One compass check: a bearing taken of a landmark whose position is known, compared with the
 * true bearing to it. Stored in the `compass_checks` table. Measured and recorded only; it never
 * changes any bearing. The photo isn't kept.
 */
@Entity(tableName = "compass_checks")
data class CompassCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Wall-clock time of the photo, milliseconds since 1970 (UTC). */
    val timeMillis: Long,
    /** Where the phone was. */
    val latitude: Double,
    val longitude: Double,
    val gpsAccuracyM: Double?,
    /** The landmark, as named and placed by the user. */
    val landmarkName: String?,
    val landmarkLatitude: Double,
    val landmarkLongitude: Double,
    /** True bearing the phone measured (compass + declination + tap correction). */
    val measuredBearingDeg: Double,
    /** True bearing from the phone's position to the landmark. */
    val expectedBearingDeg: Double,
    /** Measured − expected, -180..180. Positive = compass reads clockwise of true. */
    val differenceDeg: Double,
    val distanceM: Double,
    /** SensorManager.SENSOR_STATUS_* at the time. */
    val compassAccuracy: Int,
    val magneticFieldRatio: Double?,
    /** How much the heading wandered during the 1-second measurement. */
    val headingSpreadDeg: Double,
    /** Where the landmark was tapped on the photo (0.5, 0.5 = centre). Shows whether the tap correction is tested. */
    val tapX: Double,
    val tapY: Double,
)
