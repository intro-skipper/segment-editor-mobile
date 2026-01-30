package org.introskipper.segmenteditor.bridge

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.introskipper.segmenteditor.MainActivity
import org.introskipper.segmenteditor.api.JellyfinApiService
import org.introskipper.segmenteditor.model.SegmentCreateRequest
import org.introskipper.segmenteditor.storage.SecurePreferences

class JellyfinBridge(
    private val context: Context,
    private val activity: MainActivity,
    private val webView: WebView
) {
    private val preferences = SecurePreferences(context)
    private val gson = Gson()
    private var apiService: JellyfinApiService? = null
    
    init {
        // Initialize API service if credentials exist
        val serverUrl = preferences.getServerUrl()
        val apiKey = preferences.getApiKey()
        if (serverUrl != null && apiKey != null) {
            apiService = JellyfinApiService(serverUrl, apiKey)
        }
    }
    
    @JavascriptInterface
    fun getServerUrl(): String {
        return preferences.getServerUrl() ?: ""
    }
    
    // Note: Exposing API key to JavaScript is a security concern
    // This should only be used with trusted web content
    @JavascriptInterface
    fun getApiKey(): String {
        return preferences.getApiKey() ?: ""
    }
    
    @JavascriptInterface
    fun saveCredentials(serverUrl: String, apiKey: String) {
        preferences.saveServerUrl(serverUrl)
        preferences.saveApiKey(apiKey)
        apiService = JellyfinApiService(serverUrl, apiKey)
        notifyWebView("onCredentialsSaved", createSuccessResponse())
    }
    
    @JavascriptInterface
    fun testConnection(callbackId: String) {
        if (!isValidCallbackId(callbackId)) {
            Log.e("JellyfinBridge", "Invalid callback ID: $callbackId")
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService?.testConnection()
                }
                
                val result = if (response?.isSuccessful == true) {
                    createSuccessResponse(response.body())
                } else {
                    createErrorResponse("Connection failed: ${response?.code()}")
                }
                
                notifyWebView(callbackId, result)
            } catch (e: Exception) {
                notifyWebView(callbackId, createErrorResponse(e.message ?: "Unknown error"))
            }
        }
    }
    
    @JavascriptInterface
    fun getSegments(itemId: String, callbackId: String) {
        if (!isValidCallbackId(callbackId)) {
            Log.e("JellyfinBridge", "Invalid callback ID: $callbackId")
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService?.getSegments(itemId)
                }
                
                val result = if (response?.isSuccessful == true) {
                    createSuccessResponse(response.body())
                } else {
                    createErrorResponse("Failed to get segments: ${response?.code()}")
                }
                
                notifyWebView(callbackId, result)
            } catch (e: Exception) {
                notifyWebView(callbackId, createErrorResponse(e.message ?: "Unknown error"))
            }
        }
    }
    
    @JavascriptInterface
    fun createSegment(segmentJson: String, callbackId: String) {
        if (!isValidCallbackId(callbackId)) {
            Log.e("JellyfinBridge", "Invalid callback ID: $callbackId")
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                val segment = gson.fromJson(segmentJson, SegmentCreateRequest::class.java)
                val response = withContext(Dispatchers.IO) {
                    apiService?.createSegment(segment)
                }
                
                val result = if (response?.isSuccessful == true) {
                    createSuccessResponse(response.body())
                } else {
                    createErrorResponse("Failed to create segment: ${response?.code()}")
                }
                
                notifyWebView(callbackId, result)
            } catch (e: Exception) {
                notifyWebView(callbackId, createErrorResponse(e.message ?: "Unknown error"))
            }
        }
    }
    
    @JavascriptInterface
    fun updateSegment(itemId: String, segmentType: String, segmentJson: String, callbackId: String) {
        if (!isValidCallbackId(callbackId)) {
            Log.e("JellyfinBridge", "Invalid callback ID: $callbackId")
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                val segment = gson.fromJson(segmentJson, SegmentCreateRequest::class.java)
                val response = withContext(Dispatchers.IO) {
                    apiService?.updateSegment(itemId, segmentType, segment)
                }
                
                val result = if (response?.isSuccessful == true) {
                    createSuccessResponse(response.body())
                } else {
                    createErrorResponse("Failed to update segment: ${response?.code()}")
                }
                
                notifyWebView(callbackId, result)
            } catch (e: Exception) {
                notifyWebView(callbackId, createErrorResponse(e.message ?: "Unknown error"))
            }
        }
    }
    
    @JavascriptInterface
    fun deleteSegment(itemId: String, segmentType: String, callbackId: String) {
        if (!isValidCallbackId(callbackId)) {
            Log.e("JellyfinBridge", "Invalid callback ID: $callbackId")
            return
        }
        
        activity.lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    apiService?.deleteSegment(itemId, segmentType)
                }
                
                val result = if (response?.isSuccessful == true) {
                    createSuccessResponse()
                } else {
                    createErrorResponse("Failed to delete segment: ${response?.code()}")
                }
                
                notifyWebView(callbackId, result)
            } catch (e: Exception) {
                notifyWebView(callbackId, createErrorResponse(e.message ?: "Unknown error"))
            }
        }
    }
    
    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("timestamp", text)
        clipboard.setPrimaryClip(clip)
        notifyWebView("onClipboardCopy", createSuccessResponse())
    }
    
    @JavascriptInterface
    fun openVideoPlayer(videoUrl: String, itemId: String) {
        if (videoUrl.isBlank()) {
            Log.e("JellyfinBridge", "Video URL is empty")
            return
        }
        
        val intent = android.content.Intent(context, org.introskipper.segmenteditor.player.VideoPlayerActivity::class.java)
        intent.putExtra("VIDEO_URL", videoUrl)
        intent.putExtra("ITEM_ID", itemId)
        activity.startActivity(intent)
    }
    
    /**
     * Validate callback ID to prevent JavaScript injection
     */
    private fun isValidCallbackId(callbackId: String): Boolean {
        return callbackId.matches(Regex("^[a-zA-Z0-9_]+$"))
    }
    
    /**
     * Create a success response with optional data
     */
    private fun createSuccessResponse(data: Any? = null): String {
        return if (data != null) {
            gson.toJson(mapOf("success" to true, "data" to data))
        } else {
            gson.toJson(mapOf("success" to true))
        }
    }
    
    /**
     * Create an error response with proper JSON escaping
     */
    private fun createErrorResponse(error: String): String {
        return gson.toJson(mapOf("success" to false, "error" to error))
    }
    
    private fun notifyWebView(callback: String, data: String) {
        activity.runOnUiThread {
            webView.evaluateJavascript(
                "if (window.$callback) window.$callback($data);",
                null
            )
        }
    }
}
