package com.routewise.sa.data

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.routewise.sa.MainActivity
import com.routewise.sa.R
import com.routewise.sa.RouteWiseApplication
import com.routewise.sa.data.local.IncidentEntity
import com.routewise.sa.data.local.UserEntity
import com.routewise.sa.data.local.NotificationLogEntity
import com.routewise.sa.model.Incident
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Formerly FirebaseRepository, now strictly using Room for local storage.
 * Session persistence added via SharedPreferences.
 */
class RouteWiseRepository(private val context: Context) {
    private val dao = (context.applicationContext as RouteWiseApplication).database.dao()
    private val prefs = context.getSharedPreferences("routewise_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private var currentUser: UserEntity? = null
        private const val MAX_ADMINS = 5
        private const val KEY_USER_ID = "logged_in_user_id"
    }

    init {
        // Restore session if available
        val savedId = prefs.getString(KEY_USER_ID, null)
        if (savedId != null && currentUser == null) {
            // Note: Since init isn't suspend, we'll need to fetch carefully or 
            // handle it in a splash screen/LoginFragment check.
            // For this prototype, we'll keep it simple and fetch on first access if null.
        }
    }

    suspend fun getCurrentUser(): UserEntity? {
        if (currentUser == null) {
            val savedId = prefs.getString(KEY_USER_ID, null)
            if (savedId != null) {
                currentUser = dao.getUserById(savedId)
            }
        }
        return currentUser
    }

    suspend fun getUserLocation(): Map<String, Any>? {
        val user = getCurrentUser() ?: return null
        return mapOf(
            "province" to user.province,
            "city" to user.city,
            "isAdmin" to user.isAdmin,
        )
    }

    suspend fun login(email: String, pass: String): UserEntity {
        val cleanEmail = email.trim().lowercase()
        val user = dao.getUserByEmail(cleanEmail) ?: throw Exception("User with email $cleanEmail not found")
        if (user.password != pass.trim()) throw Exception("Incorrect password")
        
        saveSession(user)
        return user
    }

    private fun saveSession(user: UserEntity) {
        currentUser = user
        prefs.edit().putString(KEY_USER_ID, user.uid).apply()
    }

    suspend fun register(
        email: String,
        pass: String,
        isAdmin: Boolean = false,
        fullName: String = "",
        phone: String = "",
        vehicle: String = "Sedan",
        license: String = "",
        emergency: String = "",
        province: String = "Gauteng",
        city: String = "Pretoria"
    ) {
        val cleanEmail = email.trim().lowercase()
        if (dao.getUserByEmail(cleanEmail) != null) throw Exception("User already exists")
        
        if (isAdmin) {
            val adminCount = dao.getAdminCount()
            if (adminCount >= MAX_ADMINS) {
                throw Exception("Maximum of $MAX_ADMINS admins allowed")
            }
        }

        val user = UserEntity(
            uid = UUID.randomUUID().toString(),
            email = cleanEmail,
            password = pass.trim(),
            fullName = fullName,
            phoneNumber = phone,
            vehicleType = vehicle,
            licensePlate = license,
            emergencyContact = emergency,
            province = province,
            city = city,
            isAdmin = isAdmin
        )
        dao.insertUser(user)
        saveSession(user)
    }

    suspend fun updateUserProfile(
        fullName: String,
        phone: String,
        vehicle: String,
        license: String,
        emergency: String,
        province: String,
        city: String,
        newPass: String? = null
    ) {
        val user = getCurrentUser() ?: return
        val updatedUser = user.copy(
            fullName = fullName,
            phoneNumber = phone,
            vehicleType = vehicle,
            licensePlate = license,
            emergencyContact = emergency,
            province = province,
            city = city,
            password = newPass ?: user.password
        )
        dao.insertUser(updatedUser)
        saveSession(updatedUser)
    }

    fun getIncidents(province: String, city: String): Flow<List<Incident>> {
        return dao.getAllIncidentsInLocation(province, city).map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun getAllIncidents(): Flow<List<Incident>> {
        return dao.getAllIncidents().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun reportIncident(incident: Incident) {
        val user = getCurrentUser()
        val entity = IncidentEntity(
            id = UUID.randomUUID().toString(),
            type = incident.type,
            description = incident.description,
            roadName = incident.roadName,
            province = incident.province,
            city = incident.city,
            reporterId = user?.uid ?: "Anonymous",
            status = if (incident.verified) "approved" else "pending",
            isSimulated = incident.isSimulated,
            timestamp = System.currentTimeMillis(),
            latitude = incident.latitude,
            longitude = incident.longitude
        )
        dao.insertIncident(entity)

        // Increment report count for user
        user?.let {
            val updatedUser = it.copy(totalReports = it.totalReports + 1)
            dao.insertUser(updatedUser)
            saveSession(updatedUser)
        }
    }

    fun getUnverifiedIncidents(): Flow<List<Incident>> {
        return dao.getUnverifiedIncidents().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun verifyIncident(id: String) {
        val incident = dao.getIncidentById(id) ?: return
        dao.insertIncident(incident.copy(status = "approved"))

        // Trigger notification simulation
        triggerLocalNotification(
            "Incident Approved",
            "${incident.type} on ${incident.roadName} has been verified and is now live.",
            incident.id
        )

        // Award points to the reporter
        val reporter = dao.getUserById(incident.reporterId)
        reporter?.let {
            val updatedReporter = it.copy(impactPoints = it.impactPoints + 50)
            dao.insertUser(updatedReporter)
            // If the current user is the reporter, update the local session too
            if (currentUser?.uid == it.uid) {
                saveSession(updatedReporter)
            }
        }
    }

    suspend fun updateIncident(incident: Incident) {
        val existing = dao.getIncidentById(incident.id) ?: return
        val entity = IncidentEntity(
            id = incident.id,
            type = incident.type,
            description = incident.description,
            roadName = incident.roadName,
            province = incident.province,
            city = incident.city,
            reporterId = existing.reporterId,
            status = existing.status,
            isSimulated = existing.isSimulated,
            timestamp = existing.timestamp,
            latitude = incident.latitude,
            longitude = incident.longitude
        )
        dao.insertIncident(entity)
    }

    private fun triggerLocalNotification(title: String, body: String, incidentId: String? = null) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "road_alerts"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Road Alerts", NotificationManager.IMPORTANCE_HIGH)
                notificationManager.createNotificationChannel(channel)
            }

            // Log the notification in the database as per ERD
            val user = currentUser
            if (user != null && incidentId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    dao.insertNotificationLog(NotificationLogEntity(
                        logId = UUID.randomUUID().toString(),
                        userId = user.uid,
                        incidentId = incidentId,
                        deliveryStatus = "sent"
                    ))
                }
            }

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            android.util.Log.e("RouteWiseRepository", "Notification error: ${e.message}")
        }
    }

    suspend fun deleteIncident(id: String) {
        dao.deleteIncident(id)
    }

    suspend fun deleteAllIncidents() {
        dao.deleteAllIncidents()
    }

    suspend fun getAdminStats(): Map<String, Int> {
        return mapOf(
            "pending" to dao.getPendingCount(),
            "total" to dao.getTotalIncidentCount(),
            "active" to dao.getUserCount()
        )
    }

    fun logout() {
        currentUser = null
        prefs.edit().remove(KEY_USER_ID).apply()
    }

    private fun IncidentEntity.toModel() = Incident(
        id = id,
        type = type,
        description = description,
        roadName = roadName,
        province = province,
        city = city,
        reporterEmail = "UserID: $reporterId", 
        verified = status == "approved",
        isSimulated = isSimulated,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude
    )
}
