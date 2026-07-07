package com.example.facedistancemonitor

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * PIN码输入对话框 — 使用DialogFragment确保正确显示
 */
class PinDialog(
    private val mode: Mode,
    private val onSuccess: () -> Unit
) : DialogFragment() {

    enum class Mode { SET, VERIFY }

    private lateinit var pinManager: PinManager
    private lateinit var tvError: TextView
    private var currentInput = ""
    private var dotViews = arrayOf<TextView?>(null, null, null, null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 使用FrameLayout包裹整个内容
        val root = FrameLayout(requireContext())
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // 创建PIN输入面板
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        pinManager = PinManager(requireContext())

        // 标题
        val tvTitle = TextView(requireContext()).apply {
            text = if (mode == Mode.SET) "请设置4位PIN码" else "请输入PIN码"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        panel.addView(tvTitle)

        // 圆点指示器
        val dotsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 24)
        }
        for (i in 0..3) {
            val dot = TextView(requireContext()).apply {
                text = "●"
                textSize = 24f
                setTextColor(Color.LTGRAY)
                setPadding(8, 0, 8, 0)
            }
            dotsLayout.addView(dot)
            dotViews[i] = dot
        }
        panel.addView(dotsLayout)

        // 数字网格
        val grid = GridLayout(requireContext()).apply {
            columnCount = 3
            rowCount = 4
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 数字按钮
        val digits = listOf('1','2','3','4','5','6','7','8','9','0')
        digits.forEach { digit ->
            val btn = Button(requireContext()).apply {
                text = digit.toString()
                textSize = 24f
                setTextColor(Color.parseColor("#333333"))
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                layoutParams = GridLayout.LayoutParams().apply {
                    setGravity(android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_SP, 0f, resources.displayMetrics
                    ).toInt())
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener {
                    if (currentInput.length < 4) {
                        currentInput += digit
                        updateDots()
                        if (currentInput.length == 4) {
                            handlePinInput()
                        }
                    }
                }
            }
            grid.addView(btn)
        }

        // 清除按钮
        val btnClear = Button(requireContext()).apply {
            text = "清除"
            textSize = 18f
            setTextColor(Color.RED)
            setBackgroundColor(Color.parseColor("#FFEAEA"))
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                currentInput = ""
                updateDots()
                tvError.text = ""
            }
        }
        grid.addView(btnClear)

        // 确认按钮
        val btnConfirm = Button(requireContext()).apply {
            text = "✓"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2196F3"))
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                handlePinInput()
            }
        }
        grid.addView(btnConfirm)

        panel.addView(grid)

        // 错误提示
        tvError = TextView(requireContext()).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }
        panel.addView(tvError)

        root.addView(panel)
        return root
    }

    private fun updateDots() {
        dotViews.forEachIndexed { index, dot ->
            dot?.let {
                it.setTextColor(
                    if (index < currentInput.length) Color.BLACK
                    else Color.LTGRAY
                )
            }
        }
    }

    private fun handlePinInput() {
        if (currentInput.length != 4) return

        when (mode) {
            Mode.SET -> {
                if (pinManager.setPin(currentInput)) {
                    dismiss()
                    onSuccess()
                }
            }
            Mode.VERIFY -> {
                if (pinManager.verifyPin(currentInput)) {
                    dismiss()
                    onSuccess()
                }
            }
        }
    }
}
