package com.triangulation.locator.sensors

import android.Manifest
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Everything the sensor screen shows about GPS. */
data class GpsState(
    val providerEnabled: Boolean = true,
    val location: Location? = null,
    val satellitesInView: Int = 0,
    val satellitesUsed: Int = 0,
)

/**
 * Live GPS readings from the phone's own GPS chip, via the built-in [LocationManager].
 *
 * Only GPS_PROVIDER is used. The "network" provider is avoided on purpose: on many phones
 * it sends nearby Wi-Fi and cell data to an outside location service.
 *
 * Updates run only while the flow is being collected. The UI collects it with
 * collectAsStateWithLifecycle, so GPS switches off as soon as the app leaves the screen.
 */
@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
fun LocationManager.gpsUpdates(): Flow<GpsState> = callbackFlow {
    var state = GpsState(
        providerEnabled = isProviderEnabled(LocationManager.GPS_PROVIDER),
        location = getLastKnownLocation(LocationManager.GPS_PROVIDER),
    )
    fun publish(newState: GpsState) {
        state = newState
        trySend(newState)
    }
    publish(state)

    // All four methods are overridden: on Android 8-9 the interface has no default methods.
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = publish(state.copy(location = location))
        override fun onProviderEnabled(provider: String) = publish(state.copy(providerEnabled = true))
        override fun onProviderDisabled(provider: String) =
            publish(state.copy(providerEnabled = false, satellitesInView = 0, satellitesUsed = 0))

        @Deprecated("Required on older Android versions")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) if (status.usedInFix(i)) used++
            publish(state.copy(satellitesInView = status.satelliteCount, satellitesUsed = used))
        }
    }

    val looper = Looper.getMainLooper()
    requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener, looper)
    registerGnssStatusCallback(gnssCallback, Handler(looper))

    awaitClose {
        removeUpdates(listener)
        unregisterGnssStatusCallback(gnssCallback)
    }
}.conflate()

private const val UPDATE_INTERVAL_MS = 1_000L
