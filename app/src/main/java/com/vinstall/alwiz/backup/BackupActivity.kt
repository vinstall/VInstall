package com.vinstall.alwiz.backup

import com.vinstall.alwiz.ui.showToast
import com.vinstall.alwiz.ui.exportStatusText
import com.vinstall.alwiz.ui.StatusIcon
import com.vinstall.alwiz.ui.showStatusIcon
import com.vinstall.alwiz.ui.statusOk
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.R
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.appmanager.AppListAdapter
import com.vinstall.alwiz.appmanager.AppManagerViewModel
import com.vinstall.alwiz.databinding.ActivityBackupBinding
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.ui.ExportDialog
import com.vinstall.alwiz.ui.StorageAccess
import com.vinstall.alwiz.ui.applySystemBarInsets
import kotlinx.coroutines.launch
import java.io.File

class BackupActivity : ScaledActivity() {

    private lateinit var binding: ActivityBackupBinding
    private val listViewModel: AppManagerViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()
    private val storageAccess = StorageAccess(this)
    private lateinit var adapter: AppListAdapter
    private var listAnimated = false
    private var userRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = AppListAdapter(AppIconLoader(this, lifecycleScope)) { app -> beginExport(app) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.itemAnimator = null

        binding.swipeRefresh.setColorSchemeColors(
            MaterialColors.getColor(binding.swipeRefresh, androidx.appcompat.R.attr.colorPrimary)
        )
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(
            MaterialColors.getColor(
                binding.swipeRefresh,
                com.google.android.material.R.attr.colorSurfaceContainerHigh
            )
        )
        binding.swipeRefresh.setOnRefreshListener {
            userRefreshing = true
            listViewModel.refresh()
            binding.swipeRefresh.post {
                if (!listViewModel.isLoading.value) {
                    userRefreshing = false
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        binding.editSearch.doOnTextChanged { text, _, _, _ ->
            listViewModel.filter(text?.toString().orEmpty())
        }

        binding.chipSystem.isChecked = listViewModel.includeSystem.value
        binding.chipSystem.setOnCheckedChangeListener { _, checked ->
            listViewModel.setIncludeSystem(checked)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    listViewModel.displayedApps.collect { list ->
                        adapter.submitList(list) {
                            if (!listAnimated && list.isNotEmpty()) {
                                listAnimated = true
                                binding.recyclerView.scheduleLayoutAnimation()
                            }
                        }
                        updateEmptyState()
                    }
                }
                launch {
                    listViewModel.isLoading.collect { loading ->
                        if (!loading) userRefreshing = false
                        binding.swipeRefresh.isRefreshing = loading && userRefreshing
                        binding.progressBar.isVisible = loading && !userRefreshing
                        updateEmptyState()
                    }
                }
                launch { backupViewModel.backupState.collect { renderBackupState(it) } }
            }
        }

        if (savedInstanceState == null) listViewModel.loadApps()
    }

    private fun updateEmptyState() {
        binding.textEmpty.isVisible =
            !listViewModel.isLoading.value && listViewModel.displayedApps.value.isEmpty()
    }

    private fun renderBackupState(state: BackupState) {
        val normal = MaterialColors.getColor(
            binding.root,
            com.google.android.material.R.attr.colorOnSurface
        )
        var color = normal
        var icon: StatusIcon? = null
        when (state) {
            is BackupState.Idle -> {
                binding.layoutBackupStatus.isVisible = false
                return
            }
            is BackupState.Running -> {
                binding.progressBackup.isVisible = true
                binding.progressBackup.setProgressCompat(state.percent, true)
                binding.textBackupStatus.text = exportStatusText(state)
            }
            is BackupState.Done -> {
                binding.progressBackup.isVisible = false
                binding.textBackupStatus.text = getString(R.string.backup_success, state.path)
                color = statusOk(this)
                icon = StatusIcon.SUCCESS
            }
            is BackupState.Error -> {
                binding.progressBackup.isVisible = false
                binding.textBackupStatus.text = getString(R.string.backup_failed, state.message)
                color = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError)
                icon = StatusIcon.ERROR
            }
        }
        binding.textBackupStatus.setTextColor(color)
        binding.textBackupStatus.showStatusIcon(icon, color)
        binding.layoutBackupStatus.isVisible = true
    }

    private fun beginExport(app: AppInfo) {
        if (backupViewModel.isRunning()) {
            showToast(this, R.string.export_in_progress)
            return
        }
        ExportDialog.show(this, app) { password ->
            storageAccess.run {
                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    "VInstall/Backups"
                )
                backupViewModel.backup(app, outputDir, password)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
