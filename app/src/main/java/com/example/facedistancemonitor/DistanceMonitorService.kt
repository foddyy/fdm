package com.example.facedistancemonitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import java.util.Locale

class DistanceMonitorService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "DistanceMonitorChannel"
        const val NOTIFICATION_ID = 1001
        const val THRESHOLD_DISTANCE_CM = 35
        const val NORMAL_READING_DISTANCE_CM = 35
        // 降频：相机每秒最多处理1帧
        const val FRAME_INTERVAL_MS = 1000L
        // 连续检测到过近才报警（避免偶发误报）
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
    
    // 降频控制：记录上次处理帧的时间戳
    private var lastFrameTimeMs = 0L
    
    // 连续过近计数
    private var consecutiveNearCount = 0
    
    // 语音TTS
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    
    // 休息计时器：每20分钟提醒一次
    private val restReminderHandler = Handler(Looper.getMainLooper())
    private var restReminderRunnable: Runnable? = null
    private var lastRestReminderTime = 0L
    private val REST_REMINDER_INTERVAL_MS = 20 * 60 * 1000L // 20分钟
    
    override fun onCreate() {
        super.onCreate()
        distanceDataStore = DistanceDataStore(this)
        setupFaceDetector()
        setupTTS()
        cameraExecutor = Executors.newSingleThreadExecutor()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundService()
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
            if (now - lastRestReminderTime >= REST_REMINDER_INTERVAL_MS && isMonitoring) {
                speakText("休息一下眼睛吧，看看远方")
                lastRestReminderTime = now
            }
            // 每20分钟循环一次
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
            .setContentTitle("面部距离监控")
            .setContentText("正在监测面部与屏幕距离")
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

                // 记录Service启动状态
                distanceDataStore.markServiceStarted(baselineEyeDistancePx)

                if (baselineEyeDistancePx > 0) {
                    isMonitoring = true
                    startCameraMonitoring()
                    startRestReminder()
                } else {
                    stopSelf()
                }
            }
            "ACTION_STOP_MONITORING" -> {
                stopMonitoring()
                stopRestReminder()
                stopSelf()
                return START_NOT_STICKY
            }
            "ACTION_RESTART_CAMERA" -> {
                // 横竖屏切换时重启相机
                if (isMonitoring) {
                    restartCamera()
                }
            }
        }
        return START_STICKY
    }

    private fun startCameraMonitoring() {
        // ProcessCameraProvider.getInstance 必须在主线程调用
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
                cameraProvider.bindToLifecycle(this, selector, imageAnalysis)
                
                distanceDataStore.markCameraReady()
            } catch (e: Exception) {
                val errorMsg = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                distanceDataStore.markCameraError(errorMsg)
                android.util.Log.e("DistanceMonitorService", "Camera init failed: $errorMsg", e)
            }
        }
    }
    
    private fun restartCamera() {
        // 停止旧相机绑定，然后重新启动
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
                cameraProvider.bindToLifecycle(this, selector, imageAnalysis)
                
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

        // 降频：每秒最多处理1帧
        val now = System.currentTimeMillis()
        if (now - lastFrameTimeMs < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        lastFrameTimeMs = now
        
        // 标记这一帧被处理了（不管有没有检测到人脸）
        distanceDataStore.markFrameProcessed()

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        faceDetector?.process(inputImage)
            ?.addOnSuccessListener { faces ->
                if (isMonitoring && faces.isNotEmpty()) {
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
                        if (isNear) {
                            consecutiveNearCount++
                        } else {
                            consecutiveNearCount = 0
                        }

                        // 保存距离数据供UI更新
                        distanceDataStore.saveDistance(estimatedDistanceCm)
                        lastReportedDistance = estimatedDistanceCm

                        // 连续两次过近才报警
                        if (isNear && consecutiveNearCount >= CONSECUTIVE_NEAR_COUNT && !isAlertActive) {
                            startRedBlinkAlert()
                        } else if (!isNear && isAlertActive && consecutiveNearCount == 0) {
                            // 连续两次正常才取消报警
                            stopRedBlinkAlert()
                        }
                    }
                }
            }
            ?.addOnFailureListener { e ->
                e.printStackTrace()
            }
            ?.addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun startRedBlinkAlert() {
        isAlertActive = true

        alertView = ViewAlertOverlay(this)
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

        alertView?.let { view ->
            windowManager?.removeView(view)
        }
        alertView = null
    }

    private fun stopMonitoring() {
        isMonitoring = false
        consecutiveNearCount = 0
        lastFrameTimeMs = 0
        stopRedBlinkAlert()
        
        // 停止相机
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
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
