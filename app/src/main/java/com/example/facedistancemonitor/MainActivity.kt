package com.example.facedistancemonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var distanceDataStore: DistanceDataStore
    private lateinit var distanceUpdateHandler: Handler
    private val DISTANCE_UPDATE_INTERVAL = 500L
    private var distanceUpdateRunnable: Runnable? = null
    private var serviceRunning = false
    
    private lateinit var tvAppTitle: TextView
    private lateinit var btnLanguage: Button
    private lateinit var tvDistance: TextView
    private lateinit var tvDistanceLabel: TextView
    private lateinit var tvDistanceRecommendation: TextView
    private lateinit var btnStartPause: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnFeedbackTip: Button

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 100
        const val REQUEST_CODE_OVERLAY_PERMISSION = 101
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, getString(R.string.perm_all_granted), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.perm_required), Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, getString(R.string.perm_overlay_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.perm_overlay_required), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        restoreLanguage()
        
        setContentView(R.layout.activity_main)
        
        tvAppTitle = findViewById(R.id.tv_app_title)
        btnLanguage = findViewById(R.id.btn_language)
        tvDistance = findViewById(R.id.tv_distance)
        tvDistanceLabel = findViewById(R.id.tv_distance_label)
        tvDistanceRecommendation = findViewById(R.id.tv_distance_recommendation)
        btnStartPause = findViewById(R.id.btn_start_pause)
        btnCalibrate = findViewById(R.id.btn_calibrate)
        btnFeedbackTip = findViewById(R.id.btn_feedback_tip)
        
        // 设置所有按钮的浅青色背景
        val lightBg = ContextCompat.getDrawable(this, R.drawable.btn_background_light)
        btnLanguage.setBackground(lightBg)
        btnStartPause.setBackground(lightBg)
        btnCalibrate.setBackground(lightBg)
        btnFeedbackTip.setBackground(lightBg)
        
        // 移除阴影
        btnLanguage.elevation = 0f
        btnStartPause.elevation = 0f
        btnCalibrate.elevation = 0f
        btnFeedbackTip.elevation = 0f

        distanceDataStore = DistanceDataStore(this)
        distanceUpdateHandler = Handler(Looper.getMainLooper())
        
        // Setup UI
        tvAppTitle.text = getString(R.string.app_name)
        btnLanguage.setOnClickListener { toggleLanguage() }
        tvDistanceLabel.text = getString(R.string.label_realtime_distance)
        tvDistanceRecommendation.text = getString(R.string.label_distance_recommendation)
        
        // Setup buttons
        btnLanguage.setOnClickListener { toggleLanguage() }
        btnStartPause.setOnClickListener {
            if (serviceRunning) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }
        btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        btnFeedbackTip.setOnClickListener {
            openWeChatArticle(getString(R.string.url_feedback_article))
        }
        
        updateLangButtonText()
        
        // Check calibration and permissions
        val isCalibrated = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .contains("baseline_eye_distance_px")
        
        if (!isCalibrated) {
            requestAllPermissions {
                startActivity(Intent(this, CalibrationActivity::class.java))
            }
        } else {
            requestAllPermissions {
                startDistanceUpdates()
            }
        }
    }
    
    private fun openWeChatArticle(url: String) {
        try {
            // 尝试用微信打开
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage("com.tencent.mm")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // 微信未安装，用浏览器打开
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
    
    private fun requestAllPermissions(onComplete: () -> Unit) {
        if (hasAllPermissions()) {
            onComplete()
        } else {
            _permissionCallback = onComplete
            requestPermissions(getPermissionArray(), REQUEST_CODE_PERMISSIONS)
        }
    }
    
    private var _permissionCallback: (() -> Unit)? = null
    
    private fun getPermissionArray(): Array<String> {
        val list = mutableListOf<String>()
        list.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            _permissionCallback?.let { callback ->
                _permissionCallback = null
                callback()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        syncServiceStateToUI()
    }
    
    /** 同步Service真实运行状态到UI */
    private fun syncServiceStateToUI() {
        val lastFrame = distanceDataStore.getLastFrameTime()
        val cameraStatus = distanceDataStore.getCameraStatus()
        val now = System.currentTimeMillis()
        val frameAgeMs = if (lastFrame > 0) now - lastFrame else -1
        
        // 判断Service是否真的在监控
        val serviceActuallyWorking = cameraStatus == "ready" && (frameAgeMs < 0 || frameAgeMs < 10000)
        
        if (serviceActuallyWorking != serviceRunning) {
            serviceRunning = serviceActuallyWorking
            updateStartPauseButton(serviceRunning)
        }
    }

    private fun updateLangButtonText() {
        btnLanguage.text = if (localeIsChinese()) "EN" else "中文"
    }

    private fun toggleLanguage() {
        val newLocale = if (localeIsChinese()) "en" else "zh"
        saveLanguage(newLocale)
        
        val locale = java.util.Locale(newLocale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        
        refreshAllText()
    }

    private fun localeIsChinese(): Boolean {
        return resources.configuration.locale.language == "zh"
    }

    private fun saveLanguage(lang: String) {
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putString("app_locale", lang)
            .apply()
    }

    private fun restoreLanguage() {
        val lang = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("app_locale", null)
        if (lang != null) {
            val locale = java.util.Locale(lang)
            val config = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
    
    private fun refreshAllText() {
        tvAppTitle.text = getString(R.string.app_name)
        tvDistanceLabel.text = getString(R.string.label_realtime_distance)
        tvDistanceRecommendation.text = getString(R.string.label_distance_recommendation)
        updateLangButtonText()
        btnStartPause.text = if (serviceRunning) getString(R.string.btn_stop_monitor) else getString(R.string.btn_start_monitor)
        btnCalibrate.text = getString(R.string.btn_calibrate)
        btnFeedbackTip.text = getString(R.string.btn_feedback_tip)
        tvDistance.text = "--"
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
                return
            }
        }

        val intent = Intent(this, DistanceMonitorService::class.java).apply {
            action = "ACTION_START_MONITORING"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        serviceRunning = true
        updateStartPauseButton(true)
    }

    private fun stopMonitoring() {
        val intent = Intent(this, DistanceMonitorService::class.java).apply {
            action = "ACTION_STOP_MONITORING"
        }
        startService(intent)

        serviceRunning = false
        updateStartPauseButton(false)
        
        distanceUpdateRunnable?.let { 
            distanceUpdateHandler.removeCallbacks(it)
        }
        distanceUpdateRunnable = null
    }
    
    private fun updateStartPauseButton(isRunning: Boolean) {
        // 所有按钮统一使用浅色背景
        btnStartPause.text = if (isRunning) getString(R.string.btn_stop_monitor) else getString(R.string.btn_start_monitor)
        btnStartPause.setBackgroundResource(R.drawable.btn_background_light)
        btnStartPause.setTextColor(resources.getColor(R.color.teal_700, null))
    }
    
    private fun startDistanceUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                val distance = distanceDataStore.getDistance()
                val lastFrame = distanceDataStore.getLastFrameTime()
                val cameraStatus = distanceDataStore.getCameraStatus()
                
                val now = System.currentTimeMillis()
                val frameAgeSec = if (lastFrame > 0) ((now - lastFrame) / 1000).toInt() else -1
                
                tvDistance.text = if (distance >= 0) "$distance" else {
                    when {
                        cameraStatus == "none" -> "--"
                        cameraStatus.startsWith("error:") -> "Err"
                        frameAgeSec < 0 -> "--"
                        frameAgeSec > 5 -> getString(R.string.status_no_signal)
                        else -> "--"
                    }
                }
                
                distanceUpdateHandler.postDelayed(this, DISTANCE_UPDATE_INTERVAL)
            }
        }
        distanceUpdateRunnable = runnable
        distanceUpdateHandler.post(runnable)
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val intent = Intent(this, DistanceMonitorService::class.java).apply {
            action = "ACTION_RESTART_CAMERA"
        }
        startService(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        distanceUpdateRunnable?.let { 
            distanceUpdateHandler.removeCallbacks(it)
        }
    }
}
