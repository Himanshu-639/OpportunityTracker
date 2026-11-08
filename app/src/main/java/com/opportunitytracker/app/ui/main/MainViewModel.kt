package com.opportunitytracker.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opportunitytracker.app.data.model.Opportunity
import com.opportunitytracker.app.data.repository.OpportunityRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class OpportunityFilter {
    ALL,
    DUE_SOON,
    UPCOMING,
    COMPLETED
}

enum class SortMode(val label: String) {
    DEADLINE("Deadline"),
    URGENCY("Urgency"),
    ADDED_DATE("Added Date"),
    TITLE("Title (A-Z)")
}

enum class GroupMode(val label: String) {
    NONE("No Grouping"),
    CATEGORY("Group by Category")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = OpportunityRepository(application)

    private val _filter = MutableStateFlow(OpportunityFilter.ALL)
    val filter: StateFlow<OpportunityFilter> = _filter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.DEADLINE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _groupMode = MutableStateFlow(GroupMode.NONE)
    val groupMode: StateFlow<GroupMode> = _groupMode.asStateFlow()

    val opportunities: StateFlow<List<Opportunity>> = combine(
        repository.allOpportunities,
        _filter,
        _searchQuery,
        _sortMode
    ) { list, filter, query, sort ->
        val now = System.currentTimeMillis()
        val queryLower = query.trim().lowercase()

        val filtered = list.filter { opp ->
            val matchesQuery = queryLower.isEmpty() ||
                    opp.title.lowercase().contains(queryLower) ||
                    opp.organization.lowercase().contains(queryLower) ||
                    opp.category.lowercase().contains(queryLower) ||
                    opp.rawInput.lowercase().contains(queryLower)

            val matchesFilter = when (filter) {
                OpportunityFilter.ALL -> true
                OpportunityFilter.DUE_SOON -> {
                    !opp.isCompleted && opp.extractionStatus == "SUCCESS" &&
                            (opp.deadlineMillis - now) in 0..TimeUnit.HOURS.toMillis(48)
                }
                OpportunityFilter.UPCOMING -> {
                    opp.extractionStatus != "SUCCESS" || (!opp.isCompleted && opp.deadlineMillis > now)
                }
                OpportunityFilter.COMPLETED -> opp.isCompleted
            }

            matchesQuery && matchesFilter
        }

        // Apply sorting
        when (sort) {
            SortMode.DEADLINE -> filtered.sortedBy { it.deadlineMillis }
            SortMode.URGENCY -> filtered.sortedWith(
                compareBy<Opportunity> { it.isCompleted }
                    .thenBy { if (it.deadlineMillis < now) Long.MAX_VALUE else it.deadlineMillis - now }
            )
            SortMode.ADDED_DATE -> filtered.sortedByDescending { it.createdAt }
            SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: OpportunityFilter) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setGroupMode(mode: GroupMode) {
        _groupMode.value = mode
    }

    fun pasteAndExtract(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.startExtraction(trimmed)
        }
    }

    fun retryExtraction(opportunity: Opportunity) {
        viewModelScope.launch {
            repository.retryExtraction(opportunity)
        }
    }

    fun updateOpportunity(opportunity: Opportunity) {
        viewModelScope.launch {
            repository.updateOpportunity(opportunity)
        }
    }

    fun deleteOpportunity(opportunity: Opportunity) {
        viewModelScope.launch {
            repository.deleteOpportunity(opportunity)
        }
    }

    fun toggleComplete(opportunity: Opportunity) {
        viewModelScope.launch {
            repository.toggleCompleted(opportunity)
        }
    }

    fun syncGoogleCalendar(calendarId: Long) {
        viewModelScope.launch {
            try {
                repository.syncAllWithGoogleCalendar(calendarId)
            } catch (_: Exception) {}
        }
    }
}
