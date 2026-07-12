package com.example.facedistancemonitor

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import java.util.concurrent.Executors

/**
 * 用于在Service被杀后重启时判断相机是否真的在工作
 */
class CameraHealthChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraHealthChecker"
        // 最大无帧时间（毫秒），超过则认为相机已断开
        private const val MAX_FRAME_AGE_MS = 5000L
    }
    
    private var lastFrameTimeMs = 0L
    
    fun markFrameProcessed() {
        lastFrameTimeMs = System.currentTimeMillis()
    }
    
    fun isCameraStillWorking(): Boolean {
        val now = System.currentTimeMillis()
        val frameAge = now - lastFrameTimeMs
        
        if (lastFrameTimeMs == 0L) {
            Log.d(TAG, "No frames processed yet, camera not working")
            return false
        }
        
        if (frameAge > MAX_FRAME_AGE_MS) {
            Log.d(TAG, "Camera frame age ${frameAge}ms > ${MAX_FRAME_AGE_MS}ms, camera likely disconnected")
            return false
        }
        
        Log.d(TAG, "Camera frame age ${frameAge}ms, camera still working")
        return true
    }
}
