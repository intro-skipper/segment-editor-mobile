package org.introskipper.segmenteditor.data.model

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
        
        // Extract fields
        val id = jsonObject.get("Id")?.asString
        val itemId = jsonObject.get("ItemId").asString
        val startTicks = jsonObject.get("StartTicks").asLong
        val endTicks = jsonObject.get("EndTicks").asLong
        
        // Handle Type field - can be either integer or string
        val typeElement = jsonObject.get("Type")
        val type: String = when {
            typeElement.isJsonPrimitive && typeElement.asJsonPrimitive.isNumber -> {
                // Type is an integer, convert to string name
                val intValue = typeElement.asInt
                SegmentType.apiValueToString(intValue)
            }
            typeElement.isJsonPrimitive && typeElement.asJsonPrimitive.isString -> {
                // Type is already a string
                typeElement.asString
            }
            else -> "Unknown"
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
