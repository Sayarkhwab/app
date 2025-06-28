package com.akash.clipboarddict

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
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

        findViewById<Button>(R.id.start_button).setOnClickListener {
            startMonitorService()
        }

        findViewById<Button>(R.id.stop_button).setOnClickListener {
            stopMonitorService()
        }
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