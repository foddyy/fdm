package com.example.facedistancemonitor

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * 自定义 Button，防止 Material 主题改变按钮尺寸
 */
class FixedButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val lp = layoutParams
        if (lp != null && lp.height > 0 && lp.height != LayoutParams.WRAP_CONTENT && 
            lp.height != LayoutParams.MATCH_PARENT) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY))
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
