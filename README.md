# ROUTEWISE

# RouteWise: The Community-Driven Road Safety Ecosystem 🇿🇦

**RouteWise** is a specialized navigation and road safety platform tailored for the unique challenges of South African roads. By merging real-time GPS navigation with a crowdsourced hazard reporting network, RouteWise transforms individual drivers into a synchronized community working together to make every journey safer and more predictable.

---

## 🌟 Key Features

### 🚗 The Driver Experience
*   **Precision Navigation:** Real-time turn-by-turn routing powered by the Google Maps SDK.
*   **Dynamic Speedometer & Safety:** 
    *   Real-time speed tracking with visual progress indicators.
    *   **Live Speed Limit Integration:** Automatically fetches legal limits for your current road and triggers a **Red Alert** if you exceed them.
*   **"On-The-Way" Smart Stops:**
    *   Quick-search for Gas Stations, Food, Coffee, ATMs, and Parking.
    *   **Detour Time Intelligence:** The app calculates and displays exactly how many minutes each stop adds to your total trip time before you select it.
*   **Adaptive Map Visualization:**
    *   **Day/Night Modes:** Automatic theme switching based on time of day to ensure driver focus.
    *   **Live Traffic Layer:** Real-time congestion data overlay.
    *   **Incident Clustering:** Clean UI that groups multiple nearby hazards into smart clusters.

### ⚠️ Community Reporting Network
*   **Comprehensive Hazard Tagging:** Report anything from Potholes and Broken Robots to Accidents and Crime Hotspots.
*   **Precise Geolocation:** Uses Google Places Autocomplete to ensure reports are pinned to the exact road or intersection.
*   **Impact Rewards:** Earn **"Impact Points"** for every report you make. Progress from a "Road Scout" to an elite community contributor.

### 🛡️ Admin & Moderation Command Center
*   **Centralized Oversight:** A dedicated dashboard for authorities to monitor system health and active user counts.
*   **Incident Validation:** A secure workflow to **Approve**, **Edit**, or **Dismiss** user-submitted reports.
*   **Real-Time Statistics:** Track pending vs. total incidents to identify high-risk zones in the city.
*   **Automated Notification Logs:** Every verified incident triggers a system-wide log for transparency and tracking.

### 👤 Profile & Fleet Management
*   **User Personas:** Secure profiles storing vehicle types (Sedan, SUV, Truck, etc.) and license plates.
*   **Emergency Integration:** Quick-access storage for Emergency Contact numbers for rapid response.
*   **Regional Localization:** Set your default Province and City to receive tailored local alerts.

---

## 🏗️ System Architecture (3-Tier)

RouteWise follows a robust **3-Tier Architecture** to ensure scalability and performance:

1.  **Presentation Tier (Frontend):** 
    *   Built with **Kotlin** and **Material Design 3**.
    *   Utilizes **ViewBinding** for efficient UI interaction and **Navigation Component** for a seamless user flow.
2.  **Application Tier (Logic):**
    *   **MVVM Pattern:** Separation of concerns using ViewModels and LiveData.
    *   **Repository Pattern:** A centralized `RouteWiseRepository` that handles all business logic and data routing.
    *   **Custom Utils:** Proprietary logic for detour calculations and speed limit monitoring.
3.  **Data Tier (Persistence):**
    *   **Room Database:** High-speed local SQL storage for offline-first capabilities.
    *   **Session Management:** Encrypted SharedPreferences for secure user persistence.
    *   **External APIs:** Google Directions, Places, and Maps API integration.

---

## 🚀 The Future: Firebase Cloud Integration
To evolve into a national infrastructure, the next phase of RouteWise includes:
*   **Cloud Firestore:** For real-time hazard syncing across thousands of concurrent users.
*   **Firebase Auth:** Enterprise-grade security with Google Sign-In and MFA.
*   **Firebase Cloud Messaging (FCM):** To push instant emergency alerts to drivers within a specific radius of danger.
*   **Cloud Functions:** To automate the "Impact Point" reward system and data cleanup.
*   **Firebase Hosting:** A desktop-based web portal for city-wide traffic management and data visualization.

---

## 🛠️ Tech Stack
*   **Language:** Kotlin
*   **Architecture:** MVVM + Clean Architecture
*   **Database:** Room SQL
*   **UI:** XML / Material 3
*   **Maps:** Google Maps SDK / Places API / Directions API
*   **Concurrency:** Kotlin Coroutines & Flow

---

## 🏁 Getting Started
1. Clone the repository.
2. Add your `MAPS_API_KEY` to `local.properties`.
3. Open in Android Studio Ladybug+ and sync Gradle.
4. Run on any device with API 23 (Android 6.0) or higher.

*"Empowering South Africans to navigate with confidence and contribute to a safer future."*
