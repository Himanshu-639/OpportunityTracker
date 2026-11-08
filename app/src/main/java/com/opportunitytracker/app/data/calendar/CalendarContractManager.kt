package com.opportunitytracker.app.data.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.opportunitytracker.app.data.model.Opportunity
import java.util.TimeZone

class CalendarContractManager(private val context: Context) {

    data class CalendarAccount(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean
    )

    data class CalendarImportedEvent(
        val id: Long,
        val title: String,
        val description: String,
        val dtStart: Long,
        val dtEnd: Long,
        val location: String
    )

    fun hasCalendarPermission(): Boolean {
        val readPerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
        val writePerm = ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
        return readPerm == PackageManager.PERMISSION_GRANTED && writePerm == PackageManager.PERMISSION_GRANTED
    }

    fun getGoogleCalendars(): List<CalendarAccount> {
        if (!hasCalendarPermission()) return emptyList()

        val calendars = mutableListOf<CalendarAccount>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY
        )

        val uri: Uri = CalendarContract.Calendars.CONTENT_URI
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1"

        try {
            val cursor = context.contentResolver.query(uri, projection, selection, null, null)
            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountIdx = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val typeIdx = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val primaryIdx = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val name = it.getString(nameIdx) ?: "Default Calendar"
                    val account = it.getString(accountIdx) ?: ""
                    val type = if (typeIdx >= 0) it.getString(typeIdx) ?: "" else ""
                    val isPrimary = if (primaryIdx >= 0) it.getInt(primaryIdx) == 1 else false

                    calendars.add(CalendarAccount(id, name, account, type, isPrimary))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        return calendars
    }

    fun getPreferredCalendarId(): Long? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getLong("selected_calendar_id", -1L)
        val calendars = getGoogleCalendars()
        if (savedId != -1L) {
            val found = calendars.find { it.id == savedId }
            if (found != null) return found.id
        }
        // Prefer Google Account or primary calendar
        val googleCal = calendars.firstOrNull { it.accountType == "com.google" || it.accountName.contains("@gmail.com", ignoreCase = true) }
        val primaryCal = calendars.firstOrNull { it.isPrimary }
        return googleCal?.id ?: primaryCal?.id ?: calendars.firstOrNull()?.id
    }

    fun insertEvent(opportunity: Opportunity, targetCalendarId: Long? = null): Long? {
        if (!hasCalendarPermission()) return null
        val calendarId = targetCalendarId ?: getPreferredCalendarId() ?: return null

        val startTime = opportunity.deadlineMillis
        // End time is 1 hour after start or same day
        val endTime = startTime + (60 * 60 * 1000)

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.TITLE, "Deadline: ${opportunity.title}")
            put(CalendarContract.Events.DESCRIPTION, buildString {
                if (opportunity.organization.isNotBlank()) append("Organizer: ").append(opportunity.organization).append("\n\n")
                if (opportunity.summary.isNotBlank()) append(opportunity.summary).append("\n\n")
                if (opportunity.formUrl.isNotBlank()) append("Application Link:\n").append(opportunity.formUrl)
            })
            put(CalendarContract.Events.EVENT_LOCATION, opportunity.formUrl)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        return try {
            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = eventUri?.lastPathSegment?.toLongOrNull()

            if (eventId != null) {
                // Add Google Calendar reminders matching configured alarms
                val alarms = opportunity.getAlarms()
                for (alarm in alarms) {
                    val triggerTime = alarm.getTriggerTime(opportunity.deadlineMillis)
                    val minutesBefore = ((opportunity.deadlineMillis - triggerTime) / (60 * 1000)).toInt()
                    if (minutesBefore > 0) {
                        addCalendarReminder(eventId, minutesBefore)
                    }
                }
            }
            eventId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun fetchUpcomingEvents(calendarId: Long): List<CalendarImportedEvent> {
        if (!hasCalendarPermission()) return emptyList()
        val events = mutableListOf<CalendarImportedEvent>()
        val now = System.currentTimeMillis()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(calendarId.toString(), (now - 60 * 60 * 1000).toString()) // from 1 hour ago onwards
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        try {
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idIdx)
                    val title = it.getString(titleIdx) ?: "Calendar Event"
                    val desc = it.getString(descIdx) ?: ""
                    val start = it.getLong(startIdx)
                    val end = it.getLong(endIdx)
                    val loc = it.getString(locIdx) ?: ""

                    events.add(CalendarImportedEvent(id, title, desc, start, end, loc))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return events
    }

    fun createLocalCalendarIfNone(): Long? {
        return try {
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Opportunity Tracker")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()

            val values = ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "Opportunity Tracker")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME, "Opportunity Tracker Calendar")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Opportunity Deadlines")
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF38BDF8.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "Opportunity Tracker")
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
            val resultUri = context.contentResolver.insert(uri, values)
            resultUri?.lastPathSegment?.toLongOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun addCalendarReminder(eventId: Long, minutesBefore: Int) {
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutesBefore)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteEvent(eventId: Long) {
        if (!hasCalendarPermission()) return
        try {
            val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.delete(deleteUri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
