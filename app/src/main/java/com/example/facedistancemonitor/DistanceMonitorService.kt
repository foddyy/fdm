package com.example.facedistancemonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.util.Log

/**
 * 一个始终处于STARTED状态的生命周期持有者。
 * 用于绑定CameraX，使其不受Service生命周期影响（Service进后台后不会暂停相机）。
 */
class PersistentLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    
    init {
        // 必须到RESUMED状态，CameraX才会真正启动相机
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}

class DistanceMonitorService : LifecycleService(), DisplayListener {

    companion object {
        const val CHANNEL_ID = "DistanceMonitorChannel"
        const val NOTIFICATION_ID = 1001
        const val THRESHOLD_DISTANCE_CM = 35
        const val NORMAL_READING_DISTANCE_CM = 35
        const val FRAME_INTERVAL_MS = 1000L
        const val CONSECUTIVE_NEAR_COUNT = 2
    }

    private var faceDetector: FaceDetector? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isMonitoring = false
    private var baselineEyeDistancePx: Float = 0f

    private var alertView: ViewAlertOverlay? = null
    private var isAlertActive = false
    private var windowManager: WindowManager? = null
    
    private lateinit var distanceDataStore: DistanceDataStore
    private var lastReportedDistance: Int = -1
    
    private var lastFrameTimeMs = 0L
    private var lastAlertStartTime = 0L
    private var alertTimeoutHandler: Handler? = null
    private var alertTimeoutRunnable: Runnable? = null
    
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    
    private val restReminderHandler = Handler(Looper.getMainLooper())
    private var restReminderRunnable: Runnable? = null
    private var lastFaceDetectedTime = 0L
    private val REST_REMINDER_INTERVAL_MS = 20 * 60 * 1000L
    
    private lateinit var displayManager: DisplayManager
    private var currentDisplayOrientation = 0
    
    // 独立的生命周期持有者，始终处于STARTED状态，不受Service生命周期影响
    private val persistentLifecycleOwner = PersistentLifecycleOwner()
    
    // 唤醒锁：保持CPU在后台运行时不休眠
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        distanceDataStore = DistanceDataStore(this)
        setupFaceDetector()
        setupTTS()
        cameraExecutor = Executors.newSingleThreadExecutor()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(this, null)
        
        distanceDataStore.markCameraStatus("none")
        
