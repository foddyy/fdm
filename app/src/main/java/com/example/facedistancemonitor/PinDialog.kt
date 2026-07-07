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

        // PIN输入框（4位数字，隐藏输入）
        val etPin = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLength = 4
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 80
            ).apply { setMargins(0, 0, 0, 24) }
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

        // 清除按钮
        val btnClear = Button(requireContext()).apply {
            text = "清除"
            textSize = 18f
            setTextColor(Color.RED)
            setBackgroundColor(Color.parseColor("#FFEAEA"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 56
            ).apply { setMargins(0, 0, 0, 8) }
            setOnClickListener {
                currentInput = ""
                etPin.setText("")
                tvError.text = ""
            }
        }
        panel.addView(btnClear)

        // 确认按钮
        val btnConfirm = Button(requireContext()).apply {
            text = "✓ 确认"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2196F3"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 56
            )
            setOnClickListener {
                handlePinInput()
            }
        }
        panel.addView(btnConfirm)

        return panel
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
