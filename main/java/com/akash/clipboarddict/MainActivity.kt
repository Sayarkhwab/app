package com.akash.clipboarddict

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d(TAG, "Activity created")

        findViewById<Button>(R.id.start_btn).setOnClickListener {
            Log.d(TAG, "Start button clicked")
            if (checkOverlayPermission()) {
                Log.d(TAG, "Overlay permission granted, starting service")
                startClipboardService()
            }
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
            !Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Overlay permission not granted, requesting")
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun startClipboardService() {
        Log.d(TAG, "Starting clipboard service")
        val serviceIntent = Intent(this, ClipboardMonitorService::class.java)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Clipboard monitoring started", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Service start command sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}")
            Toast.makeText(this, "Failed to start service", Toast.LENGTH_SHORT).show()
        }
    }
}