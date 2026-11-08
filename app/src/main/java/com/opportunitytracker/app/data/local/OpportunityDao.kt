package com.opportunitytracker.app.data.local

import androidx.room.*
import com.opportunitytracker.app.data.model.Opportunity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpportunityDao {

    @Query("SELECT * FROM opportunities ORDER BY CASE WHEN extractionStatus = 'EXTRACTING' THEN 0 WHEN extractionStatus = 'FAILED' THEN 1 WHEN isCompleted = 1 THEN 3 ELSE 2 END, deadlineMillis ASC, id DESC")
    fun getAllOpportunities(): Flow<List<Opportunity>>

    @Query("SELECT * FROM opportunities WHERE id = :id")
    suspend fun getOpportunityById(id: Long): Opportunity?

    @Query("SELECT * FROM opportunities WHERE isCompleted = 0 AND deadlineMillis > :currentTime ORDER BY deadlineMillis ASC")
    suspend fun getActiveUpcomingOpportunities(currentTime: Long): List<Opportunity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpportunity(opportunity: Opportunity): Long

    @Update
    suspend fun updateOpportunity(opportunity: Opportunity)

    @Delete
    suspend fun deleteOpportunity(opportunity: Opportunity)

    @Query("DELETE FROM opportunities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE opportunities SET isCompleted = :completed WHERE id = :id")
    suspend fun updateCompletionStatus(id: Long, completed: Boolean)

    @Query("UPDATE opportunities SET calendarEventId = :calendarId WHERE id = :id")
    suspend fun updateCalendarEventId(id: Long, calendarId: Long?)

    @Query("SELECT * FROM opportunities")
    suspend fun getAllList(): List<Opportunity>

    @Query("SELECT * FROM opportunities WHERE calendarEventId = :eventId LIMIT 1")
    suspend fun getByCalendarEventId(eventId: Long): Opportunity?
}
