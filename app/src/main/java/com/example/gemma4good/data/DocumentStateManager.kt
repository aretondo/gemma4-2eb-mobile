package com.example.gemma4good.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DocumentState(
    val id: String,
    val extractedText: String,
    var gemmaDiagnosis: String = "",
    var status: String = "PENDING",
    val imagePaths: List<String> = emptyList(),
    var context: String = "",
    var messages: List<ChatMessage> = emptyList()
)

class DocumentStateManager(private val context: Context) {
    private val batchFile = File(context.getExternalFilesDir(null), "sync_batch.json")
    
    fun getDocuments(): List<DocumentState> {
        if (!batchFile.exists()) return emptyList()
        
        val list = mutableListOf<DocumentState>()
        try {
            val jsonString = batchFile.readText()
            val jsonObject = JSONObject(jsonString)
            val jsonArray = jsonObject.optJSONArray("documents") ?: JSONArray()
            
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val imagePathsList = mutableListOf<String>()
                if (item.has("image_paths")) {
                    val pathsArray = item.getJSONArray("image_paths")
                    for (j in 0 until pathsArray.length()) {
                        imagePathsList.add(pathsArray.getString(j))
                    }
                } else if (item.has("image_path")) {
                    // Migração legada
                    item.optString("image_path").takeIf { it.isNotBlank() }?.let { imagePathsList.add(it) }
                }

                val messagesList = mutableListOf<ChatMessage>()
                if (item.has("messages")) {
                    val msgArray = item.getJSONArray("messages")
                    for (j in 0 until msgArray.length()) {
                        val msgObj = msgArray.getJSONObject(j)
                        messagesList.add(
                            ChatMessage(
                                text = msgObj.getString("text"),
                                isUser = msgObj.getBoolean("is_user"),
                                imagePath = if (msgObj.has("image_path")) msgObj.getString("image_path") else null,
                                documentId = if (msgObj.has("document_id")) msgObj.getString("document_id") else null
                            )
                        )
                    }
                }

                list.add(
                    DocumentState(
                        id = item.optString("id"),
                        extractedText = item.optString("extracted_text"),
                        gemmaDiagnosis = item.optString("gemma_diagnosis"),
                        status = item.optString("status", "PENDING"),
                        imagePaths = imagePathsList,
                        context = item.optString("context", ""),
                        messages = messagesList
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DocumentStateManager", "Error reading batch", e)
        }
        return list
    }
    
    fun saveDocument(doc: DocumentState) {
        val currentDocs = getDocuments().toMutableList()
        val existingIndex = currentDocs.indexOfFirst { it.id == doc.id }
        if (existingIndex != -1) {
            currentDocs[existingIndex] = doc
        } else {
            currentDocs.add(doc)
        }
        
        val jsonArray = JSONArray()
        for (d in currentDocs) {
            val item = JSONObject().apply {
                put("id", d.id)
                put("extracted_text", d.extractedText)
                put("gemma_diagnosis", d.gemmaDiagnosis)
                put("status", d.status)
                put("image_paths", JSONArray(d.imagePaths))
                put("context", d.context)
                
                val msgArray = JSONArray()
                for (m in d.messages) {
                    msgArray.put(JSONObject().apply {
                        put("text", m.text)
                        put("is_user", m.isUser)
                        m.imagePath?.let { put("image_path", it) }
                        m.documentId?.let { put("document_id", it) }
                    })
                }
                put("messages", msgArray)
            }
            jsonArray.put(item)
        }
        
        val finalObject = JSONObject().apply {
            put("documents", jsonArray)
        }
        
        try {
            batchFile.writeText(finalObject.toString(2))
        } catch (e: Exception) {
            android.util.Log.e("DocumentStateManager", "Error writing batch", e)
        }
    }

    fun deleteDocument(docId: String) {
        val currentDocs = getDocuments().toMutableList()
        val docToDelete = currentDocs.find { it.id == docId }
        
        if (docToDelete != null) {
            // Delete all image files
            for (path in docToDelete.imagePaths) {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            
            currentDocs.remove(docToDelete)
            
            val jsonArray = JSONArray()
            for (d in currentDocs) {
                val item = JSONObject().apply {
                    put("id", d.id)
                    put("extracted_text", d.extractedText)
                    put("gemma_diagnosis", d.gemmaDiagnosis)
                    put("status", d.status)
                    put("image_paths", JSONArray(d.imagePaths))
                    put("context", d.context)

                    val msgArray = JSONArray()
                    for (m in d.messages) {
                        msgArray.put(JSONObject().apply {
                            put("text", m.text)
                            put("is_user", m.isUser)
                            m.imagePath?.let { put("image_path", it) }
                            m.documentId?.let { put("document_id", it) }
                        })
                    }
                    put("messages", msgArray)
                }
                jsonArray.put(item)
            }
            
            val finalObject = JSONObject().apply {
                put("documents", jsonArray)
            }
            
            try {
                batchFile.writeText(finalObject.toString(2))
            } catch (e: Exception) {
                android.util.Log.e("DocumentStateManager", "Error writing batch after delete", e)
            }
        }
    }
}
