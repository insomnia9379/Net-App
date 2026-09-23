package com.netapp.marketplacescanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netapp.marketplacescanner.data.db.Listing
import com.netapp.marketplacescanner.data.db.SavedSearch
import com.netapp.marketplacescanner.data.repo.ScanOutcome
import com.netapp.marketplacescanner.data.repo.ScannerRepository
import com.netapp.marketplacescanner.notify.Notifications
import com.netapp.marketplacescanner.work.ScanScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** UI-facing state for one-off, transient messages. */
data class ScanUiState(
    val scanning: Boolean = false,
    val message: String? = null,
    val needsLogin: Boolean = false,
)

class ScannerViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScannerRepository.get(app)
    val session = repo.session

    val searches = repo.observeSearches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLoggedIn = session.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val intervalMinutes = session.intervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30L)

    val wifiOnly = session.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _ui = MutableStateFlow(ScanUiState())
    val ui: StateFlow<ScanUiState> = _ui.asStateFlow()

    fun listings(searchId: Long) = repo.observeListings(searchId)
    fun listings(searchId: Long, nearest: Boolean) =
        if (nearest) repo.observeListingsByRank(searchId) else repo.observeListings(searchId)
    fun observeSearch(searchId: Long) = repo.observeSearch(searchId)

    suspend fun getSearchOnce(searchId: Long): SavedSearch? = repo.getSearch(searchId)

    fun saveSearch(search: SavedSearch, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repo.saveSearch(search)
        onDone(id)
    }

    fun deleteSearch(search: SavedSearch) = viewModelScope.launch { repo.deleteSearch(search) }

    fun toggleEnabled(search: SavedSearch) = viewModelScope.launch {
        repo.updateSearch(search.copy(enabled = !search.enabled))
    }

    fun markRead(searchId: Long) = viewModelScope.launch { repo.markRead(searchId) }

    fun onSessionCaptured(cookie: String, userAgent: String) = viewModelScope.launch {
        session.saveSession(cookie, userAgent)
        _ui.value = _ui.value.copy(needsLogin = false, message = "Signed in")
        rescheduleScans()
    }

    fun logout() = viewModelScope.launch {
        session.clearSession()
        ScanScheduler.cancelAll(getApplication())
    }

    fun setInterval(minutes: Long) = viewModelScope.launch {
        session.setInterval(minutes)
        rescheduleScans()
    }

    fun setWifiOnly(value: Boolean) = viewModelScope.launch {
        session.setWifiOnly(value)
        rescheduleScans()
    }

    /** Foreground scan of a single search, with immediate alerts. */
    fun scanNow(search: SavedSearch) = viewModelScope.launch {
        _ui.value = _ui.value.copy(scanning = true, message = null)
        when (val outcome = repo.scan(search)) {
            is ScanOutcome.Completed -> {
                val label = search.query.ifBlank { "Marketplace" }
                Notifications.notifyAlerts(getApplication(), label, outcome.alerts)
                _ui.value = ScanUiState(
                    scanning = false,
                    message = "${outcome.total} listings • ${outcome.alerts.size} new alert(s)"
                )
            }
            is ScanOutcome.AuthRequired ->
                _ui.value = ScanUiState(scanning = false, needsLogin = true,
                    message = "Session expired — please sign in again")
            is ScanOutcome.Failed ->
                _ui.value = ScanUiState(scanning = false, message = "Scan failed: ${outcome.message}")
        }
    }

    /** Scan every enabled search via WorkManager (background-safe). */
    fun scanAllNow() {
        ScanScheduler.scanNow(getApplication())
        _ui.value = _ui.value.copy(message = "Scanning all searches…")
    }

    fun clearMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private suspend fun rescheduleScans() {
        ScanScheduler.schedulePeriodic(
            getApplication(),
            intervalMinutes.value,
            wifiOnly.value,
        )
    }
}
