package com.vinstall.alwiz

import com.vinstall.alwiz.ui.showToast
import com.vinstall.alwiz.ui.bindExportBanner
import com.vinstall.alwiz.ui.StatusIcon
import com.vinstall.alwiz.ui.showStatusIcon
import com.vinstall.alwiz.ui.statusOk
import com.vinstall.alwiz.ui.statusWarn
import com.vinstall.alwiz.util.Haptics
import com.vinstall.alwiz.util.VersionLabel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.vinstall.alwiz.appmanager.AppManagerActivity
import com.vinstall.alwiz.backup.BackupActivity
import com.vinstall.alwiz.databinding.ActivityMainBinding
import com.vinstall.alwiz.history.InstallHistoryActivity
import com.vinstall.alwiz.model.InstallState
import com.vinstall.alwiz.model.PackageFormat
import com.vinstall.alwiz.settings.AppSettings
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.settings.InstallMode
import com.vinstall.alwiz.settings.SettingsActivity
import com.vinstall.alwiz.shizuku.ShizukuHelper
import com.vinstall.alwiz.ui.ConfirmationBottomSheet
import com.vinstall.alwiz.ui.FloatingNav
import com.vinstall.alwiz.ui.bindFloatingNav
import com.vinstall.alwiz.ui.StorageAccess
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.util.CrashHandler
import com.vinstall.alwiz.util.DebugLog
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.NotificationHelper
import com.vinstall.alwiz.util.PackageVersions
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ScaledActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val storageAccess = StorageAccess(this)

    private var shizukuPermissionPending = false
    private var isActivityResumed = false
    private var lastBatchTotal = 0
    private var lastHandledSuccess: InstallState.Success? = null
    private var lastHandledError: InstallState.Error? = null
    private var batchHadFailure = false
    private var boundFile: InstallState.FileSelected? = null
    private var passwordDialog: AlertDialog? = null

    private lateinit var queueAdapter: QueueFileAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        shizukuPermissionPending = false
        val granted = result == PackageManager.PERMISSION_GRANTED
        AppSettings.setShizukuPermissionGranted(this, granted)
        val msg = if (granted) getString(R.string.shizuku_granted) else getString(R.string.shizuku_denied)
        showToast(this, msg)
        updateInstallModeStatus()
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateInstallModeStatus()
        if (isActivityResumed) checkAndRequestShizukuPermission()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        updateInstallModeStatus()
    }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        when {
            uris.isEmpty() -> Unit
            uris.size == 1 -> viewModel.onFileSelected(uris[0])
            else -> viewModel.buildQueueItems(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindExportBanner(binding.root.findViewById(R.id.export_banner))
        binding.root.applySystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        bindFloatingNav(binding.floatingNav, FloatingNav.Tab.INSTALL)

        NotificationHelper.createChannel(this)
        requestNotificationPermissionIfNeeded()
        CrashHandler.showCrashDialogIfNeeded(this)

        setupQueueRecycler()

        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)

        binding.btnSelect.setOnClickListener { pickFiles() }
        binding.layoutEmptyState.setOnClickListener { pickFiles() }

        binding.btnInstall.setOnClickListener { handleInstallClick() }
        binding.btnCancel.setOnClickListener { viewModel.cancelInstall() }

        binding.btnSelectSplits.setOnClickListener {
            binding.btnSelectSplits.isEnabled = false
            binding.btnSelectSplits.setText(R.string.select_splits_loading)
            lifecycleScope.launch {
                viewModel.ensureSplitsLoaded()
                render()
                showSplitPicker()
            }
        }

        binding.rowInstallMode.setOnClickListener { handleModeRowClick() }
        binding.rowAppManager.setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }
        binding.rowExport.setOnClickListener {
            startActivity(Intent(this, BackupActivity::class.java))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render() } }
                launch { viewModel.queueActive.collect { render() } }
                launch {
                    viewModel.queueItems.collect { items ->
                        queueAdapter.sync(items)
                        render()
                    }
                }
                launch { viewModel.currentFile.collect { render() } }
                launch { viewModel.batchProgress.collect { render() } }
                launch { viewModel.availableSplits.collect { render() } }
                launch { viewModel.selectedSplits.collect { render() } }
            }
        }

        updateInstallModeStatus()
        DebugLog.i("MainActivity", "Application started")

        intent?.data?.let { uri ->
            if (savedInstanceState == null) viewModel.onFileSelected(uri)
        }
    }

    private fun pickFiles() {
        filePicker.launch(arrayOf("*/*"))
    }

    private fun setupQueueRecycler() {
        queueAdapter = QueueFileAdapter(
            items = mutableListOf(),
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) },
            onItemClick = { item -> showBatchApkvPasswordDialog(item) }
        )
        itemTouchHelper = ItemTouchHelper(QueueDragCallback(queueAdapter))
        itemTouchHelper.attachToRecyclerView(binding.recyclerQueue)
        binding.recyclerQueue.layoutManager = LinearLayoutManager(this)
        binding.recyclerQueue.adapter = queueAdapter
        binding.recyclerQueue.itemAnimator = null
    }

    private fun handleInstallClick() {
        if (viewModel.queueActive.value) {
            val ordered = queueAdapter.getOrderedItems()
            if (ordered.isEmpty() || ordered.any { it.isEncryptedApkv }) return
            val hasXapk = ordered.any { PackageFormat.fromFileName(it.displayName) == PackageFormat.XAPK }
            if (hasXapk && lacksAllFilesAccess()) {
                storageAccess.promptAllFilesAccess(R.string.storage_permission_rationale)
                return
            }
            lastBatchTotal = ordered.size
            if (!AppSettings.isConfirmInstallEffective(this)) {
                viewModel.enqueueFiles(ordered)
                return
            }
            DialogHelper.showConfirmation(
                activity = this,
                title = getString(R.string.confirm_install_title),
                message = getString(R.string.confirm_install_batch_message, ordered.size),
                positiveLabel = getString(R.string.install),
                negativeLabel = getString(R.string.cancel),
                appInstallInfo = null,
                onConfirm = { viewModel.enqueueFiles(ordered) }
            )
            return
        }
        handleSingleInstallClick()
    }

    private fun handleSingleInstallClick() {
        val file = viewModel.currentFile.value ?: return
        if (file.format == PackageFormat.XAPK && lacksAllFilesAccess()) {
            storageAccess.promptAllFilesAccess(R.string.storage_permission_rationale)
            return
        }
        if (!AppSettings.isConfirmInstallEffective(this)) {
            viewModel.install()
            return
        }
        val installInfo = if (file.packageName.isNotEmpty()) {
            val installed = PackageVersions.installed(this, file.packageName)
            ConfirmationBottomSheet.AppInstallInfo(
                icon = file.appIcon,
                appLabel = file.appLabel,
                packageName = file.packageName,
                versionName = file.versionName,
                versionCode = file.versionCode,
                installedVersionName = installed?.first,
                installedVersionCode = installed?.second,
                minSdk = file.minSdk,
                targetSdk = file.targetSdk
            )
        } else null
        DialogHelper.showConfirmation(
            activity = this,
            title = getString(R.string.confirm_install_title),
            message = getString(R.string.confirm_install_message),
            positiveLabel = getString(R.string.install),
            negativeLabel = getString(R.string.cancel),
            appInstallInfo = installInfo,
            onConfirm = { viewModel.install() }
        )
    }

    private fun lacksAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    private fun handleModeRowClick() {
        val needsShizukuGrant = AppSettings.getInstallMode(this) == InstallMode.SHIZUKU &&
            ShizukuHelper.isAvailable() && !ShizukuHelper.isGranted()
        if (needsShizukuGrant) {
            requestShizukuPermissionManually()
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_request_shizuku)?.isVisible =
            AppSettings.getInstallMode(this) == InstallMode.SHIZUKU
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_request_shizuku -> {
                requestShizukuPermissionManually()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkAndRequestShizukuPermission() {
        if (AppSettings.getInstallMode(this) != InstallMode.SHIZUKU) return
        if (!ShizukuHelper.isAvailable()) return
        if (ShizukuHelper.isGranted()) return
        if (shizukuPermissionPending) return
        if (ShizukuHelper.shouldShowRationale()) return

        shizukuPermissionPending = true
        if (!ShizukuHelper.requestPermission(shizukuPermissionListener)) {
            shizukuPermissionPending = false
        }
    }

    private fun requestShizukuPermissionManually() {
        if (!ShizukuHelper.isAvailable()) {
            showToast(this, getString(R.string.shizuku_not_available_toast))
            return
        }
        if (ShizukuHelper.isGranted()) {
            showToast(this, getString(R.string.shizuku_already_granted_toast))
            updateInstallModeStatus()
            return
        }
        shizukuPermissionPending = true
        if (!ShizukuHelper.requestPermission(shizukuPermissionListener)) {
            shizukuPermissionPending = false
            showToast(this, getString(R.string.shizuku_not_available_toast))
        }
    }

    private fun colorOnSurfaceVariant(): Int = MaterialColors.getColor(
        binding.root,
        com.google.android.material.R.attr.colorOnSurfaceVariant
    )

    private fun colorError(): Int = MaterialColors.getColor(
        binding.root,
        androidx.appcompat.R.attr.colorError
    )

    private fun render() {
        val state = viewModel.state.value
        val queueMode = viewModel.queueActive.value
        val queue = viewModel.queueItems.value
        val batch = viewModel.batchProgress.value
        val file = viewModel.currentFile.value

        val batchRunning = batch != null
        if (batch != null) lastBatchTotal = batch.total
        val working = state is InstallState.Analyzing || state is InstallState.Installing
        val loading = state is InstallState.FileLoading
        val busy = batchRunning || working || loading

        val showQueue = queueMode
        val showLoading = !showQueue && loading
        val showFile = !showQueue && !loading && file != null
        val showEmpty = !showQueue && !loading && file == null

        binding.layoutEmptyState.isVisible = showEmpty
        binding.layoutLoading.isVisible = showLoading
        binding.layoutQueueInfo.isVisible = showQueue
        binding.layoutFileInfo.isVisible = showFile

        if (showQueue) {
            binding.textQueueLabel.text = if (queue.isEmpty()) {
                getString(R.string.analyzing)
            } else {
                getString(R.string.queue_label, queue.size)
            }
            binding.textQueueHint.isVisible = !batchRunning
            val lockedCount = queue.count { it.isEncryptedApkv }
            binding.textQueueWarning.isVisible = lockedCount > 0 && !batchRunning
            binding.textQueueWarning.text = getString(R.string.queue_locked_hint, lockedCount)
            queueAdapter.setLocked(batchRunning, if (batch != null) batch.current - 1 else -1)
        } else {
            queueAdapter.setLocked(false)
        }

        if (showFile && file != null) {
            if (boundFile !== file) {
                bindFile(file)
                boundFile = file
            }
            binding.btnSelectSplits.isVisible = file.hasSplits
            binding.btnSelectSplits.isEnabled = !busy
            val available = viewModel.availableSplits.value
            binding.btnSelectSplits.text = if (available.isNotEmpty()) {
                getString(R.string.select_splits_count, viewModel.selectedSplits.value.size, available.size)
            } else {
                getString(R.string.select_splits)
            }
        } else {
            boundFile = null
            binding.btnSelectSplits.isVisible = false
        }

        var statusText: String? = null
        var statusColor = colorOnSurfaceVariant()
        var showProgress = false
        var statusIcon: StatusIcon? = null

        when (state) {
            is InstallState.Analyzing -> {
                showProgress = true
                binding.progressBar.isIndeterminate = true
                statusText = getString(R.string.analyzing)
            }
            is InstallState.Installing -> {
                showProgress = true
                if (state.progress >= 0f) {
                    val pct = (state.progress * 100).toInt()
                    binding.progressBar.isIndeterminate = false
                    binding.progressBar.setProgressCompat(pct, true)
                    statusText = "${state.step} ($pct%)"
                } else {
                    binding.progressBar.isIndeterminate = true
                    statusText = state.step
                }
            }
            is InstallState.Success -> {
                statusText = getString(R.string.install_success)
                statusColor = statusOk(this)
                statusIcon = StatusIcon.SUCCESS
                if (!batchRunning && lastHandledSuccess !== state) {
                    lastHandledSuccess = state
                    Haptics.success(this)
                    showInstallSuccessSnackbar(state.packageName)
                }
            }
            is InstallState.Error -> {
                statusText = getString(R.string.install_failed, state.message)
                statusColor = colorError()
                statusIcon = StatusIcon.ERROR
                if (batchRunning) batchHadFailure = true
                if (!batchRunning && lastHandledError !== state) {
                    lastHandledError = state
                    Haptics.failure(this)
                }
            }
            is InstallState.Cancelled -> {
                statusText = state.reason
                statusColor = statusWarn(this)
                statusIcon = StatusIcon.CANCELLED
                lastBatchTotal = 0
            }
            is InstallState.PasswordRequired -> showPasswordDialog(state)
            is InstallState.Idle -> {
                if (lastBatchTotal > 0 && !batchRunning && !queueMode) {
                    val total = lastBatchTotal
                    lastBatchTotal = 0
                    if (batchHadFailure) Haptics.failure(this) else Haptics.success(this)
                    batchHadFailure = false
                    showBatchFinishedSnackbar(total)
                }
            }
            else -> Unit
        }

        if (state !is InstallState.PasswordRequired) {
            passwordDialog?.dismiss()
            passwordDialog = null
        }

        binding.progressBar.isVisible = showProgress
        binding.textStatus.isVisible = statusText != null
        binding.textStatus.text = statusText.orEmpty()
        binding.textStatus.setTextColor(statusColor)
        binding.textStatus.showStatusIcon(statusIcon, statusColor)
        binding.textBatchProgress.isVisible = batch != null
        if (batch != null) {
            val label = if (batch.label.isNotEmpty()) " · ${batch.label}" else ""
            binding.textBatchProgress.text =
                getString(R.string.batch_progress, batch.current, batch.total) + label
        }
        binding.btnCancel.isVisible = working || batchRunning
        binding.layoutStatus.isVisible =
            showProgress || statusText != null || batch != null || binding.btnCancel.isVisible

        binding.btnSelect.isEnabled = !busy

        binding.btnInstall.text = when {
            queueMode && queue.size > 1 -> getString(R.string.install_queue, queue.size)
            !queueMode && (state is InstallState.Error || state is InstallState.Cancelled) ->
                getString(R.string.retry)
            else -> getString(R.string.install)
        }
        binding.btnInstall.isEnabled = when {
            queueMode -> !batchRunning && queue.isNotEmpty() && queue.none { it.isEncryptedApkv }
            else -> !busy && file != null && file.format != PackageFormat.UNKNOWN &&
                (state is InstallState.FileSelected ||
                    state is InstallState.Error ||
                    state is InstallState.Cancelled)
        }
    }

    private fun bindFile(file: InstallState.FileSelected) {
        binding.textFileName.text = file.appLabel.ifEmpty { file.name }

        if (file.appIcon != null) {
            binding.imageAppIcon.setImageBitmap(file.appIcon)
            binding.imageAppIcon.isVisible = true
        } else {
            binding.imageAppIcon.isVisible = false
        }

        val titlesParams = binding.layoutFileTitles.layoutParams as android.view.ViewGroup.MarginLayoutParams
        titlesParams.marginStart = if (file.appIcon != null) {
            (12 * resources.displayMetrics.density).toInt()
        } else {
            0
        }
        binding.layoutFileTitles.layoutParams = titlesParams

        if (file.packageName.isNotEmpty()) {
            binding.textPackageMeta.text =
                getString(R.string.package_meta, file.packageName, file.versionName)
            binding.textPackageMeta.isVisible = true
        } else {
            binding.textPackageMeta.isVisible = false
        }

        if (file.appLabel.isNotEmpty()) {
            binding.textFilePath.text = file.name
            binding.textFilePath.isVisible = true
        } else {
            binding.textFilePath.isVisible = false
        }

        binding.textFormat.text = if (file.format == PackageFormat.UNKNOWN) {
            getString(R.string.unknown)
        } else {
            file.format.label
        }
        binding.textSize.text = FileUtil.formatSize(file.size)

        if (file.hash.isNotEmpty()) {
            binding.textHash.text = file.hash
            binding.layoutHash.isVisible = true
        } else {
            binding.layoutHash.isVisible = false
        }
    }

    private fun showPasswordDialog(state: InstallState.PasswordRequired) {
        if (passwordDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_apkv_password, null)
        val layoutPassword = dialogView.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.edit_password)

        val title = state.label.ifEmpty { state.fileName }
        val subtitle = if (state.packageName.isNotEmpty() && state.versionName.isNotEmpty()) {
            "${state.packageName} · ${VersionLabel.format(state.versionName)}"
        } else {
            state.packageName
        }
        dialogView.findViewById<android.widget.TextView>(R.id.text_apkv_info).text = subtitle

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apkv_unlock), null)
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> viewModel.reset() }
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = editPassword.text?.toString().orEmpty()
                if (password.isBlank()) {
                    layoutPassword.error = getString(R.string.apkv_password_empty)
                    return@setOnClickListener
                }
                layoutPassword.error = null
                passwordDialog = null
                dialog.dismiss()
                viewModel.submitApkvPassword(password)
            }
        }

        passwordDialog = dialog
        dialog.show()
    }

    private fun showBatchApkvPasswordDialog(item: QueueItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_apkv_password, null)
        val layoutPassword = dialogView.findViewById<TextInputLayout>(R.id.layout_password)
        val editPassword = dialogView.findViewById<TextInputEditText>(R.id.edit_password)
        val title = item.appLabel.ifEmpty { item.displayName }
        val subtitle = if (item.packageName.isNotEmpty() && item.versionName.isNotEmpty()) {
            "${item.packageName} · ${VersionLabel.format(item.versionName)}"
        } else {
            item.packageName
        }
        dialogView.findViewById<android.widget.TextView>(R.id.text_apkv_info).text = subtitle

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.apkv_unlock), null)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = editPassword.text?.toString().orEmpty()
                if (password.isBlank()) {
                    layoutPassword.error = getString(R.string.apkv_password_empty)
                    return@setOnClickListener
                }
                layoutPassword.error = null
                viewModel.submitBatchApkvPassword(item.uri, password) { valid ->
                    if (valid) {
                        dialog.dismiss()
                    } else {
                        layoutPassword.error = getString(R.string.apkv_wrong_password)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showSplitPicker() {
        val splits = viewModel.availableSplits.value
        if (splits.isEmpty()) return
        val selected = viewModel.selectedSplits.value
        val checkedItems = BooleanArray(splits.size) { selected.contains(splits[it]) }

        lateinit var dialog: AlertDialog

        fun refreshPositive() {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                viewModel.selectedSplits.value.isNotEmpty()
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_splits))
            .setMultiChoiceItems(splits.toTypedArray(), checkedItems) { _, which, isChecked ->
                viewModel.toggleSplit(splits[which], isChecked)
                refreshPositive()
            }
            .setPositiveButton(getString(R.string.ok), null)
            .setNeutralButton(getString(R.string.select_all), null)
            .setNegativeButton(getString(R.string.deselect_all), null)
            .create()

        dialog.setOnShowListener {
            val list = dialog.listView
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                viewModel.selectAllSplits()
                for (index in splits.indices) list.setItemChecked(index, true)
                refreshPositive()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                viewModel.deselectAllSplits()
                for (index in splits.indices) list.setItemChecked(index, false)
                refreshPositive()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { dialog.dismiss() }
            refreshPositive()
        }

        dialog.show()
    }

    private fun updateInstallModeStatus() {
        val mode = AppSettings.getInstallMode(this)
        val shizukuAvail = ShizukuHelper.isAvailable()
        val shizukuGranted = ShizukuHelper.isGranted()

        binding.textInstallModeStatus.text = when (mode) {
            InstallMode.NORMAL -> getString(R.string.mode_normal)
            InstallMode.ROOT -> getString(R.string.mode_root)
            InstallMode.SHIZUKU -> getString(R.string.mode_shizuku)
        }

        val detail = if (mode == InstallMode.SHIZUKU) {
            when {
                !shizukuAvail -> getString(R.string.shizuku_inactive)
                !shizukuGranted -> getString(R.string.shizuku_tap_to_grant)
                else -> getString(R.string.shizuku_active)
            }
        } else null
        binding.textInstallModeDetail.isVisible = detail != null
        binding.textInstallModeDetail.text = detail.orEmpty()

        val ready = mode != InstallMode.SHIZUKU || (shizukuAvail && shizukuGranted)
        binding.installModeDot.setBackgroundResource(
            if (ready) R.drawable.dot_active else R.drawable.dot_pending
        )
    }

    private fun showInstallSuccessSnackbar(packageName: String) {
        val snackbar = Snackbar.make(binding.root, getString(R.string.install_success), Snackbar.LENGTH_LONG)
            .setAnchorView(binding.layoutBottomBar)
        if (packageName.isNotEmpty() && packageManager.getLaunchIntentForPackage(packageName) != null) {
            snackbar.setAction(getString(R.string.open_app)) {
                packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
            }
        }
        snackbar.show()
    }

    private fun showBatchFinishedSnackbar(total: Int) {
        Snackbar.make(binding.root, getString(R.string.batch_finished, total), Snackbar.LENGTH_LONG)
            .setAnchorView(binding.layoutBottomBar)
            .setAction(getString(R.string.view_history_short)) {
                startActivity(Intent(this, InstallHistoryActivity::class.java))
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        updateInstallModeStatus()
        checkAndRequestShizukuPermission()
        invalidateOptionsMenu()
        binding.floatingNav.setDebugVisible(AppSettings.isDebugTabEnabled(this))
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
    }

    override fun onDestroy() {
        super.onDestroy()
        passwordDialog?.dismiss()
        passwordDialog = null
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        ShizukuHelper.removePermissionListener(shizukuPermissionListener)
    }
}
