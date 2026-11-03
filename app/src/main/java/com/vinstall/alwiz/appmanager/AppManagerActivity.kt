package com.vinstall.alwiz.appmanager

import android.content.Intent
import android.os.Bundle
import android.view.Menu
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
import com.vinstall.alwiz.ui.bindExportBanner
import com.vinstall.alwiz.databinding.ActivityAppManagerBinding
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.timeline.AppTimelineActivity
import com.vinstall.alwiz.timeline.AppTimelineManager
import com.vinstall.alwiz.ui.applySystemBarInsets
import kotlinx.coroutines.launch

class AppManagerActivity : ScaledActivity() {

    private lateinit var binding: ActivityAppManagerBinding
    private val viewModel: AppManagerViewModel by viewModels()
    private lateinit var adapter: AppListAdapter
    private var listAnimated = false
    private var userRefreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindExportBanner(binding.root.findViewById(R.id.export_banner))
        binding.root.applySystemBarInsets()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = AppListAdapter(AppIconLoader(this, lifecycleScope)) { app -> openDetail(app) }
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
            viewModel.refresh()
            binding.swipeRefresh.post {
                if (!viewModel.isLoading.value) {
                    userRefreshing = false
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }

        binding.editSearch.doOnTextChanged { text, _, _, _ ->
            viewModel.filter(text?.toString().orEmpty())
        }

        binding.chipSystem.isChecked = viewModel.includeSystem.value
        binding.chipSystem.setOnCheckedChangeListener { _, checked ->
            viewModel.setIncludeSystem(checked)
        }

        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val sort = when (checkedIds.firstOrNull()) {
                R.id.chip_sort_size -> AppManagerViewModel.SortOrder.SIZE
                R.id.chip_sort_install -> AppManagerViewModel.SortOrder.INSTALL_DATE
                R.id.chip_sort_update -> AppManagerViewModel.SortOrder.UPDATE_DATE
                else -> AppManagerViewModel.SortOrder.NAME
            }
            viewModel.setSort(sort)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.displayedApps.collect { list ->
                        adapter.submitList(list) {
                            if (!listAnimated && list.isNotEmpty()) {
                                listAnimated = true
                                binding.recyclerView.scheduleLayoutAnimation()
                            }
                        }
                        supportActionBar?.subtitle =
                            resources.getQuantityString(R.plurals.app_count, list.size, list.size)
                        updateEmptyState()
                    }
                }
                launch {
                    viewModel.isLoading.collect { loading ->
                        if (!loading) userRefreshing = false
                        binding.swipeRefresh.isRefreshing = loading && userRefreshing
                        binding.progressBar.isVisible = loading && !userRefreshing
                        updateEmptyState()
                    }
                }
            }
        }

        if (savedInstanceState == null) viewModel.loadApps()
    }

    override fun onStart() {
        super.onStart()
        AppTimelineManager.syncAsync(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_manager_menu, menu)
        return true
    }

    private fun updateEmptyState() {
        val loading = viewModel.isLoading.value
        binding.textEmpty.isVisible = !loading && viewModel.displayedApps.value.isEmpty()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_timeline -> {
                startActivity(Intent(this, AppTimelineActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openDetail(app: AppInfo) {
        startActivity(
            Intent(this, AppDetailActivity::class.java).putExtra(
                AppDetailActivity.EXTRA_PACKAGE_NAME,
                app.packageName
            )
        )
    }
}
