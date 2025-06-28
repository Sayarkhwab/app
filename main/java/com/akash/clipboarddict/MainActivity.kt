package com.akash.clipboarddict

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView
    
    // Broadcast receiver to get logs from the service
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "CLIPBOARD_LOG") {
                val message = intent.getStringExtra("message") ?: ""
                log(message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        logView = findViewById(R.id.log_view)
        scrollView = findViewById(R.id.scroll_view)
        log("App started")

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        // Start service button
        findViewById<Button>(R.id.start_btn).setOnClickListener {
            if (checkOverlayPermission()) {
                startClipboardService()
            }
        }

        // Stop service button
        findViewById<Button>(R.id.stop_btn).setOnClickListener {
            stopClipboardService()
        }
    }

    override fun onResume() {
        super.onResume()
        // Register the broadcast receiver
        registerReceiver(logReceiver, IntentFilter("CLIPBOARD_LOG"))
    }

    override fun onPause() {
        super.onPause()
        // Unregister to avoid leaks
        unregisterReceiver(logReceiver)
    }

    private fun checkOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            log("Overlay permission requested")
            return false
        }
        return true
    }

    private fun startClipboardService() {
        val serviceIntent = Intent(this, ClipboardService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        log("Clipboard service started")
    }

    private fun stopClipboardService() {
        val serviceIntent = Intent(this, ClipboardService::class.java)
        stopService(serviceIntent)
        log("Clipboard service stopped")
    }

    private fun log(message: String) {
        runOnUiThread {
            logView.append("$message\n")
            // Scroll to bottom
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}