        startForegroundService()
    }
    
    override fun onDisplayAdded(displayId: Int) {}
    
    override fun onDisplayRemoved(displayId: Int) {}
    
    override fun onDisplayChanged(displayId: Int) {
        // 屏幕方向变化时不再重启相机
        android.util.Log.d("DistanceMonitorService", "Display changed, keeping camera running")
    }

    private fun setupTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(0.9f)
                ttsInitialized = true
            }
        }
    }
    
    private fun speakText(text: String) {
        if (ttsInitialized && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rest_reminder")
        }
    }
    
    private fun startRestReminder() {
        restReminderRunnable = Runnable {
            val now = System.currentTimeMillis()
            if (lastFaceDetectedTime > 0 && now - lastFaceDetectedTime >= REST_REMINDER_INTERVAL_MS && isMonitoring) {
                speakText("休息一下眼睛吧，看看远方")
                lastFaceDetectedTime = now
            }
            restReminderHandler.postDelayed(restReminderRunnable!!, REST_REMINDER_INTERVAL_MS)
        }
        restReminderHandler.post(restReminderRunnable!!)
    }
    
    private fun stopRestReminder() {
        restReminderRunnable?.let {
            restReminderHandler.removeCallbacks(it)
        }
    }

    private fun setupFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("爱眼")
            .setContentText("正在守护你的眼睛")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // 获取WakeLock，防止后台CPU休眠导致相机断流
        acquireWakeLock()
    }
    
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FaceDistanceMonitor:CameraWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
        android.util.Log.d("DistanceMonitorService", "WakeLock acquired")
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                android.util.Log.d("DistanceMonitorService", "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "距离监控服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于监控面部与手机屏幕距离的通知"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            "ACTION_START_MONITORING" -> {
                baselineEyeDistancePx = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getFloat("baseline_eye_distance_px", 0f)

                distanceDataStore.markServiceStarted(baselineEyeDistancePx)

                if (baselineEyeDistancePx > 0) {
                    isMonitoring = true
                    getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                        .putBoolean("service_monitoring", true)
                        .apply()
                    startCameraMonitoring()
                    startRestReminder()
                } else {
                    stopSelf()
                }
            }
            "ACTION_STOP_MONITORING" -> {
                stopMonitoring()
                stopRestReminder()
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .remove("service_monitoring")
                    .apply()
                stopSelf()
                return START_NOT_STICKY
            }
            "ACTION_RESTART_CAMERA" -> {
                if (isMonitoring) {
                    restartCamera()
                }
            }
        }
        
        // 修复：START_STICKY 重建时，如果 SharedPreferences 说在监控且已校准过距离，自动恢复
        if (intent == null) {
            val wasMonitoring = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getBoolean("service_monitoring", false)
            baselineEyeDistancePx = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getFloat("baseline_eye_distance_px", 0f)
            if (wasMonitoring && baselineEyeDistancePx > 0) {
                android.util.Log.d("DistanceMonitorService", "Service restarted after kill, restoring monitoring")
                isMonitoring = true
                startCameraMonitoring()
                startRestReminder()
            }
        }
        
        return START_STICKY
    }

    private fun startCameraMonitoring() {
        Handler(Looper.getMainLooper()).post {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(applicationContext).get()
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                    analyzeFrame(imageProxy)
                }

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.unbindAll()
                // 关键修复：绑定到persistentLifecycleOwner而非Service本身
                // 这样即使Service进入STOPPED状态（App进后台），相机仍持续传帧
                cameraProvider.bindToLifecycle(persistentLifecycleOwner, selector, imageAnalysis)
                distanceDataStore.markCameraReady()
            } catch (e: Exception) {
                val errorMsg = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                distanceDataStore.markCameraError(errorMsg)
                android.util.Log.e("DistanceMonitorService", "Camera init failed: $errorMsg", e)
            }
        }
    }
    
    private fun restartCamera() {
        Handler(Looper.getMainLooper()).post {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(applicationContext).get()
                cameraProvider.unbindAll()
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                    analyzeFrame(imageProxy)
                }

                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider.unbindAll()
                // 关键修复：绑定到persistentLifecycleOwner而非Service本身
                cameraProvider.bindToLifecycle(persistentLifecycleOwner, selector, imageAnalysis)
                
                distanceDataStore.markCameraReady()
                android.util.Log.d("DistanceMonitorService", "Camera restarted after orientation change")
            } catch (e: Exception) {
                val errorMsg = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                distanceDataStore.markCameraError(errorMsg)
                android.util.Log.e("DistanceMonitorService", "Camera restart failed: $errorMsg", e)
            }
        }
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        if (!isMonitoring || baselineEyeDistancePx <= 0) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastFrameTimeMs < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastFrameTimeMs = now
        
        distanceDataStore.markFrameProcessed()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        faceDetector?.process(inputImage)?.addOnSuccessListener { faces ->
            if (!isMonitoring) return@addOnSuccessListener
            
            if (faces.isNotEmpty()) {
                lastFaceDetectedTime = System.currentTimeMillis()
                
                val face = faces[0]
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

                if (leftEye != null && rightEye != null) {
                    val leftPos = leftEye.position
                    val rightPos = rightEye.position
                    val dx = rightPos.x - leftPos.x
                    val dy = rightPos.y - leftPos.y
                    val currentEyeDistancePx = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                    val estimatedDistanceCm = (NORMAL_READING_DISTANCE_CM.toFloat() *
                        baselineEyeDistancePx / currentEyeDistancePx).toInt()

                    val isNear = estimatedDistanceCm < THRESHOLD_DISTANCE_CM
                    
                    if (isAlertActive && !isNear) {
                        stopRedBlinkAlert()
                    } else if (!isAlertActive && isNear) {
                        startRedBlinkAlert()
                    }
                    
                    alertTimeoutHandler?.removeCallbacks(alertTimeoutRunnable!!)
                    
                    distanceDataStore.saveDistance(estimatedDistanceCm)
                    lastReportedDistance = estimatedDistanceCm
                }
            } else {
                lastFaceDetectedTime = 0L
                if (isAlertActive) {
                    stopRedBlinkAlert()
                }
            }
        }?.addOnFailureListener { e ->
            e.printStackTrace()
        }?.addOnCompleteListener {
            imageProxy.close()
        }
    }

    private fun startRedBlinkAlert() {
        isAlertActive = true
        lastAlertStartTime = System.currentTimeMillis()
        
        if (alertTimeoutHandler == null) {
            alertTimeoutHandler = Handler(Looper.getMainLooper())
        }
        alertTimeoutRunnable = Runnable {
            if (isAlertActive) {
                android.util.Log.d("DistanceMonitorService", "Alert timeout: closing after 2s without near signal")
                stopRedBlinkAlert()
            }
        }
        alertTimeoutHandler!!.postDelayed(alertTimeoutRunnable!!, 2000L)

        alertView = ViewAlertOverlay(this)
        
        val currentLang = resources.configuration.locale.language
        val titleResId = if (currentLang == "en") {
            R.string.alert_overlay_title_en
        } else {
            R.string.alert_overlay_title_zh
        }
        val overlay = alertView!!
        overlay.setAlertTitle(titleResId)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            android.graphics.PixelFormat.TRANSLUCENT
        )

        windowManager?.addView(alertView, params)
    }

    private fun stopRedBlinkAlert() {
        isAlertActive = false
        lastAlertStartTime = 0L
        
        alertTimeoutHandler?.removeCallbacks(alertTimeoutRunnable!!)

        alertView?.let { view ->
            windowManager?.removeView(view)
        }
        alertView = null
    }

    private fun stopMonitoring() {
        isMonitoring = false
        lastFrameTimeMs = 0
        stopRedBlinkAlert()
        
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(applicationContext).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            android.util.Log.w("DistanceMonitorService", "Failed to unbind camera", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        stopRestReminder()
        tts?.stop()
        tts?.shutdown()
        cameraExecutor.shutdown()
        faceDetector?.close()
        releaseWakeLock()
        try {
            displayManager.unregisterDisplayListener(this)
        } catch (e: Exception) {
            android.util.Log.w("DistanceMonitorService", "Failed to unregister display listener", e)
        }
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().remove("service_monitoring").apply()
        distanceDataStore.markCameraStatus("none")
        android.util.Log.d("DistanceMonitorService", "Service destroyed, cleared monitoring state")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
