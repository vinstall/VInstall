package com.vinstall.alwiz.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ActivityCrashLogBinding
import com.vinstall.alwiz.model.CrashEntry
import com.vinstall.alwiz.settings.DialogHelper
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.util.CrashHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CrashLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCrashLogBinding
    private lateinit var adapter: CrashLogAdapter
    private val entries = mutableListOf<CrashEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityCrashLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.crash_log_title)

        adapter = CrashLogAdapter(
            entries = entries,
            onItemClick = { showDetail(it) },
            onDeleteClick = { deleteEntry(it) }
        )
        binding.recyclerCrash.layoutManager = LinearLayoutManager(this)
        binding.recyclerCrash.adapter = adapter

        loadEntries()
    }

    private fun loadEntries() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { CrashHandler.readEntries(this@CrashLogActivity) }
            entries.clear()
            entries.addAll(loaded)
            adapter.notifyDataSetChanged()
            updateEmptyState()
            binding.recyclerCrash.scheduleLayoutAnimation()
        }
    }

    private fun updateEmptyState() {
        val isEmpty = entries.isEmpty()
        binding.layoutEmpty.isVisible = isEmpty
        binding.recyclerCrash.isVisible = !isEmpty
        supportActionBar?.subtitle =
            if (isEmpty) null else getString(R.string.crash_log_has_entries, entries.size)
        invalidateOptionsMenu()
    }

    private fun deleteEntry(entry: CrashEntry) {
        if (!adapter.remove(entry)) return
        lifecycleScope.launch(Dispatchers.IO) { CrashHandler.removeEntry(this@CrashLogActivity, entry) }
        updateEmptyState()
    }

    private fun showDetail(entry: CrashEntry) {
        DialogHelper.showInfo(
            activity = this,
            title = entry.exceptionType,
            subtitle = listOf(entry.time, entry.device).filter { it.isNotEmpty() }.joinToString("  ·  "),
            body = entry.report,
            closeLabel = getString(R.string.crash_log_close),
            monospace = true,
            primaryLabel = getString(R.string.crash_log_copy),
            onPrimary = {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("VInstall Crash Report", entry.report))
                Toast.makeText(this, getString(R.string.log_copied), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun confirmClear() {
        DialogHelper.showConfirmation(
            activity = this,
            title = getString(R.string.crash_log_clear_title),
            message = getString(R.string.crash_log_clear_confirm),
            positiveLabel = getString(R.string.crash_log_clear_yes),
            negativeLabel = getString(R.string.cancel),
            isDangerous = true,
            onConfirm = {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { CrashHandler.clearCrashLog(this@CrashLogActivity) }
                    entries.clear()
                    adapter.notifyDataSetChanged()
                    updateEmptyState()
                    Toast.makeText(this@CrashLogActivity, getString(R.string.crash_log_cleared), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.crash_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_clear_crash)?.isEnabled = entries.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear_crash -> {
                confirmClear()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
