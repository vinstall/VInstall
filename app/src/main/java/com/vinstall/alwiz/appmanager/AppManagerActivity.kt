package com.vinstall.alwiz.appmanager

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ActivityAppManagerBinding
import com.vinstall.alwiz.model.AppInfo
import kotlinx.coroutines.launch

class AppManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppManagerBinding
    private val viewModel: AppManagerViewModel by viewModels()

    private val adapter = AppListAdapter { app ->
        openDetail(app)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setHasFixedSize(true)
        binding.recyclerView.itemAnimator = null

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filter(newText.orEmpty())
                return true
            }
        })

        binding.switchSystem.setOnCheckedChangeListener { _, checked ->
            viewModel.setIncludeSystem(checked)
        }

        binding.chipGroupSort.setOnCheckedStateChangeListener { _, checkedIds ->
            val sort = when (checkedIds.firstOrNull()) {
                R.id.chip_sort_name -> AppManagerViewModel.SortOrder.NAME
                R.id.chip_sort_size -> AppManagerViewModel.SortOrder.SIZE
                R.id.chip_sort_install -> AppManagerViewModel.SortOrder.INSTALL_DATE
                R.id.chip_sort_update -> AppManagerViewModel.SortOrder.UPDATE_DATE
                else -> AppManagerViewModel.SortOrder.NAME
            }
            viewModel.setSort(sort)
        }

        lifecycleScope.launch {
            viewModel.displayedApps.collect { list ->
                adapter.submitList(list)
                binding.textCount.text = resources.getQuantityString(R.plurals.app_count, list.size, list.size)
            }
        }

        lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }

        viewModel.loadApps()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.app_manager_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_refresh -> { viewModel.refresh(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openDetail(app: AppInfo) {
        val intent = Intent(this, AppDetailActivity::class.java).apply {
            putExtra(AppDetailActivity.EXTRA_PACKAGE_NAME, app.packageName)
        }
        startActivity(intent)
    }
}
