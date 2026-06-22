package com.routewise.sa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.routewise.sa.R
import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.databinding.FragmentReportIncidentBinding
import com.routewise.sa.model.Incident
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.gms.common.api.Status
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch

class ReportIncidentFragment : Fragment() {
    private var _binding: FragmentReportIncidentBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository
    
    private var selectedRoadName: String = ""
    private var selectedLatLng: LatLng? = null

    private val incidentTypes = arrayOf(
        "Accident", "Heavy Traffic", "Congestion", "Hazard", "Police Trap",
        "Broken Robot", "Roadblock", "Crime Hotspot", "Pothole",
        "Sharp Curve", "Speed Bump", "Toll Booth", "Emergency Vehicle", "Other",
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        _binding = FragmentReportIncidentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val incidentTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, incidentTypes)
        incidentTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerIncidentType.setAdapter(incidentTypeAdapter)

        setupAutocomplete()

        binding.btnSubmit.setOnClickListener {
            submitReport()
        }
    }

    private fun setupAutocomplete() {
        val autocompleteFragment = childFragmentManager.findFragmentById(R.id.autocomplete_fragment_report) as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS))
        autocompleteFragment.setHint("Search for road or address...")

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                selectedRoadName = place.displayName ?: ""
                selectedLatLng = place.location
            }

            override fun onError(status: Status) {
                android.util.Log.e("ReportIncident", "Autocomplete error: ${status.statusMessage}")
            }
        })
    }

    private fun submitReport() {
        val type = binding.spinnerIncidentType.text.toString()
        val description = binding.etDescription.text.toString()

        if (selectedRoadName.isEmpty()) {
            Toast.makeText(context, "Please select a road or address", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val userData = repository.getUserLocation()
                val province = (userData?.get("province") as? String) ?: "Gauteng"
                val city = (userData?.get("city") as? String) ?: "Pretoria"
                
                val finalLat: Double
                val finalLng: Double

                if (selectedLatLng != null) {
                    finalLat = selectedLatLng!!.latitude
                    finalLng = selectedLatLng!!.longitude
                } else {
                    // Fallback (should ideally not be reached if validation works)
                    val baseLat = if (city.contains("Johannesburg")) -26.20 else -25.74
                    val baseLng = if (city.contains("Johannesburg")) 28.04 else 28.22
                    finalLat = baseLat + (Math.random() - 0.5) / 10.0
                    finalLng = baseLng + (Math.random() - 0.5) / 10.0
                }

                val currentUser = repository.getCurrentUser()
                val incident = Incident(
                    type = type,
                    description = description,
                    roadName = selectedRoadName,
                    province = province,
                    city = city,
                    reporterEmail = currentUser?.email ?: "",
                    verified = false,
                    latitude = finalLat,
                    longitude = finalLng
                )
                
                repository.reportIncident(incident)
                Toast.makeText(context, "Incident reported successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
