package com.routewise.sa.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.routewise.sa.databinding.ItemIncidentBinding
import com.routewise.sa.model.Incident
import java.text.SimpleDateFormat
import java.util.*

class IncidentAdapter(
    private var incidents: List<Incident>,
    private val onDeleteClick: (Incident) -> Unit = {},
    private val onNavigateClick: (Incident) -> Unit = {}
) : RecyclerView.Adapter<IncidentAdapter.IncidentViewHolder>() {

    class IncidentViewHolder(val binding: ItemIncidentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncidentViewHolder {
        val binding = ItemIncidentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IncidentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IncidentViewHolder, position: Int) {
        val incident = incidents[position]
        holder.binding.apply {
            tvProvince.text = incident.province
            tvRoadLocation.text = incident.roadName
            
            tvRouteBadge.text = incident.roadName.split(" ").firstOrNull() ?: "Route"
            tvDirectionBadge.text = if (position % 2 == 0) "Inbound" else "Outbound"
            tvCategoryLabel.text = "${incident.type}s And Incidents"
            
            tvStatusBox.text = when {
                incident.description.contains("closed", ignoreCase = true) -> "Lane Closed"
                incident.description.contains("shoulder", ignoreCase = true) -> "Shoulder Closed"
                else -> "Active Incident"
            }

            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeString = incident.timestamp?.let { sdf.format(Date(it)) } ?: "08:05:13"
            tvTimeBadge.text = timeString
            
            tvSeverityBadge.text = when (incident.type.lowercase()) {
                "accident" -> "Major"
                "pothole" -> "Minor"
                else -> "Moderate"
            }
            
            tvReporterEmail.text = "by: ${incident.reporterEmail.ifBlank { "System" }}"
            
            tvFullDescription.text = "REPORT-${incident.id.take(8).uppercase()}: ${incident.description}"

            btnNavigate.setOnClickListener {
                onNavigateClick(incident)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(incident)
            }
        }
    }

    override fun getItemCount() = incidents.size

    fun updateData(newIncidents: List<Incident>) {
        incidents = newIncidents
        notifyDataSetChanged()
    }
}
