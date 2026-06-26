package com.example.facedistancemonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat

/**
 * DistanceMonitorService - Foreground service that monitors face-to-screen distance
 * using CameraX + ML Kit face detection. Triggers red flashing overlay alert
 * when estimated distance < 30cm.
 */
class DistanceMonitorService : android.app.Service() {

    companion object {
        const val CHANNEL_ID = "DistanceMonitorChannel"
        const val NOTIFICATION_ID = 1001
        const val THRESHOLD_DISTANCE_CM = 30
        const val NORMAL_READING_DISTANCE_CM = 35
    }

    private var faceDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private var cameraExecutor: java.util.concurrent.ExecutorService? = null
    private var isMonitoring = false
    private var baselineEyeDistancePx: Float = 0f

    private var alertView: ViewAlertOverlay? = null
    private var isAlertActive = false
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        setupFaceDetector()
        cameraExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
        startForegroundService()
    }

    private fun setupFaceDetector() {
        val options = com.google.mlkit.vision.face.FaceDetectorOptions.Builder()
            .setPerformanceMode(com.google.mlkit.vision.face.FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(com.google.mlkit.vision.face.FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setTrackingMode(com.google.mlkit.vision.face.FaceDetectorOptions.TRACKING_MODE_OFF)
            .build()
        faceDetector = com.google.mlkit.vision.face.FaceDetection.getClient(options)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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
        when (intent?.action) {
            "ACTION_START_MONITORING" -> {
                baselineEyeDistancePx = getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .getFloat("baseline_eye_distance_px", 0f)

                if (baselineEyeDistancePx > 0) {
                    isMonitoring = true
                    startCameraMonitoring()
                } else {
                    stopSelf()
                }
            }
            "ACTION_STOP_MONITORING" -> {
                stopMonitoring()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startCameraMonitoring() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder()
                .build()

            val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(java.util.concurrent.Executors.newSingleThreadExecutor()) { imageProxy ->
                analyzeFrame(imageProxy)
            }

            val selector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_FACING
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, java.util.concurrent.Executors.newSingleThreadExecutor())
    }

    private fun analyzeFrame(imageProxy: androidx.camera.core.ImageAnalysis.ImageProxy) {
        if (!isMonitoring || baselineEyeDistancePx <= 0) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = androidx.camera.core.ImageProxy.toInputImage(imageProxy, rotation)

        faceDetector?.recognizeFaces(inputImage)?.addOnSuccessListener { faces ->
            if (faces.isNotEmpty() && isMonitoring) {
                val face = faces[0]
                val leftEye = face.getLandmark(
                    com.google.mlkit.vision.face.landmark.Landmark.LEFT_EYE
                )
                val rightEye = face.getLandmark(
                    com.google.mlkit.vision.face.landmark.Landmark.RIGHT_EYE
                )

                if (leftEye != null && rightEye != null) {
                    val leftPos = leftEye.position
                    val rightPos = rightEye.position
                    val currentEyeDistancePx = kotlin.math.sqrt(
                        kotlin.math.pow(rightPos.x - leftPos.x, 2.0) +
                        kotlin.math.pow(rightPos.y - leftPos.y, 2.0)
                    ).toFloat()

                    // Closer face = larger eye distance in pixels
                    val estimatedDistanceCm = (NORMAL_READING_DISTANCE_CM *
                        baselineEyeDistancePx / currentEyeDistancePx).toInt()

                    if (estimatedDistanceCm < THRESHOLD_DISTANCE_CM) {
                        if (!isAlertActive) {
                            startRedBlinkAlert()
                        }
                    } else {
                        if (isAlertActive) {
                            stopRedBlinkAlert()
                        }
                    }
                }
            }
        }

        imageProxy.close()
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
        stopRedBlinkAlert()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        cameraExecutor?.shutdown()
        faceDetector?.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
