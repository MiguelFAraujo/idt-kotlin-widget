package com.idt.widget.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.idt.widget.R

/**
 * Gráfico de linha de latência (ms) ao longo do tempo.
 * Desenha um sparkline com gradiente e linha de média pontilhada.
 */
class LatencyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var values: List<Long> = emptyList()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.status_online)
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(30, 255, 255, 255)
    }
    private val avgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(140, 255, 255, 255)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
    }

    fun setValues(v: List<Long>) {
        values = v
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // grade horizontal
        for (i in 0..3) {
            val y = h * i / 3f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (values.size < 2) {
            canvas.drawText("sem dados", w / 2f, h / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = dp(12f)
                color = Color.argb(120, 255, 255, 255)
            })
            return
        }

        val maxV = maxOf(1L, values.maxOrNull() ?: 1L)
        val pad = dp(4f)
        val stepX = (w - 2 * pad) / (values.size - 1)

        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = pad + i * stepX
            val y = h - pad - (v.toFloat() / maxV) * (h - 2 * pad)
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, h)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(pad + (values.size - 1) * stepX, h)
        fill.close()

        fillPaint.shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h,
            Color.argb(70, 76, 175, 80),
            Color.TRANSPARENT,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawPath(fill, fillPaint)
        canvas.drawPath(line, linePaint)

        val avg = values.average().toFloat()
        val avgY = h - pad - (avg / maxV) * (h - 2 * pad)
        canvas.drawLine(pad, avgY, w - pad, avgY, avgPaint)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
