package com.akash.clipboarddict

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ClipboardService : Service() {

    private lateinit var clipboard: ClipboardManager
    private var isActive = true
    private val CHANNEL_ID = "clipboard_service_channel"
    private val NOTIFICATION_ID = 1
    private val TAG = "ClipboardService"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.addPrimaryClipChangedListener {
                if (isActive) {
                    val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                    if (!clipText.isNullOrBlank()) {
                        logDebug("Clipboard text: $clipText")
                        Toast.makeText(this, "Clipboard: $clipText", Toast.LENGTH_SHORT).show()
                        fetchAndShow(clipText)
                    } else {
                        logDebug("Clipboard is empty")
                        Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            logDebug("Failed to initialize clipboard listener: ${e.message}")
            Toast.makeText(this, "Clipboard listener error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isActive = intent?.getBooleanExtra("TOGGLE_ACTIVE", isActive) ?: isActive
        intent?.getStringExtra("CLIP_TEXT")?.let {
            if (isActive) {
                logDebug("Received text via intent: $it")
                fetchAndShow(it)
            }
        }
        logDebug("Service started, isActive: $isActive")
        updateNotification()
        return START_STICKY
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val toggleIntent = Intent(this, ClipboardService::class.java).apply {
            putExtra("TOGGLE_ACTIVE", !isActive)
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionLabel = if (isActive) "Disable" else "Enable"

        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Dictionary")
            .setContentText(if (isActive) "Monitoring: ON" else "Monitoring: OFF")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .addAction(0, actionLabel, togglePendingIntent)
            .addAction(0, "Download App", openPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for Clipboard Dictionary service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun fetchAndShow(word: String) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val response = callApi(word)
                if (!response.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        logDebug("Showing prompt for: $response")
                        Toast.makeText(this@ClipboardService, "Translation: $response", Toast.LENGTH_SHORT).show()
                        FloatingPromptView(this@ClipboardService).show(response)
                    }
                } else {
                    logDebug("Empty or null API response for word: $word")
                    Toast.makeText(this@ClipboardService, "No translation received", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                logDebug("API error for word '$word': ${e.message}")
                Toast.makeText(this@ClipboardService, "API error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun callApi(word: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val prompt = """
                【Role Setting】
                You are a refined and intelligent **English-to-Hindi Dictionary**. For every word, phrase, or sentence provided, return a precise, polished definition in Hindi. Your tone must be scholarly yet accessible. 
                Honor the origin and field of the word — whether it belongs to **biology**, **philosophy**, **technology**, or any other domain, reflect that in your definition very short and brief. 

                【User's Provided Prompt】
                "$word"
            """.trimIndent()

            val json = JSONObject().apply {
                put("prompt", prompt)
                put("stream", false)
                put("identity_id", "897f785c-ef8f-4c2c-8d07-c53cf9d5cfa9")
            }

            val url = URL("https://supawork.ai/supawork/headshot/api/media/gpt/chat")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.outputStream.write(json.toString().toByteArray())

            val responseCode = conn.responseCode
            return if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                logDebug("API response: $response")
                val resJson = JSONObject(response)
                resJson.getJSONObject("data").getString("text").also {
                    reader.close()
                }
            } else {
                logDebug("API failed with response code: $responseCode")
                null
            }
        } catch (e: Exception) {
            logDebug("API call failed: ${e.message}")
            return null
        } finally {
            conn?.disconnect()
        }
    }

    private fun logDebug(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        try {
            val file = File(filesDir, "debug_log.txt")
            FileOutputStream(file, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to write log to file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        logDebug("Service destroyed")
        Toast.makeText(this, "Clipboard service stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}