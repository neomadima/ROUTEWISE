package com.routewise.sa.model

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

data class Incident(
    val id: String = "",
    val type: String = "",
    val description: String = "",
    val roadName: String = "",
    val province: String = "",
    val city: String = "",
    val reporterEmail: String = "",
    val verified: Boolean = false,
    val isSimulated: Boolean = false,
    val timestamp: Long? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) : ClusterItem {
    override fun getPosition(): LatLng = LatLng(latitude, longitude)
    override fun getTitle(): String = type
    override fun getSnippet(): String = description
    override fun getZIndex(): Float = 0f
}
