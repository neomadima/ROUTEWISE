package com.routewise.sa.utils

import com.google.android.gms.maps.model.LatLng
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RouteUtils {
    const val API_KEY = "AIzaSyAXALm6HyCeqSPR2x0CBTT4XtzPyeK32Nk"

    data class StepData(
        val startLocation: LatLng,
        val maneuver: String?,
        val instruction: String?,
    )

    data class RouteDetails(
        val path: List<LatLng>,
        val steps: List<StepData> = emptyList(),
        val durationSec: Int = 0,
        val distanceMeters: Int = 0
    )

    data class RouteOption(
        val summary: String,
        val duration: Int,      // minutes
        val distance: String,
        val polyline: String,
        val path: List<LatLng>,
    )

    suspend fun getRouteDetails(
        origin: LatLng, 
        dest: LatLng,
        mode: String = "driving",
        avoid: String? = null,
        waypoints: List<LatLng>? = null,
        departureTime: Long? = null
    ): RouteDetails? = withContext(Dispatchers.IO) {
        val urlBuilder = StringBuilder("https://maps.googleapis.com/maps/api/directions/json?")
            .append("origin=${origin.latitude},${origin.longitude}")
            .append("&destination=${dest.latitude},${dest.longitude}")
            .append("&mode=$mode")
            .append("&key=$API_KEY")
        
        avoid?.let { urlBuilder.append("&avoid=$it") }
        departureTime?.let { urlBuilder.append("&departure_time=$it") }
        
        if (!waypoints.isNullOrEmpty()) {
            val waypointsString = "optimize:true|" + waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
            urlBuilder.append("&waypoints=$waypointsString")
        }

        try {
            val response = URL(urlBuilder.toString()).readText()
            val jsonResponse = JSONObject(response)
            
            if (jsonResponse.getString("status") == "OK") {
                val routes = jsonResponse.getJSONArray("routes")
                if (routes.length() > 0) {
                    val route = routes.getJSONObject(0)
                    val points = route.getJSONObject("overview_polyline").getString("points")
                    val path = decodePolyline(points)
                    
                    val stepsList = mutableListOf<StepData>()
                    val legs = route.getJSONArray("legs")
                    var totalDuration = 0
                    var totalDistance = 0
                    
                    for (j in 0 until legs.length()) {
                        val leg = legs.getJSONObject(j)
                        totalDuration += leg.getJSONObject("duration").getInt("value")
                        totalDistance += leg.getJSONObject("distance").getInt("value")
                        
                        val steps = leg.getJSONArray("steps")
                        for (i in 0 until steps.length()) {
                            val step = steps.getJSONObject(i)
                            val startLoc = step.getJSONObject("start_location")
                            stepsList.add(StepData(
                                LatLng(startLoc.getDouble("lat"), startLoc.getDouble("lng")),
                                step.optString("maneuver", ""),
                                step.optString("html_instructions", "")
                            ))
                        }
                    }
                    
                    return@withContext RouteDetails(path, stepsList, totalDuration, totalDistance)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteUtils", "Error details: ${e.message}")
        }
        return@withContext null
    }

    suspend fun getRoutePolyline(origin: LatLng, dest: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        getRouteDetails(origin, dest)?.path ?: emptyList()
    }

    suspend fun getSpeedLimit(location: LatLng): Int? = withContext(Dispatchers.IO) {
        val query = """
            [out:json];
            way(around:50, ${location.latitude}, ${location.longitude})[maxspeed];
            out tags;
        """.trimIndent()
        
        val url = "https://overpass-api.de/api/interpreter?data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        
        try {
            val response = URL(url).readText()
            val json = JSONObject(response)
            val elements = json.getJSONArray("elements")
            if (elements.length() > 0) {
                val tags = elements.getJSONObject(0).getJSONObject("tags")
                val maxspeed = tags.optString("maxspeed")
                return@withContext maxspeed.filter { it.isDigit() }.toIntOrNull()
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteUtils", "Error fetching speed limit: ${e.message}")
        }
        return@withContext null
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }
}
