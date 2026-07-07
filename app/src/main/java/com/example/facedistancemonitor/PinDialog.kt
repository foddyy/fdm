package com.example.facedistancemonitor

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment

/**
 * PIN码输入对话框 — 简洁版，使用系统数字键盘
 */
class PinDialog(
    private val mode: Mode,
    private val onSuccess: () -> Unit
) : DialogFragment() {

    enum class Mode { SET, VERIFY }

    private lateinit var pinManager: PinManager
    private lateinit var etPin: EditText
    private lateinit var tvError: TextView
    private var currentInput = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        panel.addView(tvTitle)

        // 说明文字
        val tvHint = TextView(requireContext()).apply {
            text = if (mode == Mode.SET) "用于保护监控设置，防止他人关闭" else "输入PIN码以继续使用"
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        panel.addView(tvHint)

        // PIN输入框（大框，小字体）
        etPin = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(4))
            textSize = 32f
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 100
            ).apply { setMargins(0, 0, 0, 16) }
            setOnEditorActionListener { _, _, _ ->
                if (currentInput.length == 4) handlePinInput()
                true
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    currentInput = s.toString()
                    if (currentInput.length == 4) handlePinInput()
                }
            })
        }
        panel.addView(etPin)

        // 错误提示
        tvError = TextView(requireContext()).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        panel.addView(tvError)

        return panel
    }

    private fun handlePinInput() {
        if (currentInput.length != 4) return

        when (mode) {
            Mode.SET -> {
                if (pinManager.setPin(currentInput)) {
                    dismiss()
                    onSuccess()
                } else {
                    tvError.text = "PIN码设置失败"
                    currentInput = ""
                    etPin.setText("")
                }
            }
            Mode.VERIFY -> {
                if (pinManager.verifyPin(currentInput)) {
                    dismiss()
                    onSuccess()
                } else {
                    tvError.text = "PIN码错误"
                    currentInput = ""
                    etPin.setText("")
                }
            }
        }
    }
}
