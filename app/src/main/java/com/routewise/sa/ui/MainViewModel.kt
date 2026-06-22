package com.routewise.sa.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.routewise.sa.utils.RouteUtils

enum class TransportType {
    CAR, BIKE, WALKING, TRUCK, TRANSIT
}

data class ActiveRoute(
    val destination: LatLng,
    val destinationName: String,
    val path: List<LatLng> = emptyList(),
    val steps: List<RouteUtils.StepData> = emptyList(),
    val transportType: TransportType = TransportType.CAR,
    val waypoints: List<LatLng> = emptyList(),
    val durationSec: Int = 0,
    val distanceMeters: Int = 0,
)

class MainViewModel : ViewModel() {
    private val _activeRoute = MutableLiveData<ActiveRoute?>(null)
    val activeRoute: LiveData<ActiveRoute?> = _activeRoute

    fun setActiveRoute(
        lat: Double, 
        lng: Double, 
        name: String, 
        path: List<LatLng> = emptyList(), 
        steps: List<RouteUtils.StepData> = emptyList(),
        transportType: TransportType = TransportType.CAR,
        waypoints: List<LatLng> = emptyList(),
        durationSec: Int = 0,
        distanceMeters: Int = 0
    ) {
        _activeRoute.value = ActiveRoute(LatLng(lat, lng), name, path, steps, transportType, waypoints, durationSec, distanceMeters)
    }

    fun addWaypoint(waypoint: LatLng, newPath: List<LatLng>, newSteps: List<RouteUtils.StepData>, newDuration: Int, newDistance: Int) {
        val current = _activeRoute.value ?: return
        _activeRoute.value = current.copy(
            waypoints = current.waypoints + waypoint,
            path = newPath,
            steps = newSteps,
            durationSec = newDuration,
            distanceMeters = newDistance
        )
    }

    fun clearActiveRoute() {
        _activeRoute.value = null
    }
}
