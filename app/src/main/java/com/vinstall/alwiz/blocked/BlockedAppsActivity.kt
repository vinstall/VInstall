package com.vinstall.alwiz.blocked

import com.vinstall.alwiz.ui.showToast
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import com.vinstall.alwiz.ui.ScaledActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vinstall.alwiz.R
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.databinding.ActivityBlockedAppsBinding
import com.vinstall.alwiz.settings.BlockedCallers
import com.vinstall.alwiz.ui.FloatingNav
import com.vinstall.alwiz.ui.bindFloatingNav
import com.vinstall.alwiz.ui.applySystemBarInsets
import com.vinstall.alwiz.util.CallerResolver

class BlockedAppsActivity : ScaledActivity() {

    private lateinit var binding: ActivityBlockedAppsBinding
    private lateinit var adapter: BlockedAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        bindFloatingNav(binding.floatingNav, FloatingNav.Tab.BLOCKED)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.blocked_apps_title)

        adapter = BlockedAppsAdapter(
            entries = mutableListOf(),
            iconLoader = AppIconLoader(this, lifecycleScope),
            onUnblock = { entry ->
                BlockedCallers.unblock(this, entry.packageName)
                adapter.remove(entry)
                updateEmptyState()
                showToast(this, getString(R.string.blocked_unblocked_toast, CallerResolver.labelOf(this, entry.packageName)))
            }
        )
        binding.recyclerBlocked.layoutManager = LinearLayoutManager(this)
        binding.recyclerBlocked.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter.replaceAll(BlockedCallers.all(this))
        updateEmptyState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateEmptyState() {
        val isEmpty = adapter.itemCount == 0
        binding.layoutEmpty.isVisible = isEmpty
        binding.recyclerBlocked.isVisible = !isEmpty
    }
}
