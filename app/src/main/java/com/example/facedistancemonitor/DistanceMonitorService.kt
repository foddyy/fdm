package com.example.facedistancemonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DistanceMonitorService : android.app.Service() {

    companion object {
        const val CHANNEL_ID = "DistanceMonitorChannel"
        const val NOTIFICATION_ID = 1001
        const val THRESHOLD_DISTANCE_CM = 30
        const val NORMAL_READING_DISTANCE_CM = 35
    }

    private var faceDetector: FaceDetector? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isMonitoring = false
    private var baselineEyeDistancePx: Float = 0f

    private var alertView: ViewAlertOverlay? = null
    private var isAlertActive = false
    private var windowManager: WindowManager? = null

    override fun onCreate() {
        super.onCreate()
        setupFaceDetector()
        cameraExecutor = Executors.newSingleThreadExecutor()
        startForegroundService()
    }

    private fun setupFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
        faceDetector = FaceDetection.getClient(options)
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

            val preview = Preview.Builder()
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }

            val selector = CameraSelector.DEFAULT_FRONT_FACING
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, Executors.newSingleThreadExecutor())
    }

    private fun analyzeFrame(imageProxy: ImageAnalysis.ImageProxy) {
        if (!isMonitoring || baselineEyeDistancePx <= 0) {
            imageProxy.close()
            return
        }

        faceDetector?.detectInImage(imageProxy)?.addOnSuccessListener { faceContainer ->
            if (isMonitoring) {
                val faces = faceContainer.faces
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val leftEye = face.getLandmark(Face.LANDMARK_LEFT_EYE)
                    val rightEye = face.getLandmark(Face.LANDMARK_RIGHT_EYE)

                    if (leftEye != null && rightEye != null) {
                        val leftPos = leftEye.position
                        val rightPos = rightEye.position
                        val dx = rightPos.x - leftPos.x
                        val dy = rightPos.y - leftPos.y
                        val currentEyeDistancePx = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                        val estimatedDistanceCm = (NORMAL_READING_DISTANCE_CM.toFloat() *
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
        cameraExecutor.shutdown()
        faceDetector?.close()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
