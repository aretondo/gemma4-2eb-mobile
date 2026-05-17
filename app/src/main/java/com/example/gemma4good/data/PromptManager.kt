package com.example.gemma4good.data

import android.content.Context
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class PromptManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Fallback constants - used only when the backend is unreachable
    private val DEFAULT_CHAT_PROMPT = "You are a technical AI assistant. Be brief and direct."
    private val DEFAULT_OCR_PROMPT = "Extract technical data from this OCR text."

    private var chatPrompt = DEFAULT_CHAT_PROMPT
    private var ocrPrompt = DEFAULT_OCR_PROMPT

    fun getChatPrompt() = chatPrompt
    fun getOcrPrompt() = ocrPrompt

    suspend fun fetchPrompts(serverIp: String): Boolean {
        val url = "http://$serverIp:8000/prompts"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        val json = JSONObject(body)
                        // This updates the local variables with whatever the Backend sends
                        chatPrompt = json.optString("chat_system_prompt", DEFAULT_CHAT_PROMPT)
                        ocrPrompt = json.optString("ocr_system_prompt", DEFAULT_OCR_PROMPT)
                        android.util.Log.d("PromptManager", "Successfully updated prompts from backend")
                        true
                    } else false
                } else {
                    android.util.Log.w("PromptManager", "Failed to fetch prompts: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PromptManager", "Error fetching prompts, using local fallbacks", e)
            false
        }
    }
}
