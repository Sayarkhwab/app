package com.akash.clipboarddict

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardMonitorService : Service() {

    private lateinit var clipboard: ClipboardManager
    private var lastClipText = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ID = "clipboard_monitor_channel"
    private val NOTIFICATION_ID = 101
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        clipboard.addPrimaryClipChangedListener {
            try {
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                logToFile("Clipboard changed: ${clipText ?: "null"}")
                
                if (!clipText.isNullOrBlank() && clipText != lastClipText) {
                    lastClipText = clipText
                    logToFile("Processing copied text: $clipText")
                    processCopiedText(clipText)
                }
            } catch (e: Exception) {
                logToFile("Clipboard error: ${e.message}")
            }
        }
        
        logToFile("Service started")
    }

    private fun processCopiedText(text: String) {
        scope.launch {
            try {
                logToFile("Fetching meaning for: $text")
                val meaning = getMeaningFromAPI(text)
                
                if (!meaning.isNullOrBlank()) {
                    logToFile("Meaning received: $meaning")
                    showFloatingPrompt(meaning)
                } else {
                    logToFile("Empty meaning received")
                }
            } catch (e: Exception) {
                logToFile("API error: ${e.message}")
            }
        }
    }

    private suspend fun getMeaningFromAPI(word: String): String? {
        // This is a placeholder - implement your actual API call here
        return "हिंदी अर्थ: $word (सफलता)"
    }

    private fun showFloatingPrompt(meaning: String) {
        // Remove any existing floating view
        removeFloatingView()
        
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_prompt, null)
        
        // Set the meaning text
        floatingView?.findViewById<TextView>(R.id.promptText)?.text = meaning
        
        // Set up layout parameters
        val params = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }
        
        // Add the view to window
        floatingView?.let {
            windowManager.addView(it, params)
        }
        
        // Set close button action
        floatingView?.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            removeFloatingView()
        }
    }
    
    private fun removeFloatingView() {
        floatingView?.let {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.removeView(it)
            floatingView = null
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Dictionary Active")
            .setContentText("Monitoring for copied text")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring clipboard for dictionary lookups"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun logToFile(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        
        try {
            val file = filesDir.resolve("clip_log.txt")
            file.appendText(logMessage)
        } catch (e: Exception) {
            Log.e("ClipLog", "Failed to write log: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingView()
        logToFile("Service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}