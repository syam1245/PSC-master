package com.example.pscmaster.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) {
            // If not a JSON array, treat the entire string as the only item in the list.
            // This is safer than splitting by comma which could be part of the text.
            listOf(value)
        }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return JSONArray(list).toString()
    }
}
