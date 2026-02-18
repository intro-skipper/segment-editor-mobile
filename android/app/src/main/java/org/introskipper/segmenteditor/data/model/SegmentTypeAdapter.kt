package org.introskipper.segmenteditor.data.model

import android.util.Log
import com.google.gson.*
import java.lang.reflect.Type

/**
 * Custom Gson TypeAdapter for Segment to handle Type field that can be either Int or String.
 * The server returns Type as an integer when creating/updating segments,
 * but as a string when listing segments.
 */
class SegmentTypeAdapter : JsonDeserializer<Segment> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Segment {
        val jsonObject = json.asJsonObject
        
        // Log the raw JSON for debugging
        Log.d("SegmentTypeAdapter", "Deserializing: $jsonObject")
        
        // Extract fields with lenient handling for missing/null values
        val id = jsonObject.get("Id")?.takeIf { !it.isJsonNull }?.asString
        
        // Required fields - use safe fallbacks if missing
        val itemId = try {
            jsonObject.get("ItemId")?.takeIf { !it.isJsonNull }?.asString ?: ""
        } catch (e: Exception) {
            Log.w("SegmentTypeAdapter", "Error getting ItemId: ${e.message}")
            ""
        }
        
        val startTicks = try {
            jsonObject.get("StartTicks")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        } catch (e: Exception) {
            Log.w("SegmentTypeAdapter", "Error getting StartTicks: ${e.message}")
            0L
        }
        
        val endTicks = try {
            jsonObject.get("EndTicks")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        } catch (e: Exception) {
            Log.w("SegmentTypeAdapter", "Error getting EndTicks: ${e.message}")
            0L
        }
        
        // Handle Type field - can be either integer or string
        val typeElement = jsonObject.get("Type")
        val type: String = try {
            when {
                typeElement == null || typeElement.isJsonNull -> {
                    Log.w("SegmentTypeAdapter", "Type field is null or missing")
                    "Unknown"
                }
                typeElement.isJsonPrimitive && typeElement.asJsonPrimitive.isNumber -> {
                    // Type is an integer, convert to string name
                    val intValue = typeElement.asInt
                    SegmentType.apiValueToString(intValue)
                }
                typeElement.isJsonPrimitive && typeElement.asJsonPrimitive.isString -> {
                    // Type is already a string
                    typeElement.asString
                }
                else -> {
                    Log.w("SegmentTypeAdapter", "Unexpected Type format: $typeElement")
                    "Unknown"
                }
            }
        } catch (e: Exception) {
            Log.w("SegmentTypeAdapter", "Error parsing Type: ${e.message}")
            "Unknown"
        }
        
        return Segment(
            id = id,
            itemId = itemId,
            type = type,
            startTicks = startTicks,
            endTicks = endTicks
        )
    }
}
