package com.idt.widget.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.idt.widget.R

/**
 * Anel de status animado. Mostra a fração ok/online (0..1) como um arco
 * progressivo com cor dinâmica (verde = ok, âmbar = parcial, vermelho = down).
 */
class StatusRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        color = Color.argb(40, 255, 255, 255)
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(10f)
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = dp(22f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.argb(160, 255, 255, 255)
        textSize = dp(10f)
    }

    private var fraction = 0f
    private var animator: ValueAnimator? = null

    /** Define o valor-alvo (0..1) e anima do valor atual até ele. */
    fun setFraction(target: Float, animate: Boolean = true) {
        val clamped = target.coerceIn(0f, 1f)
        if (!animate) {
            fraction = clamped
            invalidate()
            return
        }
        animator?.cancel()
        animator = ValueAnimator.ofFloat(fraction, clamped).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                fraction = a.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun arcColor(): Int = when {
        fraction >= 1f -> ContextCompat.getColor(context, R.color.status_online)
        fraction >= 0.5f -> ContextCompat.getColor(context, R.color.status_partial)
        else -> ContextCompat.getColor(context, R.color.status_offline)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = dp(10f)
        val rect = RectF(stroke, stroke, w - stroke, h - stroke)

        canvas.drawArc(rect, -90f, 360f, false, trackPaint)

        arcPaint.color = arcColor()
        val sweep = 360f * fraction
        canvas.drawArc(rect, -90f, sweep, false, arcPaint)

        val centerY = h / 2f
        val pctText = "${(fraction * 100).toInt()}%"
        canvas.drawText(pctText, w / 2f, centerY + dp(7f), textPaint)
        canvas.drawText("uptime", w / 2f, centerY + dp(24f), subPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
