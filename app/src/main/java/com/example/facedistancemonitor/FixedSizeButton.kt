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
        // 强制使用指定的高度，忽略 MeasureSpec
        val lp = layoutParams
        if (lp.height != LayoutParams.MATCH_PARENT && lp.height != LayoutParams.WRAP_CONTENT) {
            val fixedHeight = lp.height
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    
    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        // 强制使用 0 padding
        super.setPadding(0, 0, 0, 0)
    }
}
