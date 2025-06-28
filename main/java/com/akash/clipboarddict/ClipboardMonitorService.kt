package com.akash.clipboarddict

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardMonitorService : Service() {
    private val TAG = "ClipService"
    private lateinit var clipboard: ClipboardManager
    private var lastClipText = ""
    private val CHANNEL_ID = "clipboard_monitor"
    private val NOTIFICATION_ID = 101
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        logToFile("Service created")
        
        // Create notification channel
        createNotificationChannel()
        
        // Start as foreground service
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        logToFile("Foreground service started")
        
        // Setup clipboard monitoring
        setupClipboardListener()
    }

    private fun setupClipboardListener() {
        try {
            clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.addPrimaryClipChangedListener {
                logToFile("Clipboard changed detected")
                handleClipboardChange()
            }
            Log.d(TAG, "Clipboard listener registered")
            logToFile("Clipboard listener registered")
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard setup error: ${e.message}")
            logToFile("Clipboard setup error: ${e.message}")
        }
    }

    private fun handleClipboardChange() {
        try {
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            
            val item = clip.getItemAt(0)
            val text = item.text?.toString()?.trim() ?: ""
            logToFile("Clipboard text: '$text'")
            
            if (text.isNotEmpty() && text != lastClipText) {
                lastClipText = text
                logToFile("Processing new text: '$text'")
                processText(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard error: ${e.message}")
            logToFile("Clipboard error: ${e.message}")
        }
    }

    private fun processText(text: String) {
        logToFile("Showing floating prompt for: '$text'")
        showFloatingPrompt(text)
    }

    private fun showFloatingPrompt(message: String) {
        try {
            removeFloatingView()
            
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(this)
            
            val view = inflater.inflate(R.layout.floating_prompt, null)
            view.findViewById<TextView>(R.id.promptText).text = message
            
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }
            
            windowManager.addView(view, params)
            floatingView = view
            logToFile("Floating window shown")
            
            // Auto-remove after 5 seconds
            view.postDelayed({
                logToFile("Auto-removing floating window")
                removeFloatingView()
            }, 5000)
        } catch (e: Exception) {
            Log.e(TAG, "Floating window error: ${e.message}")
            logToFile("Floating window error: ${e.message}")
        }
    }
    
    private fun removeFloatingView() {
        floatingView?.let { view ->
            try {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
                logToFile("Floating window removed")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view: ${e.message}")
                logToFile("Error removing floating view: ${e.message}")
            }
            floatingView = null
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Dictionary")
            .setContentText("Monitoring clipboard for words")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Monitor",
                NotificationManager.IMPORTANCE_MIN
            ).apply { 
                description = "Background clipboard monitoring"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
            logToFile("Notification channel created")
        }
    }
    
    private fun logToFile(message: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = "[$timestamp] $message\n"
            
            val file = File(filesDir, "clip_log.txt")
            file.appendText(logMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Log write failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        logToFile("Service onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        logToFile("Service onDestroy")
        removeFloatingView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}