package com.example.facedistancemonitor

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton

/**
 * 自定义 Button，完全绕过 Material 主题对按钮尺寸的控制
 */
class FixedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    
    init {
        // 在初始化时移除所有 Material 默认行为
        backgroundTintList = null
        elevation = 0f
        stateListAnimator = null
        useMaterialThemeColors = false
    }
    
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // 强制不设置 padding
        super.setPadding(0, 0, 0, 0)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 获取布局参数中指定的高度
        val lp = layoutParams
        if (lp != null && lp.height > 0) {
            // 使用指定的固定高度
            val fixedHeight = lp.height
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    
    override fun setBackground(drawable: Drawable?) {
        // 允许设置背景
        super.setBackground(drawable)
    }
    
    override fun setBackgroundResource(resId: Int) {
        // 允许设置背景资源
        super.setBackgroundResource(resId)
    }
}
