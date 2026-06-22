package com.routewise.sa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.routewise.sa.R
import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.databinding.FragmentLiveAlertsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.fragment.app.activityViewModels

class LiveAlertsFragment : Fragment() {

    private var _binding: FragmentLiveAlertsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository
    private lateinit var adapter: IncidentAdapter
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        _binding = FragmentLiveAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = IncidentAdapter(
            incidents = emptyList(),
            onDeleteClick = { incident ->
                lifecycleScope.launch {
                    repository.deleteIncident(incident.id)
                }
            },
            onNavigateClick = { incident ->
                // Set Persistent Route
                viewModel.setActiveRoute(incident.latitude, incident.longitude, incident.roadName)
                // Navigate to guidance
                findNavController().navigate(R.id.navigationFragment)
            }
        )

        binding.rvLiveAlerts.layoutManager = LinearLayoutManager(context)
        binding.rvLiveAlerts.adapter = adapter

        binding.btnClearAlerts.setOnClickListener {
            lifecycleScope.launch {
                repository.deleteAllIncidents()
            }
        }

        loadAlerts()
    }

    private fun loadAlerts() {
        lifecycleScope.launch {
            val location = repository.getUserLocation()
            val province = (location?.get("province") as? String) ?: "Gauteng"
            val city = (location?.get("city") as? String) ?: "Pretoria"
            
            repository.getIncidents(province, city).collectLatest {
                adapter.updateData(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
