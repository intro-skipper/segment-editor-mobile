/*
 * Copyright (c) 2026 Intro-Skipper contributors <intro-skipper.org>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package org.introskipper.segmenteditor.data.model

import android.util.Log
import com.google.gson.*
import java.lang.reflect.Type

/**
 * Custom Gson TypeAdapter for Segment to handle Type field that can be either Int or String.
 * The server returns Type as an integer when creating/updating segments,
 * but as a string when listing segments.
 * 
 * This adapter is lenient with missing/null fields because:
 * - Some API responses (especially POST creates) may return minimal data
 * - The caller typically calls refreshSegments() immediately after to get complete data
 * - Default values are temporary placeholders, not used for actual operations
 */
class SegmentTypeAdapter : JsonDeserializer<Segment> {
    
    /**
     * Safely extracts a string field with a default value
     */
    private fun getStringOrDefault(jsonObject: JsonObject, fieldName: String, default: String): String {
        return try {
            jsonObject.get(fieldName)?.takeIf { !it.isJsonNull }?.asString ?: default
        } catch (e: Exception) {
            Log.w(TAG, "Error getting $fieldName: ${e.message}")
            default
        }
    }
    
    /**
     * Safely extracts a long field with a default value
     */
    private fun getLongOrDefault(jsonObject: JsonObject, fieldName: String, default: Long): Long {
        return try {
            jsonObject.get(fieldName)?.takeIf { !it.isJsonNull }?.asLong ?: default
        } catch (e: Exception) {
            Log.w(TAG, "Error getting $fieldName: ${e.message}")
            default
        }
    }
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Segment {
        val jsonObject = json.asJsonObject
        
        // Extract fields with lenient handling for missing/null values
        val id = jsonObject.get("Id")?.takeIf { !it.isJsonNull }?.asString
        val itemId = getStringOrDefault(jsonObject, "ItemId", "")
        val startTicks = getLongOrDefault(jsonObject, "StartTicks", 0L)
        val endTicks = getLongOrDefault(jsonObject, "EndTicks", 0L)
        
        // Handle Type field - can be either integer or string
        val type = parseTypeField(jsonObject.get("Type"))
        
        return Segment(
            id = id,
            itemId = itemId,
            type = type,
            startTicks = startTicks,
            endTicks = endTicks
        )
    }
    
    /**
     * Parses the Type field which can be either an integer or string
     */
    private fun parseTypeField(typeElement: JsonElement?): String {
        return try {
            when {
                typeElement == null || typeElement.isJsonNull -> {
                    Log.w(TAG, "Type field is null or missing")
                    "Unknown"
                }
                typeElement.isJsonPrimitive && typeElement.asJsonPrimitive.isNumber -> {
                    // Type is an integer, convert to string name
                    SegmentType.apiValueToString(typeElement.asInt)
                }
                typeElement.isJsonPrimitive && typeElement.asJsonPrimitive.isString -> {
                    // Type is already a string
                    typeElement.asString
                }
                else -> {
                    Log.w(TAG, "Unexpected Type format: $typeElement")
                    "Unknown"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Type: ${e.message}")
            "Unknown"
        }
    }
    
    companion object {
        private const val TAG = "SegmentTypeAdapter"
    }
}
