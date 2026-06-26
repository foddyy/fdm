package com.example.facedistancemonitor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.example.facedistancemonitor.databinding.ActivityMainBinding

/**
 * MainActivity - Main control panel for the Face Distance Monitor app.
 * Handles permissions, calibration navigation, and service start/stop.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        // Check if calibrated
        val isCalibrated = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .contains("baseline_eye_distance_px")

        if (!isCalibrated) {
            // Redirect to calibration
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        setupUI()
    }

    private fun setupUI() {
        // Check if we have all required permissions
        if (!hasAllPermissions()) {
            requestPermissions()
        }

        // Check overlay permission for alert display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Will request when user tries to start monitoring
            }
        }

        // Start monitoring button
        binding.btnStartMonitor.setOnClickListener {
            startMonitoring()
        }

        // Recalibrate button
        binding.btnRecalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
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
        // Check overlay permission
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

        // Start the foreground service
        val intent = Intent(this, DistanceMonitorService::class.java).apply {
            action = "ACTION_START_MONITORING"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Update UI
        binding.tvStatusText.text = getString(R.string.status_running)
        binding.viewStatusCircle.background = ContextCompat.getDrawable(
            this, R.drawable.status_circle_running
        )
        binding.btnStartMonitor.text = getString(R.string.btn_stop_monitor)
        binding.btnStartMonitor.setOnClickListener {
            stopMonitoring()
        }
    }

    private fun stopMonitoring() {
        val intent = Intent(this, DistanceMonitorService::class.java).apply {
            action = "ACTION_STOP_MONITORING"
        }
        startService(intent)

        // Reset UI
        binding.tvStatusText.text = getString(R.string.status_idle)
        binding.viewStatusCircle.background = ContextCompat.getDrawable(
            this, R.drawable.status_circle_idle
        )
        binding.btnStartMonitor.text = getString(R.string.btn_start_monitor)
        binding.btnStartMonitor.setOnClickListener {
            startMonitoring()
        }
    }
}
