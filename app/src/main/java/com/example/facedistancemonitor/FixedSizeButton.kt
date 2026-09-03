package com.example.facedistancemonitor

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

/**
 * 固定尺寸的 Button，防止 Material 主题或父容器改变尺寸
 */
class FixedSizeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {
    
    private var fixedWidth = -1
    private var fixedHeight = -1
    
    init {
        // 从 XML 读取固定尺寸
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.FixedSizeButton)
            fixedWidth = a.getDimensionPixelSize(R.styleable.FixedSizeButton_fixedWidth, -1)
            fixedHeight = a.getDimensionPixelSize(R.styleable.FixedSizeButton_fixedHeight, -1)
            a.recycle()
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (fixedWidth > 0 && fixedHeight > 0) {
            // 使用固定尺寸，忽略父容器的测量约束
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(fixedWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(fixedHeight, MeasureSpec.EXACTLY)
            )
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
