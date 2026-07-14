package com.example.facedistancemonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.facedistancemonitor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var distanceDataStore: DistanceDataStore
    private lateinit var distanceUpdateHandler: Handler
    private val DISTANCE_UPDATE_INTERVAL = 500L
    private var distanceUpdateRunnable: Runnable? = null
    private var serviceRunning = false

    companion object {
        const val REQUEST_CODE_PERMISSIONS = 100
        const val REQUEST_CODE_OVERLAY_PERMISSION = 101
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要权限才能运行此应用", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要悬浮窗权限以显示警示", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        restoreLanguage()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        distanceDataStore = DistanceDataStore(this)
        distanceUpdateHandler = Handler(Looper.getMainLooper())
        
        val isCalibrated = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .contains("baseline_eye_distance_px")
        
        if (!isCalibrated) {
            requestAllPermissions {
                startActivity(Intent(this, CalibrationActivity::class.java))
            }
        } else {
            requestAllPermissions {
                setupUI()
                startDistanceUpdates()
            }
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

    private var setupUICalled = false
    
    override fun onResume() {
        super.onResume()
        
        // 修复问题3：同步Service真实运行状态到UI
        syncServiceStateToUI()
        
        // 修复问题2：回到前台时如果Service在监控但相机未工作，重启相机
        if (serviceRunning) {
            val cameraStatus = distanceDataStore.getCameraStatus()
            if (cameraStatus != "ready") {
                android.util.Log.d("MainActivity", "Restarting camera on resume, current status: $cameraStatus")
                val intent = Intent(this, DistanceMonitorService::class.java).apply {
                    action = "ACTION_RESTART_CAMERA"
                }
                startService(intent)
            }
        }
        
        if (!setupUICalled) {
            val isCalibrated = getSharedPreferences("app_prefs", MODE_PRIVATE)
                .contains("baseline_eye_distance_px")
            
            if (isCalibrated) {
                setupUI()
                startDistanceUpdates()
                setupUICalled = true
            }
        }
    }
    
    /** 同步Service真实运行状态到UI */
    private fun syncServiceStateToUI() {
        // 修复问题2：通过检查相机帧是否还在更新来判断Service是否真实存活
        // 不能只用SharedPreferences，因为Service被杀后SharedPreferences残留旧值
        // 如果相机状态为ready且超过10秒没有新帧，说明Service已经不工作了
        
        val lastFrame = distanceDataStore.getLastFrameTime()
        val cameraStatus = distanceDataStore.getCameraStatus()
        val now = System.currentTimeMillis()
        val frameAgeMs = if (lastFrame > 0) now - lastFrame else -1
        
        // 判断Service是否真的在监控：相机状态为ready 且 帧在10秒内有更新
        val serviceActuallyWorking = cameraStatus == "ready" && (frameAgeMs < 0 || frameAgeMs < 10000)
        
        if (serviceActuallyWorking != serviceRunning) {
            serviceRunning = serviceActuallyWorking
            if (serviceRunning) {
                binding.tvStatusText.text = getString(R.string.status_running)
                binding.viewStatusCircle.background = ContextCompat.getDrawable(this, R.drawable.status_led_running)
                binding.btnStartMonitor.text = getString(R.string.btn_stop_monitor)
                binding.btnStartMonitor.setOnClickListener { stopMonitoring() }
            } else {
                // Service已死，清理状态
                getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                    .remove("service_monitoring")
                    .apply()
                distanceDataStore.markCameraStatus("none")
                
                binding.tvStatusText.text = getString(R.string.status_idle)
                binding.viewStatusCircle.background = ContextCompat.getDrawable(this, R.drawable.status_led_idle)
                binding.btnStartMonitor.text = getString(R.string.btn_start_monitor)
                binding.btnStartMonitor.setOnClickListener { startMonitoring() }
            }
        }
    }

    private fun setupUI() {
        if (!hasAllPermissions()) {
            requestPermissions()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Will request when user tries to start monitoring
            }
        }

        binding.btnStartMonitor.setOnClickListener {
            startMonitoring()
        }

        binding.btnRecalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        updateLangButtonText()
        
        binding.btnLangSwitch.setOnClickListener {
            toggleLanguage()
        }
    }
    
    private fun updateLangButtonText() {
        binding.btnLangSwitch.text = if (localeIsChinese()) "EN" else "中文"
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
        binding.tvStatusText.text = if (serviceRunning) getString(R.string.status_running) else getString(R.string.status_idle)
        binding.btnStartMonitor.text = if (serviceRunning) getString(R.string.btn_stop_monitor) else getString(R.string.btn_start_monitor)
        binding.btnRecalibrate.text = getString(R.string.btn_recalibrate)
        updateLangButtonText()
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
                    android.net.Uri.parse("package:$packageName")
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

        binding.tvStatusText.text = getString(R.string.status_running)
        binding.viewStatusCircle.background = ContextCompat.getDrawable(
            this, R.drawable.status_led_running
        )
        binding.btnStartMonitor.text = getString(R.string.btn_stop_monitor)
        serviceRunning = true
        binding.btnStartMonitor.setOnClickListener {
            stopMonitoring()
        }
    }

    private fun stopMonitoring() {
        val intent = Intent(this, DistanceMonitorService::class.java).apply {
            action = "ACTION_STOP_MONITORING"
        }
        startService(intent)

        binding.tvStatusText.text = getString(R.string.status_idle)
        binding.viewStatusCircle.background = ContextCompat.getDrawable(
            this, R.drawable.status_led_idle
        )
        binding.btnStartMonitor.text = getString(R.string.btn_start_monitor)
        serviceRunning = false
        binding.btnStartMonitor.setOnClickListener {
            startMonitoring()
        }
        
        distanceUpdateRunnable?.let { 
            distanceUpdateHandler.removeCallbacks(it)
        }
        distanceUpdateRunnable = null
    }
    
    private fun startDistanceUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                val distance = distanceDataStore.getDistance()
                val lastFrame = distanceDataStore.getLastFrameTime()
                val cameraStatus = distanceDataStore.getCameraStatus()
                
                val now = System.currentTimeMillis()
                val frameAgeSec = if (lastFrame > 0) ((now - lastFrame) / 1000).toInt() else -1
                
                if (distance >= 0) {
                    binding.tvEstimatedDistance.text = getString(R.string.distance_info, distance)
                } else {
                    binding.tvEstimatedDistance.text = "等待检测..."
                    when {
                        cameraStatus == "none" -> binding.tvEstimatedDistance.text = "等待检测..."
                        cameraStatus.startsWith("error:") -> binding.tvEstimatedDistance.text = "相机错误"
                        frameAgeSec < 0 -> binding.tvEstimatedDistance.text = "相机就绪..."
                        frameAgeSec > 5 -> binding.tvEstimatedDistance.text = "相机卡住"
                        else -> binding.tvEstimatedDistance.text = "未检测到人脸"
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
