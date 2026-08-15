package com.idt.widget.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.idt.widget.R

/**
 * Tira de uptime: N barras verticais, cada uma verde (online) ou vermelha (offline).
 * Representa o histórico recente de disponibilidade por endpoint.
 */
class UptimeStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var okSeries: List<Boolean> = emptyList()
    private val okPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.status_online)
    }
    private val downPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.status_offline)
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.argb(30, 255, 255, 255)
    }

    fun setOkSeries(series: List<Boolean>) {
        okSeries = series
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val n = maxOf(8, okSeries.size)
        val gap = dp(2f)
        val barW = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val ok = okSeries.getOrNull(okSeries.size - n + i) ?: true
            val paint = when {
                i < n - okSeries.size -> emptyPaint
                ok -> okPaint
                else -> downPaint
            }
            val left = i * (barW + gap)
            val radius = dp(1.5f)
            canvas.drawRoundRect(RectF(left, 0f, left + barW, h), radius, radius, paint)
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
