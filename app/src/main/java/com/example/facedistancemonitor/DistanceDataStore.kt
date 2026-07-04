package com.example.facedistancemonitor

import android.content.Context
import android.content.SharedPreferences

class DistanceDataStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("distance_data", Context.MODE_PRIVATE)
    
    fun saveDistance(distanceCm: Int) {
        prefs.edit().putInt("current_distance_cm", distanceCm).apply()
    }
    
    fun getDistance(): Int {
        return prefs.getInt("current_distance_cm", -1)
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
}
