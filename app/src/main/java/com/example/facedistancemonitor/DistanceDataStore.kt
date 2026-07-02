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
}
