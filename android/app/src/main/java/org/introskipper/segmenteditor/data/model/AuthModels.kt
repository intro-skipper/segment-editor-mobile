/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import com.google.gson.annotations.SerializedName

/**
 * Authentication request for logging in with username/password
 */
data class AuthenticationRequest(
    @SerializedName("Username")
    val username: String,
    
    @SerializedName("Pw")
    val password: String
)

/**
 * Result from authentication request
 */
data class AuthenticationResult(
    @SerializedName("User")
    val user: User,
    
    @SerializedName("AccessToken")
    val accessToken: String,
    
    @SerializedName("ServerId")
    val serverId: String,
    
    @SerializedName("SessionInfo")
    val sessionInfo: SessionInfo? = null
)

/**
 * Jellyfin user information
 */
data class User(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("Name")
    val name: String,
    
    @SerializedName("ServerId")
    val serverId: String,
    
    @SerializedName("HasPassword")
    val hasPassword: Boolean = false,
    
    @SerializedName("HasConfiguredPassword")
    val hasConfiguredPassword: Boolean = false,
    
    @SerializedName("HasConfiguredEasyPassword")
    val hasConfiguredEasyPassword: Boolean = false,
    
    @SerializedName("EnableAutoLogin")
    val enableAutoLogin: Boolean = false,
    
    @SerializedName("LastLoginDate")
    val lastLoginDate: String? = null,
    
    @SerializedName("LastActivityDate")
    val lastActivityDate: String? = null,
    
    @SerializedName("Policy")
    val policy: UserPolicy? = null,
    
    @SerializedName("PrimaryImageTag")
    val primaryImageTag: String? = null
)

/**
 * User policy and permissions
 */
data class UserPolicy(
    @SerializedName("IsAdministrator")
    val isAdministrator: Boolean = false,
    
    @SerializedName("IsHidden")
    val isHidden: Boolean = false,
    
    @SerializedName("IsDisabled")
    val isDisabled: Boolean = false,
    
    @SerializedName("EnableAllFolders")
    val enableAllFolders: Boolean = true,
    
    @SerializedName("EnabledFolders")
    val enabledFolders: List<String>? = null,
    
    @SerializedName("EnableContentDeletion")
    val enableContentDeletion: Boolean = false,
    
    @SerializedName("EnableContentDownloading")
    val enableContentDownloading: Boolean = true,
    
    @SerializedName("EnableMediaPlayback")
    val enableMediaPlayback: Boolean = true,
    
    @SerializedName("EnablePublicSharing")
    val enablePublicSharing: Boolean = false
)

/**
 * Active session information
 */
data class SessionInfo(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("UserId")
    val userId: String,
    
    @SerializedName("UserName")
    val userName: String,
    
    @SerializedName("Client")
    val client: String,
    
    @SerializedName("LastActivityDate")
    val lastActivityDate: String,
    
    @SerializedName("DeviceName")
    val deviceName: String,
    
    @SerializedName("DeviceId")
    val deviceId: String,
    
    @SerializedName("ApplicationVersion")
    val applicationVersion: String,
    
    @SerializedName("SupportsRemoteControl")
    val supportsRemoteControl: Boolean = false
)

/**
 * Jellyfin server information
 */
data class ServerInfo(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("ServerName")
    val serverName: String,
    
    @SerializedName("Version")
    val version: String,
    
    @SerializedName("ProductName")
    val productName: String? = null,
    
    @SerializedName("OperatingSystem")
    val operatingSystem: String? = null,
    
    @SerializedName("LocalAddress")
    val localAddress: String? = null,
    
    @SerializedName("WanAddress")
    val wanAddress: String? = null,
    
    @SerializedName("HasPendingRestart")
    val hasPendingRestart: Boolean = false,
    
    @SerializedName("IsShuttingDown")
    val isShuttingDown: Boolean = false,
    
    @SerializedName("SupportsLibraryMonitor")
    val supportsLibraryMonitor: Boolean = true,
    
    @SerializedName("WebSocketPortNumber")
    val webSocketPortNumber: Int? = null,
    
    @SerializedName("StartupWizardCompleted")
    val startupWizardCompleted: Boolean = true
)

/**
 * Public system info (accessible without authentication)
 */
data class PublicSystemInfo(
    @SerializedName("Id")
    val id: String,
    
    @SerializedName("ServerName")
    val serverName: String,
    
    @SerializedName("Version")
    val version: String,
    
    @SerializedName("ProductName")
    val productName: String? = null,
    
    @SerializedName("OperatingSystem")
    val operatingSystem: String? = null,
    
    @SerializedName("LocalAddress")
    val localAddress: String? = null,
    
    @SerializedName("StartupWizardCompleted")
    val startupWizardCompleted: Boolean = true
)
