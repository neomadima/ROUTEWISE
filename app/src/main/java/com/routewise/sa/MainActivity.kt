package com.routewise.sa

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.routewise.sa.databinding.ActivityMainBinding
import com.routewise.sa.data.RouteWiseRepository
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import com.google.android.libraries.places.api.Places
import com.routewise.sa.utils.RouteUtils

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { RouteWiseRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Places SDK
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, RouteUtils.API_KEY)
        }

        // enableEdgeToEdge() - Commented out to prevent crash on emulators
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        /* ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        } */

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as androidx.navigation.fragment.NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
        binding.sideNavigation.setupWithNavController(navController)

        // Sync header data
        updateNavHeader()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavVisibility(destination.id)
            // Lock drawer for login/register
            if ((destination.id == R.id.loginFragment) || (destination.id == R.id.registerFragment)) {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        }

        binding.sideNavigation.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_logout -> {
                    repository.logout()
                    navController.navigate(R.id.loginFragment)
                    binding.drawerLayout.closeDrawers()
                    true
                }
                else -> {
                    val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                    if (handled) binding.drawerLayout.closeDrawers()
                    handled
                }
            }
        }

        // Removed global fabReport as it is now fragment-specific to avoid blocking UI
        /*
        binding.fabReport.setOnClickListener {
            navController.navigate(R.id.reportIncidentFragment)
        }
        */
    }

    fun updateNavHeader() {
        val headerView = binding.sideNavigation.getHeaderView(0)
        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            headerView.findViewById<TextView>(R.id.tvUserEmail).text = user?.email ?: "Guest User"
            headerView.findViewById<TextView>(R.id.tvUserRank).text = if (user?.isAdmin == true) "Admin" else "Road Scout"
        }
    }

    private fun updateBottomNavVisibility(destinationId: Int) {
        lifecycleScope.launch {
            val user = repository.getCurrentUser()
            val isAdmin = user?.isAdmin ?: false
            
            // Hide Admin menu item if not an admin
            binding.bottomNavigation.menu.findItem(R.id.adminPanelFragment).isVisible = isAdmin
            // Hide Report menu item if admin (they use the Moderation tab)
            binding.bottomNavigation.menu.findItem(R.id.reportIncidentFragment).isVisible = !isAdmin

            when (destinationId) {
                R.id.loginFragment, R.id.registerFragment -> {
                    binding.bottomNavigation.visibility = android.view.View.GONE
                }
                else -> {
                    binding.bottomNavigation.visibility = android.view.View.VISIBLE
                }
            }
        }
    }
}
