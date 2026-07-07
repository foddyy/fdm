package com.example.facedistancemonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.facedistancemonitor.databinding.ActivityCalibrationBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var cameraExecutor: ExecutorService
    private var faceDetector: FaceDetector? = null

    private var baselineEyeDistancePx: Float = 0f
    private var calibrationCount: Int = 0

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupCamera()
        } else {
            setStatusText("需要相机权限才能校准")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)

        // 检查相机权限
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnCalibrate.setOnClickListener {
            startCalibration()
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                processImageProxy(imageProxy)
            }

            val selector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
                setStatusText("相机绑定失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        faceDetector?.process(inputImage)
            ?.addOnSuccessListener { faces ->
                runOnUiThread {
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

                        if (leftEye != null && rightEye != null) {
                            val leftPos = leftEye.position
                            val rightPos = rightEye.position
                            val dx = rightPos.x - leftPos.x
                            val dy = rightPos.y - leftPos.y
                            val eyeDistancePx = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                            baselineEyeDistancePx += eyeDistancePx
                            calibrationCount++

                            setStatusText("双眼间距: ${eyeDistancePx.toInt()} px (已采集 $calibrationCount 帧)")
                        } else {
                            setStatusText("未检测到眼部关键点")
                        }
                    } else {
                        setStatusText("未检测到人脸，请对准摄像头")
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

    private fun startCalibration() {
        baselineEyeDistancePx = 0f
        calibrationCount = 0
        setStatusText("请保持不动... 正在采集数据...")
        binding.btnCalibrate.isEnabled = false

        Handler(Looper.getMainLooper()).postDelayed({
            if (calibrationCount > 10) {
                val avgDistance = baselineEyeDistancePx / calibrationCount
                getSharedPreferences("app_prefs", MODE_PRIVATE)
                    .edit()
                    .putFloat("baseline_eye_distance_px", avgDistance)
                    .apply()

                setStatusText("校准成功！基准双眼间距: ${avgDistance.toInt()} px")
                binding.btnCalibrate.isEnabled = true

                // 只在首次校准时提示设置PIN码
                if (!PinManager(this).isPinSet()) {
                    PinDialog(PinDialog.Mode.SET) {
                        android.util.Log.d("CalibrationActivity", "PIN码已设置")
                    }.show(supportFragmentManager, "pin_dialog")
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 500)
            } else {
                setStatusText("校准失败：未检测到足够的人脸数据")
                binding.btnCalibrate.isEnabled = true
            }
        }, 2000)
    }

    private fun setStatusText(text: String) {
        // Status text removed from layout, but keep method for potential future use
        android.util.Log.d("CalibrationActivity", text)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector?.close()
    }
}
