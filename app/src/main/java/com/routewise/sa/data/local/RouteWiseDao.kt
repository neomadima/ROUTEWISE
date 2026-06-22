package com.routewise.sa.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteWiseDao {
    // User operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE uid = :uid")
    suspend fun getUserById(uid: String): UserEntity?
    
    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT COUNT(*) FROM users WHERE isAdmin = 1")
    suspend fun getAdminCount(): Int

    @Query("SELECT * FROM incidents WHERE id = :id LIMIT 1")
    suspend fun getIncidentById(id: String): IncidentEntity?

    // Incident operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Query("SELECT * FROM incidents ORDER BY timestamp DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE status = 'approved' ORDER BY timestamp DESC")
    fun getAllVerifiedIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE province = :province AND city = :city AND status = 'approved' ORDER BY timestamp DESC")
    fun getVerifiedIncidents(province: String, city: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE province = :province AND city = :city ORDER BY timestamp DESC")
    fun getAllIncidentsInLocation(province: String, city: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE status = 'pending' ORDER BY timestamp DESC")
    fun getUnverifiedIncidents(): Flow<List<IncidentEntity>>

    @Update
    suspend fun updateIncident(incident: IncidentEntity)

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteIncident(id: String)

    @Query("DELETE FROM incidents")
    suspend fun deleteAllIncidents()

    @Query("SELECT COUNT(*) FROM incidents WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getTotalIncidentCount(): Int

    // Notification Log operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationLog(log: NotificationLogEntity)

    @Query("SELECT * FROM notification_logs WHERE userId = :userId ORDER BY sentAt DESC")
    fun getLogsForUser(userId: String): Flow<List<NotificationLogEntity>>
}