package com.vinstall.alwiz.ui

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.vinstall.alwiz.R

class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    private var maxHeightPx = Int.MAX_VALUE

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.MaxHeightRecyclerView)
            maxHeightPx = a.getDimensionPixelSize(
                R.styleable.MaxHeightRecyclerView_maxListHeight,
                Int.MAX_VALUE
            )
            a.recycle()
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val limited = if (maxHeightPx != Int.MAX_VALUE) {
            MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        } else {
            heightSpec
        }
        super.onMeasure(widthSpec, limited)
    }
}
