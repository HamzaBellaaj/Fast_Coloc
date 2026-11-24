package com.iset.fastcolloc.ui

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import androidx.core.view.doOnDetach
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.iset.fastcolloc.R
import com.iset.fastcolloc.ui.motion.Motion

private data class BusyPresentation(val icon: Drawable?, val size: Int, val spinner: Drawable)
/** Only changes presentation; callers retain all existing enabled/permission rules. */
fun MaterialButton.showBusy(loading: Boolean) {
    val previous = getTag(R.id.fc_busy_presentation) as? BusyPresentation
    if (loading && previous == null) {
        val indicator = CircularProgressIndicator(context).apply {
            indicatorSize = (20 * resources.displayMetrics.density).toInt()
            trackThickness = (2 * resources.displayMetrics.density).toInt()
            setIndicatorColor(currentTextColor)
            isIndeterminate = true
        }
        val spinner = indicator.indeterminateDrawable ?: return
        setTag(R.id.fc_busy_presentation, BusyPresentation(icon, iconSize, spinner))
        iconSize = (20 * resources.displayMetrics.density).toInt()
        icon = spinner
        if (Motion.enabled(context)) (spinner as? Animatable)?.start()
        doOnDetach { (spinner as? Animatable)?.stop() }
    } else if (!loading && previous != null) {
        (previous.spinner as? Animatable)?.stop()
        icon = previous.icon
        iconSize = previous.size
        setTag(R.id.fc_busy_presentation, null)
    }
}
