package com.akash.clipboarddict

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipAccessibilityService : AccessibilityService() {

    private lateinit var clipboard: ClipboardManager
    private var lastProcessedText = ""
    private val appPackageName = "com.akash.clipboarddict"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipChangedListener)
        logDebug("Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                logDebug("Text selection detected in ${event.packageName}")
            }
        }
    }

    override fun onInterrupt() {
        logDebug("Accessibility service interrupted")
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipChangedListener)
        job.cancel()
        super.onDestroy()
    }

    private fun handleClipboardChange() {
        // Skip if our own app is in foreground
        if (isAppInForeground(appPackageName)) {
            logDebug("Ignoring clipboard change from our own app")
            return
        }

        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (!clipText.isNullOrBlank() && clipText != lastProcessedText) {
            lastProcessedText = clipText
            logDebug("New clipboard text: $clipText")
            fetchAndShow(clipText)
        }
    }

    private fun isAppInForeground(targetPackage: String): Boolean {
        return try {
            rootInActiveWindow?.packageName == targetPackage
        } catch (e: SecurityException) {
            logDebug("Security exception: ${e.message}")
            false
        }
    }

    private fun fetchAndShow(word: String) {
        scope.launch {
            try {
                val response = callApi(word)
                if (!response.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        FloatingPromptView(this@ClipAccessibilityService).show(response)
                    }
                }
            } catch (e: Exception) {
                logDebug("API error: ${e.message}")
            }
        }
    }

    private fun callApi(word: String): String? {
        var conn: HttpURLConnection? = null
        try {
            val prompt = """
                【Role Setting】
                You are a refined English-to-Hindi Dictionary. Return precise definitions in Hindi.
                【User's Prompt】
                "$word"
            """.trimIndent()

            val json = JSONObject().apply {
                put("prompt", prompt)
                put("stream", false)
                put("identity_id", "897f785c-ef8f-4c2c-8d07-c53cf9d5cfa9")
            }

            val url = URL("https://supawork.ai/supawork/headshot/api/media/gpt/chat")
            conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 8000
                readTimeout = 8000
                outputStream.write(json.toString().toByteArray())
            }

            return if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                    val response = reader.readText()
                    JSONObject(response).getJSONObject("data").getString("text")
                }
            } else {
                logDebug("API failed with code: ${conn.responseCode}")
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
            File(filesDir, "debug_log.txt").appendText(logMessage)
        } catch (e: Exception) {
            Log.e("ClipDebug", "Log write failed: ${e.message}")
        }
    }
}