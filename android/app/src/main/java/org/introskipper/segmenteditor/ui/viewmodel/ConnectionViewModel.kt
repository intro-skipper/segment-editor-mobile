/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.data.repository.AuthRepository
import org.introskipper.segmenteditor.storage.SecurePreferences
import java.net.URL

data class ConnectionState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverUrl: String = "",
    val isValidUrl: Boolean = false,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val isDiscovering: Boolean = false,
    val selectedServer: DiscoveredServer? = null,
    val serverValidated: Boolean = false
)

data class DiscoveredServer(
    val name: String,
    val url: String,
    val version: String?,
    val id: String
)

class ConnectionViewModel(
    private val securePreferences: SecurePreferences,
    private val authRepository: AuthRepository,
    private val apiService: JellyfinApiService
) : ViewModel() {
    
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()
    
    fun onServerUrlChange(url: String) {
        _state.value = _state.value.copy(
            serverUrl = url,
            isValidUrl = isValidServerUrl(url),
            error = null
        )
    }
    
    fun validateAndSaveServer() {
        val url = _state.value.serverUrl.trim()
        if (!isValidServerUrl(url)) {
            _state.value = _state.value.copy(error = "Please enter a valid server URL")
            return
        }
        
        _state.value = _state.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val normalizedUrl = normalizeServerUrl(url)
                apiService.updateBaseUrl(normalizedUrl)
                authRepository.getPublicServerInfoResult().fold(
                    onSuccess = { serverInfo ->
                        securePreferences.saveServerUrl(normalizedUrl)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            serverValidated = true,
                            selectedServer = DiscoveredServer(
                                name = serverInfo.serverName,
                                url = normalizedUrl,
                                version = serverInfo.version,
                                id = serverInfo.id
                            )
                        )
                    },
                    onFailure = { error ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = "Failed to connect to server: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Connection error: ${e.message}"
                )
            }
        }
    }
    
    fun discoverServers() {
        _state.value = _state.value.copy(
            isDiscovering = true,
            discoveredServers = emptyList(),
            error = null
        )
        
        viewModelScope.launch {
            try {
                val servers = performServerDiscovery()
                _state.value = _state.value.copy(
                    isDiscovering = false,
                    discoveredServers = servers
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isDiscovering = false,
                    error = "Discovery failed: ${e.message}"
                )
            }
        }
    }
    
    fun selectDiscoveredServer(server: DiscoveredServer) {
        securePreferences.saveServerUrl(server.url)
        apiService.updateBaseUrl(server.url)
        _state.value = _state.value.copy(
            selectedServer = server,
            serverUrl = server.url,
            serverValidated = true
        )
    }
    
    private fun isValidServerUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val normalized = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            URL(normalized)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun normalizeServerUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }
    
    private suspend fun performServerDiscovery(): List<DiscoveredServer> {
        val servers = mutableListOf<DiscoveredServer>()
        
        // Common local addresses to try
        val commonAddresses = listOf(
            "jellyfin.local",
            "localhost:8096",
            "127.0.0.1:8096"
        )
        
        // Try to discover servers in parallel for better performance
        val results = commonAddresses.map { address ->
            viewModelScope.async {
                try {
                    val url = normalizeServerUrl(address)
                    apiService.updateBaseUrl(url)
                    val result = authRepository.getPublicServerInfoResult()
                    result.getOrNull()?.let { info ->
                        DiscoveredServer(
                            name = info.serverName,
                            url = url,
                            version = info.version,
                            id = info.id
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }.awaitAll()
        
        // Filter out nulls and add to list
        servers.addAll(results.filterNotNull())
        
        return servers
    }
    
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}

class ConnectionViewModelFactory(
    private val securePreferences: SecurePreferences,
    private val authRepository: AuthRepository,
    private val apiService: JellyfinApiService
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConnectionViewModel::class.java)) {
            return ConnectionViewModel(securePreferences, authRepository, apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
