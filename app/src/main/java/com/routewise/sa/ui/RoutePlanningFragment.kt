package com.routewise.sa.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.Places
import com.routewise.sa.R
import com.routewise.sa.databinding.FragmentRoutePlanningBinding
import com.routewise.sa.databinding.ItemWaypointBinding
import com.routewise.sa.databinding.DialogAddStopSuggestionsBinding
import com.routewise.sa.databinding.ItemSuggestionBinding
import com.google.android.libraries.places.api.model.RectangularBounds
import com.routewise.sa.utils.RouteUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class RoutePlanningFragment : Fragment() {

    private var _binding: FragmentRoutePlanningBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient

    private var startPlace: Place? = null
    private var endPlace: Place? = null
    private val waypoints = mutableListOf<Place>()
    private var currentRoutePath: List<LatLng>? = null
    
    private var selectedCalendar: Calendar? = null // Mandatory: must be set by user

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        placesClient = Places.createClient(requireContext())
        _binding = FragmentRoutePlanningBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAutocomplete()
        setupTiming()
        
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnAddStop.setOnClickListener {
            if (endPlace == null) {
                Toast.makeText(context, "Please pick a destination first", Toast.LENGTH_SHORT).show()
            } else {
                showSmartStopSuggestions()
            }
        }

        binding.btnGetDirection.setOnClickListener {
            generateRoute()
        }

        // Pre-fill if editing an active route
        viewModel.activeRoute.value?.let { route ->
            val destFragment = childFragmentManager.findFragmentById(R.id.dest_autocomplete) as? AutocompleteSupportFragment
            destFragment?.setText(route.destinationName)
            
            // Re-create a Place object for the destination (minimal version)
            endPlace = Place.builder()
                .setDisplayName(route.destinationName)
                .setLocation(route.destination)
                .build()
            
            // Add existing waypoints to UI
            route.waypoints.forEach { latLng ->
                val p = Place.builder()
                    .setDisplayName("Stop")
                    .setLocation(latLng)
                    .build()
                addWaypointToUI(p)
            }
            
            // Match transport type
            when (route.transportType) {
                TransportType.CAR -> binding.cgTransport.check(R.id.chipCar)
                TransportType.WALKING -> binding.cgTransport.check(R.id.chipWalk)
                TransportType.BIKE -> binding.cgTransport.check(R.id.chipBike)
                TransportType.TRANSIT -> binding.cgTransport.check(R.id.chipTransit)
                else -> {}
            }
        }

        updateArrivalPreview()
    }

    private fun setupAutocomplete() {
        val startFragment = childFragmentManager.findFragmentById(R.id.start_autocomplete) as AutocompleteSupportFragment
        startFragment.setHint("Starting Point")
        startFragment.setText("Your location")
        startFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION))
        startFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) { 
                startPlace = place 
                updateArrivalPreview()
            }
            override fun onError(status: Status) {}
        })

        val destFragment = childFragmentManager.findFragmentById(R.id.dest_autocomplete) as AutocompleteSupportFragment
        destFragment.setHint("Enter Destination *")
        destFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION))
        destFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) { 
                endPlace = place 
                updateArrivalPreview()
            }
            override fun onError(status: Status) {}
        })
    }

    private fun setupTiming() {
        binding.toggleTime.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnLeaveNow -> {
                    selectedCalendar = Calendar.getInstance()
                    binding.tvSelectedTime.text = "Leaving Now"
                    updateArrivalPreview()
                }
                R.id.btnDepartAt, R.id.btnArriveBy -> {
                    showDateTimePicker()
                }
            }
        }
    }

    private fun showDateTimePicker() {
        val current = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            val tempCal = Calendar.getInstance()
            tempCal.set(Calendar.YEAR, year)
            tempCal.set(Calendar.MONTH, month)
            tempCal.set(Calendar.DAY_OF_MONTH, day)
            
            TimePickerDialog(requireContext(), { _, hour, minute ->
                tempCal.set(Calendar.HOUR_OF_DAY, hour)
                tempCal.set(Calendar.MINUTE, minute)
                
                selectedCalendar = tempCal
                val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                binding.tvSelectedTime.text = "Departing at ${sdf.format(selectedCalendar!!.time)}"
                updateArrivalPreview()
            }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
            
        }, current.get(Calendar.YEAR), current.get(Calendar.MONTH), current.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateArrivalPreview() {
        val dest = endPlace ?: run {
            binding.tvEstimatedArrival.text = "--:--"
            binding.btnGetDirection.isEnabled = false
            return
        }

        val leavingTime = selectedCalendar ?: run {
            binding.tvEstimatedArrival.text = "Set leaving time *"
            binding.btnGetDirection.isEnabled = false
            return
        }

        binding.btnGetDirection.isEnabled = true

        viewLifecycleOwner.lifecycleScope.launch {
            val origin = startPlace?.location ?: run {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val location = fusedLocationClient.lastLocation.await()
                        location?.let { LatLng(it.latitude, it.longitude) }
                    } catch (e: Exception) { null }
                } else null
            } ?: LatLng(-26.2041, 28.0473)

            val details = RouteUtils.getRouteDetails(
                origin = origin,
                dest = dest.location!!,
                waypoints = waypoints.mapNotNull { it.location }
            )

            if (details != null) {
                currentRoutePath = details.path
                val totalSeconds = details.durationSec
                val arrivalCal = leavingTime.clone() as Calendar
                arrivalCal.add(Calendar.SECOND, totalSeconds)
                
                val sdf = SimpleDateFormat("HH:mm, MMM dd", Locale.getDefault())
                binding.tvEstimatedArrival.text = sdf.format(arrivalCal.time)
            } else {
                binding.tvEstimatedArrival.text = "Calculating..."
            }
        }
    }

    private fun showSmartStopSuggestions() {
        val dialog = BottomSheetDialog(requireContext())
        val dialogBinding = DialogAddStopSuggestionsBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        dialogBinding.rvSuggestions.layoutManager = LinearLayoutManager(context)
        val adapter = SuggestionAdapter { placeSuggestion ->
            addWaypointToUI(placeSuggestion)
            dialog.dismiss()
        }
        dialogBinding.rvSuggestions.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            dialogBinding.loadingIndicator.visibility = View.VISIBLE
            val origin = startPlace?.location ?: LatLng(-26.2041, 28.0473)
            val details = RouteUtils.getRouteDetails(origin, endPlace!!.location!!)
            
            if (details != null) {
                // Mocking search along route with proximity search for now
                val request = com.google.android.libraries.places.api.net.SearchByTextRequest.builder(
                    "gas station",
                    listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
                ).setMaxResultCount(5)
                .setLocationRestriction(RectangularBounds.newInstance(
                    LatLng(origin.latitude - 0.05, origin.longitude - 0.05),
                    LatLng(origin.latitude + 0.05, origin.longitude + 0.05)
                )).build()

                placesClient.searchByText(request).addOnSuccessListener { response ->
                    dialogBinding.loadingIndicator.visibility = View.GONE
                    adapter.submitList(response.places)
                }
            }
        }
        dialog.show()
    }

    private fun addWaypointToUI(place: Place) {
        val waypointBinding = ItemWaypointBinding.inflate(layoutInflater, binding.waypointsContainer, false)
        waypointBinding.tvWaypointName.text = place.displayName
        waypointBinding.btnRemoveWaypoint.setOnClickListener {
            binding.waypointsContainer.removeView(waypointBinding.root)
            waypoints.remove(place)
            updateArrivalPreview()
        }
        binding.waypointsContainer.addView(waypointBinding.root)
        waypoints.add(place)
        updateArrivalPreview()
    }

    inner class SuggestionAdapter(private val onSelected: (Place) -> Unit) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {
        private var items = listOf<Place>()
        fun submitList(newItems: List<Place>) { items = newItems; notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemSuggestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvSuggestionName.text = item.displayName
            holder.binding.tvSuggestionAddress.text = item.formattedAddress
            holder.binding.root.setOnClickListener { onSelected(item) }
            
            // Random detour time for visual preview
            val detour = (2..12).random()
            holder.binding.tvDetourTime.text = "+$detour min"
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemSuggestionBinding) : RecyclerView.ViewHolder(binding.root)
    }

    private fun generateRoute() {
        val destLoc = endPlace?.location ?: return
        val leavingTime = selectedCalendar ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val origin = startPlace?.location ?: run {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val location = fusedLocationClient.lastLocation.await()
                        location?.let { LatLng(it.latitude, it.longitude) }
                    } catch (e: Exception) { null }
                } else null
            } ?: LatLng(-26.2041, 28.0473)

            val details = RouteUtils.getRouteDetails(
                origin = origin,
                dest = destLoc,
                waypoints = waypoints.mapNotNull { it.location }
            )

            if (details != null) {
                viewModel.setActiveRoute(
                    lat = destLoc.latitude,
                    lng = destLoc.longitude,
                    name = endPlace?.displayName ?: "Destination",
                    path = details.path,
                    steps = details.steps,
                    waypoints = waypoints.mapNotNull { it.location },
                    durationSec = details.durationSec,
                    distanceMeters = details.distanceMeters
                )
                findNavController().navigate(R.id.navigationFragment)
            } else {
                Toast.makeText(context, "Could not calculate route", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
