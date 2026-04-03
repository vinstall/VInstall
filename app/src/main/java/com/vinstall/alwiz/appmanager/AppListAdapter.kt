package com.vinstall.alwiz.appmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.databinding.ItemAppBinding
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.util.FileUtil

class AppListAdapter(
    private val onItemClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppInfo, b: AppInfo) =
                a.label == b.label && a.versionName == b.versionName && a.sizeBytes == b.sizeBytes
        }
    }

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            binding.textAppName.text = app.label
            binding.textPackageName.text = app.packageName
            binding.textVersion.text = app.versionName
            binding.textSize.text = FileUtil.formatSize(app.sizeBytes)
            binding.chipSplit.visibility = if (app.isSplitApp)
                android.view.View.VISIBLE else android.view.View.GONE
            binding.chipSystem.visibility = if (app.isSystemApp)
                android.view.View.VISIBLE else android.view.View.GONE

            if (app.icon != null) {
                binding.imageIcon.setImageDrawable(app.icon)
            } else {
                binding.imageIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            binding.root.setOnClickListener { onItemClick(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
