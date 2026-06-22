package com.routewise.sa.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.routewise.sa.databinding.ItemIncidentAdminBinding
import com.routewise.sa.model.Incident

class AdminIncidentAdapter(
    private var incidents: List<Incident>,
    private val onVerify: (String) -> Unit,
    private val onEdit: (Incident) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<AdminIncidentAdapter.AdminViewHolder>() {

    class AdminViewHolder(val binding: ItemIncidentAdminBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val binding = ItemIncidentAdminBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AdminViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        val incident = incidents[position]
        holder.binding.apply {
            adminIncidentType.text = "NEW ${incident.type.uppercase()}"
            adminIncidentDetails.text = "${incident.roadName}, ${incident.city} (${incident.province})"
            adminIncidentDescription.text = if (incident.description.isNullOrBlank()) "No description provided." else incident.description
            
            btnVerify.setOnClickListener { onVerify(incident.id) }
            btnEdit.setOnClickListener { onEdit(incident) }
            btnDelete.setOnClickListener { onDelete(incident.id) }
        }
    }

    override fun getItemCount() = incidents.size

    fun updateData(newIncidents: List<Incident>) {
        incidents = newIncidents
        notifyDataSetChanged()
    }
}
