package com.routewise.sa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.routewise.sa.R
import com.routewise.sa.data.RouteWiseRepository
import com.routewise.sa.databinding.FragmentLoginBinding
import android.widget.ArrayAdapter
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: RouteWiseRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        repository = RouteWiseRepository(requireContext())
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val roles = arrayOf("User", "Admin")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
        binding.spinnerRole.setAdapter(adapter)

        lifecycleScope.launch {
            if (repository.getCurrentUser() != null) {
                navigateToDashboard()
            }
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val selectedRole = binding.spinnerRole.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        val user = repository.login(email, password)
                        
                        // Check if the selected role matches the user's actual role
                        if ((selectedRole == "Admin" && !user.isAdmin) || (selectedRole == "User" && user.isAdmin)) {
                            val actualRole = if (user.isAdmin) "Admin" else "User"
                            Toast.makeText(context, "This account is registered as $actualRole. Please select the correct role.", Toast.LENGTH_LONG).show()
                            repository.logout()
                            return@launch
                        }

                        navigateToDashboard()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Login failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.btnSkipLogin.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
        }
    }

    private fun navigateToDashboard() {
        if (!isAdded) return
        lifecycleScope.launch {
            try {
                val userData = repository.getUserLocation()
                val isAdmin = userData?.get("isAdmin") as? Boolean ?: false

                if (isAdded) {
                    if (isAdmin) {
                        findNavController().navigate(R.id.action_loginFragment_to_adminPanelFragment)
                    } else {
                        findNavController().navigate(R.id.action_loginFragment_to_dashboardFragment)
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    Toast.makeText(context, "Navigation error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
