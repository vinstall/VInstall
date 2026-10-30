package com.vinstall.alwiz.appmanager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.R
import com.vinstall.alwiz.databinding.ItemAppBinding
import com.vinstall.alwiz.model.AppInfo
import com.vinstall.alwiz.ui.applyPressAnimation
import com.vinstall.alwiz.util.FileUtil

class AppListAdapter(
    private val icons: AppIconLoader,
    private val onItemClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(a: AppInfo, b: AppInfo) = a.packageName == b.packageName
            override fun areContentsTheSame(a: AppInfo, b: AppInfo) =
                a.label == b.label &&
                    a.versionName == b.versionName &&
                    a.sizeBytes == b.sizeBytes &&
                    a.isSystemApp == b.isSystemApp &&
                    a.isSplitApp == b.isSplitApp
        }
    }

    inner class ViewHolder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            val context = binding.root.context
            binding.textAppName.text = app.label
            binding.textPackageName.text = app.packageName

            val meta = mutableListOf("v${app.versionName}", FileUtil.formatSize(app.sizeBytes))
            if (app.isSplitApp) meta += context.getString(R.string.tag_split)
            if (app.isSystemApp) meta += context.getString(R.string.tag_system)
            binding.textMeta.text = meta.joinToString(context.getString(R.string.separator_dot))

            icons.load(app.packageName, binding.imageIcon)
            binding.root.setOnClickListener { onItemClick(app) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.applyPressAnimation(0.98f)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
