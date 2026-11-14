package com.healthapp.data.local.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class Converters {
    private val gson = Gson()
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? {
        return value?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, dateTimeFormatter) }
    }

    @TypeConverter
    fun fromList(value: List<String>?): String? {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toList(value: String?): List<String>? {
        return if (value == null) null else gson.fromJson(value, Array<String>::class.java).toList()
    }
}