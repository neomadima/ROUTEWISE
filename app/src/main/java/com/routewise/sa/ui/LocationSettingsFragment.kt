package com.routewise.sa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.databinding.FragmentLocationSettingsBinding

class LocationSettingsFragment : Fragment() {
    private var _binding: FragmentLocationSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        _binding = FragmentLocationSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSaveSettings.setOnClickListener {
            Toast.makeText(context, "Preferences Saved Successfully", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
