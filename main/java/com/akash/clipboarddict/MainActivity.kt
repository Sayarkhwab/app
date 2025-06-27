package com.akash.clipboarddict

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var debugTextView: TextView
    private val debugLogs = StringBuilder()
    private val OVERLAY_PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        debugTextView = findViewById(
R.id.debug_text_view)
        updateDebugLog("MainActivity started")

        // Check overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        } else {
            startClipboardService()
        }

        findViewById<Button>(R.id.toggle_service_button)?.setOnClickListener {
            if (isServiceRunning(ClipboardService::class.java)) {
                stopService(Intent(this, ClipboardService::class.java))
                updateDebugLog("Clipboard monitoring stopped")
                Toast.makeText(this, "Clipboard monitoring stopped", Toast.LENGTH_SHORT).show()
            } else {
                startClipboardService()
                updateDebugLog("Clipboard monitoring started")
                Toast.makeText(this, "Clipboard monitoring started", Toast.LENGTH_SHORT).show()
            }
            updateUI()
        }

        findViewById<Button>(R.id.copy_test_button)?.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clipText.isNullOrBlank()) {
                updateDebugLog("Clipboard text: $clipText")
                Toast.makeText(this, "Clipboard: $clipText", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ClipboardService::class.java).apply {
                    putExtra("CLIP_TEXT", clipText)
                }
                startService(intent)
            } else {
                updateDebugLog("Clipboard is empty")
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        updateUI()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs 'Draw over other apps' permission to display translations. Please enable it in settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
            }
            .setNegativeButton("Cancel") { _, _ ->
                updateDebugLog("Overlay permission denied")
                Toast.makeText(this, "Permission denied, app functionality limited", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Settings.canDrawOverlays(this)) {
                updateDebugLog("Overlay permission granted")
                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                startClipboardService()
            } else {
                updateDebugLog("Overlay permission denied")
                Toast.makeText(this, "Overlay permission denied, cannot show prompts", Toast.LENGTH_LONG).show()
                showOverlayPermissionDialog()
            }
        }
    }

    private fun startClipboardService() {
        if (!isServiceRunning(ClipboardService::class.java)) {
            val serviceIntent = Intent(this, ClipboardService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            updateDebugLog("ClipboardService started")
        } else {
            updateDebugLog("ClipboardService already running")
        }
    }

    private fun updateUI() {
        val statusText = findViewById<TextView>(R.id.status_text)
        val toggleButton = findViewById<Button>(R.id.toggle_service_button)
        if (isServiceRunning(ClipboardService::class.java)) {
            statusText?.text = "Clipboard Monitoring: ON"
            toggleButton?.text = "Stop Monitoring"
        } else {
            statusText?.text = "Clipboard Monitoring: OFF"
            toggleButton?.text = "Start Monitoring"
        }
        debugTextView.text = debugLogs.toString()
    }

    private fun updateDebugLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        debugLogs.append("[$timestamp] $message\n")
        debugTextView.text = debugLogs.toString()
        writeLogToFile(message)
    }

    private fun writeLogToFile(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message\n"
            val file = File(filesDir, "debug_log.txt")
            FileOutputStream(file, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to write log to file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
