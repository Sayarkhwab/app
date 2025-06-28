package com.akash.clipboarddict

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

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

        findViewById<Button>(R.id.enable_accessibility_button).setOnClickListener {
            openAccessibilitySettings()
        }

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
        ) ?: return false
        
        return enabledServices.contains(serviceName.flattenToString())
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun loadDebugLog() {
        try {
            val logFile = File(filesDir, "debug_log.txt")
            debugTextView.text = if (logFile.exists()) {
                logFile.readText()
            } else {
                "No debug logs available"
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