package com.routewise.sa.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.routewise.sa.databinding.FragmentHelpSupportBinding

import android.content.Intent
import android.net.Uri
import android.widget.ArrayAdapter
import android.widget.Toast
import com.routewise.sa.R

class HelpSupportFragment : Fragment() {

    private var _binding: FragmentHelpSupportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpSupportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupIssueTypeSpinner()

        binding.btnEmailSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:support@routewise.co.za")
                putExtra(Intent.EXTRA_SUBJECT, "Support Request - RouteWise")
            }
            startActivity(Intent.createChooser(intent, "Send Email"))
        }

        binding.btnCallSupport.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:+27111234567")
            }
            startActivity(intent)
        }

        binding.btnSubmitProblem.setOnClickListener {
            val type = binding.spinnerIssueType.text.toString()
            val description = binding.etProblemDescription.text.toString()

            if (type.isEmpty() || description.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            } else {
                // Simulate sending report
                Toast.makeText(requireContext(), "Report submitted. Thank you!", Toast.LENGTH_LONG).show()
                binding.etProblemDescription.text?.clear()
                binding.spinnerIssueType.setText("", false)
            }
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            openWebPage("https://routewise.co.za/privacy")
        }

        binding.btnTermsOfService.setOnClickListener {
            openWebPage("https://routewise.co.za/terms")
        }

        binding.btnOpenSource.setOnClickListener {
            Toast.makeText(requireContext(), "Opening Open Source Licenses...", Toast.LENGTH_SHORT).show()
        }

        binding.tvTutorial1.setOnClickListener {
            Toast.makeText(requireContext(), "Playing: How to set a destination...", Toast.LENGTH_SHORT).show()
        }

        binding.tvTutorial2.setOnClickListener {
            Toast.makeText(requireContext(), "Playing: How to hide the route card...", Toast.LENGTH_SHORT).show()
        }

        binding.tvTutorial3.setOnClickListener {
            Toast.makeText(requireContext(), "Playing: How to start voice navigation...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupIssueTypeSpinner() {
        val issueTypes = arrayOf("Wrong Address", "Routing Error", "App Crash/Bug", "Feature Suggestion", "Other")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, issueTypes)
        binding.spinnerIssueType.setAdapter(adapter)
    }

    private fun openWebPage(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}