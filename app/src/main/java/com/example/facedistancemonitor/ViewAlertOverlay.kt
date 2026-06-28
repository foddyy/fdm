package com.example.facedistancemonitor

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.example.facedistancemonitor.databinding.LayoutAlertOverlayBinding

class ViewAlertOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: LayoutAlertOverlayBinding = LayoutAlertOverlayBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    init {
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

        views.forEach { view ->
            view.visibility = View.VISIBLE
        }

        startBlinking(views)
    }

    private fun startBlinking(views: List<View>) {
        val brightAlpha = 255
        val dimAlpha = 60
        var isBright = true

        val blinkRunnable = object : Runnable {
            override fun run() {
                if (isBright) {
                    views.forEach { view ->
                        view.background?.alpha = dimAlpha
                    }
                } else {
                    views.forEach { view ->
                        view.background?.alpha = brightAlpha
                    }
                }
                isBright = !isBright
                postDelayed(this, 200)
            }
        }
        post(blinkRunnable)
    }
}
