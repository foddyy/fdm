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
        
        // 先恢复语言设置（必须在setContentView之前）
        restoreLanguage()
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        distanceDataStore = DistanceDataStore(this)
        distanceUpdateHandler = Handler(Looper.getMainLooper())
        
        // 先检查是否已校准
        val isCalibrated = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .contains("baseline_eye_distance_px")
        
        if (!isCalibrated) {
            // 未校准：跳转校准界面
            requestAllPermissions {
                startActivity(Intent(this, CalibrationActivity::class.java))
            }
        } else {
            // 已校准：请求权限后启动
            requestAllPermissions {
                setupUI()
                startDistanceUpdates()
            }
        }
    }
    
    /** 请求所有权限 */
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
        
        // 同步Service真实运行状态到UI（关键！Activity重建后serviceRunning被重置为false）
        syncServiceStateToUI()
        
        // 从校准界面返回时，确保UI和距离更新已启动
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
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val actuallyRunning = prefs.getBoolean("service_monitoring", false)
        
        if (actuallyRunning != serviceRunning) {
            serviceRunning = actuallyRunning
            if (serviceRunning) {
                binding.tvStatusText.text = getString(R.string.status_running)
                binding.viewStatusCircle.background = ContextCompat.getDrawable(this, R.drawable.status_led_running)
                binding.btnStartMonitor.text = getString(R.string.btn_stop_monitor)
                binding.btnStartMonitor.setOnClickListener { stopMonitoring() }
            } else {
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

        // 设置语言按钮初始文本
        updateLangButtonText()
        
        // 语言切换按钮
        binding.btnLangSwitch.setOnClickListener {
            toggleLanguage()
        }
    }
    
    /** 更新语言按钮文本 */
    private fun updateLangButtonText() {
        binding.btnLangSwitch.text = if (localeIsChinese()) "EN" else "中文"
    }

    /** 切换中英文 */
    private fun toggleLanguage() {
        val newLocale = if (localeIsChinese()) "en" else "zh"
        saveLanguage(newLocale)
        
        // 更新资源配置（关键！否则 getString 仍返回旧语言）
        val locale = java.util.Locale(newLocale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        
        // 更新UI文本
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
    
    /** 更新所有UI文本（用于语言切换时） */
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
        
        // 停止距离更新
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
        // 横竖屏切换时通知Service重启相机
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
