package com.routewise.sa.ui

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.routewise.sa.R
import com.routewise.sa.databinding.FragmentNavigationBinding
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.navigation.fragment.findNavController

import com.google.android.gms.location.*
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.location.Location

import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.model.Incident
import kotlinx.coroutines.flow.collectLatest

import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager

import com.google.android.gms.maps.model.RoundCap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.content.Context
import androidx.core.graphics.toColorInt

import com.routewise.sa.utils.RouteUtils
import com.google.android.gms.maps.model.CameraPosition
import java.text.SimpleDateFormat
import java.util.Calendar

import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

class NavigationFragment : Fragment(), OnMapReadyCallback, TextToSpeech.OnInitListener {

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private var clusterManager: ClusterManager<Incident>? = null
    private var tts: TextToSpeech? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: MainViewModel by activityViewModels()
    
    private var destinationLatLng: LatLng? = null
    private var destinationName: String? = null
    private var routePolyline: Polyline? = null
    private var isUsingFallbackLine = false
    private var followUser = true

    private lateinit var repository: RouteWiseRepository
    private val alertedIncidentIds = mutableSetOf<String>()
    private var currentIncidents: List<Incident> = emptyList()
    private lateinit var adapter: IncidentAdapter
    private var lastSpeedLimitUpdate: Long = 0
    private var currentRoadSpeedLimit: Int? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val bindingObj = _binding ?: return
                
                val speedKmH = (location.speed * 3.6).toInt()
                bindingObj.tvSpeed.text = speedKmH.toString()
                
                if (System.currentTimeMillis() - lastSpeedLimitUpdate > 10000) {
                    lastSpeedLimitUpdate = System.currentTimeMillis()
                    viewLifecycleOwner.lifecycleScope.launch {
                        val realLimit = RouteUtils.getSpeedLimit(LatLng(location.latitude, location.longitude))
                        if (realLimit != null) {
                            currentRoadSpeedLimit = realLimit
                            _binding?.tvSpeedLimit?.text = realLimit.toString()
                        }
                    }
                }

                val speedLimit = currentRoadSpeedLimit ?: 120
                bindingObj.tvSpeedLimit.text = speedLimit.toString()

