package com.example.facedistancemonitor

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * PIN码输入对话框 — 简化版，确保按钮可点击
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
        // 外层FrameLayout
        val root = FrameLayout(requireContext()).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.85).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        // 内层LinearLayout
        val panel = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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

        // 数字按钮 - 3行
        val digitRows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9')
        )

        digitRows.forEach { rowDigits ->
            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { weightSum = 3f }
            }
            rowDigits.forEach { digit ->
                val btn = Button(requireContext()).apply {
                    text = digit.toString()
                    textSize = 24f
                    setTextColor(Color.parseColor("#333333"))
                    setBackgroundColor(Color.parseColor("#E0E0E0"))
                    layoutParams = LinearLayout.LayoutParams(0, 64, 1f).apply {
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
                rowLayout.addView(btn)
            }
            panel.addView(rowLayout)
        }

        // 第四行：清除、0、确认
        val row4 = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { weightSum = 3f }
        }

        val btnClear = Button(requireContext()).apply {
            text = "清除"
            textSize = 18f
            setTextColor(Color.RED)
            setBackgroundColor(Color.parseColor("#FFEAEA"))
            layoutParams = LinearLayout.LayoutParams(0, 64, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                currentInput = ""
                updateDots()
                tvError.text = ""
            }
        }
        row4.addView(btnClear)

        val btn0 = Button(requireContext()).apply {
            text = "0"
            textSize = 24f
            setTextColor(Color.parseColor("#333333"))
            setBackgroundColor(Color.parseColor("#E0E0E0"))
            layoutParams = LinearLayout.LayoutParams(0, 64, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                if (currentInput.length < 4) {
                    currentInput += '0'
                    updateDots()
                    if (currentInput.length == 4) {
                        handlePinInput()
                    }
                }
            }
        }
        row4.addView(btn0)

        val btnConfirm = Button(requireContext()).apply {
            text = "✓"
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2196F3"))
            layoutParams = LinearLayout.LayoutParams(0, 64, 1f).apply {
                setMargins(4, 4, 4, 4)
            }
            setOnClickListener {
                handlePinInput()
            }
        }
        row4.addView(btnConfirm)

        panel.addView(row4)

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
