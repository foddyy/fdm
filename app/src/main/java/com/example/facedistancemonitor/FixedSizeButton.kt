package com.example.facedistancemonitor

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * 自定义 Button，防止 Material 主题在某些状态下改变按钮尺寸
 */
class FixedSizeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 获取布局参数中指定的高度
        val lp = layoutParams
        if (lp != null && lp.height > 0 && lp.height != LayoutParams.WRAP_CONTENT && 
            lp.height != LayoutParams.MATCH_PARENT) {
            // 强制使用指定的高度
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
