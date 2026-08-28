package com.example.facedistancemonitor

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * 自定义 Button，防止 Material 主题在某些状态下改变按钮尺寸
 */
class FixedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    
    init {
        // 移除 Material 默认行为
        backgroundTintList = null
        elevation = 0f
        stateListAnimator = null
    }
    
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // 强制不设置 padding
        super.setPadding(0, 0, 0, 0)
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 获取布局参数中指定的高度
        val lp = layoutParams
        if (lp != null && lp.height > 0 && lp.height != LayoutParams.WRAP_CONTENT && 
            lp.height != LayoutParams.MATCH_PARENT) {
            // 强制使用指定的固定高度
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
