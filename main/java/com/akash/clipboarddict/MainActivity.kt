package com.akash.clipboarddict

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var debugTextView: TextView
    private var isServiceActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        debugTextView = findViewById(R.id.debug_text_view)

        // Start clipboard service
        val serviceIntent = Intent(this, ClipboardService::class.java)
        startForegroundService(serviceIntent)
        updateStatusText()
        logDebug("MainActivity started")

        // Toggle service button
        findViewById<Button>(R.id.toggle_service_button).setOnClickListener {
            isServiceActive = !isServiceActive
            val intent = Intent(this, ClipboardService::class.java).apply {
                putExtra("TOGGLE_ACTIVE", isServiceActive)
            }
            startForegroundService(intent)
            updateStatusText()
            logDebug("Service toggled to: $isServiceActive")
            Toast.makeText(this, "Clipboard monitoring ${if (isServiceActive) "started" else "stopped"}", Toast.LENGTH_SHORT).show()
        }

        // Test clipboard button
        findViewById<Button>(R.id.copy_test_button).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clipText.isNullOrBlank()) {
                logDebug("Test button clicked, clipboard text: $clipText")
                Toast.makeText(this, "Clipboard: $clipText", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, ClipboardService::class.java).apply {
                    putExtra("CLIP_TEXT", clipText)
                }
                startForegroundService(intent)
            } else {
                logDebug("Test button clicked, clipboard is empty")
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }

        // Accessibility permission button
        findViewById<Button>(R.id.enable_accessibility_button)?.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Please enable Clipboard Dictionary accessibility service", Toast.LENGTH_LONG).show()
            logDebug("Accessibility settings opened")
        }
    }

    private fun updateStatusText() {
        statusText.text = "Clipboard Monitoring: ${if (isServiceActive) "ON" else "OFF"}"
        findViewById<Button>(R.id.toggle_service_button).text = if (isServiceActive) "Stop Monitoring" else "Start Monitoring"
    }

    private fun logDebug(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        debugTextView.append(logMessage)
        try {
            val file = File(filesDir, "debug_log.txt")
            FileOutputStream(file, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to write log to file", Toast.LENGTH_SHORT).show()
        }
    }
}