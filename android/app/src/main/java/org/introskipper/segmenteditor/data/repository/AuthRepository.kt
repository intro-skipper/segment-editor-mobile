package org.introskipper.segmenteditor.data.repository

import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.model.AuthenticationResult
import org.introskipper.segmenteditor.data.model.PublicSystemInfo
import org.introskipper.segmenteditor.data.model.ServerInfo
import org.introskipper.segmenteditor.data.model.User
import retrofit2.Response

/**
 * Repository for managing authentication operations.
 * Wraps the JellyfinApiService authentication-related calls.
 */
class AuthRepository(private val apiService: JellyfinApiService) {
    
    /**
     * Authenticates a user with username and password
     * @param username User's username
     * @param password User's password
     * @param deviceId Unique device identifier
     * @param deviceName Human-readable device name
     * @param appVersion Application version
     * @return Response containing authentication result
     */
    suspend fun authenticate(
        username: String,
        password: String,
        deviceId: String,
        deviceName: String,
        appVersion: String
    ): Response<AuthenticationResult> {
        return apiService.authenticate(username, password, deviceId, deviceName, appVersion)
    }
    
    /**
     * Gets server information (requires authentication)
     * @return Response containing server info
     */
    suspend fun getServerInfo(): Response<ServerInfo> {
        return apiService.getSystemInfo()
    }
    
    /**
     * Gets public server information (no authentication required)
     * @return Response containing public server info
     */
    suspend fun getPublicServerInfo(): Response<PublicSystemInfo> {
        return apiService.getPublicSystemInfo()
    }
    
    /**
     * Gets all users (requires authentication)
     * @return Response containing list of users
     */
    suspend fun getUsers(): Response<List<User>> {
        return apiService.getUsers()
    }
    
    /**
     * Gets user information by ID
     * @param userId The user ID
     * @return Response containing user info
     */
    suspend fun getUserById(userId: String): Response<User> {
        return apiService.getUserById(userId)
    }
    
    /**
     * Validates the current API key
     * @return True if the API key is valid
     */
    suspend fun validateApiKey(): Boolean {
        return apiService.validateApiKey()
    }
    
    /**
     * Authenticates and returns a Result
     */
    suspend fun authenticateResult(
        username: String,
        password: String,
        deviceId: String,
        deviceName: String,
        appVersion: String
    ): Result<AuthenticationResult> {
        return try {
            val response = authenticate(username, password, deviceId, deviceName, appVersion)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Invalid username or password"
                    403 -> "Access forbidden"
                    404 -> "Server not found"
                    else -> "Authentication failed: ${response.code()} ${response.message()}"
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets server info and returns a Result
     */
    suspend fun getServerInfoResult(): Result<ServerInfo> {
        return try {
            val response = getServerInfo()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get server info: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets public server info and returns a Result
     */
    suspend fun getPublicServerInfoResult(): Result<PublicSystemInfo> {
        return try {
            val response = getPublicServerInfo()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get public server info: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets user by ID and returns a Result
     */
    suspend fun getUserByIdResult(userId: String): Result<User> {
        return try {
            val response = getUserById(userId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get user: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Gets all users and returns a Result
     */
    suspend fun getUsersResult(): Result<List<User>> {
        return try {
            val response = getUsers()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get users: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates API key and returns a Result
     */
    suspend fun validateApiKeyResult(): Result<Boolean> {
        return try {
            val isValid = validateApiKey()
            Result.success(isValid)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
