package com.example.gemma4good.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemma4good.data.ChatMessage
import com.example.gemma4good.data.DocumentState
import com.example.gemma4good.data.GemmaInferenceManager
import com.example.gemma4good.data.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableStateOf

sealed class ChatState {
    object ModelMissing : ChatState()
    object Downloading : ChatState()
    object LoadingModel : ChatState()
    object Idle : ChatState()
    object Generating : ChatState()
    data class Error(val message: String) : ChatState()
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val inferenceManager = GemmaInferenceManager(application)
    private val modelDownloader = ModelDownloader(application)
    private val knowledgeManager = com.example.gemma4good.data.KnowledgeManager(application)
    private val documentManager = com.example.gemma4good.data.DocumentStateManager(application)
    private val syncManager = com.example.gemma4good.data.SyncManager(application)
    private val promptManager = com.example.gemma4good.data.PromptManager(application)

    private val _state = MutableStateFlow<ChatState>(ChatState.LoadingModel)
    val state: StateFlow<ChatState> = _state

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _documents = MutableStateFlow<List<DocumentState>>(emptyList())
    val documents: StateFlow<List<DocumentState>> = _documents

    var currentDocumentId: String? = null
    var selectedDocumentId = mutableStateOf<String?>(null)

    fun getDocumentManager() = documentManager

    init {
        loadRecentMemory()
        checkModelState()
        refreshDocuments()
        fetchSystemPrompts()
    }

    private fun fetchSystemPrompts() {
        viewModelScope.launch(Dispatchers.IO) {
            promptManager.fetchPrompts("192.168.68.103")
        }
    }

