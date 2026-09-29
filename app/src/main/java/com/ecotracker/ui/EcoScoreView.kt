package com.ecotracker.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.ecotracker.R

/**
 * Circular ring that visualises the user's Eco Score as an arc from 0 to [maxValue].
 * Drawing only — text (score number, "/100", status) is layered on top via XML
 * TextViews for simpler typography control.
 */
class EcoScoreView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var maxValue: Int = 100
    private var animatedValue: Float = 0f

    private val strokeWidth = resources.getDimension(R.dimen.eco_score_ring_stroke)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.eco_score_ring_stroke)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.track_light)
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.eco_score_ring_stroke)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.fresh_green)
    }

    private val arcBounds = RectF()
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = strokeWidth / 2f
        arcBounds.set(inset, inset, w - inset, h - inset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(arcBounds, -90f, 360f, false, trackPaint)
        val sweep = 360f * (animatedValue / maxValue.coerceAtLeast(1))
        canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint)
    }

    /**
     * Animates the ring to [score] out of [max]. Subsequent calls animate smoothly
     * from whatever the ring is currently showing, so incremental updates (e.g.
     * after logging an activity) read as "the score just went up" rather than a
     * jarring reset back to zero.
     */
    fun setScore(score: Int, max: Int = 100, animate: Boolean = true) {
        maxValue = max
        val target = score.toFloat().coerceIn(0f, max.toFloat())
        animator?.cancel()

        if (!animate) {
            animatedValue = target
            invalidate()
            return
        }

        animator = ValueAnimator.ofFloat(animatedValue, target).apply {
            duration = 900L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                this@EcoScoreView.animatedValue = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