                if (speedKmH > speedLimit) {
                    bindingObj.tvSpeed.setTextColor(android.graphics.Color.RED)
                } else {
                    bindingObj.tvSpeed.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_light))
                }

                updateNavigationData(location)
                checkForNearbyHazards(location)
                
                bindingObj.vMovingIndicator.let { indicator ->
                    indicator.visibility = View.VISIBLE
                    indicator.animate().alpha(0f).setDuration(500).withEndAction {
                        indicator.alpha = 1f
                    }.start()
                }

                val pos = LatLng(location.latitude, location.longitude)
                if (::mMap.isInitialized && followUser) {
                    val currentCamera = mMap.cameraPosition
                    val cameraPosition = CameraPosition.Builder()
                        .target(pos)
                        .zoom(18f)
                        .bearing(if (location.hasBearing() && location.speed > 1) location.bearing else currentCamera.bearing)
                        .tilt(45f)
                        .build()
                    
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }
                
                if (::mMap.isInitialized) {
                    drawRoute(pos)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.navMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        tts = TextToSpeech(requireContext(), this)

        binding.btnStopNav.setOnClickListener {
            viewModel.clearActiveRoute()
            findNavController().popBackStack()
        }

        binding.btnViewAlerts.setOnClickListener {
            binding.alertsContainer.visibility = View.VISIBLE
        }

        binding.btnCloseAlerts.setOnClickListener {
            binding.alertsContainer.visibility = View.GONE
        }
        
        binding.fabReport.setOnClickListener {
            findNavController().navigate(R.id.reportIncidentFragment)
        }

        binding.btnSideEdit.setOnClickListener {
            findNavController().navigate(R.id.routePlanningFragment)
        }

        binding.fabRecenter.setOnClickListener {
            followUser = true
            updateMapToUser()
            binding.fabRecenter.visibility = View.GONE
        }

        binding.btnCloseWarning.setOnClickListener {
            binding.warningCard.visibility = View.GONE
        }

        binding.btnVoice.setOnClickListener {
            viewModel.activeRoute.value?.let { route ->
                speakInstruction("Navigating to ${route.destinationName}")
            }
        }

        adapter = IncidentAdapter(
            incidents = emptyList(),
            onDeleteClick = { incident ->
                viewLifecycleOwner.lifecycleScope.launch {
                    repository.deleteIncident(incident.id)
                }
            },
            onNavigateClick = { incident ->
                val pos = LatLng(incident.latitude, incident.longitude)
                followUser = false
                binding.fabRecenter.visibility = View.VISIBLE
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
                binding.alertsContainer.visibility = View.GONE
            }
        )
        binding.rvNavIncidents.layoutManager = LinearLayoutManager(context)
        binding.rvNavIncidents.adapter = adapter
        
        startIncidentListener()
    }

    private fun startIncidentListener() {
        viewLifecycleOwner.lifecycleScope.launch {
            val location = repository.getUserLocation()
            val province = location?.get("province") as? String ?: "Gauteng"
            val city = location?.get("city") as? String ?: "Pretoria"

            repository.getIncidents(province, city).collectLatest { incidents ->
                currentIncidents = incidents
                adapter.updateData(incidents)
                updateMapMarkers(incidents)
            }
        }
    }

    private fun updateMapMarkers(incidents: List<Incident>) {
        if (!::mMap.isInitialized) return
        
        mMap.clear()
        routePolyline = null 
        clusterManager?.clearItems()
        
        destinationLatLng?.let {
            mMap.addMarker(MarkerOptions()
                .position(it)
                .title(destinationName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        }

        viewModel.activeRoute.value?.let { route ->
            if (route.path.isNotEmpty()) {
                displayRouteOnMap(route.path)
            }
            
            route.waypoints.forEach { waypoint ->
                mMap.addMarker(MarkerOptions()
                    .position(waypoint)
                    .title("Stop")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
            }
        }

        clusterManager?.addItems(incidents)
        clusterManager?.cluster()
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = android.graphics.Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        applyMapStyle(googleMap)
        mMap.isTrafficEnabled = true 
        
        setupClusterManager(googleMap)
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
        }
        
        mMap.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                followUser = false
                binding.fabRecenter.visibility = View.VISIBLE
            }
        }

        observeViewModel()
        startLocationUpdates()
        updateMapToUser()
    }

    private fun setupClusterManager(map: GoogleMap) {
        clusterManager = ClusterManager<Incident>(requireContext(), map)
        map.setOnCameraIdleListener(clusterManager)
        map.setOnMarkerClickListener(clusterManager)
        
        clusterManager?.renderer = object : DefaultClusterRenderer<Incident>(requireContext(), map, clusterManager) {
            override fun onBeforeClusterItemRendered(item: Incident, markerOptions: MarkerOptions) {
                val iconRes = when (item.type) {
                    "Accident" -> R.drawable.ic_traffic_sign_accident
                    "Heavy Traffic", "Congestion" -> R.drawable.ic_traffic_sign_congestion
                    "Hazard" -> R.drawable.ic_traffic_sign_hazard
                    "Police Trap" -> R.drawable.ic_traffic_sign_police
                    "Pothole" -> R.drawable.ic_traffic_sign_pothole
                    "Broken Robot" -> R.drawable.ic_traffic_sign_robot
                    "Road Work", "Construction" -> android.R.drawable.ic_menu_manage
                    "Closed Road" -> android.R.drawable.ic_menu_close_clear_cancel
                    else -> R.drawable.ic_traffic_sign_hazard
                }
                bitmapDescriptorFromVector(requireContext(), iconRes)?.let {
                    markerOptions.icon(it)
                }
                markerOptions.title(item.type)
                markerOptions.snippet(item.description)
            }
        }
    }

    private fun applyMapStyle(map: GoogleMap) {
        try {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isNight = hour < 6 || hour >= 18
            
            if (isNight) {
                val success = map.setMapStyle(
                    com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                        requireContext(), R.raw.map_style_night
                    )
                )
                if (!success) android.util.Log.e("NavFragment", "Style parsing failed.")
            }
        } catch (e: Exception) {
            android.util.Log.e("NavFragment", "Can't find style. Error: ", e)
        }
    }

    private fun observeViewModel() {
        viewModel.activeRoute.observe(viewLifecycleOwner) { route ->
            if (route != null) {
                destinationLatLng = route.destination
                destinationName = route.destinationName
                
                binding.tvTransportMode.text = route.transportType.name.lowercase().replaceFirstChar { it.uppercase() }

                if (::mMap.isInitialized) {
                    updateMapMarkers(currentIncidents)
                }
            } else {
                findNavController().popBackStack()
            }
        }
    }

    private fun drawRoute(currentPos: LatLng) {
        val activeRoute = viewModel.activeRoute.value
        if (activeRoute != null && activeRoute.path.isNotEmpty()) {
            if (routePolyline == null) {
                displayRouteOnMap(activeRoute.path)
            }
            return
        }

        if (routePolyline == null || isUsingFallbackLine) {
            updateRouteLine(currentPos)
        }
    }

    private fun displayRouteOnMap(path: List<LatLng>) {
        if (!::mMap.isInitialized) return
        routePolyline?.remove()
        routePolyline = mMap.addPolyline(PolylineOptions()
            .addAll(path)
            .width(16f)
            .color("#0087FF".toColorInt())
            .startCap(RoundCap())
            .endCap(RoundCap())
            .jointType(JointType.ROUND)
            .geodesic(true))
        isUsingFallbackLine = false
    }

    private fun updateRouteLine(currentPos: LatLng) {
        destinationLatLng?.let { dest ->
            viewLifecycleOwner.lifecycleScope.launch {
                val path = RouteUtils.getRoutePolyline(currentPos, dest)
                if (path.isNotEmpty()) {
                    displayRouteOnMap(path)
                } else if (routePolyline == null) {
                    isUsingFallbackLine = true
                    routePolyline = mMap.addPolyline(PolylineOptions()
                        .add(currentPos, dest)
                        .width(16f)
                        .color("#0087FF".toColorInt())
                        .startCap(RoundCap())
                        .endCap(RoundCap())
                        .jointType(JointType.ROUND)
                        .geodesic(true))
                }
            }
        }
    }

    private fun updateMapToUser() {
        if (!::mMap.isInitialized) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val cameraPosition = CameraPosition.Builder()
                        .target(LatLng(it.latitude, it.longitude))
                        .zoom(18f)
                        .bearing(it.bearing)
                        .tilt(45f)
                        .build()
                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(1f)
            .build()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    private fun checkForNearbyHazards(currentLocation: Location) {
        for (incident in currentIncidents) {
            if (alertedIncidentIds.contains(incident.id)) continue

            val incidentLoc = Location("").apply {
                latitude = incident.latitude
                longitude = incident.longitude
            }
            val distance = currentLocation.distanceTo(incidentLoc)
            
            if (distance < 500) {
                alertedIncidentIds.add(incident.id)
                showWarningPopup(incident, distance.toInt())
                break 
            }
        }
    }

    private fun showWarningPopup(incident: Incident, distance: Int) {
        binding.apply {
            tvWarningTitle.text = incident.type.uppercase()
            tvWarningDesc.text = "${incident.description} • ${distance}m away"
            warningCard.visibility = View.VISIBLE
            
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(10000)
                _binding?.warningCard?.visibility = View.GONE
            }
        }
    }

    private fun updateNavigationData(currentLocation: Location) {
        destinationLatLng?.let { dest ->
            val results = FloatArray(1)
            Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, dest.latitude, dest.longitude, results)
            val distanceMeters = results[0]
            
            val transport = viewModel.activeRoute.value?.transportType ?: TransportType.CAR
            val defaultSpeed = when (transport) {
                TransportType.CAR -> 13.88f // 50 km/h
                TransportType.TRUCK -> 11.11f // 40 km/h
                TransportType.BIKE -> 5.55f   // 20 km/h
                TransportType.WALKING, TransportType.TRANSIT -> 1.38f // 5 km/h
            }
            
            val speedMs = if (currentLocation.speed > 1) currentLocation.speed else defaultSpeed
            
            val remainingSeconds = (distanceMeters / speedMs).toInt()
            val minutes = remainingSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            
            val distanceText: String
            val unitText: String
            if (distanceMeters > 1000) {
                distanceText = String.format(Locale.getDefault(), "%.1f", distanceMeters / 1000)
                unitText = " km"
            } else {
                distanceText = distanceMeters.toInt().toString()
                unitText = " m"
            }

            binding.apply {
                tvRemainingTime.text = if (hours > 0) "$hours hr $remainingMins min" else "$minutes min"
                
                tvMapDistance.text = distanceText
                tvMapDistanceUnit.text = unitText

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.SECOND, remainingSeconds)
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                tvEta.text = sdf.format(calendar.time)

                tvManeuverDistance.text = if (distanceMeters > 1000) 
                    String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000) else "${distanceMeters.toInt()} m"
                tvNextRoadName.text = destinationName

                routeProgress.progress = (100 - (distanceMeters / 10000 * 100).toInt()).coerceIn(0, 100)
            }

            if (distanceMeters < 50) {
                speakInstruction("You have arrived at your destination")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
        }
    }

    private fun speakInstruction(instruction: String) {
        tts?.let { 
             it.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tts?.stop()
        tts?.shutdown()
        _binding = null
    }
}
