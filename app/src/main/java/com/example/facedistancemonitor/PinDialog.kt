package com.example.facedistancemonitor

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.TextView

/**
 * PIN码输入对话框
 * 用于验证PIN码或设置新PIN码
 */
class PinDialog(
    private val context: Context,
    private val mode: Mode, // SET或VERIFY
    private val onSuccess: () -> Unit
) : Dialog(context) {

    enum class Mode { SET, VERIFY }

    private lateinit var pinManager: PinManager
    private var currentInput = ""
    private var dots = arrayOf<TextView?>(null, null, null, null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_pin)

        pinManager = PinManager(context)
        
        // 设置标题
        val tvTitle = findViewById<TextView>(R.id.tvPinTitle)
        tvTitle.text = if (mode == Mode.SET) "请设置4位PIN码" else "请输入PIN码"

        // 获取圆点引用
        dots = arrayOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4)
        )

        // 数字按钮
        val btnNumbers = listOf(
            R.id.btn0 to '0', R.id.btn1 to '1', R.id.btn2 to '2',
            R.id.btn3 to '3', R.id.btn4 to '4', R.id.btn5 to '5',
            R.id.btn6 to '6', R.id.btn7 to '7', R.id.btn8 to '8',
            R.id.btn9 to '9'
        )

        btnNumbers.forEach { (resId, digit) ->
            findViewById<Button>(resId).setOnClickListener {
                if (currentInput.length < 4) {
                    currentInput += digit
                    updateDots()
                    if (currentInput.length == 4) {
                        // 自动验证
                        handlePinInput()
                    }
                }
            }
        }

        // 清除按钮
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            currentInput = ""
            updateDots()
            findViewById<TextView>(R.id.tvPinError).text = ""
        }

        // 确认按钮
        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            handlePinInput()
        }
    }

    private fun updateDots() {
        dots.forEachIndexed { index, dot ->
            dot?.let {
                it.setBackgroundResource(
                    if (index < currentInput.length) R.drawable.pin_dot_filled
                    else R.drawable.pin_dot_empty
                )
            }
        }
    }

    private fun handlePinInput() {
        val errorTv = findViewById<TextView>(R.id.tvPinError)
        
        if (currentInput.length != 4) {
            errorTv.text = "请输入4位数字"
            return
        }

        when (mode) {
            Mode.SET -> {
                // 设置PIN码
                if (pinManager.setPin(currentInput)) {
                    dismiss()
                    onSuccess()
                } else {
                    errorTv.text = "PIN码设置失败"
                    currentInput = ""
                    updateDots()
                }
            }
            Mode.VERIFY -> {
                // 验证PIN码
                if (pinManager.verifyPin(currentInput)) {
                    dismiss()
                    onSuccess()
                } else {
                    errorTv.text = "PIN码错误"
                    currentInput = ""
                    updateDots()
                }
            }
        }
    }
}
