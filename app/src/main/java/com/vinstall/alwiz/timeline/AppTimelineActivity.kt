package com.vinstall.alwiz.timeline

import com.vinstall.alwiz.ui.showToast
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vinstall.alwiz.R
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.databinding.ActivityTimelineBinding
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.util.CallerResolver
import com.vinstall.alwiz.util.FileUtil
import com.vinstall.alwiz.util.VersionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppTimelineActivity : ScaledActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    private lateinit var binding: ActivityTimelineBinding
    private lateinit var treeAdapter: TimelineTreeAdapter
    private lateinit var appAdapter: TimelineAppAdapter
    private var targetPackage: String? = null
    private var hasData = false
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityTimelineBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.timeline_title)

        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        targetPackage?.let { supportActionBar?.subtitle = it }

        treeAdapter = TimelineTreeAdapter { showNodeDetail(it) }
        appAdapter = TimelineAppAdapter(AppIconLoader(this, lifecycleScope)) { summary ->
            startActivity(
                Intent(this, AppTimelineActivity::class.java)
                    .putExtra(EXTRA_PACKAGE_NAME, summary.packageName)
            )
        }
        binding.recyclerTimeline.layoutManager = LinearLayoutManager(this)
        binding.recyclerTimeline.adapter = if (targetPackage == null) appAdapter else treeAdapter
    }

    override fun onStart() {
        super.onStart()
        load()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (targetPackage == null) menuInflater.inflate(R.menu.timeline_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear_timeline)?.isEnabled = hasData
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_clear_timeline -> {
            confirmClear()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun load() {
        val app = applicationContext
        val packageName = targetPackage
        lifecycleScope.launch {
            if (packageName == null) {
                val list = withContext(Dispatchers.IO) {
                    AppTimelineManager.syncSnapshots(app)
                    AppTimelineManager.summaries(app)
                }
                appAdapter.submit(list)
                showContent(list.isNotEmpty())
            } else {
                val nodes = withContext(Dispatchers.IO) {
                    AppTimelineManager.syncSnapshots(app)
                    AppTimelineManager.ensureBaseline(app, packageName)
                    AppTimelineManager.nodesFor(app, packageName)
                }
                nodes.lastOrNull { it.appLabel.isNotBlank() }?.let { supportActionBar?.title = it.appLabel }
                val rows = withContext(Dispatchers.Default) { TimelineTree.build(nodes) }
                treeAdapter.submit(rows)
                showContent(rows.isNotEmpty())
                if (rows.isNotEmpty()) {
                    binding.recyclerTimeline.post {
                        binding.recyclerTimeline.scrollToPosition(treeAdapter.currentPosition())
                    }
                }
            }
        }
    }

    private fun showContent(hasItems: Boolean) {
        hasData = hasItems
        binding.recyclerTimeline.isVisible = hasItems
        binding.layoutEmpty.isVisible = !hasItems
        invalidateOptionsMenu()
    }

    private fun confirmClear() {
        DialogHelper.showConfirmation(
            activity = this,
            title = getString(R.string.timeline_clear_title),
            message = getString(R.string.timeline_clear_confirm),
            positiveLabel = getString(R.string.timeline_clear_yes),
            negativeLabel = getString(R.string.cancel),
            isDangerous = true,
            onConfirm = {
                AppTimelineManager.clearAll(applicationContext)
                appAdapter.submit(emptyList())
                showContent(false)
                showToast(this, getString(R.string.timeline_cleared))
            }
        )
    }

    private fun showNodeDetail(node: TimelineNode) {
        val dash = "-"
        val body = buildString {
            appendLine("${getString(R.string.timeline_detail_event)}: ${TimelineStyle.typeLabel(this@AppTimelineActivity, node.type)}")
            appendLine("${getString(R.string.history_detail_status)}: ${TimelineStyle.outcomeLabel(this@AppTimelineActivity, node.outcome)}")
            val version = if (node.versionName.isBlank() && node.versionCode == 0L) dash else
                "${VersionLabel.format(node.versionName)} (${node.versionCode})"
            appendLine("${getString(R.string.history_detail_version)}: $version")
            if (!node.prevVersionName.isNullOrBlank() || node.prevVersionCode != null) {
                appendLine("${getString(R.string.timeline_detail_previous)}: ${VersionLabel.format(node.prevVersionName.orEmpty())} (${node.prevVersionCode ?: 0})")
            }
            appendLine("${getString(R.string.history_detail_time)}: ${dateFormat.format(Date(node.timestamp))}")
            node.installMode?.takeIf { it.isNotBlank() }?.let { appendLine("${getString(R.string.history_detail_mode)}: $it") }
            node.source?.takeIf { it.isNotBlank() }?.let {
                appendLine("${getString(R.string.history_detail_source)}: ${CallerResolver.labelOf(this@AppTimelineActivity, it)} ($it)")
            }
            node.installer?.takeIf { it.isNotBlank() }?.let { appendLine("${getString(R.string.timeline_detail_installer)}: $it") }
            node.format?.takeIf { it.isNotBlank() }?.let { appendLine("${getString(R.string.history_detail_format)}: $it") }
            if (node.fileSize > 0) appendLine("${getString(R.string.history_detail_size)}: ${FileUtil.formatSize(node.fileSize)}")
            if (node.minSdk > 0 || node.targetSdk > 0) {
                appendLine("${getString(R.string.timeline_detail_sdk)}: ${node.minSdk} / ${node.targetSdk}")
            }
            node.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                val short = signature.split(",").joinToString(", ") { it.take(16) + "…" }
                val changed = if (node.signatureChanged) " (${getString(R.string.timeline_tag_signature)})" else ""
                appendLine("${getString(R.string.timeline_detail_signature)}: $short$changed")
            }
            node.keptData?.let {
                appendLine("${getString(R.string.timeline_detail_data)}: ${getString(if (it) R.string.timeline_tag_data_kept else R.string.timeline_tag_data_erased)}")
            }
            if (node.forced) appendLine("${getString(R.string.timeline_tag_forced)}")
            node.permissionsAdded?.takeIf { it.isNotEmpty() }?.let {
                appendLine("${getString(R.string.timeline_detail_perm_added)}: ${shortPermissions(it)}")
            }
            node.permissionsRemoved?.takeIf { it.isNotEmpty() }?.let {
                appendLine("${getString(R.string.timeline_detail_perm_removed)}: ${shortPermissions(it)}")
            }
            if (node.type == TimelineType.BASELINE) {
                appendLine()
                append(getString(R.string.timeline_note_baseline))
            } else if (node.external) {
                appendLine()
                append(getString(R.string.timeline_note_external))
            }
            node.detail?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("${getString(R.string.history_detail_info)}:")
                append(it)
            }
        }.trimEnd()

        DialogHelper.showInfo(
            activity = this,
            title = node.appLabel.ifBlank { node.packageName },
            body = body,
            closeLabel = getString(R.string.crash_log_close),
            subtitle = node.packageName
        )
    }

    private fun shortPermissions(list: List<String>): String =
        list.joinToString(", ") { it.removePrefix("android.permission.") }
}
