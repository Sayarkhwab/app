package com.akash.clipboarddict

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class ClipAccessibilityService : AccessibilityService() {

    private lateinit var clipboard: ClipboardManager
    private val TAG = "ClipAccessibilityService"
    private var lastProcessedText = ""
    private val appPackageName = "com.akash.clipboarddict"

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
        // Optional: Add additional event handling if needed
        // For example, detect text selections for better context
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
        return rootInActiveWindow?.packageName == targetPackage
    }

    private fun fetchAndShow(word: String) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
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

            if (conn.responseCode == 200) {
                return BufferedReader(InputStreamReader(conn.inputStream)).use {
                    val response = it.readText()
                    JSONObject(response).getJSONObject("data").getString("text")
                }
            }
            return null
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
            // Ignore logging errors
        }
    }
}