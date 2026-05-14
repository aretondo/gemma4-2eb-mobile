package com.example.gemma4good.data

import android.content.Context
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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
    
    // IP da sua máquina local
    private var serverIp = "192.168.68.102"

    fun setServerIp(ip: String) {
        serverIp = ip
    }

    suspend fun syncDocument(doc: DocumentState): Boolean {
        val url = "http://$serverIp:8000/sync" // Endpoint proposto no blueprint
        
        val json = JSONObject().apply {
            put("id", doc.id)
            put("extracted_text", doc.extractedText)
            put("gemma_diagnosis", doc.gemmaDiagnosis)
            put("status", doc.status)
            put("context", doc.context)

            // Converter imagens para Base64 para envio
            val imagesArray = JSONArray()
            for (path in doc.imagePaths) {
                try {
                    val base64 = encodeImageToBase64(path)
                    if (base64 != null) {
                        imagesArray.put(base64)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SyncManager", "Error encoding image $path", e)
                }
            }
            put("images", imagesArray)
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

    private fun encodeImageToBase64(path: String): String? {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }
}
