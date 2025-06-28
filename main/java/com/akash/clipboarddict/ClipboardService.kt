package com.akash.clipboarddict

import android.app.Notification
import android.os.Handler
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardService : Service() {

    private lateinit var clipboard: ClipboardManager
    private var lastClipText = ""
    private val CHANNEL_ID = "clipboard_service_channel"
    private val NOTIFICATION_ID = 1
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        // Start with current clipboard content
        checkClipboard()
        
        // Set up clipboard listener
        clipboard.addPrimaryClipChangedListener {
            log("Clipboard changed detected")
            checkClipboard()
        }
    }

    private fun checkClipboard() {
        try {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                val text = item.text.toString().trim()
                
                if (text.isNotEmpty() && text != lastClipText) {
                    log("New clipboard text: $text")
                    lastClipText = text
                    processText(text)
                }
            }
        } catch (e: Exception) {
            log("Clipboard error: ${e.message}")
        }
    }

    private fun processText(text: String) {
        // In a real app, you would call your API here
        // For now, just show the text in a floating window
        showFloatingPrompt("Detected: $text")
    }

    private fun showFloatingPrompt(message: String) {
        runOnUiThread {
            try {
                // Remove existing view if any
                removeFloatingView()
                
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                
                // Create floating view
                floatingView = inflater.inflate(R.layout.floating_prompt, null)
                floatingView?.findViewById<TextView>(R.id.promptText)?.text = message
                
                // Set layout parameters
                val params = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
                
                // Add view to window
                floatingView?.let { windowManager.addView(it, params) }
                
                // Set close button action
                floatingView?.findViewById<View>(R.id.closeButton)?.setOnClickListener {
                    removeFloatingView()
                }
                
                log("Floating window shown")
            } catch (e: Exception) {
                log("Floating window error: ${e.message}")
            }
        }
    }
    
    private fun removeFloatingView() {
        floatingView?.let {
            try {
                val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(it)
                floatingView = null
                log("Floating window removed")
            } catch (e: Exception) {
                log("Error removing floating view: ${e.message}")
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clipboard Dictionary")
            .setContentText("Monitoring clipboard")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Clipboard Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background clipboard monitoring"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun runOnUiThread(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainExecutor.execute(action)
        } else {
            // Fallback for older versions
            Handler(mainLooper).post(action)
        }
    }
    
    private fun log(message: String) {
        // Send broadcast to update UI in MainActivity
        val intent = Intent("CLIPBOARD_LOG")
        intent.putExtra("message", message)
        sendBroadcast(intent)
        
        // Also log to system
        Log.d("ClipboardService", message)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingView()
        log("Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
