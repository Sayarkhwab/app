package com.akash.clipboarddict

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

class FloatingPromptView(context: Context) {
    private val dialog = BottomSheetDialog(context)
    private val view: View = LayoutInflater.from(context).inflate(R.layout.floating_prompt, null)
    
    init {
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
    }

    fun show(content: String) {
        view.findViewById<TextView>(R.id.promptText).text = content
        if (!dialog.isShowing) {
            dialog.show()
        }
    }

    fun dismiss() {
        dialog.dismiss()
    }
}