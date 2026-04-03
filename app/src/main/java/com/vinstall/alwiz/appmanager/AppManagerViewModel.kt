package com.vinstall.alwiz.appmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vinstall.alwiz.backup.BackupManager
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppManagerViewModel(app: Application) : AndroidViewModel(app) {

    enum class SortOrder { NAME, SIZE, INSTALL_DATE, UPDATE_DATE }

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _includeSystem = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(SortOrder.NAME)
    private val _isLoading = MutableStateFlow(false)
    private var loadJob: Job? = null

    val isLoading: StateFlow<Boolean> = _isLoading

    val displayedApps: StateFlow<List<AppInfo>> = combine(
        _allApps, _query, _includeSystem, _sortOrder
    ) { all, query, includeSystem, sort ->
        val filtered = if (includeSystem) all else all.filter { !it.isSystemApp }
        val queried = if (query.isBlank()) filtered else {
            val q = query.lowercase()
            filtered.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
        when (sort) {
            SortOrder.NAME -> queried.sortedBy { it.label.lowercase() }
            SortOrder.SIZE -> queried.sortedByDescending { it.sizeBytes }
            SortOrder.INSTALL_DATE -> queried.sortedByDescending { it.installTimeMs }
            SortOrder.UPDATE_DATE -> queried.sortedByDescending { it.updateTimeMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            DebugLog.i("AppManagerVM", "Loading application list...")
            try {
                val apps = BackupManager.listInstalledApps(getApplication(), includeSystem = true)
                _allApps.value = apps
                DebugLog.i("AppManagerVM", "Loaded ${apps.size} application(s)")
            } catch (e: Exception) {
                DebugLog.e("AppManagerVM", "Failed to load applications: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filter(query: String) { _query.value = query }
    fun setIncludeSystem(include: Boolean) { _includeSystem.value = include }
    fun setSort(order: SortOrder) { _sortOrder.value = order }
    fun refresh() { loadApps() }
}
