package com.vinstall.alwiz.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.R
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.databinding.ItemTrustedAppBinding

class TrustableApp(val packageName: String, val label: String, val system: Boolean = false)

class TrustedAppsAdapter(
    private var apps: List<TrustableApp>,
    private val iconLoader: AppIconLoader,
    val selected: MutableSet<String>,
) : RecyclerView.Adapter<TrustedAppsAdapter.Holder>() {

    class Holder(val binding: ItemTrustedAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemTrustedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<TrustableApp>) {
        apps = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = apps[position]
        val b = holder.binding
        b.textAppName.text = app.label
        b.textPackageName.text = app.packageName
        b.checkbox.isChecked = app.packageName in selected
        updateState(b.root, b.checkbox.isChecked)
        iconLoader.load(app.packageName, b.imageIcon)
        b.root.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            val pkg = apps[pos].packageName
            if (!selected.remove(pkg)) selected.add(pkg)
            b.checkbox.isChecked = pkg in selected
            updateState(b.root, b.checkbox.isChecked)
        }
    }

    private fun updateState(root: View, checked: Boolean) {
        ViewCompat.setStateDescription(
            root,
            root.context.getString(if (checked) R.string.state_selected else R.string.state_not_selected)
        )
    }
}
