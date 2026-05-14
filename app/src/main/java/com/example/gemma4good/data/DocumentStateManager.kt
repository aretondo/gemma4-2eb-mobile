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
    val imagePath: String? = null,
    var context: String = ""
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
                list.add(
                    DocumentState(
                        id = item.optString("id"),
                        extractedText = item.optString("extracted_text"),
                        gemmaDiagnosis = item.optString("gemma_diagnosis"),
                        status = item.optString("status", "PENDING"),
                        imagePath = if (item.has("image_path")) item.optString("image_path") else null,
                        context = item.optString("context", "")
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
                if (d.imagePath != null) put("image_path", d.imagePath)
                put("context", d.context)
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
            // Delete image file if exists
            docToDelete.imagePath?.let { path ->
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
                    if (d.imagePath != null) put("image_path", d.imagePath)
                    put("context", d.context)
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
