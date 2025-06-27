package com.akash.clipboarddict
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import com.akash.clipboarddict.R // Explicit import

class FloatingPromptView(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "FloatingPromptView"

    fun show(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            logDebug("Cannot show prompt: Overlay permission not granted")
            Toast.makeText(context, "Cannot show prompt: Overlay permission needed", Toast.LENGTH_SHORT).show()
            return
        }

        remove()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val displayMetrics = context.resources.displayMetrics
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutParams.y = (displayMetrics.heightPixels * 0.2).roundToInt()

        val promptLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.rounded_background)
            setPadding(24)
            elevation = 10f
            setOnTouchListener(touchListener)
        }

        val promptText = TextView(context).apply {
            text = message
            setTextColor(Color.BLACK)
            textSize = 16f
        }

        val closeButton = Button(context).apply {
            text = "Close"
            setOnClickListener { remove() }
        }

        promptLayout.addView(promptText)
        promptLayout.addView(closeButton)

        try {
            overlayView = promptLayout
            windowManager.addView(promptLayout, layoutParams)
            logDebug("Prompt shown with message: $message")
            Toast.makeText(context, "Showing translation", Toast.LENGTH_SHORT).show()
            scheduleDismiss()
        } catch (e: Exception) {
            logDebug("Failed to show prompt: ${e.message}")
            Toast.makeText(context, "Prompt error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun scheduleDismiss() {
        handler.postDelayed({
            remove()
        }, 10_000)
    }

    private val touchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacksAndMessages(null)
                logDebug("Dismiss paused on touch")
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scheduleDismiss()
                logDebug("Dismiss resumed")
                true
            }
            else -> false
        }
    }

    fun remove() {
        try {
            handler.removeCallbacksAndMessages(null)
            overlayView?.let {
                windowManager.removeView(it)
                logDebug("Prompt removed")
                Toast.makeText(context, "Prompt closed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            logDebug("Failed to remove prompt: ${e.message}")
        } finally {
            overlayView = null
        }
    }

    private fun logDebug(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val logMessage = "[$timestamp] $message\n"
        try {
            val file = File(context.filesDir, "debug_log.txt")
            FileOutputStream(file, true).use { fos ->
                fos.write(logMessage.toByteArray())
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to write log to file", Toast.LENGTH_SHORT).show()
        }
    }
}
