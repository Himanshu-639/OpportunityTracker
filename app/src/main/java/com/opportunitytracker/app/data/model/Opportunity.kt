package com.opportunitytracker.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class AlarmTiming(
    val amount: Int = 1,
    val unit: String = "HOURS", // "DAYS", "HOURS", "MINUTES", "EXACT"
    val exactTimeMillis: Long? = null
) {
    fun toMillis(): Long {
        return when (unit) {
            "DAYS" -> amount * 24L * 60 * 60 * 1000L
            "HOURS" -> amount * 60L * 60 * 1000L
            "MINUTES" -> amount * 60L * 1000L
            else -> amount * 60L * 60 * 1000L
        }
    }

    fun getTriggerTime(deadlineMillis: Long): Long {
        return if (unit == "EXACT" && exactTimeMillis != null) {
            exactTimeMillis
        } else {
            deadlineMillis - toMillis()
        }
    }

    fun toDisplayString(): String {
        return when (unit) {
            "EXACT" -> {
                if (exactTimeMillis != null) {
                    val sdf = SimpleDateFormat("EEE, dd MMM yyyy 'at' hh:mm a", Locale.getDefault())
                    sdf.format(Date(exactTimeMillis))
                } else {
                    "Exact time"
                }
            }
            "DAYS" -> "$amount ${if (amount == 1) "day" else "days"} before"
            "HOURS" -> "$amount ${if (amount == 1) "hour" else "hours"} before"
            "MINUTES" -> "$amount ${if (amount == 1) "minute" else "minutes"} before"
            else -> "$amount $unit before"
        }
    }
}

@Entity(tableName = "opportunities")
@Serializable
data class Opportunity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val organization: String = "",
    val deadlineMillis: Long,
    val hasSpecificTime: Boolean = false,
    val formUrl: String = "",
    val sourceUrl: String = "",
    val category: String = "General", // Internship, Job, Hackathon, Grant, Event
    val summary: String = "",
    val notes: String = "",
    val calendarEventId: Long? = null,
    val alarm1Scheduled: Boolean = false,
    val alarm2Scheduled: Boolean = false,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val extractionStatus: String = "SUCCESS", // "EXTRACTING", "SUCCESS", "FAILED"
    val errorMessage: String? = null,
    val rawInput: String = "",
    val alarmsJson: String = "",
    val tags: String = ""
) {
    fun getAlarms(): List<AlarmTiming> {
        if (alarmsJson.isBlank()) {
            return defaultAlarms()
        }
        return try {
            val list = Json.decodeFromString<List<AlarmTiming>>(alarmsJson)
            if (list.isEmpty()) defaultAlarms() else list.take(5)
        } catch (_: Exception) {
            defaultAlarms()
        }
    }

    fun getTagsList(): List<String> {
        if (tags.isBlank()) return emptyList()
        return tags.split(",", ";", " ")
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .map { "#$it" }
            .distinct()
    }

    companion object {
        fun defaultAlarms(): List<AlarmTiming> = listOf(
            AlarmTiming(amount = 2, unit = "DAYS"),
            AlarmTiming(amount = 2, unit = "HOURS")
        )

        fun defaultAlarmsJson(): String = Json.encodeToString(defaultAlarms())
    }
}
