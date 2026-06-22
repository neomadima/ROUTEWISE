package com.routewise.sa.ui

import android.os.Bundle
import android.util.Log
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
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.routewise.sa.R
import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.databinding.FragmentDashboardBinding
import com.routewise.sa.model.Incident
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import com.google.android.gms.location.*
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import android.Manifest
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import com.google.android.gms.maps.model.BitmapDescriptor

import com.routewise.sa.MainActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.Polyline

import com.routewise.sa.utils.RouteUtils
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.gms.common.api.Status
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

class DashboardFragment : Fragment(), OnMapReadyCallback {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    
    private val viewModel: MainViewModel by activityViewModels()
    private var activeRoutePolylines: MutableList<Polyline> = mutableListOf()
    private var clusterManager: ClusterManager<Incident>? = null
    private var lastKnownIncidents: List<Incident> = emptyList()
    private var cachedRoutePath: List<LatLng>? = null
    private var cachedSteps: List<RouteUtils.StepData>? = null
    private var cachedDurationSec: Int = 0
    private var cachedDistanceMeters: Int = 0
    private var followUser = true

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var selectedPlace: Place? = null
    private var lastSpeedLimitUpdate: Long = 0
    private var currentRoadSpeedLimit: Int? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val bindingObj = _binding ?: return
                
