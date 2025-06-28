package com.akash.clipboarddict

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        // Check and request overlay permission
        findViewById<Button>(R.id.start_button).setOnClickListener {
            if (checkOverlayPermission()) {
                startMonitorService()
            }
        }

        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopMonitorService()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun startMonitorService() {
        val serviceIntent = Intent(this, ClipboardMonitorService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        Toast.makeText(this, "Clipboard monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopMonitorService() {
        val serviceIntent = Intent(this, ClipboardMonitorService::class.java)
        stopService(serviceIntent)
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }
}