package com.example.gemma4good.data

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    // IP do servidor Streamlit local (ajuste conforme necessário)
    private var serverIp = "10.0.2.2" // Gateway padrão para o host do emulador Android

    fun setServerIp(ip: String) {
        serverIp = ip
    }

    suspend fun syncDocument(doc: DocumentState): Boolean {
        val url = "http://$serverIp:8501/sync" // Endpoint proposto no blueprint
        
        val json = JSONObject().apply {
            put("id", doc.id)
            put("extracted_text", doc.extractedText)
            put("gemma_diagnosis", doc.gemmaDiagnosis)
            put("status", doc.status)
            put("context", doc.context)
        }

        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncManager", "Error syncing document ${doc.id}", e)
            false
        }
    }
}