                if (followUser) {
                    val pos = LatLng(location.latitude, location.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                }
                
                // Update Speedometer
                val speedKmH = (location.speed * 3.6).toInt()
                bindingObj.tvMapSpeed.text = speedKmH.toString()
                bindingObj.speedProgressBar.progress = speedKmH.coerceAtMost(160)

                // Fetch Real Speed Limit
                if (System.currentTimeMillis() - lastSpeedLimitUpdate > 15000) {
                    lastSpeedLimitUpdate = System.currentTimeMillis()
                    lifecycleScope.launch {
                        val realLimit = RouteUtils.getSpeedLimit(LatLng(location.latitude, location.longitude))
                        if (realLimit != null) {
                            currentRoadSpeedLimit = realLimit
                        }
                    }
                }

                currentRoadSpeedLimit?.let { limit ->
                    if (speedKmH > limit) {
                        bindingObj.tvMapSpeed.setTextColor(android.graphics.Color.RED)
                    } else {
                        bindingObj.tvMapSpeed.setTextColor(android.graphics.Color.BLACK)
                    }
                }
                
                // Update Navigation UI
                viewModel.activeRoute.value?.let { route ->
                    val userLoc = android.location.Location("").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                    val destLoc = android.location.Location("").apply {
                        latitude = route.destination.latitude
                        longitude = route.destination.longitude
                    }
                    val totalDistanceMeters = userLoc.distanceTo(destLoc)
                    
                    // Update Trip Status (Bottom)
                    updateTripStatus(totalDistanceMeters, location.speed)

                    // Update Instruction Bar (Top)
                    updateNavigationInstruction(route, location)
                }
            }
        }
    }

    private fun updateTripStatus(distanceMeters: Float, speedMs: Float) {
        val distanceText: String
        val unitText: String
        if (distanceMeters > 1000) {
            distanceText = String.format(Locale.getDefault(), "%.1f", distanceMeters / 1000)
            unitText = " km"
        } else {
            distanceText = distanceMeters.toInt().toString()
            unitText = " m"
        }
        
        binding.tvMapDistance.text = distanceText
        binding.tvMapDistanceUnit.text = unitText

        val speed = if (speedMs > 1) speedMs else 13.8f // 50km/h default
        val remainingSeconds = (distanceMeters / speed).toInt()
        val minutes = remainingSeconds / 60
        val hours = minutes / 60
        val remainingMins = minutes % 60
        
        binding.tvMapDuration.text = if (hours > 0) "$hours hr $remainingMins min" else "$minutes min"
        
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, remainingSeconds)
        binding.tvMapEta.text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(calendar.time)
    }

    private fun updateNavigationInstruction(route: ActiveRoute, userLocation: android.location.Location) {
        val steps = route.steps
        if (steps.isEmpty()) return

        // Find the "next" step (the first step that is in front of the user)
        var nextStep: RouteUtils.StepData? = null
        var minDistanceToStep = Float.MAX_VALUE

        for (step in steps) {
            val stepLoc = android.location.Location("").apply {
                latitude = step.startLocation.latitude
                longitude = step.startLocation.longitude
            }
            val dist = userLocation.distanceTo(stepLoc)
            
            // If we are close to the start of this step, or it's the first one in the future
            if (dist < minDistanceToStep && dist > 10) { 
                minDistanceToStep = dist
                nextStep = step
            }
        }

        nextStep?.let { step ->
            binding.tvManeuverDistance.text = if (minDistanceToStep > 1000) 
                String.format(Locale.getDefault(), "%.1f km", minDistanceToStep / 1000) else "${minDistanceToStep.toInt()} m"
            
            binding.tvNextRoadName.text = route.destinationName // Or step.instruction if parsed better

            // Set the correct arrow based on the maneuver
            val maneuver = step.maneuver ?: "straight"
            val rotation = when {
                maneuver.contains("left") -> -90f
                maneuver.contains("right") -> 90f
                maneuver.contains("u-turn") -> 180f
                else -> 0f
            }
            
            // Use a standard arrow icon and rotate it
            binding.ivManeuverIcon.setImageResource(android.R.drawable.ic_menu_directions)
            binding.ivManeuverIcon.rotation = rotation
            binding.ivManeuverIcon.imageTintList = ColorStateList.valueOf(android.graphics.Color.BLACK)
        }
    }

    private fun showAddStopOptions() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_stop, null, false)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btnStopGas).setOnClickListener {
            searchStopsAlongRoute("gas_station")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnStopFood).setOnClickListener {
            searchStopsAlongRoute("restaurant")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnStopCoffee).setOnClickListener {
            searchStopsAlongRoute("cafe")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnStopAtm).setOnClickListener {
            searchStopsAlongRoute("atm")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnStopParking).setOnClickListener {
            searchStopsAlongRoute("parking")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnStopCustom).setOnClickListener {
            binding.searchCard.visibility = View.VISIBLE
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun searchStopsAlongRoute(type: String) {
        val currentRoute = viewModel.activeRoute.value ?: return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                val allFoundPlaces = mutableSetOf<Place>()
                val path = currentRoute.path
                if (path.isEmpty()) return@addOnSuccessListener
                
                // Target points along the remaining route path to ensure results are "on the way"
                val searchPoints = mutableListOf<LatLng>()
                searchPoints.add(userLatLng)
                
                // Add sample points from path (avoiding points already passed if possible)
                val stepSize = (path.size / 4).coerceAtLeast(1)
                for (i in 0 until path.size step stepSize) {
                    searchPoints.add(path[i])
                }
                searchPoints.add(currentRoute.destination)

                var completed = 0
                searchPoints.take(5).forEach { point ->
                    val request = SearchByTextRequest.builder(
                        type.replace("_", " "),
                        listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
                    ).setLocationRestriction(RectangularBounds.newInstance(
                        LatLng(point.latitude - 0.02, point.longitude - 0.02),
                        LatLng(point.latitude + 0.02, point.longitude + 0.02)
                    )).setMaxResultCount(3).build()

                    placesClient.searchByText(request).addOnSuccessListener { response ->
                        allFoundPlaces.addAll(response.places)
                        completed++
                        if (completed == searchPoints.take(5).size) {
                            showStopSuggestions(allFoundPlaces.toList())
                        }
                    }.addOnFailureListener {
                        completed++
                        if (completed == searchPoints.take(5).size) {
                            showStopSuggestions(allFoundPlaces.toList())
                        }
                    }
                }
            }
        }
    }

    private fun showStopSuggestions(places: List<Place>) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_stop_suggestions, null, false)
        dialog.setContentView(view)

        val container = view.findViewById<ViewGroup>(R.id.suggestionsContainer)
        val currentRoute = viewModel.activeRoute.value ?: return

        lifecycleScope.launch {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let { loc ->
                        val userLatLng = LatLng(loc.latitude, loc.longitude)
                        places.distinctBy { it.id }.forEach { place ->
                            val stopLoc = place.location ?: return@forEach
                            val itemView = layoutInflater.inflate(R.layout.item_stop_suggestion, container, false)
                            itemView.findViewById<TextView>(R.id.tvStopName).text = place.displayName
                            itemView.findViewById<TextView>(R.id.tvStopAddress).text = place.formattedAddress
                            
                            lifecycleScope.launch {
                                val detailsWithStop = RouteUtils.getRouteDetails(
                                    userLatLng, 
                                    currentRoute.destination, 
                                    waypoints = listOf(stopLoc)
                                )
                                
                                if (detailsWithStop != null) {
                                    val detourMin = (detailsWithStop.durationSec - currentRoute.durationSec) / 60
                                    
                                    // "On the way" filter: Only show if detour is less than 15 minutes
                                    if (detourMin <= 15) {
                                        itemView.findViewById<TextView>(R.id.tvDetourTime).text = getString(R.string.detour_time_format, detourMin)
                                        
                                        itemView.setOnClickListener {
                                            viewModel.addWaypoint(
                                                stopLoc, 
                                                detailsWithStop.path, 
                                                detailsWithStop.steps,
                                                detailsWithStop.durationSec,
                                                detailsWithStop.distanceMeters
                                            )
                                            dialog.dismiss()
                                        }
                                        container.addView(itemView)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    companion object {
        private const val TAG = "DashboardFragment"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        placesClient = Places.createClient(requireContext())
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        
        checkLocationPermission()
        setupSearch()
        setupBottomSheet()

        binding.btnOpenDrawer.setOnClickListener {
            (activity as? MainActivity)?.findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
        }

        binding.fabReport.setOnClickListener {
            findNavController().navigate(R.id.reportIncidentFragment)
        }

        binding.fabRecenter.setOnClickListener {
            followUser = true
            zoomToUserLocation()
            binding.fabRecenter.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_light))
        }

        binding.btnSideRecenter.setOnClickListener {
            followUser = true
            zoomToUserLocation()
        }

        binding.btnSideReport.setOnClickListener {
            findNavController().navigate(R.id.reportIncidentFragment)
        }

        binding.btnSideSearch.setOnClickListener {
            showAddStopOptions()
        }

        binding.btnSideEdit.setOnClickListener {
            findNavController().navigate(R.id.routePlanningFragment)
        }

        binding.btnCancelTrip.setOnClickListener {
            selectedPlace = null
            viewModel.clearActiveRoute()
            googleMap?.clear()
            updateMapMarkers()
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        observeViewModel()
        observeIncidents()
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.placeDetailSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.placeDetailSheet.visibility = View.GONE

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        binding.placeDetailSheet.visibility = View.GONE
                        val isRouteActive = viewModel.activeRoute.value != null
                        binding.searchCard.visibility = if (isRouteActive) View.GONE else View.VISIBLE
                        binding.tripStatusContainer.visibility = if (isRouteActive) View.VISIBLE else View.GONE
                        binding.fabRecenter.visibility = if (isRouteActive) View.GONE else View.VISIBLE
                        binding.sideActionsContainer.visibility = if (isRouteActive) View.VISIBLE else View.GONE
                        binding.btnSideEdit.visibility = if (isRouteActive) View.VISIBLE else View.GONE
                        binding.fabReport.visibility = View.VISIBLE
                        binding.quickActionRow.visibility = View.VISIBLE
                    }
                    else -> {
                        binding.placeDetailSheet.visibility = View.VISIBLE
                        binding.tripStatusContainer.visibility = View.GONE
                        binding.fabRecenter.visibility = View.GONE
                        binding.sideActionsContainer.visibility = View.GONE
                        binding.fabReport.visibility = View.GONE
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.btnCloseSheet.setOnClickListener {
            selectedPlace = null
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            googleMap?.clear()
            viewModel.clearActiveRoute()
            updateMapMarkers()
        }

        binding.btnSheetStart.setOnClickListener {
            selectedPlace?.let { place ->
                val location = place.location ?: return@let
                viewModel.setActiveRoute(
                    location.latitude, 
                    location.longitude, 
                    place.displayName ?: "Destination",
                    cachedRoutePath ?: emptyList(),
                    cachedSteps ?: emptyList(),
                    durationSec = cachedDurationSec,
                    distanceMeters = cachedDistanceMeters
                )
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }

        binding.btnSheetDirections.setOnClickListener {
            selectedPlace?.let { place ->
                val location = place.location ?: return@let
                drawActiveRoute(LatLng(location.latitude, location.longitude))
            }
        }
    }

    private fun setupSearch() {
        val autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS))

        val gautengBounds = RectangularBounds.newInstance(LatLng(-26.9, 27.2), LatLng(-25.1, 29.1))
        autocompleteFragment.setLocationRestriction(gautengBounds)

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                selectedPlace = place
                val selectedLocation = place.location
                if (selectedLocation != null) {
                    updateMapMarkers() 
                    googleMap?.addMarker(MarkerOptions().position(selectedLocation).title(place.displayName))
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLocation, 15f))

                    binding.tvPlaceName.text = place.displayName
                    binding.tvPlaceAddress.text = place.formattedAddress ?: "Address not available"
                    binding.tvTravelTime.text = getString(R.string.travel_time_format, 0).replace("0", "--")

                    lifecycleScope.launch {
                        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                location?.let {
                                    lifecycleScope.launch {
                                        val details = RouteUtils.getRouteDetails(LatLng(it.latitude, it.longitude), selectedLocation)
                                        if (details != null) {
                                            cachedRoutePath = details.path
                                            cachedSteps = details.steps
                                            cachedDurationSec = details.durationSec
                                            cachedDistanceMeters = details.distanceMeters
                                            
                                            binding.tvTravelTime.text = getString(R.string.travel_time_format, details.durationSec / 60)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    binding.placeDetailSheet.visibility = View.VISIBLE
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
            override fun onError(status: Status) {
                Log.e(TAG, "Autocomplete error: ${status.statusMessage}")
            }
        })
    }

    private fun observeViewModel() {
        viewModel.activeRoute.observe(viewLifecycleOwner) { route ->
            if (route != null) {
                drawActiveRoute(route.destination)
                binding.btnCancelTrip.visibility = View.VISIBLE
                binding.searchCard.visibility = View.GONE
                binding.tripStatusContainer.visibility = View.VISIBLE
                binding.topInstructionBar.visibility = View.VISIBLE
                binding.speedCircleContainer.visibility = View.VISIBLE
                binding.sideActionsContainer.visibility = View.VISIBLE
                binding.btnSideEdit.visibility = View.VISIBLE
                binding.drawerCard.visibility = View.GONE
                binding.fabRecenter.visibility = View.GONE
            } else {
                binding.btnCancelTrip.visibility = View.GONE
                binding.searchCard.visibility = View.VISIBLE 
                binding.quickActionRow.visibility = View.VISIBLE
                binding.tripStatusContainer.visibility = View.GONE
                binding.topInstructionBar.visibility = View.GONE
                binding.speedCircleContainer.visibility = View.GONE
                binding.sideActionsContainer.visibility = View.GONE
                binding.btnSideEdit.visibility = View.GONE
                binding.drawerCard.visibility = View.VISIBLE
                binding.fabRecenter.visibility = View.VISIBLE

                activeRoutePolylines.forEach { it.remove() }
                activeRoutePolylines.clear()
                cachedRoutePath = null
                cachedSteps = null
                updateMapMarkers()
            }
        }
    }

    private fun drawActiveRoute(destination: LatLng) {
        val activeRoute = viewModel.activeRoute.value
        if (activeRoute != null && activeRoute.path.isNotEmpty()) {
            displayRouteOnMap(activeRoute.path)
            return
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val start = LatLng(it.latitude, it.longitude)
                    if (cachedRoutePath != null) {
                        displayRouteOnMap(cachedRoutePath!!)
                        return@addOnSuccessListener
                    }
                    
                    lifecycleScope.launch {
                        val details = RouteUtils.getRouteDetails(start, destination)
                        if (details != null) {
                            cachedRoutePath = details.path
                            cachedSteps = details.steps
                            displayRouteOnMap(details.path)
                        } else {
                            displayRouteOnMap(listOf(start, destination))
                        }
                    }
                }
            }
        }
    }

    private fun displayRouteOnMap(path: List<LatLng>) {
        googleMap?.let { map ->
            activeRoutePolylines.forEach { it.remove() }
            activeRoutePolylines.clear()
            val poly = map.addPolyline(PolylineOptions()
                .addAll(path)
                .width(12f)
                .color("#0087FF".toColorInt())
                .geodesic(true))
            activeRoutePolylines.add(poly)

            // Draw Waypoints
            viewModel.activeRoute.value?.waypoints?.forEach { waypoint ->
                map.addMarker(MarkerOptions()
                    .position(waypoint)
                    .title("Stop")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            startLocationUpdates()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
            zoomToUserLocation()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(1f)
            .build()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            googleMap?.isMyLocationEnabled = true
        }
    }

    private fun zoomToUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
                    } ?: run {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            lastLoc?.let {
                                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f))
                            }
                        }
                    }
                }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        applyMapStyle(map)
        map.isTrafficEnabled = true 
        map.uiSettings.apply {
            isZoomControlsEnabled = true
            isMyLocationButtonEnabled = false 
            isCompassEnabled = true
        }

        setupClusterManager(map)

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                followUser = false
                binding.fabRecenter.imageTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.darker_gray)
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
            zoomToUserLocation()
        }

        val density = resources.displayMetrics.density
        googleMap?.setPadding(0, 0, 0, (120 * density).toInt())

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-30.5595, 22.9375), 5f))
        }
        updateMapMarkers()
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
                    "Road Work", "Construction" -> android.R.drawable.ic_menu_manage // Placeholder or custom
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
            val isNight = hour !in 6..17 // Night before 6 AM or after 5:59 PM
            
            if (isNight) {
                val success = map.setMapStyle(
                    com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                        requireContext(), R.raw.map_style_night
                    )
                )
                if (!success) Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }

    private fun updateMapMarkers() {
        googleMap?.clear()
        clusterManager?.clearItems()
        viewModel.activeRoute.value?.let { route ->
            drawActiveRoute(route.destination)
        }
        updateIncidentMarkers(lastKnownIncidents)
    }

    private fun observeIncidents() {
        lifecycleScope.launch {
            repository.getAllIncidents().collectLatest { incidents ->
                lastKnownIncidents = incidents
                updateIncidentMarkers(incidents)
            }
        }
    }

    private fun updateIncidentMarkers(incidents: List<Incident>) {
        clusterManager?.let { manager ->
            manager.clearItems()
            manager.addItems(incidents)
            manager.cluster()
        }
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _binding = null
    }
}
