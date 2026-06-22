package com.routewise.sa

import android.app.Application
import com.routewise.sa.data.local.RouteWiseDatabase
import com.google.android.libraries.places.api.Places

class RouteWiseApplication : Application() {
    val database by lazy { RouteWiseDatabase.getDatabase(this) }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Google Places SDK
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, "AIzaSyAXALm6HyCeqSPR2x0CBTT4XtzPyeK32Nk")
        }
    }
}