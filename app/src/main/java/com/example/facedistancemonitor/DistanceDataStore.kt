package com.example.facedistancemonitor

import android.content.Context
import android.content.SharedPreferences

class DistanceDataStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("distance_data", Context.MODE_PRIVATE)
    
    fun saveDistance(distanceCm: Int) {
        prefs.edit()
            .putInt("current_distance_cm", distanceCm)
            .putLong("last_distance_time", System.currentTimeMillis())
            .apply()
    }
    
    fun getDistance(): Int {
        val distance = prefs.getInt("current_distance_cm", -1)
        val lastTime = prefs.getLong("last_distance_time", 0)
        // 如果最后更新时间在10秒内，返回距离；否则返回-1（表示信号丢失）
        if (distance >= 0 && System.currentTimeMillis() - lastTime < 10000) {
            return distance
        }
        return -1
    }
    
    fun markFrameProcessed() {
        prefs.edit().putLong("last_frame_time", System.currentTimeMillis()).apply()
    }
    
    fun getLastFrameTime(): Long {
        return prefs.getLong("last_frame_time", 0)
    }
    
    fun markServiceStarted(baselinePx: Float) {
        prefs.edit().putFloat("service_baseline", baselinePx).apply()
    }
    
    fun getServiceBaseline(): Float {
        return prefs.getFloat("service_baseline", -1f)
    }
    
    fun markCameraReady() {
        prefs.edit().putString("camera_status", "ready").apply()
    }
    
    fun markCameraError(msg: String) {
        prefs.edit().putString("camera_status", "error:$msg").apply()
    }
    
    fun markCameraStatus(status: String) {
        prefs.edit().putString("camera_status", status).apply()
    }
    
    fun getCameraStatus(): String {
        return prefs.getString("camera_status", "none") ?: "none"
    }
}
