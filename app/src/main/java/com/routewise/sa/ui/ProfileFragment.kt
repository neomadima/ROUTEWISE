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
import com.routewise.sa.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

import com.routewise.sa.MainActivity

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository

    private val provinces = arrayOf(
        "Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", 
        "Limpopo", "Mpumalanga", "Northern Cape", "North West", "Western Cape"
    )

    private val citiesMap = mapOf(
        "Gauteng" to arrayOf("Pretoria", "Johannesburg", "Soweto", "Centurion"),
        "Western Cape" to arrayOf("Cape Town", "Stellenbosch", "Paarl", "George"),
        "KwaZulu-Natal" to arrayOf("Durban", "Pietermaritzburg", "Newcastle", "Umhlanga"),
        "Free State" to arrayOf("Bloemfontein", "Welkom", "Sasolburg"),
        "Eastern Cape" to arrayOf("Gqeberha", "East London", "Mthatha"),
        "Limpopo" to arrayOf("Polokwane", "Mokopane", "Thohoyandou"),
        "Mpumalanga" to arrayOf("Mbombela", "Secunda", "Witbank"),
        "North West" to arrayOf("Mahikeng", "Potchefstroom", "Rustenburg"),
        "Northern Cape" to arrayOf("Kimberley", "Upington")
    )

    private val vehicles = arrayOf("Sedan", "SUV", "Hatchback", "Truck", "Motorcycle", "Delivery Van")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        loadUserData()

        binding.btnUpdateProfile.setOnClickListener {
            updateProfile()
        }

        binding.btnLogout.setOnClickListener {
            repository.logout()
            findNavController().navigate(R.id.action_global_loginFragment)
        }
    }

    private fun setupSpinners() {
        val provinceAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, provinces)
        binding.spinnerProvince.setAdapter(provinceAdapter)
        binding.spinnerProvince.setOnItemClickListener { _, _, _, _ ->
            updateCitySpinner(binding.spinnerProvince.text.toString())
        }

        val vehicleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, vehicles)
        binding.spinnerVehicle.setAdapter(vehicleAdapter)
    }

    private fun updateCitySpinner(province: String) {
        val cities = citiesMap[province] ?: emptyArray()
        val cityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cities)
        binding.spinnerCity.setAdapter(cityAdapter)
        if (cities.isNotEmpty()) binding.spinnerCity.setText(cities[0], false)
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            val user = repository.getCurrentUser() ?: return@launch
            binding.apply {
                etFullName.setText(user.fullName)
                etPhone.setText(user.phoneNumber)
                etLicense.setText(user.licensePlate)
                etEmergency.setText(user.emergencyContact)
                spinnerVehicle.setText(user.vehicleType, false)
                spinnerProvince.setText(user.province, false)
                updateCitySpinner(user.province)
                spinnerCity.setText(user.city, false)
            }
        }
    }

    private fun updateProfile() {
        val name = binding.etFullName.text.toString()
        val phone = binding.etPhone.text.toString()
        val license = binding.etLicense.text.toString()
        val emergency = binding.etEmergency.text.toString()
        val vehicle = binding.spinnerVehicle.text.toString()
        val province = binding.spinnerProvince.text.toString()
        val city = binding.spinnerCity.text.toString()
        val newPass = binding.etNewPassword.text.toString().takeIf { it.isNotBlank() }

        lifecycleScope.launch {
            try {
                repository.updateUserProfile(name, phone, vehicle, license, emergency, province, city, newPass)
                (activity as? MainActivity)?.updateNavHeader()
                Toast.makeText(context, "Profile Updated Successfully", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            } catch (e: Exception) {
                Toast.makeText(context, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
