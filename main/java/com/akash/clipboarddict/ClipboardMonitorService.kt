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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardMonitorService : Service() {

    private lateinit var clipboard: ClipboardManager
    private var lastClipText = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val CHANNEL_ID = "clipboard_monitor_channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        clipboard.addPrimaryClipChangedListener {
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clipText.isNullOrBlank() && clipText != lastClipText) {
                lastClipText = clipText
                Log.d("ClipMonitor", "Copied text: $clipText")
                processCopiedText(clipText)
            }
        }
    }

    private fun processCopiedText(text: String) {
        scope.launch {
            try {
                val meaning = getMeaningFromAPI(text)
                if (!meaning.isNullOrBlank()) {
                    showFloatingPrompt(meaning)
                }
            } catch (e: Exception) {
                Log.e("ClipMonitor", "API error: ${e.message}")
            }
        }
    }

    private suspend fun getMeaningFromAPI(word: String): String? {
        // Implement your API call here
        // Return the meaning/translation
        return "हिंदी अर्थ: $word" // Placeholder
    }

    private fun showFloatingPrompt(meaning: String) {
        // Implement your floating prompt display
        // This will require a system alert window permission
        // For simplicity, we'll log it for now
        Log.d("ClipMonitor", "Showing meaning: $meaning")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
