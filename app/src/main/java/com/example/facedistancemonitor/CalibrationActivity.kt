package com.example.facedistancemonitor

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.landmark.Landmark
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CalibrationActivity - Calibration screen that uses CameraX + ML Kit face detection
 * to measure the eye-to-eye pixel distance as a baseline for distance estimation.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalibrationBinding
    private lateinit var cameraExecutor: ExecutorService
    private var faceDetector: FaceDetector? = null
    private var previewUseCase: androidx.camera.core.Preview? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null

    private var baselineEyeDistancePx: Float = 0f
    private var calibrationCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_calibration)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // ML Kit face detector with landmarks (for eye positions)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
        faceDetector = FaceDetection.getClient(options)

        setupCamera()

        binding.btnCalibrate.setOnClickListener {
            startCalibration()
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            previewUseCase = androidx.camera.core.Preview.Builder()
                .build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            // Image analysis for face detection
            imageAnalysisUseCase = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysisUseCase?.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            val selector = CameraSelector.DEFAULT_FRONT_FACING
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, selector, previewUseCase!!, imageAnalysisUseCase!!
                )
            } catch (e: Exception) {
                e.printStackTrace()
                setStatusText("相机绑定失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageAnalysis.ImageProxy) {
        if (imageProxy.image == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = androidx.camera.core.ImageProxy.toInputImage(imageProxy, rotation)

        faceDetector?.recognizeFaces(inputImage)?.addOnSuccessListener { faces ->
            runOnUiThread {
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val leftEye = face.getLandmark(Landmark.LEFT_EYE)
                    val rightEye = face.getLandmark(Landmark.RIGHT_EYE)

                    if (leftEye != null && rightEye != null) {
                        val leftPos = leftEye.position
                        val rightPos = rightEye.position
                        val eyeDistancePx = kotlin.math.sqrt(
                            kotlin.math.pow(rightPos.x - leftPos.x, 2.0) +
                            kotlin.math.pow(rightPos.y - leftPos.y, 2.0)
                        ).toFloat()

                        baselineEyeDistancePx += eyeDistancePx
                        calibrationCount++

                        setStatusText("检测到双眼间距: ${eyeDistancePx.toInt()} px  (已采集 $calibrationCount 帧)")
                    } else {
                        setStatusText("未检测到眼部关键点")
                    }
                } else {
                    setStatusText("未检测到人脸，请对准摄像头")
                }
            }
        }.addOnFailureListener { e ->
            e.printStackTrace()
        }

        imageProxy.close()
    }

    private fun startCalibration() {
        baselineEyeDistancePx = 0f
        calibrationCount = 0
        setStatusText("请保持不动... 正在采集数据...")
        binding.btnCalibrate.isEnabled = false

        Handler(Looper.getMainLooper()).postDelayed({
            if (calibrationCount > 10) {
                val avgDistance = baselineEyeDistancePx / calibrationCount
                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putFloat("baseline_eye_distance_px", avgDistance)
                    .apply()

                setStatusText("校准成功！基准双眼间距: ${avgDistance.toInt()} px")
                binding.btnCalibrate.isEnabled = true

                Handler(Looper.getMainLooper()).postDelayed({
                    finish()
                }, 1500)
            } else {
                setStatusText("校准失败：未检测到足够的人脸数据")
                binding.btnCalibrate.isEnabled = true
            }
        }, 2000)
    }

    private fun setStatusText(text: String) {
        binding.tvStatus.text = text
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector?.close()
    }
}
