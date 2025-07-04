package com.akash.clipboarddict

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButtonView: View
    private lateinit var closeAreaView: View
    private lateinit var meaningPopupView: View
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private val CHANNEL_ID = "floating_button_channel"
    private val NOTIFICATION_ID = 102
    private val TAG = "FloatingButtonService"
    
    // Touch handling constants
    private val MAX_CLICK_DURATION = 200 // milliseconds
    private val MAX_CLICK_DISTANCE = 10 // pixels
    private var pressStartTime = 0L
    private var pressStartX = 0f
    private var pressStartY = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
        createCloseArea()
    }

    private fun createFloatingButton() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingButtonView = inflater.inflate(R.layout.floating_button, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
        
        windowManager.addView(floatingButtonView, params)
        
        floatingButtonView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressStartTime = System.currentTimeMillis()
                    pressStartX = event.rawX
                    pressStartY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    
                    closeAreaView.visibility = View.VISIBLE
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val duration = System.currentTimeMillis() - pressStartTime
                    val distanceX = abs(event.rawX - pressStartX)
                    val distanceY = abs(event.rawY - pressStartY)
                    
                    // Only start dragging after 200ms or significant movement
                    if (duration > 200 || distanceX > 20 || distanceY > 20) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingButtonView, params)
                        
                        // Show/hide close area based on position
                        val metrics = resources.displayMetrics
                        if (params.y > metrics.heightPixels * 0.7) {
                            closeAreaView.findViewById<ImageView>(R.id.close_icon)
                                .setImageResource(R.drawable.ic_close_active)
                        } else {
                            closeAreaView.findViewById<ImageView>(R.id.close_icon)
                                .setImageResource(R.drawable.ic_close_normal)
                        }
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - pressStartTime
                    val distanceX = abs(event.rawX - pressStartX)
                    val distanceY = abs(event.rawY - pressStartY)
                    
                    // Handle as click if short duration and minimal movement
                    if (duration < MAX_CLICK_DURATION && 
                        distanceX < MAX_CLICK_DISTANCE && 
                        distanceY < MAX_CLICK_DISTANCE) {
                        getClipboardTextAndShowMeaning()
                    } 
                    // Handle as close if in bottom area
                    else if (params.y > resources.displayMetrics.heightPixels * 0.7) {
                        stopSelf()
                    }
                    
                    closeAreaView.visibility = View.GONE
                    true
                }
                
                else -> false
            }
        }
    }

    private fun createCloseArea() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        closeAreaView = inflater.inflate(R.layout.close_area, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            200,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }
        
        windowManager.addView(closeAreaView, params)
        closeAreaView.visibility = View.GONE
    }

    private fun createMeaningPopup() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        meaningPopupView = inflater.inflate(R.layout.meaning_popup, null)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        
        windowManager.addView(meaningPopupView, params)
        meaningPopupView.visibility = View.GONE
    }

    private fun getClipboardTextAndShowMeaning() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        
        val text = clip.getItemAt(0).text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        
        // Show loading state
        showMeaningPopup("Loading meaning...", true)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val meaning = getMeaningFromAPI(text)
                if (meaning != null) {
                    saveToDictionaryFile(text, meaning)
                    withContext(Dispatchers.Main) {
                        showMeaningPopup(meaning, false)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showMeaningPopup("Meaning not found", false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showMeaningPopup("Error: ${e.message}", false)
                }
            }
        }
    }

    private fun showMeaningPopup(message: String, isLoading: Boolean) {
        if (!::meaningPopupView.isInitialized) {
            createMeaningPopup()
        }
        
        meaningPopupView.visibility = View.VISIBLE
        meaningPopupView.findViewById<TextView>(R.id.meaning_text).text = message
        
        if (isLoading) {
            meaningPopupView.findViewById<TextView>(R.id.meaning_text)
                .setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_loading, 0)
        } else {
            meaningPopupView.findViewById<TextView>(R.id.meaning_text)
                .setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            
            // Auto-hide after 5 seconds
            meaningPopupView.postDelayed({
                meaningPopupView.visibility = View.GONE
            }, 5000)
        }
    }

    private suspend fun getMeaningFromAPI(word: String): String? {
        try {
            val prompt = """
                【Role Setting】
                You are a refined and intelligent **English-to-Hindi Dictionary**. 
                For every word, phrase, or sentence provided, return a precise, 
                polished definition in Hindi. Your tone must be scholarly yet 
                accessible and briefed. 
                【User's Provided Prompt】
                \"$word\"
            """.trimIndent()

            val json = JSONObject().apply {
                put("prompt", prompt)
                put("stream", false)
                put("identity_id", "897f785c-ef8f-4c2c-8d07-c53cf9d5cfa9")
            }

            val url = URL("https://supawork.ai/supawork/headshot/api/media/gpt/chat")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            
            // Write JSON payload
            conn.outputStream.use { os ->
                os.write(json.toString().toByteArray())
            }

            return if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { reader ->
                    val response = reader.readText()
                    JSONObject(response).getJSONObject("data").getString("text")
                }
            } else {
                // Read error stream for debugging
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "No error details"
                Log.e(TAG, "API error ${conn.responseCode}: $error")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call failed", e)
            return null
        }
    }

    private fun saveToDictionaryFile(word: String, meaning: String) {
        try {
            val file = File(getExternalFilesDir(null), "dictionary.json")
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val entry = "$timestamp: $word - $meaning\n"
            
            if (!file.exists()) {
                file.createNewFile()
            }
            
            FileOutputStream(file, true).use {
                it.write(entry.toByteArray())
            }
            Log.d(TAG, "Saved to dictionary: $word")
        } catch (e: Exception) {
            Log.e(TAG, "Save failed: ${e.message}")
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipDict Active")
            .setContentText("Floating button is enabled")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Button Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Background service for floating dictionary button"
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(floatingButtonView)
            windowManager.removeView(closeAreaView)
            if (::meaningPopupView.isInitialized) {
                windowManager.removeView(meaningPopupView)
            }
        } catch (e: Exception) {
            // Views not attached
        }
        Toast.makeText(this, "Floating button disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
