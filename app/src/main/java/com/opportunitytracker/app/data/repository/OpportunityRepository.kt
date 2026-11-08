package com.opportunitytracker.app.data.repository

import android.content.Context
import com.opportunitytracker.app.alarm.AlarmScheduler
import com.opportunitytracker.app.data.calendar.CalendarContractManager
import com.opportunitytracker.app.data.local.AppDatabase
import com.opportunitytracker.app.data.model.Opportunity
import com.opportunitytracker.app.data.remote.GroqApiClient
import com.opportunitytracker.app.data.remote.WebScraper
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class OpportunityRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.opportunityDao()
    private val groqClient = GroqApiClient(context)
    private val calendarManager = CalendarContractManager(context)
    private val alarmScheduler = AlarmScheduler(context)

    val allOpportunities: Flow<List<Opportunity>> = dao.getAllOpportunities()

    /**
     * Optimistically inserts a pending entry immediately with extractionStatus = "EXTRACTING",
     * then executes the Groq extraction asynchronously and updates the same entry.
     */
    suspend fun startExtraction(input: String): Long {
        val trimmedInput = input.trim()
        val initialOpportunity = Opportunity(
            title = trimmedInput.take(150),
            rawInput = trimmedInput,
            deadlineMillis = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000),
            extractionStatus = "EXTRACTING",
            category = "Analyzing...",
            alarmsJson = Opportunity.defaultAlarmsJson()
        )

        val insertedId = dao.insertOpportunity(initialOpportunity)
        performExtraction(insertedId, trimmedInput)
        return insertedId
    }

    suspend fun retryExtraction(opportunity: Opportunity) {
        val inputToUse = opportunity.rawInput.ifBlank { opportunity.title }
        dao.updateOpportunity(
            opportunity.copy(
                extractionStatus = "EXTRACTING",
                errorMessage = null
            )
        )
        performExtraction(opportunity.id, inputToUse)
    }

    private suspend fun performExtraction(id: Long, input: String) {
        val detectedUrls = WebScraper.extractUrls(input)
        val primaryUrl = detectedUrls.firstOrNull()

        var contentToParse = input
        var formUrlCandidate = primaryUrl.orEmpty()

        if (primaryUrl != null && (input.startsWith("http://") || input.startsWith("https://") || input.length < 300)) {
            val scraped = WebScraper.fetchUrlContent(primaryUrl)
            contentToParse = scraped.textSnippet
            if (scraped.formLinks.isNotEmpty()) {
                formUrlCandidate = scraped.formLinks.first()
            }
        }

        val extractionResult = groqClient.extractOpportunity(contentToParse, primaryUrl)
        val existing = dao.getOpportunityById(id) ?: return

        if (extractionResult.isFailure) {
            val errorMsg = extractionResult.exceptionOrNull()?.localizedMessage ?: "Failed to extract opportunity details"
            dao.updateOpportunity(
                existing.copy(
                    extractionStatus = "FAILED",
                    errorMessage = errorMsg
                )
            )
            return
        }

        val extracted = extractionResult.getOrThrow()
        val deadlineMillis = parseIsoDeadline(extracted.deadline)
        val finalFormUrl = extracted.formUrl?.takeIf { it.isNotBlank() } ?: formUrlCandidate
        val extractedTags = extracted.tags?.joinToString(", ") { tag ->
            val clean = tag.trim()
            if (clean.startsWith("#")) clean else "#$clean"
        }.orEmpty()

        var updatedOpp = existing.copy(
            title = extracted.title.ifBlank { existing.title },
            organization = extracted.organization.orEmpty(),
            deadlineMillis = deadlineMillis,
            hasSpecificTime = extracted.hasSpecificTime,
            formUrl = finalFormUrl,
            sourceUrl = extracted.sourceUrl ?: primaryUrl.orEmpty(),
            category = extracted.category ?: "General",
            summary = extracted.summary.orEmpty(),
            tags = if (existing.tags.isNotBlank()) existing.tags else extractedTags,
            extractionStatus = "SUCCESS",
            errorMessage = null,
            alarm1Scheduled = true,
            alarm2Scheduled = true,
            alarmsJson = existing.alarmsJson.ifBlank { Opportunity.defaultAlarmsJson() }
        )

        // 1. Sync with Google Calendar if permissions are granted
        if (calendarManager.hasCalendarPermission()) {
            val calendarEventId = calendarManager.insertEvent(updatedOpp)
            if (calendarEventId != null) {
                updatedOpp = updatedOpp.copy(calendarEventId = calendarEventId)
            }
        }

        // 2. Schedule AlarmManager alarms
        alarmScheduler.scheduleAlarmsForOpportunity(updatedOpp)

        // 3. Update database record in place
        dao.updateOpportunity(updatedOpp)
    }

    suspend fun processAndSaveOpportunity(input: String): Result<Opportunity> {
        val id = startExtraction(input)
        val result = dao.getOpportunityById(id)
        return if (result != null && result.extractionStatus == "SUCCESS") {
            Result.success(result)
        } else if (result != null && result.extractionStatus == "FAILED") {
            Result.failure(Exception(result.errorMessage ?: "Extraction failed"))
        } else {
            Result.success(result ?: Opportunity(id = id, title = input, deadlineMillis = System.currentTimeMillis()))
        }
    }

    suspend fun updateOpportunity(opportunity: Opportunity) {
        dao.updateOpportunity(opportunity)

        // Update calendar if synced
        if (opportunity.calendarEventId != null && calendarManager.hasCalendarPermission()) {
            calendarManager.deleteEvent(opportunity.calendarEventId)
            val newCalId = calendarManager.insertEvent(opportunity)
            dao.updateCalendarEventId(opportunity.id, newCalId)
        }

        // Reschedule alarms
        alarmScheduler.cancelAlarms(opportunity.id)
        if (!opportunity.isCompleted && opportunity.extractionStatus == "SUCCESS") {
            alarmScheduler.scheduleAlarmsForOpportunity(opportunity)
        }
    }

    suspend fun deleteOpportunity(opportunity: Opportunity) {
        dao.deleteOpportunity(opportunity)
        alarmScheduler.cancelAlarms(opportunity.id)
        opportunity.calendarEventId?.let {
            calendarManager.deleteEvent(it)
        }
    }

    suspend fun toggleCompleted(opportunity: Opportunity) {
        val newStatus = !opportunity.isCompleted
        dao.updateCompletionStatus(opportunity.id, newStatus)
        if (newStatus) {
            alarmScheduler.cancelAlarms(opportunity.id)
        } else {
            alarmScheduler.scheduleAlarmsForOpportunity(opportunity)
        }
    }

    data class SyncResult(val pushedCount: Int, val pulledCount: Int)

    suspend fun syncAllWithGoogleCalendar(calendarId: Long): SyncResult {
        if (!calendarManager.hasCalendarPermission()) {
            return SyncResult(0, 0)
        }

        val allOpps = dao.getAllList()
        var pushedCount = 0

        // 1. Push un-synced or existing opportunities to this Google Calendar
        for (opp in allOpps) {
            if (!opp.isCompleted && opp.extractionStatus == "SUCCESS") {
                if (opp.calendarEventId == null) {
                    val newCalId = calendarManager.insertEvent(opp, calendarId)
                    if (newCalId != null) {
                        dao.updateCalendarEventId(opp.id, newCalId)
                        pushedCount++
                    }
                }
            }
        }

        // 2. Pull upcoming events from Google Calendar that are not already imported
        var pulledCount = 0
        val calendarEvents = calendarManager.fetchUpcomingEvents(calendarId)
        val currentOpps = dao.getAllList()

        for (event in calendarEvents) {
            val cleanTitle = event.title.removePrefix("Deadline: ").trim().ifBlank { "Calendar Event" }

            val alreadyExists = currentOpps.any { existing ->
                existing.calendarEventId == event.id ||
                (existing.title.equals(cleanTitle, ignoreCase = true) &&
                 Math.abs(existing.deadlineMillis - event.dtStart) < 60000)
            }

            if (!alreadyExists) {
                val newOpp = Opportunity(
                    title = cleanTitle,
                    rawInput = "Imported from Google Calendar",
                    deadlineMillis = event.dtStart,
                    summary = event.description.ifBlank { "Event scheduled in Google Calendar" },
                    organization = event.location.ifBlank { "Google Calendar" },
                    category = "Calendar",
                    extractionStatus = "SUCCESS",
                    calendarEventId = event.id,
                    alarmsJson = Opportunity.defaultAlarmsJson(),
                    alarm1Scheduled = true,
                    alarm2Scheduled = true
                )
                val newId = dao.insertOpportunity(newOpp)
                alarmScheduler.scheduleAlarmsForOpportunity(newOpp.copy(id = newId))
                pulledCount++
            }
        }

        return SyncResult(pushedCount, pulledCount)
    }

    private fun parseIsoDeadline(deadlineStr: String?): Long {
        if (deadlineStr.isNullOrBlank()) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 7)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 0)
            }
            return cal.timeInMillis
        }

        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        )

        for (format in formats) {
            try {
                val date = format.parse(deadlineStr)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    if (cal.get(Calendar.HOUR_OF_DAY) == 0 && cal.get(Calendar.MINUTE) == 0 && !deadlineStr.contains(":")) {
                        cal.set(Calendar.HOUR_OF_DAY, 23)
                        cal.set(Calendar.MINUTE, 59)
                    }
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
        }

        return System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
    }
}
