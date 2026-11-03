package com.vinstall.alwiz.blocked

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.R
import com.vinstall.alwiz.appmanager.AppIconLoader
import com.vinstall.alwiz.databinding.ItemBlockedAppBinding
import com.vinstall.alwiz.settings.BlockedCallers
import com.vinstall.alwiz.util.CallerResolver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedAppsAdapter(
    private val entries: MutableList<BlockedCallers.Entry>,
    private val iconLoader: AppIconLoader,
    private val onUnblock: (BlockedCallers.Entry) -> Unit,
) : RecyclerView.Adapter<BlockedAppsAdapter.Holder>() {

    class Holder(val binding: ItemBlockedAppBinding) : RecyclerView.ViewHolder(binding.root)

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemBlockedAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = entries.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = entries[position]
        val b = holder.binding
        val ctx = b.root.context
        b.textAppName.text = CallerResolver.labelOf(ctx, entry.packageName)
        b.textPackageName.text = entry.packageName
        b.textExpiry.text = if (entry.permanent) {
            ctx.getString(R.string.blocked_permanent_label)
        } else {
            ctx.getString(R.string.blocked_until, dateFormat.format(Date(entry.expiresAt)))
        }
        iconLoader.load(entry.packageName, b.imageIcon)
        b.btnUnblock.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onUnblock(entries[pos])
        }
    }

    fun remove(entry: BlockedCallers.Entry) {
        val index = entries.indexOfFirst { it.packageName == entry.packageName }
        if (index < 0) return
        entries.removeAt(index)
        notifyItemRemoved(index)
    }

    fun replaceAll(items: List<BlockedCallers.Entry>) {
        entries.clear()
        entries.addAll(items)
        notifyDataSetChanged()
    }
}
