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
            promptManager.fetchPrompts("192.168.68.102")
        }
    }

    fun startNewChat() {
        currentDocumentId = null
        _messages.value = emptyList()
        _state.value = ChatState.Idle
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

        // Se não houver documento vinculado, cria um agora para salvar o relato manual
        if (currentDocumentId == null) {
            val newDocId = "doc_${System.currentTimeMillis()}"
            val newDoc = DocumentState(
                id = newDocId,
                extractedText = "", // Texto OCR vazio pois é um relato manual
                context = "Relato inicial: $text"
            )
            documentManager.saveDocument(newDoc)
            currentDocumentId = newDocId
            refreshDocuments()
        }

        updateCurrentDocumentMessages()
        _state.value = ChatState.Generating

        val relevantChunks = knowledgeManager.findRelevantChunks(text)
        val ragContext = if (relevantChunks.isNotEmpty()) {
            "CONTEXTO DA BASE DE CONHECIMENTO:\n" + relevantChunks.joinToString("\n\n") { "Fonte: ${it.source}\nConteúdo: ${it.text}" }
        } else ""
        var docContext = ""

        currentDocumentId?.let { docId ->
            val doc = documentManager.getDocuments().find { it.id == docId }
            if (doc != null && doc.context.isNotBlank()) {
                docContext = "\n\nCONTEXTO DO DOCUMENTO ATUAL:\n" + doc.context
            }
        }

        val systemPrompt = promptManager.getChatPrompt() + "\n\nIMPORTANTE: Se o usuário pedir para marcar o documento como pronto ou pendente, você DEVE responder com uma confirmação amigável E incluir a tag correspondente (ex: [SET_STATUS:READY]). NUNCA responda apenas com a tag."
        
        val finalPrompt = if (ragContext.isNotEmpty() || docContext.isNotEmpty()) {
            "$systemPrompt\n\n[CONTEXTO RELEVANTE]:\n$ragContext$docContext\n\n[DADOS/PERGUNTA DO PROFISSIONAL]: $text"
        } else {
            "$systemPrompt\n\n[DADOS/PERGUNTA DO PROFISSIONAL]: $text"
        }

        viewModelScope.launch {
            try {
                var firstToken = true
                var fullResponse = ""
                inferenceManager.generateResponse(finalPrompt).collect { token ->
                    fullResponse += token
                    
                    // Remove tags do texto exibido ao usuário
                    val cleanText = fullResponse
                        .replace("[SET_STATUS:READY]", "")
                        .replace("[SET_STATUS:PENDING]", "")
                        .trim()

                    val updatedMessages = _messages.value.toMutableList()
                    if (cleanText.isNotBlank()) {
                        if (firstToken) {
                            updatedMessages.add(ChatMessage(cleanText, false))
                            firstToken = false
                        } else {
                            if (updatedMessages.isNotEmpty()) {
                                val lastMsg = updatedMessages.last()
                                if (!lastMsg.isUser) {
                                    updatedMessages[updatedMessages.size - 1] = lastMsg.copy(text = cleanText)
                                } else {
                                    updatedMessages.add(ChatMessage(cleanText, false))
                                }
                            }
                        }
                        _messages.value = updatedMessages
                        updateCurrentDocumentMessages()
                    }
                }

                // Processar tags de comando na resposta final
                android.util.Log.d("ChatViewModel", "Processing final response tags. FullResponse length: ${fullResponse.length}")
                if (fullResponse.contains("[SET_STATUS:READY]")) {
                    android.util.Log.d("ChatViewModel", "Tag [SET_STATUS:READY] detected")
                    currentDocumentId?.let { docId ->
                        documentManager.getDocuments().find { it.id == docId }?.let { doc ->
                            updateDocument(doc.copy(status = "READY"))
                            android.util.Log.d("ChatViewModel", "Document $docId status updated to READY")
                        } ?: android.util.Log.w("ChatViewModel", "Document $docId not found for status update")
                    } ?: android.util.Log.w("ChatViewModel", "currentDocumentId is null")
                } else if (fullResponse.contains("[SET_STATUS:PENDING]")) {
                    android.util.Log.d("ChatViewModel", "Tag [SET_STATUS:PENDING] detected")
                    currentDocumentId?.let { docId ->
                        documentManager.getDocuments().find { it.id == docId }?.let { doc ->
                            updateDocument(doc.copy(status = "PENDING"))
                            android.util.Log.d("ChatViewModel", "Document $docId status updated to PENDING")
                        } ?: android.util.Log.w("ChatViewModel", "Document $docId not found for status update")
                    } ?: android.util.Log.w("ChatViewModel", "currentDocumentId is null")
                }

                currentDocumentId?.let { docId ->
                    val docs = documentManager.getDocuments()
                    val doc = docs.find { it.id == docId }
                    if (doc != null) {
                        val newContext = doc.context + "\nUser: $text\nGemma: $fullResponse"
                        documentManager.saveDocument(doc.copy(context = newContext))
                    }
                }

                saveRecentMemory()
                _state.value = ChatState.Idle
            } catch (e: Exception) {
                _state.value = ChatState.Error(e.localizedMessage ?: "Erro desconhecido")
            }
        }
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

        val statusText = if (extractedText.isNotBlank()) "Metadados extraídos" else "Imagem capturada (sem texto detectado)"
        val prefix = if (isExistingDoc) "➕ Nova Imagem no Doc" else "📄 Documento Digitalizado"
        val textToUser = "$prefix (ID: $docId)\n[$statusText e enviado ao Gemma]"
        
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(textToUser, true, imagePath = imagePath, documentId = docId))
        _messages.value = currentMessages
        updateCurrentDocumentMessages()

        _state.value = ChatState.Generating

        val systemPrompt = promptManager.getOcrPrompt()
        val finalPrompt = if (extractedText.isNotBlank()) {
            "$systemPrompt\n\n[TEXTO BRUTO DO OCR]:\n$extractedText"
        } else {
            "$systemPrompt\n\n[ALERTA: O OCR não detectou texto nesta imagem]"
        }

        viewModelScope.launch {
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
                    val newContext = if (doc.context.isBlank()) "Gemma: $fullResponse" else doc.context + "\nGemma: $fullResponse"
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
            val systemMessage = "Usando contexto do Documento ID: $docId"
            val currentMessages = mutableListOf<ChatMessage>()
            currentMessages.add(ChatMessage(systemMessage, true, imagePath = doc.imagePaths.firstOrNull(), documentId = docId))
            _messages.value = currentMessages
            updateCurrentDocumentMessages()
            sendMessage("Quero revisar o documento $docId. O que falta para enviarmos para o sync?")
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

    fun syncData(serverIp: String = "192.168.68.102") {
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
                    loadedMessages.add(
                        ChatMessage(
                            text = item.getString("text"),
                            isUser = item.getBoolean("isUser"),
                            imagePath = if (item.has("imagePath")) item.getString("imagePath") else null,
                            documentId = if (item.has("documentId")) item.getString("documentId") else null
                        )
                    )
                }
                _messages.value = loadedMessages
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Error loading memory", e)
            }
        }
    }
}
