package com.example.gemma4good.data

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

class GemmaInferenceManager(context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    // Path to the model file in the internal storage
    private val modelPath = File(context.getExternalFilesDir(null), "gemma-4-E2B-it.litertlm").absolutePath

    fun isModelAvailable(): Boolean {
        return File(modelPath).exists()
    }

    fun isInitialized(): Boolean = engine != null

    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        if (engine != null) return@withContext

        val file = File(modelPath)
        android.util.Log.d("GemmaInference", "Initializing LiteRT-LM with model at: $modelPath (Size: ${file.length()} bytes)")

        if (!file.exists()) {
            throw Exception("Gemma 4 model not found: $modelPath")
        }

        try {
            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(numOfThreads = 4)
            )
            
            val newEngine = Engine(engineConfig)
            newEngine.initialize()
            engine = newEngine
            conversation = newEngine.createConversation()
            
            android.util.Log.d("GemmaInference", "Gemma 4 initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("GemmaInference", "Failed to initialize LiteRT-LM: ${e.message}", e)
            throw e
        }
    }

    fun generateResponse(prompt: String): Flow<String> = flow {
        val currentConversation = conversation ?: return@flow
        try {
            // Usando a API sendMessageAsync que retorna um Flow no LiteRT-LM 2026
            currentConversation.sendMessageAsync(prompt).collect { messageChunk ->
                val text = messageChunk.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }

                if (text.isNotEmpty()) {
                    emit(text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GemmaInference", "Error generating response", e)
            emit("Erro na geração: ${e.localizedMessage}")
        }
    }.flowOn(Dispatchers.IO)

    fun recreateConversation() {
        conversation?.close()
        conversation = engine?.createConversation()
        android.util.Log.d("GemmaInference", "Native conversation recreated")
    }

    fun close() {
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }
}
