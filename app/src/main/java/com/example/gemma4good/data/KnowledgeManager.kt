package com.example.gemma4good.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class KnowledgeChunk(
    val source: String,
    val text: String
)

class KnowledgeManager(private val context: Context) {
    private val knowledgeFile = File(context.filesDir, "knowledge.json")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var cachedChunks: List<KnowledgeChunk>? = null

    fun getChunks(): List<KnowledgeChunk> {
        if (cachedChunks != null) return cachedChunks!!
        
        if (!knowledgeFile.exists()) return emptyList()
        
        val list = mutableListOf<KnowledgeChunk>()
        try {
            val jsonString = knowledgeFile.readText()
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.optJSONArray("chunks") ?: org.json.JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                list.add(
                    KnowledgeChunk(
                        source = item.optString("source"),
                        text = item.optString("text")
                    )
                )
            }
            cachedChunks = list
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeManager", "Error reading knowledge", e)
        }
        return list
    }

    suspend fun syncKnowledge(serverIp: String): Boolean {
        val url = "http://$serverIp:8000/knowledge"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        knowledgeFile.writeText(body)
                        cachedChunks = null // Invalidate cache
                        true
                    } else false
                } else false
            }
        } catch (e: Exception) {
            android.util.Log.e("KnowledgeManager", "Error syncing knowledge", e)
            false
        }
    }

    /**
     * Busca simplificada por palavras-chave.
     * Retorna os N trechos mais relevantes.
     */
    fun findRelevantChunks(query: String, limit: Int = 3): List<KnowledgeChunk> {
        val chunks = getChunks()
        if (chunks.isEmpty()) return emptyList()

        // Incluindo palavras menores (com 3 letras) e removendo acentos/caracteres especiais se necessário
        val queryWords = query.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length >= 3 }
        
        if (queryWords.isEmpty()) return emptyList()

        android.util.Log.d("KnowledgeManager", "Searching for keywords: $queryWords")

        return chunks.map { chunk ->
            val chunkTextLower = chunk.text.lowercase()
            val score = queryWords.count { word -> chunkTextLower.contains(word) }
            chunk to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
    }
}
