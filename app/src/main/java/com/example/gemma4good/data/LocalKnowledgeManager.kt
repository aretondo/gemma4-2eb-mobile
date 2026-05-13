package com.example.gemma4good.data

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class KnowledgeItem(val keywords: List<String>, val content: String)

class LocalKnowledgeManager(private val context: Context) {

    private val knowledgeBase = mutableListOf<KnowledgeItem>()

    init {
        loadKnowledgeBase()
    }

    private fun loadKnowledgeBase() {
        try {
            // Read from raw resources
            val resourceId = context.resources.getIdentifier("knowledge_base", "raw", context.packageName)
            if (resourceId == 0) return

            val inputStream = context.resources.openRawResource(resourceId)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val keywordsArray = item.getJSONArray("keywords")
                val keywordsList = mutableListOf<String>()
                for (j in 0 until keywordsArray.length()) {
                    keywordsList.add(keywordsArray.getString(j).lowercase())
                }
                knowledgeBase.add(KnowledgeItem(keywordsList, item.getString("content")))
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalKnowledgeManager", "Error loading knowledge base", e)
        }
    }

    fun searchContext(query: String): String {
        val lowerQuery = query.lowercase()
        val matchedContents = mutableSetOf<String>()

        for (item in knowledgeBase) {
            for (keyword in item.keywords) {
                if (lowerQuery.contains(keyword)) {
                    matchedContents.add(item.content)
                    break // Evita adicionar o mesmo conteúdo várias vezes se mais de uma palavra-chave der match
                }
            }
        }

        if (matchedContents.isEmpty()) return ""

        return "CONTEXTO LOCAL DE REFERÊNCIA:\n" + matchedContents.joinToString("\n\n")
    }
}