    fun startNewChat() {
        currentDocumentId = null
        _messages.value = emptyList()
        _state.value = ChatState.Idle
        
        // Refresh native conversation to clear any leaked memory/state in liblitertlm
        inferenceManager.recreateConversation()

        // Clear persistent memory file to stop the model from "copying" previous Portuguese responses
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val memoryFile = java.io.File(getApplication<Application>().getExternalFilesDir(null), "recent_memory.json")
                if (memoryFile.exists()) {
                    memoryFile.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error clearing memory", e)
            }
        }
    }

    private fun updateCurrentDocumentMessages() {
        currentDocumentId?.let { docId ->
            val doc = documentManager.getDocuments().find { it.id == docId }
            if (doc != null) {
                documentManager.saveDocument(doc.copy(messages = _messages.value))
            }
        }
    }

    fun refreshDocuments() {
        _documents.value = documentManager.getDocuments()
    }

    private fun checkModelState() {
        viewModelScope.launch {
            if (!modelDownloader.isModelDownloaded()) {
                _state.value = ChatState.ModelMissing
            } else {
                loadModel()
            }
        }
    }

    private var currentDownloadId: Long? = null

    fun startDownload() {
        _state.value = ChatState.Downloading
        currentDownloadId = modelDownloader.downloadModel()
        monitorDownload()
    }

    private fun monitorDownload() {
        val downloadId = currentDownloadId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var downloading = true
            while (downloading) {
                val statusResult = modelDownloader.getDownloadStatus(downloadId)
                val status = statusResult.first
                val reason = statusResult.second
                
                android.util.Log.d("ChatViewModel", "Download status: $status, reason: $reason")

                when (status) {
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        downloading = false
                        withContext(Dispatchers.Main) {
                            loadModel()
                        }
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        downloading = false
                        withContext(Dispatchers.Main) {
                            _state.value = ChatState.Error("Download falhou (Erro $reason). Verifique se tem acesso ao modelo no Hugging Face.")
                        }
                    }
                    -1 -> { }
                }
                if (downloading) delay(3000)
            }
        }
    }

    private fun loadModel() {
        viewModelScope.launch {
            _state.value = ChatState.LoadingModel
            try {
                inferenceManager.initialize(modelDownloader.getModelPath())
                _state.value = ChatState.Idle
            } catch (e: Exception) {
                _state.value = ChatState.Error("Falha ao carregar modelo: ${e.localizedMessage}")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(text, true))
        _messages.value = currentMessages

        // Se não houver documento vinculado, cria um agora
        if (currentDocumentId == null) {
            val newDocId = "doc_${System.currentTimeMillis()}"
            val newDoc = DocumentState(
                id = newDocId,
                extractedText = "",
                context = "Relato inicial: $text",
                messages = _messages.value
            )
            documentManager.saveDocument(newDoc)
            currentDocumentId = newDocId
            refreshDocuments()
        }

        _state.value = ChatState.Generating

        viewModelScope.launch(Dispatchers.Default) {
            try {
                // --- ETAPA 1: Intent Parsing com Gemma ---
                val intentPrompt = """
                    Classify the user's intent in exactly ONE word:
                    - 'READY' (if they want to finalize, save, or say it's done/ok)
                    - 'PENDING' (if they want to change, correct, or add something new)
                    - 'QUERY' (if it's a technical question or doubt)
                    
                    Message: "$text"
                    Intent:
                """.trimIndent()
                
                var intentResponse = ""
                inferenceManager.generateResponse(intentPrompt).collect { token ->
                    intentResponse += token
                }
                val intent = intentResponse.uppercase().trim()
                android.util.Log.d("ChatViewModel", "Gemma detected intent: $intent")

                // --- ETAPA 2: Execução baseada na intenção ---
                if (intent.contains("READY")) {
                    withContext(Dispatchers.Main) { handleStatusCommand("READY") }
                    return@launch
                }

                if (intent.contains("PENDING")) {
                    updateDocumentStatusSilently("PENDING")
                }

                // Fluxo Normal (RAG + Resposta Técnica)
                val relevantChunks = knowledgeManager.findRelevantChunks(text)
                val ragContext = if (relevantChunks.isNotEmpty()) {
                    "\n[TECHNICAL REFERENCE DATA]:\n" + 
                    relevantChunks.joinToString("\n\n") { "Source: ${it.source}\nContent: ${it.text}" }
                } else ""

                var docContextString = ""
                currentDocumentId?.let { docId ->
                    val doc = documentManager.getDocuments().find { it.id == docId }
                    if (doc != null && doc.context.isNotBlank()) {
                        docContextString = "\n[CURRENT FILE CONTENT]:\n" + doc.context
                    }
                }

                val conversationHistory = _messages.value.takeLast(6).joinToString("\n") { 
                    (if (it.isUser) "User: " else "Gemma: ") + it.text 
                }

                val finalPrompt = """
                    ${promptManager.getChatPrompt()}
                    $ragContext
                    $docContextString
                    
                    Conversation History:
                    $conversationHistory
                    
                    User: $text
                    Gemma:
                """.trimIndent()

                processGemmaResponse(finalPrompt, relevantChunks.map { it.source }.distinct())

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = ChatState.Error(e.localizedMessage ?: "Erro")
                }
            }
        }
    }

    private suspend fun processGemmaResponse(prompt: String, sources: List<String>) {
        var firstToken = true
        var fullResponse = ""
        inferenceManager.generateResponse(prompt).collect { token ->
            fullResponse += token
            val tagRegex = Regex("""\s*\[\s*SET_STATUS\s*:\s*(READY|PENDING|SYNCED)\s*\]\s*""", RegexOption.IGNORE_CASE)
            val cleanText = fullResponse.replace(tagRegex, "").trim()

            withContext(Dispatchers.Main) {
                val updatedMessages = _messages.value.toMutableList()
                if (cleanText.isNotBlank()) {
                    if (firstToken) {
                        updatedMessages.add(ChatMessage(cleanText, false, sources = sources))
                        firstToken = false
                    } else {
                        if (updatedMessages.isNotEmpty()) {
                            val lastMsg = updatedMessages.last()
                            if (!lastMsg.isUser) {
                                updatedMessages[updatedMessages.size - 1] = lastMsg.copy(text = cleanText, sources = sources)
                            } else {
                                updatedMessages.add(ChatMessage(cleanText, false, sources = sources))
                            }
                        }
                    }
                    _messages.value = updatedMessages
                    updateCurrentDocumentMessages()
                }
            }
        }
        
        // Atualiza contexto do documento no final
        currentDocumentId?.let { docId ->
            val doc = documentManager.getDocuments().find { it.id == docId }
            if (doc != null) {
                val updatedContext = doc.context + "\n[Update]: " + fullResponse
                documentManager.saveDocument(doc.copy(context = updatedContext, messages = _messages.value))
            }
        }
        withContext(Dispatchers.Main) { _state.value = ChatState.Idle }
    }

    fun onDocumentScanned(extractedText: String, imagePath: String? = null) {
        android.util.Log.d("ChatViewModel", "onDocumentScanned called. Text: '${extractedText.take(20)}...', Image: $imagePath")
        
        val docId: String
        val isExistingDoc = currentDocumentId != null
        
        if (isExistingDoc) {
            docId = currentDocumentId!!
            val doc = documentManager.getDocuments().find { it.id == docId }
            if (doc != null) {
                val newPaths = doc.imagePaths.toMutableList()
                imagePath?.let { newPaths.add(it) }
                val newText = if (doc.extractedText.isBlank()) extractedText else doc.extractedText + "\n" + extractedText
                documentManager.saveDocument(doc.copy(imagePaths = newPaths, extractedText = newText))
            }
        } else {
            docId = "doc_${System.currentTimeMillis()}"
            val newDoc = DocumentState(
                id = docId, 
                extractedText = extractedText, 
                imagePaths = if (imagePath != null) listOf(imagePath) else emptyList()
            )
            documentManager.saveDocument(newDoc)
            currentDocumentId = docId
        }
        
        refreshDocuments()

        val statusText = if (extractedText.isNotBlank()) "Metadata extracted" else "Image captured (no text detected)"
        val prefix = if (isExistingDoc) "➕ New Image in Doc" else "📄 Document Digitized"
        val textToUser = "$prefix (ID: $docId)\n[$statusText and sent to Gemma]"
        
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(textToUser, true, imagePath = imagePath, documentId = docId))
        _messages.value = currentMessages
        updateCurrentDocumentMessages()

        _state.value = ChatState.Generating

        val systemPrompt = promptManager.getOcrPrompt()
        val finalPrompt = if (extractedText.isNotBlank()) {
            "$systemPrompt\n\n[RAW OCR TEXT]:\n$extractedText"
        } else {
            "$systemPrompt\n\n[ALERT: OCR did not detect text in this image]"
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                android.util.Log.d("ChatViewModel", "Starting AI response for document")
                var firstToken = true
                var fullResponse = ""
                inferenceManager.generateResponse(finalPrompt).collect { token ->
                    fullResponse += token
                    val updatedMessages = _messages.value.toMutableList()
                    if (firstToken) {
                        updatedMessages.add(ChatMessage(token, false))
                        firstToken = false
                    } else {
                        if (updatedMessages.isNotEmpty()) {
                            val lastMsg = updatedMessages.last()
                            if (!lastMsg.isUser) {
                                updatedMessages[updatedMessages.size - 1] = lastMsg.copy(text = lastMsg.text + token)
                            } else {
                                updatedMessages.add(ChatMessage(token, false))
                            }
                        }
                    }
                    _messages.value = updatedMessages
                    updateCurrentDocumentMessages()
                }

                val doc = documentManager.getDocuments().find { it.id == docId }
                if (doc != null) {
                    val newContext = if (doc.context.isBlank()) "Gemma: $fullResponse" else doc.context + "\n[Update]: $fullResponse"
                    documentManager.saveDocument(doc.copy(context = newContext, messages = _messages.value))
                    refreshDocuments()
                }

                saveRecentMemory()
                _state.value = ChatState.Idle
                android.util.Log.d("ChatViewModel", "AI response finished for document")
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error in document AI response", e)
                _state.value = ChatState.Error(e.localizedMessage ?: "Erro desconhecido")
            }
        }
    }

    fun useDocument(docId: String) {
        val doc = documentManager.getDocuments().find { it.id == docId } ?: return
        currentDocumentId = docId
        _messages.value = doc.messages
        
        if (_messages.value.isEmpty()) {
            val systemMessage = "Using context from Document ID: $docId"
            val currentMessages = mutableListOf<ChatMessage>()
            currentMessages.add(ChatMessage(systemMessage, true, imagePath = doc.imagePaths.firstOrNull(), documentId = docId))
            _messages.value = currentMessages
            updateCurrentDocumentMessages()
            sendMessage("I want to review document $docId. What is missing to sync it?")
        }
    }

    fun deleteDocument(docId: String) {
        documentManager.deleteDocument(docId)
        refreshDocuments()
        if (currentDocumentId == docId) {
            currentDocumentId = null
        }
    }

    fun updateDocument(doc: DocumentState) {
        documentManager.saveDocument(doc)
        refreshDocuments()
    }

    fun syncData(serverIp: String = "192.168.68.103") {
        viewModelScope.launch(Dispatchers.IO) {
            // Atualizar Base de Conhecimento
            knowledgeManager.syncKnowledge(serverIp)

            val readyDocs = documentManager.getDocuments().filter { it.status == "READY" }
            if (readyDocs.isEmpty()) return@launch
            
            syncManager.setServerIp(serverIp)
            var successCount = 0
            for (doc in readyDocs) {
                if (syncManager.syncDocument(doc)) {
                    documentManager.saveDocument(doc.copy(status = "SYNCED"))
                    successCount++
                }
            }
            withContext(Dispatchers.Main) {
                refreshDocuments()
                // Aqui poderíamos emitir um evento para a UI mostrar um Toast
            }
        }
    }

    private fun handleStatusCommand(status: String) {
        val docId = currentDocumentId ?: return
        val doc = documentManager.getDocuments().find { it.id == docId } ?: return
        
        updateDocument(doc.copy(status = status))
        
        val response = if (status == "READY") {
            "Understood. I have marked this document as **READY** for sync."
        } else {
            "Changed to **PENDING**. What else do we need to adjust?"
        }
        
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(response, false))
        _messages.value = currentMessages
        updateCurrentDocumentMessages()
        _state.value = ChatState.Idle
    }

    private fun updateDocumentStatusSilently(status: String) {
        val docId = currentDocumentId ?: return
        val doc = documentManager.getDocuments().find { it.id == docId } ?: return
        updateDocument(doc.copy(status = status))
    }

    private fun saveRecentMemory() {
        val memoryFile = java.io.File(getApplication<Application>().getExternalFilesDir(null), "recent_memory.json")
        try {
            val jsonArray = org.json.JSONArray()
            for (msg in _messages.value.takeLast(20)) { 
                val item = org.json.JSONObject()
                item.put("text", msg.text)
                item.put("isUser", msg.isUser)
                msg.imagePath?.let { item.put("imagePath", it) }
                msg.documentId?.let { item.put("documentId", it) }
                if (msg.sources.isNotEmpty()) {
                    val sourcesArray = org.json.JSONArray()
                    msg.sources.forEach { sourcesArray.put(it) }
                    item.put("sources", sourcesArray)
                }
                jsonArray.put(item)
            }
            memoryFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            android.util.Log.e("ChatViewModel", "Error saving memory", e)
        }
    }

    private fun loadRecentMemory() {
        val memoryFile = java.io.File(getApplication<Application>().getExternalFilesDir(null), "recent_memory.json")
        if (memoryFile.exists()) {
            try {
                val jsonString = memoryFile.readText()
                val jsonArray = org.json.JSONArray(jsonString)
                val loadedMessages = mutableListOf<ChatMessage>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val sourcesList = mutableListOf<String>()
                    if (item.has("sources")) {
                        val sArray = item.getJSONArray("sources")
                        for (j in 0 until sArray.length()) {
                            sourcesList.add(sArray.getString(j))
                        }
                    }
                    loadedMessages.add(
                        ChatMessage(
                            text = item.getString("text"),
                            isUser = item.getBoolean("isUser"),
                            imagePath = if (item.has("imagePath")) item.getString("imagePath") else null,
                            documentId = if (item.has("documentId")) item.getString("documentId") else null,
                            sources = sourcesList
                        )
                    )
                }
                _messages.value = loadedMessages
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading memory", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        inferenceManager.close()
    }
}
