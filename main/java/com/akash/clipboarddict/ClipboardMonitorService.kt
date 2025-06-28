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
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// Add these imports at the top
import android.view.View
import android.view.LayoutInflater
import android.widget.TextView

class ClipboardMonitorService : Service() {

    private lateinit var clipboard: ClipboardManager
    private var lastClipText = ""
    private val CHANNEL_ID = "clipboard_monitor"
    private val NOTIFICATION_ID = 101
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.addPrimaryClipChangedListener(::handleClipboardChange)
        
        Log.d("ClipService", "Service started and monitoring clipboard")
    }

    private fun handleClipboardChange() {
        try {
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            
            val item = clip.getItemAt(0)
            val text = item.text.toString().trim()
            
            if (text.isNotEmpty() && text != lastClipText) {
                lastClipText = text
                processText(text)
            }
        } catch (e: Exception) {
            Log.e("ClipService", "Error: ${e.message}")
        }
    }

    private fun processText(text: String) {
        // For now, just show the text in a floating window
        showFloatingPrompt(text)
        
        // In your actual implementation, call your API here:
        // CoroutineScope(Dispatchers.IO).launch {
        //     val meaning = getMeaningFromAPI(text)
        //     showFloatingPrompt(meaning)
        // }
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
            
            // Auto-remove after 5 seconds
            view.postDelayed(::removeFloatingView, 5000)
        } catch (e: Exception) {
            Log.e("ClipService", "Floating window error: ${e.message}")
        }
    }
    
    private fun removeFloatingView() {
        floatingView?.let {
            try {
                val windowManager = getSystemService(WindowManager::class.java)
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View not attached
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
            ).apply { description = "Background clipboard monitoring" }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        removeFloatingView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
