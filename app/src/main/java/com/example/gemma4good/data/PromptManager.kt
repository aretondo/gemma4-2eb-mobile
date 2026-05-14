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

    // Fallbacks padrão
    private var chatPrompt = "INSTRUÇÃO DO SISTEMA: Você é a 'Gemma Scan Assistant', uma ferramenta especializada para profissionais de saúde e pesquisadores. Seu papel é auxiliar na organização, digitalização e análise técnica de dados médicos. Seja extremamente técnica e objetiva. Se o usuário confirmar que o documento está pronto, confirme verbalmente (ex: 'Entendido, marquei como pronto') E inclua a tag [SET_STATUS:READY] na resposta."
    private var ocrPrompt = "INSTRUÇÃO DO SISTEMA: Você é a 'Gemma Scan Assistant'. O profissional enviou um documento médico via OCR. Extraia os dados técnicos. Se o documento parecer completo, pergunte se pode marcar como pronto para sincronizar."

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
