package com.akash.clipboarddict

// Add at the top of MainActivity.kt
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var debugTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        debugTextView = findViewById(R.id.debug_text_view)

        updateAccessibilityStatus()
        loadDebugLog()

        // Accessibility permission button
        findViewById<Button>(R.id.enable_accessibility_button).setOnClickListener {
            openAccessibilitySettings()
        }

        // Clear logs button
        findViewById<Button>(R.id.clear_logs_button).setOnClickListener {
            clearDebugLog()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        loadDebugLog()
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        statusText.text = "Accessibility Service: ${if (isEnabled) "ENABLED" else "DISABLED"}"
        findViewById<Button>(R.id.enable_accessibility_button).text = 
            if (isEnabled) "Reconfigure Service" else "Enable Service"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = ComponentName(this, ClipAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName.flattenToString()) == true
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            // Fallback if accessibility settings can't be opened
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun loadDebugLog() {
        try {
            val logFile = File(filesDir, "debug_log.txt")
            if (logFile.exists()) {
                debugTextView.text = logFile.readText()
            } else {
                debugTextView.text = "No debug logs available"
            }
        } catch (e: Exception) {
            debugTextView.text = "Error loading logs: ${e.message}"
        }
    }

    private fun clearDebugLog() {
        try {
            File(filesDir, "debug_log.txt").delete()
            debugTextView.text = "Logs cleared"
        } catch (e: Exception) {
            debugTextView.text = "Error clearing logs: ${e.message}"
        }
    }
}