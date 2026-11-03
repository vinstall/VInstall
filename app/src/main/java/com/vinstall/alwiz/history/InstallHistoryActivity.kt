package com.vinstall.alwiz.history

import com.vinstall.alwiz.ui.showToast
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ActivityInstallHistoryBinding
import com.vinstall.alwiz.model.HistoryStatus
import com.vinstall.alwiz.model.InstallHistoryEntry
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.settings.DialogStyle
import com.vinstall.alwiz.ui.FloatingNav
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.ui.bindFloatingNav
import com.vinstall.alwiz.util.CallerResolver
import com.vinstall.alwiz.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InstallHistoryActivity : ScaledActivity() {

    private lateinit var binding: ActivityInstallHistoryBinding
    private lateinit var adapter: InstallHistoryAdapter
    private val entries = mutableListOf<InstallHistoryEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityInstallHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        bindFloatingNav(binding.floatingNav, FloatingNav.Tab.LOG)
        supportActionBar?.title = getString(R.string.history_title)

        adapter = InstallHistoryAdapter(
            entries = entries,
            onItemClick = { entry -> showDetailDialog(entry) },
            onDeleteClick = { entry -> deleteEntry(entry) }
        )

        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        loadHistory()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear_history)?.isEnabled = entries.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    private fun confirmClear() {
        DialogHelper.showConfirmation(
            activity = this,
            title = getString(R.string.history_clear_title),
            message = getString(R.string.history_clear_confirm),
            positiveLabel = getString(R.string.history_clear_yes),
            negativeLabel = getString(R.string.cancel),
            isDangerous = true,
            onConfirm = {
                InstallHistoryManager.clear(this)
                entries.clear()
                adapter.notifyDataSetChanged()
                updateEmptyState()
                showToast(this, getString(R.string.history_cleared))
            }
        )
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { InstallHistoryManager.getAll(this@InstallHistoryActivity) }
            entries.clear()
            entries.addAll(loaded)
            adapter.notifyDataSetChanged()
            updateEmptyState()
            binding.recyclerHistory.scheduleLayoutAnimation()
        }
    }

    private fun updateEmptyState() {
        val isEmpty = entries.isEmpty()
        binding.layoutEmpty.isVisible = isEmpty
        binding.recyclerHistory.isVisible = !isEmpty
        invalidateOptionsMenu()
    }

    private fun deleteEntry(entry: InstallHistoryEntry) {
        if (!adapter.remove(entry)) return
        InstallHistoryManager.remove(this, entry.id)
        updateEmptyState()
    }

    private fun showDetailDialog(entry: InstallHistoryEntry) {
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

        val statusLabel = when (entry.status) {
            HistoryStatus.SUCCESS -> getString(R.string.history_status_success)
            HistoryStatus.FAILED -> getString(R.string.history_status_failed)
            HistoryStatus.CANCELLED -> getString(R.string.history_status_cancelled)
            HistoryStatus.BLOCKED -> getString(R.string.history_status_blocked)
        }

        val durationText = if (entry.durationMs < 1000) {
            "${entry.durationMs} ms"
        } else {
            String.format(Locale.getDefault(), "%.1f s", entry.durationMs / 1000.0)
        }

        val bottomSheet = AppSettings.getDialogStyle(this) == DialogStyle.BOTTOM_SHEET
        val source = entry.sourcePackage?.takeIf { it.isNotEmpty() }
        val detail = buildString {
            appendLine("${getString(R.string.history_detail_status)}: $statusLabel")
            appendLine("${getString(R.string.history_detail_package)}: ${entry.packageName.ifEmpty { "-" }}")
            source?.takeIf { !bottomSheet }?.let { source ->
                appendLine("${getString(R.string.history_detail_source)}: ${CallerResolver.labelOf(this@InstallHistoryActivity, source)} ($source)")
            }
            appendLine("${getString(R.string.history_detail_version)}: ${entry.versionName.ifEmpty { "-" }}")
            appendLine("${getString(R.string.history_detail_format)}: ${entry.format}")
            appendLine("${getString(R.string.history_detail_size)}: ${FileUtil.formatSize(entry.fileSize)}")
            appendLine("${getString(R.string.history_detail_mode)}: ${entry.installMode}")
            appendLine("${getString(R.string.history_detail_duration)}: $durationText")
            appendLine("${getString(R.string.history_detail_time)}: ${dateFormat.format(Date(entry.timestamp))}")
            if (entry.detail.isNotEmpty()) {
                appendLine()
                appendLine("${getString(R.string.history_detail_info)}:")
                append(entry.detail)
            }
        }

        DialogHelper.showInfo(
            activity = this,
            title = entry.displayTitle(this),
            body = detail,
            closeLabel = getString(R.string.crash_log_close),
            appInfo = CallerResolver.resolve(this, entry.packageName.ifEmpty { null }, false),
            caller = source?.let {
                CallerResolver.resolve(this, it, it in AppSettings.getTrustedCallers(this))
            },
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_history -> {
                confirmClear()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
