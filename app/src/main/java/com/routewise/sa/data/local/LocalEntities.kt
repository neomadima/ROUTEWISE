package com.routewise.sa.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val email: String,
    val password: String = "password", // Matches passwordHash in ERD
    val fullName: String = "",
    val phoneNumber: String = "",
    val vehicleType: String = "Sedan",
    val licensePlate: String = "",
    val emergencyContact: String = "",
    val province: String = "Gauteng",
    val city: String = "Pretoria",
    val locationConsent: Boolean = true,
    val notificationEnabled: Boolean = true,
    val fcmToken: String? = null,
    val isAdmin: Boolean = false,
    val registrationDate: Long = System.currentTimeMillis(),
    val impactPoints: Int = 0,
    val totalReports: Int = 0
)

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val type: String, // Enum in ERD, string here for flexibility
    val description: String,
    val roadName: String,
    val province: String,
    val city: String,
    val reporterId: String, // Matches reportedByUserId in ERD
    val status: String = "pending", // pending, approved, deleted
    val isSimulated: Boolean = false,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double
)

@Entity(tableName = "notification_logs")
data class NotificationLogEntity(
    @PrimaryKey val logId: String,
    val userId: String,
    val incidentId: String,
    val sentAt: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "sent"
)