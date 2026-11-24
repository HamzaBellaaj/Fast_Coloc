package com.iset.fastcolloc.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.iset.fastcolloc.R

/** Layout-sized loading placeholder. Uses platform motion preferences, never changes data. */
class HousingSkeletonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        orientation = VERTICAL
        contentDescription = "Chargement du contenu"
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        fun block(height: Int, fraction: Float) {
            val placeholder = View(context)
            placeholder.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(ContextCompat.getColor(context, R.color.fc_surface_alt))
            }
            addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, dp(height)).apply { bottomMargin = dp(12); marginEnd = dp(((1f - fraction) * 100f).toInt()) })
            placeholder.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        block(200, 1f); block(16, .85f); block(12, .6f)
    }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        animate().cancel()
        alpha = 1f
    }
}
