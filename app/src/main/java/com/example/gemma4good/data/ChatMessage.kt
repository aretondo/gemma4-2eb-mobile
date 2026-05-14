package com.example.gemma4good.data

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val imagePath: String? = null,
    val documentId: String? = null
)
