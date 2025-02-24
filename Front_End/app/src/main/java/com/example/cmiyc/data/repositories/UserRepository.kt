package com.example.cmiyc.repositories

import com.example.cmiyc.api.ApiClient
import com.example.cmiyc.api.LocationUpdateRequest
import com.example.cmiyc.data.Log
import com.example.cmiyc.data.User
import com.mapbox.geojson.Point
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.seconds

object UserRepository {
    private val api = ApiClient.apiService
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _logs = MutableStateFlow<List<Log>>(emptyList())
    val logs: StateFlow<List<Log>> = _logs

    // Queue for pending location updates
    private val locationUpdateQueue = ConcurrentLinkedQueue<LocationUpdateRequest>()

    // Flag to track if update job is running
    private var isUpdating = false

    init {
        startLocationUpdateWorker()
    }

    private fun startLocationUpdateWorker() {
        scope.launch {
            while (isActive) {
                if (isAuthenticated() && locationUpdateQueue.isNotEmpty()) {
                    processLocationUpdates()
                }
                delay(1.seconds)
            }
        }
    }

    private suspend fun processLocationUpdates() {
        if (isUpdating) return
        isUpdating = true

        try {
            val userId = getCurrentUserId()
            val latestUpdate = locationUpdateQueue.poll() ?: return

            try {
                val response = api.updateUserLocation(userId, latestUpdate)
                if (response.isSuccessful) {
                    _currentUser.value = _currentUser.value?.copy(
                        currentLocation = Point.fromLngLat(
                            latestUpdate.longitude,
                            latestUpdate.latitude
                        ),
                        lastLocationUpdate = latestUpdate.timestamp
                    )
                    locationUpdateQueue.clear()
                } else {
                    locationUpdateQueue.offer(latestUpdate)
                }
            } catch (e: Exception) {
                locationUpdateQueue.offer(latestUpdate)
                throw e
            }
        } finally {
            isUpdating = false
        }
    }

    fun isAuthenticated(): Boolean {
        return _currentUser.value != null
    }

    fun getCurrentUserId(): String {
        return currentUser.value?.userId
            ?: throw Exception("User not authenticated")
    }

    fun setCurrentUser(credentials: User) {
        _currentUser.value = User(credentials.userId, credentials.email, credentials.displayName, credentials.photoUrl)
    }

    fun updateUserLocation(location: Point) {
        val request = LocationUpdateRequest(
            longitude = location.longitude(),
            latitude = location.latitude()
        )
        locationUpdateQueue.offer(request)
    }

    suspend fun registerUser(user: User) {
        api.registerUser(user.userId, user)
    }

    suspend fun broadcastMessage(activity: String) {
        val location = _currentUser.value?.currentLocation
            ?: throw Exception("User not authenticated")
        val timestamp = System.currentTimeMillis()
        val userId = getCurrentUserId()
        api.broadcastMessage(userId, activity, location, timestamp)
    }

    fun clearCurrentUser() {
        _currentUser.value = null
    }

    suspend fun refreshLogs() {
        try {
            val userId = _currentUser.value?.userId ?: return
            val updatedLogs = api.getLogs(userId)
            _logs.value = updatedLogs
        } catch (e: Exception) {
            throw e
        }
    }

    fun getLogs(): List<Log> {
        return _logs.value
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                clearCurrentUser()
            } catch (e: Exception) {
                throw e
            }
        }
    }

    fun cleanup() {
        scope.cancel()
    }
}