package com.vinstall.alwiz

import com.vinstall.alwiz.util.VersionLabel
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.vinstall.alwiz.util.FileUtil

data class QueueItem(
    val uri: Uri,
    val displayName: String,
    val formatLabel: String,
    val fileSize: Long = 0L,
    val appLabel: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Long = 0L,
    val appIcon: Bitmap? = null,
    val sha256: String = "",
    val isEncryptedApkv: Boolean = false,
    val apkvPassword: String? = null
)

class QueueFileAdapter(
    private val items: MutableList<QueueItem>,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onItemClick: ((QueueItem) -> Unit)? = null
) : RecyclerView.Adapter<QueueFileAdapter.ViewHolder>() {

    private var locked = false
    private var activeIndex = -1

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textNumber: TextView = itemView.findViewById(R.id.text_queue_number)
        val imageIcon: ImageView = itemView.findViewById(R.id.image_queue_icon)
        val textLabel: TextView = itemView.findViewById(R.id.text_queue_label)
        val textMeta: TextView = itemView.findViewById(R.id.text_queue_meta)
        val textHash: TextView = itemView.findViewById(R.id.text_queue_hash)
        val iconDrag: ImageView = itemView.findViewById(R.id.icon_drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        val isActive = locked && position == activeIndex
        val isDone = locked && activeIndex >= 0 && position < activeIndex
        holder.textNumber.text = if (isDone) "✓" else (position + 1).toString()
        holder.textNumber.contentDescription = if (isDone) (position + 1).toString() else null
        ViewCompat.setStateDescription(
            holder.itemView,
            when {
                isDone -> context.getString(R.string.queue_state_done)
                isActive -> context.getString(R.string.queue_state_active)
                locked -> context.getString(R.string.queue_state_waiting)
                else -> null
            }
        )
        holder.textNumber.setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
        holder.textNumber.setTextColor(
            MaterialColors.getColor(
                holder.itemView,
                if (isActive) androidx.appcompat.R.attr.colorPrimary
                else com.google.android.material.R.attr.colorOnSurfaceVariant
            )
        )
        holder.itemView.alpha = if (isDone) 0.5f else 1f

        if (item.appIcon != null) {
            holder.imageIcon.setImageBitmap(item.appIcon)
        } else {
            holder.imageIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }

        holder.textLabel.text = item.appLabel.ifEmpty { item.displayName }

        val metaParts = mutableListOf<String>()
        if (item.packageName.isNotEmpty()) metaParts += item.packageName
        if (item.versionName.isNotEmpty()) metaParts += VersionLabel.format(item.versionName)
        metaParts += item.formatLabel
        if (item.fileSize > 0) metaParts += FileUtil.formatSize(item.fileSize)
        holder.textMeta.text = metaParts.joinToString(" · ")

        val onSurfaceVariant = MaterialColors.getColor(
            holder.itemView,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )

        if (item.isEncryptedApkv) {
            holder.textHash.isVisible = true
            holder.textHash.text = context.getString(R.string.apkv_tap_to_unlock)
            holder.textHash.setTextColor(
                MaterialColors.getColor(holder.itemView, androidx.appcompat.R.attr.colorError)
            )
            if (locked) {
                holder.itemView.setOnClickListener(null)
                holder.itemView.isClickable = false
            } else {
                holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
            }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.isClickable = false
            holder.textHash.setTextColor(onSurfaceVariant)
            when {
                item.sha256.isNotEmpty() -> {
                    holder.textHash.isVisible = true
                    holder.textHash.text = "SHA-256: ${item.sha256.take(20)}…"
                }
                item.packageName.isNotEmpty() -> {
                    holder.textHash.isVisible = true
                    holder.textHash.text = "SHA-256: …"
                }
                else -> holder.textHash.isVisible = false
            }
        }

        holder.iconDrag.visibility = if (locked) View.INVISIBLE else View.VISIBLE
        if (locked) {
            holder.iconDrag.setOnTouchListener(null)
        } else {
            holder.iconDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
                false
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun onItemMoved(from: Int, to: Int) {
        if (locked) return
        if (from < 0 || to < 0 || from >= items.size || to >= items.size) return
        val moved = items.removeAt(from)
        items.add(to, moved)
        notifyItemMoved(from, to)
        notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(from - to) + 1, PAYLOAD_NUMBER)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_NUMBER)) {
            holder.textNumber.text = (position + 1).toString()
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun getOrderedItems(): List<QueueItem> = items.toList()

    fun setLocked(value: Boolean, active: Int = -1) {
        if (locked == value && activeIndex == active) return
        locked = value
        activeIndex = active
        notifyDataSetChanged()
    }

    fun sync(newItems: List<QueueItem>) {
        val sameSet = newItems.size == items.size &&
            newItems.map { it.uri }.toSet() == items.map { it.uri }.toSet()
        if (!sameSet) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
            return
        }
        val byUri = newItems.associateBy { it.uri }
        for (index in items.indices) {
            val updated = byUri[items[index].uri] ?: continue
            if (updated != items[index]) {
                items[index] = updated
                notifyItemChanged(index)
            }
        }
    }

    private companion object {
        const val PAYLOAD_NUMBER = "number"
    }
}

class QueueDragCallback(private val adapter: QueueFileAdapter) : ItemTouchHelper.Callback() {

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) =
        makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from < 0 || to < 0) return false
        adapter.onItemMoved(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun isLongPressDragEnabled(): Boolean = false
}
