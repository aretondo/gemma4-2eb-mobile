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

    // Fallbacks padrão ultra-simplificados para Gemma 2B
    private var chatPrompt = """
        Você é uma IA médica assistente. 
        OBJETIVO: Responder ao usuário e manter um resumo estruturado dos dados clínicos no campo [CONTEÚDO DO ARQUIVO ATUAL].
        INSTRUÇÃO: 
        1. Responda de forma direta e técnica.
        2. Se o usuário informar novos dados (remédios, sintomas, evolução), resuma-os brevemente para serem salvos no arquivo.
        3. Foco exclusivo em dados médicos. Não faça comentários sobre o sistema.
    """.trimIndent()
    private var ocrPrompt = "Analise o OCR médico e liste: Medicamentos, Dosagens e Orientações. Seja breve."

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
                        chatPrompt = json.optString("chat_system_prompt", chatPrompt)
                        ocrPrompt = json.optString("ocr_system_prompt", ocrPrompt)
                        true
                    } else false
                } else false
            }
        } catch (e: Exception) {
            android.util.Log.e("PromptManager", "Error fetching prompts", e)
            false
        }
    }
}
