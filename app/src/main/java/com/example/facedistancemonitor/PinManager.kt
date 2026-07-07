package com.example.facedistancemonitor

import android.content.Context
import android.content.SharedPreferences

/**
 * PIN码管理 — 存储和验证家长设置的4位PIN码
 */
class PinManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "pin_prefs"
        private const val KEY_PIN_SET = "pin_set"
        private const val KEY_PIN_HASH = "pin_hash"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /** 是否已设置PIN码 */
    fun isPinSet(): Boolean = prefs.getBoolean(KEY_PIN_SET, false)
    
    /** 设置PIN码（4位数字） */
    fun setPin(pin: String): Boolean {
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        prefs.edit()
            .putBoolean(KEY_PIN_SET, true)
            .putString(KEY_PIN_HASH, simpleHash(pin))
            .apply()
        return true
    }
    
    /** 验证PIN码 */
    fun verifyPin(pin: String): Boolean {
        if (!isPinSet()) return true // 未设置PIN时放行
        if (pin.length != 4 || !pin.all { it.isDigit() }) return false
        return prefs.getString(KEY_PIN_HASH, "") == simpleHash(pin)
    }
    
    /** 清除PIN码 */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_SET)
            .remove(KEY_PIN_HASH)
            .apply()
    }
    
    /** 简单的字符串哈希（用于存储PIN，非加密级别） */
    private fun simpleHash(input: String): String {
        var hash = 5381
        for (c in input) {
            hash = ((hash shl 5) + hash + c.code) and 0x7FFFFFFF
        }
        return hash.toString()
    }
}
