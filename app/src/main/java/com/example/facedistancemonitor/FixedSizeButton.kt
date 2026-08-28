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
    
    override fun applyInsets(insets: android.graphics.Insets) {
        // 不应用 Material Button 的默认 insets
        // 这样可以防止按钮尺寸被 Material 主题改变
    }
    
    override fun setUseMaterialThemeColors(useMaterialThemeColors: Boolean) {
        // 禁用 Material 主题颜色，防止 Material 改变按钮样式
        super.setUseMaterialThemeColors(false)
    }
}
