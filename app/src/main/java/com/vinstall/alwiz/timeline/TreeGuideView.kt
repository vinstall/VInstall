package com.vinstall.alwiz.timeline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class TreeGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val step = 20f * density
    private val dotCenterY = 26f * density
    private val dotRadius = 5f * density
    private val ringRadius = 9f * density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * density
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private var row: TimelineRow? = null
    private var columns = 1

    fun bind(row: TimelineRow, maxDepth: Int, dotColor: Int, lineColor: Int) {
        this.row = row
        columns = maxDepth + 1
        dotPaint.color = dotColor
        ringPaint.color = dotColor
        linePaint.color = lineColor
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            (columns * step).toInt(),
            getDefaultSize(suggestedMinimumHeight, heightMeasureSpec)
        )
    }

    private fun x(column: Int) = column * step + step / 2f

    override fun onDraw(canvas: Canvas) {
        val row = row ?: return
        val h = height.toFloat()
        val cy = dotCenterY.coerceAtMost(h / 2f + 8f * density)

        row.through.forEachIndexed { column, on ->
            if (on) canvas.drawLine(x(column), 0f, x(column), h, linePaint)
        }
        if (row.kind == RowKind.SIDE) {
            val ex = x(row.depth - 1)
            canvas.drawLine(ex, 0f, ex, if (row.elbowContinues) h else cy, linePaint)
            canvas.drawLine(ex, cy, x(row.depth), cy, linePaint)
        }
        if (row.kind == RowKind.MAIN) {
            canvas.drawLine(x(row.depth), 0f, x(row.depth), cy, linePaint)
        }
        if (row.hasChildren) {
            canvas.drawLine(x(row.depth), cy, x(row.depth), h, linePaint)
        }
        canvas.drawCircle(x(row.depth), cy, dotRadius, dotPaint)
        if (row.isCurrent) canvas.drawCircle(x(row.depth), cy, ringRadius, ringPaint)
    }
}
