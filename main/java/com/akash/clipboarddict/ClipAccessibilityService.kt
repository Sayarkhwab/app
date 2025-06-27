package com.akash.clipboarddict

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        logDebug("Accessibility service connected")
        Toast.makeText(this, "Accessibility service enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED || 
            event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!clipText.isNullOrBlank()) {
                logDebug("Copy or selection detected: $clipText")
                Toast.makeText(this, "Copied: $clipText", Toast.LENGTH_SHORT).show()
                fetchAndShow(clipText)
            } else {
                logDebug("Copy or selection detected but clipboard is empty")
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {
        logDebug("Accessibility service interrupted")
    }

    private fun fetchAndShow(word: String) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val response = callApi(word)
                if (!response.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        logDebug("Showing prompt for: $response")
                        Toast.makeText(this@ClipAccessibilityService, "Translation: $response", Toast.LENGTH_SHORT).show()
                        FloatingPromptView(this@ClipAccessibilityService).show(response)
                    }
                } else {
                    logDebug("Empty or null API response for word: $word")
                    Toast.makeText(this@ClipAccessibilityService, "No translation received", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                logDebug("API error for word '$word': ${e.message}")
                Toast.makeText(this@ClipAccessibilityService, "API error: ${e.message}", Toast.LENGTH_SHORT).show()
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
}