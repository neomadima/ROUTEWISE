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
import com.routewise.sa.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
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
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()

        binding.btnRegister.setOnClickListener {
            val email = binding.etRegEmail.text.toString().trim()
            val fullName = binding.etFullName.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()
            val vehicle = binding.spinnerVehicle.text.toString()
            val license = binding.etLicense.text.toString().trim()
            val emergency = binding.etEmergency.text.toString().trim()
            val province = binding.spinnerProvince.text.toString()
            val city = binding.spinnerCity.text.toString()
            val password = binding.etRegPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val isAdmin = binding.cbIsAdmin.isChecked

            if (email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() && fullName.isNotEmpty()) {
                if (password == confirmPassword) {
                    lifecycleScope.launch {
                        try {
                            repository.register(
                                email = email,
                                pass = password,
                                isAdmin = isAdmin,
                                fullName = fullName,
                                phone = phone,
                                vehicle = vehicle,
                                license = license,
                                emergency = emergency,
                                province = province,
                                city = city
                            )
                            Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT).show()
                            if (isAdmin) {
                                findNavController().navigate(R.id.action_registerFragment_to_adminPanelFragment)
                            } else {
                                findNavController().navigate(R.id.action_registerFragment_to_dashboardFragment)
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Registration failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.tvLoginLink.setOnClickListener {
            findNavController().navigateUp()
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
        
        // Set defaults
        binding.spinnerProvince.setText("Gauteng", false)
        updateCitySpinner("Gauteng")
        binding.spinnerVehicle.setText("Sedan", false)
    }

    private fun updateCitySpinner(province: String) {
        val cities = citiesMap[province] ?: emptyArray()
        val cityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, cities)
        binding.spinnerCity.setAdapter(cityAdapter)
        if (cities.isNotEmpty()) binding.spinnerCity.setText(cities[0], false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
