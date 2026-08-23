package com.example.facedistancemonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
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

    private fun setupUI() {
        if (!hasAllPermissions()) {
            requestPermissions()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Will request when user tries to start monitoring
            }
        }

        // Header card - app icon and title
        binding.ivAppIcon.setImageResource(R.drawable.ic_logo)
        binding.tvAppTitle.text = getString(R.string.app_name)

        // Header card - language toggle
        binding.btnLanguage.setOnClickListener {
            toggleLanguage()
        }

        // Start/Pause button
        binding.btnStartPause.setOnClickListener {
            if (serviceRunning) {
                stopMonitoring()
            } else {
                startMonitoring()
            }
        }

        // Calibrate button
        binding.btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
        
        updateLangButtonText()
    }

    private fun updateLangButtonText() {
        binding.btnLanguage.text = if (localeIsChinese()) "EN" else "中文"
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

    private fun refreshAllText() {
        // Update header
        binding.tvAppTitle.text = getString(R.string.app_name)
        updateLangButtonText()
        
        // Update buttons
        binding.btnStartPause.text = if (serviceRunning) getString(R.string.btn_stop_monitor) else getString(R.string.btn_start_monitor)
        binding.btnCalibrate.text = getString(R.string.btn_calibrate)
        
        // Update threshold
        binding.tvDistance.text = "--"
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
        if (isRunning) {
            binding.btnStartPause.text = getString(R.string.btn_stop_monitor)
            binding.btnStartPause.alpha = 1.0f
        } else {
            binding.btnStartPause.text = getString(R.string.btn_start_monitor)
            binding.btnStartPause.alpha = 0.7f
        }
    }
    
    private fun startDistanceUpdates() {
        val runnable = object : Runnable {
            override fun run() {
                val distance = distanceDataStore.getDistance()
                val lastFrame = distanceDataStore.getLastFrameTime()
                val cameraStatus = distanceDataStore.getCameraStatus()
                
                val now = System.currentTimeMillis()
                val frameAgeSec = if (lastFrame > 0) ((now - lastFrame) / 1000).toInt() else -1
                
                binding.tvDistance.text = if (distance >= 0) "$distance" else {
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
