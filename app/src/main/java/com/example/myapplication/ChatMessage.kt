package com.example.myapplication

import android.graphics.Bitmap

class ChatMessage(
    var content: String?,
    val type: Int,
    val isImage: Boolean = false,
    val imageBitmap: Bitmap? = null,
    val suggestions: MutableList<String?>? = null
) {
    companion object {
        const val TYPE_MINE: Int = 0
        const val TYPE_OTHER: Int = 1
    }
}