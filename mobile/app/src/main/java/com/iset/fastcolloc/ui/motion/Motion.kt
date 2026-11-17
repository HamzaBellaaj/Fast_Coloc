package com.iset.fastcolloc.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator

object Motion {
    fun enabled(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    fun press(view: View) {
        if (!enabled(view.context)) return
        view.animate().cancel()
        view.animate().scaleX(.94f).scaleY(.94f).setDuration(80).setInterpolator(DecelerateInterpolator())
            .withEndAction { view.animate().scaleX(1f).scaleY(1f).setDuration(130).withEndAction(null).start() }.start()
    }
}
