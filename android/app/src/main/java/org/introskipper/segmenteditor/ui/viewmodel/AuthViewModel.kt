package org.introskipper.segmenteditor.ui.viewmodel

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.data.model.User
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import java.util.UUID

data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val authMethod: AuthMethod = AuthMethod.API_KEY,
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val isAuthenticated: Boolean = false,
    val user: User? = null,
    val serverName: String = ""
)

enum class AuthMethod {
    API_KEY,
    USERNAME_PASSWORD
}

class AuthViewModel(
    private val securePreferences: SecurePreferences,
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()
    
    fun setAuthMethod(method: AuthMethod) {
        _state.value = _state.value.copy(authMethod = method, error = null)
    }
    
    fun onApiKeyChange(apiKey: String) {
        _state.value = _state.value.copy(apiKey = apiKey, error = null)
    }
    
    fun onUsernameChange(username: String) {
        _state.value = _state.value.copy(username = username, error = null)
    }
    
    fun onPasswordChange(password: String) {
        _state.value = _state.value.copy(password = password, error = null)
    }
    
    fun authenticate() {
        when (_state.value.authMethod) {
            AuthMethod.API_KEY -> authenticateWithApiKey()
            AuthMethod.USERNAME_PASSWORD -> authenticateWithCredentials()
        }
    }
    
    private fun authenticateWithApiKey() {
        val apiKey = _state.value.apiKey.trim()
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter an API key")
            return
        }
        
        _state.value = _state.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                // Save API key first so it's available for the request
                securePreferences.saveApiKey(apiKey)
                
                val result = authRepository.validateApiKeyResult()
                result.fold(
                    onSuccess = { isValid ->
                        if (isValid) {
                            // Get server info to verify connection
                            authRepository.getServerInfoResult().fold(
                                onSuccess = { serverInfo ->
                                    _state.value = _state.value.copy(
                                        isLoading = false,
                                        isAuthenticated = true,
                                        serverName = serverInfo.serverName
                                    )
                                },
                                onFailure = { error ->
                                    securePreferences.clearAuthentication()
                                    _state.value = _state.value.copy(
                                        isLoading = false,
                                        error = "Failed to verify server: ${error.message}"
                                    )
                                }
                            )
                        } else {
                            securePreferences.clearAuthentication()
                            _state.value = _state.value.copy(
                                isLoading = false,
                                error = "Invalid API key"
                            )
                        }
                    },
                    onFailure = { error ->
                        securePreferences.clearAuthentication()
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Authentication failed: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                securePreferences.clearAuthentication()
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Authentication error: ${e.message}"
                )
            }
        }
    }
    
    private fun authenticateWithCredentials() {
        val username = _state.value.username.trim()
        val password = _state.value.password
        
        if (username.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter a username")
            return
        }
        
        if (password.isBlank()) {
            _state.value = _state.value.copy(error = "Please enter a password")
            return
        }
        
        _state.value = _state.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val deviceId = getDeviceId()
                val deviceName = getDeviceName()
                val appVersion = "1.0.0"
                
                val result = authRepository.authenticateResult(
                    username = username,
                    password = password,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    appVersion = appVersion
                )
                
                result.fold(
                    onSuccess = { authResult ->
                        // Save authentication credentials
                        securePreferences.saveApiKey(authResult.accessToken)
                        securePreferences.saveUserId(authResult.user.id)
                        securePreferences.saveUsername(authResult.user.name)
                        
                        _state.value = _state.value.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            user = authResult.user,
                            serverName = "" // Will be populated from server info
                        )
                    },
                    onFailure = { error ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.message ?: "Authentication failed"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Authentication error: ${e.message}"
                )
            }
        }
    }
    
    fun loadServerName() {
        viewModelScope.launch {
            try {
                authRepository.getServerInfoResult().fold(
                    onSuccess = { serverInfo ->
                        _state.value = _state.value.copy(serverName = serverInfo.serverName)
                    },
                    onFailure = { /* Ignore errors when loading server name */ }
                )
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }
    
    private fun getDeviceId(): String {
        // Get or generate a persistent device ID
        val savedDeviceId = securePreferences.getDeviceId()
        if (!savedDeviceId.isNullOrBlank()) {
            return savedDeviceId
        }
        
        val newDeviceId = UUID.randomUUID().toString()
        securePreferences.saveDeviceId(newDeviceId)
        return newDeviceId
    }
    
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

class AuthViewModelFactory(
    private val securePreferences: SecurePreferences,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            return AuthViewModel(securePreferences, authRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
