package com.routewise.sa.model

import kotlinx.serialization.Serializable

@Serializable
data class SerpApiResponse(
    val directions: List<Direction>? = null
)

@Serializable
data class Direction(
    val travel_mode: String? = null,
    val via: String? = null,
    val distance: Int? = null,         // in meters
    val duration: Int? = null,         // in seconds
    val formatted_distance: String? = null,
    val formatted_duration: String? = null,
    val typical_duration_range: String? = null,
    val extensions: List<String>? = null,
    val trips: List<Trip>? = null
)

@Serializable
data class Trip(
    val travel_mode: String? = null,
    val title: String? = null,
    val distance: Int? = null,
    val duration: Int? = null,
    val formatted_distance: String? = null,
    val formatted_duration: String? = null,
    val details: List<StepDetail>? = null
)

@Serializable
data class StepDetail(
    val title: String? = null,
    val action: String? = null,
    val distance: Int? = null,
    val duration: Int? = null,
    val formatted_distance: String? = null,
    val formatted_duration: String? = null,
    val geo_photo: String? = null,
    val gps_coordinates: Gps? = null,
    val extensions: List<String>? = null
)

@Serializable
data class Gps(
    val latitude: Double? = null,
    val longitude: Double? = null
)
