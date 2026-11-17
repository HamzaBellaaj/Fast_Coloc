package com.iset.fastcolloc.ui.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

/** One activity overlay, two explicit entry points. Never waits for network data. */
class LiquidPopView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(113, 56, 216) }
    private val path = Path()
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var originX = 0f
    private var originY = 0f
    var running = false
        private set
    init { visibility = GONE; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun play(source: View, navigate: () -> Unit) {
        if (running) return
        if (!Motion.enabled(context) || width == 0 || height == 0) { navigate(); return }
        val point = IntArray(2); val own = IntArray(2)
        source.getLocationOnScreen(point); getLocationOnScreen(own)
        originX = point[0] - own[0] + source.width / 2f
        originY = point[1] - own[1] + source.height / 2f
        running = true; isClickable = true; visibility = VISIBLE
        var navigated = false
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 460; interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                if (progress >= .43f && !navigated) { navigated = true; navigate() }
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = clear()
                override fun onAnimationCancel(animation: Animator) = clear()
            })
            start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val expanding = progress < .45f
        val fraction = if (expanding) progress / .43f else 1f - (progress - .45f) / .55f
        val ease = (fraction.coerceIn(0f, 1f)).let { it * it * (3 - 2 * it) }
        val radius = hypot(width.toFloat(), height.toFloat()) * 1.28f * ease
        val travel = if (expanding) 0f else ((progress - .45f) / .55f).coerceIn(0f, 1f)
        val cx = originX + (width - originX) * travel * .65f
        val cy = originY - height * travel * .35f
        path.reset()
        val points = 72
        for (i in 0..points) {
            val a = i * PI * 2 / points
            val organic = 1 + .055 * sin(a * 3 + progress * 3) * (1 - ease)
            val x = cx + (cos(a) * radius * organic).toFloat()
            val y = cy + (sin(a) * radius * (1 + .08 * (1 - ease)) * organic).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close(); canvas.drawPath(path, paint)
    }
    private fun clear() { running = false; isClickable = false; visibility = GONE; progress = 0f; animator = null }
    fun cancel() { animator?.cancel(); clear() }
    override fun onDetachedFromWindow() { cancel(); super.onDetachedFromWindow() }
}
