package com.routewise.sa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.routewise.sa.R
import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.databinding.FragmentAdminPanelBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

import androidx.navigation.fragment.findNavController
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.routewise.sa.MainActivity

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.routewise.sa.model.Incident

class AdminPanelFragment : Fragment() {
    private var _binding: FragmentAdminPanelBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository
    private lateinit var adapter: AdminIncidentAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        _binding = FragmentAdminPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()

        adapter = AdminIncidentAdapter(emptyList(),
            onVerify = { id -> 
                lifecycleScope.launch { 
                    try {
                        repository.verifyIncident(id)
                        context?.let { Toast.makeText(it, "Incident Accepted and Live", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        context?.let { Toast.makeText(it, "Acceptance failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                    }
                }
            },
            onEdit = { incident ->
                showEditDialog(incident)
            },
            onDelete = { id ->
                lifecycleScope.launch {
                    try {
                        repository.deleteIncident(id)
                        context?.let { Toast.makeText(it, "Incident Dismissed", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        context?.let { Toast.makeText(it, "Action failed: ${e.message}", Toast.LENGTH_SHORT).show() }
                    }
                }
            }
        )
        
        binding.rvUnverifiedIncidents.layoutManager = LinearLayoutManager(context)
        binding.rvUnverifiedIncidents.adapter = adapter
        
        loadAdminData()
    }

    private fun showEditDialog(incident: Incident) {
        val input = EditText(requireContext())
        input.setText(incident.description)
        input.setPadding(50, 40, 50, 40)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Description")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newDesc = input.text.toString()
                lifecycleScope.launch {
                    repository.updateIncident(incident.copy(description = newDesc))
                    context?.let { Toast.makeText(it, "Incident updated", Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupToolbar() {
        binding.adminToolbar.setNavigationOnClickListener {
            (activity as? MainActivity)?.findViewById<DrawerLayout>(R.id.drawer_layout)?.openDrawer(GravityCompat.START)
        }
        binding.adminToolbar.inflateMenu(R.menu.admin_toolbar_menu)
        binding.adminToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_user_view -> {
                    findNavController().navigate(R.id.dashboardFragment)
                    true
                }
                R.id.action_logout -> {
                    repository.logout()
                    findNavController().navigate(R.id.loginFragment)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadAdminData() {
        lifecycleScope.launch {
            try {
                // Load stats
                val stats = repository.getAdminStats()
                binding.tvPendingCount.text = stats["pending"].toString()
                binding.tvTotalCount.text = stats["total"].toString()
                binding.tvActiveUsers.text = stats["active"].toString()
                binding.tvSystemHealth.text = getString(R.string.system_healthy)

                // Load unverified incidents
                repository.getUnverifiedIncidents().collectLatest { incidents ->
                    adapter.updateData(incidents)
                    binding.tvEmptyState.visibility = if (incidents.isEmpty()) View.VISIBLE else View.GONE

                    // Refresh stats when data changes
                    val updatedStats = repository.getAdminStats()
                    binding.tvPendingCount.text = updatedStats["pending"].toString()
                }
            } catch (e: Exception) {
                context?.let { Toast.makeText(it, "Admin access error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
