package com.example.facedistancemonitor

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import com.example.facedistancemonitor.databinding.LayoutAlertOverlayBinding

/**
 * ViewAlertOverlay - Full-screen overlay showing four red blinking borders
 * when face is too close to the screen (< 30cm).
 * 
 * This view is added to the WindowManager to ensure the alert is visible
 * even when the app is in the background.
 */
class ViewAlertOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: LayoutAlertOverlayBinding = LayoutAlertOverlayBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    init {
        // Make the overlay fully transparent except for the red borders
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setupBorderAnimations()
    }

    private fun setupBorderAnimations() {
        val views = listOf(
            binding.alertTop,
            binding.alertBottom,
            binding.alertLeft,
            binding.alertRight
        )

        // Ensure all borders are visible
        views.forEach { view ->
            view.visibility = View.VISIBLE
        }

        // Start continuous blinking animation
        startBlinking(views)
    }

    private fun startBlinking(views: List<View>) {
        var isBright = true
        val brightAlpha = 255
        const dimAlpha = 60

        val blinkRunnable = object : Runnable {
            override fun run() {
                if (isBright) {
                    views.forEach { view ->
                        view.background.alpha = dimAlpha
                    }
                } else {
                    views.forEach { view ->
                        view.background.alpha = brightAlpha
                    }
                }
                isBright = !isBright
                postDelayed(this, 200)
            }
        }
        post(blinkRunnable)
    }
}
