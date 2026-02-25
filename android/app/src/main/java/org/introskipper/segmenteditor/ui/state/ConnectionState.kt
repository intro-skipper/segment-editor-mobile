/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.ui.state

/**
 * Represents the current connection state to the Jellyfin server
 */
sealed class ConnectionState {
    /**
     * Not yet attempted to connect
     */
    data object Idle : ConnectionState()
    
    /**
     * Currently attempting to connect
     */
    data object Connecting : ConnectionState()
    
    /**
     * Successfully connected to the server
     */
    data class Connected(
        val serverUrl: String,
        val serverName: String,
        val serverVersion: String
    ) : ConnectionState()
    
    /**
     * Connection failed
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : ConnectionState()
    
    /**
     * Currently authenticated and have a valid API key
     */
    data class Authenticated(
        val serverUrl: String,
        val serverName: String,
        val serverVersion: String,
        val userId: String,
        val username: String
    ) : ConnectionState()
    
    /**
     * Disconnected from the server
     */
    data object Disconnected : ConnectionState()
}

/**
 * Extension functions for ConnectionState
 */
fun ConnectionState.isConnected(): Boolean {
    return this is ConnectionState.Connected || this is ConnectionState.Authenticated
}

fun ConnectionState.isAuthenticated(): Boolean {
    return this is ConnectionState.Authenticated
}

fun ConnectionState.isError(): Boolean {
    return this is ConnectionState.Error
}

fun ConnectionState.isLoading(): Boolean {
    return this is ConnectionState.Connecting
}

fun ConnectionState.getServerUrl(): String? {
    return when (this) {
        is ConnectionState.Connected -> serverUrl
        is ConnectionState.Authenticated -> serverUrl
        else -> null
    }
}

fun ConnectionState.getServerName(): String? {
    return when (this) {
        is ConnectionState.Connected -> serverName
        is ConnectionState.Authenticated -> serverName
        else -> null
    }
}

fun ConnectionState.getUserId(): String? {
    return when (this) {
        is ConnectionState.Authenticated -> userId
        else -> null
    }
}
