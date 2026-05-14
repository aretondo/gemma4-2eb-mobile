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
    private val localKnowledgeManager = com.example.gemma4good.data.LocalKnowledgeManager(application)
    private val documentManager = com.example.gemma4good.data.DocumentStateManager(application)

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

        _state.value = ChatState.Generating

        val ragContext = localKnowledgeManager.searchContext(text)
        var docContext = ""

        currentDocumentId?.let { docId ->
            val doc = documentManager.getDocuments().find { it.id == docId }
            if (doc != null && doc.context.isNotBlank()) {
                docContext = "\n\nCONTEXTO DO DOCUMENTO ATUAL:\n" + doc.context
            }
        }

        val systemPrompt = "INSTRUÇÃO DO SISTEMA: Você é a 'Gemma Scan Assistant', uma ferramenta especializada para profissionais de saúde e pesquisadores. Seu papel é auxiliar na organização, digitalização e análise técnica de dados médicos. Seja extremamente técnica, objetiva e direta. Não dê conselhos médicos, não dê lições de moral e não sugira encaminhamentos a pacientes. Foque na extração de dados e síntese técnica das informações fornecidas. Você também pode informar ao usuário que ele pode excluir documentos pendentes na aba de Arquivos se necessário."
        
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
                    val updatedMessages = _messages.value.toMutableList()
                    if (firstToken) {
                        updatedMessages.add(ChatMessage(token, false))
                        firstToken = false
                    } else {
                        val lastMsg = updatedMessages.last()
                        updatedMessages[updatedMessages.size - 1] = lastMsg.copy(text = lastMsg.text + token)
                    }
                    _messages.value = updatedMessages
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
        val docId = "doc_${System.currentTimeMillis()}"
        val newDoc = DocumentState(id = docId, extractedText = extractedText, imagePath = imagePath)
        documentManager.saveDocument(newDoc)
        refreshDocuments()

        currentDocumentId = docId

        val statusText = if (extractedText.isNotBlank()) "Metadados extraídos" else "Imagem capturada (sem texto detectado)"
        val textToUser = "📄 Documento Digitalizado (ID: $docId)\n[$statusText e enviado ao Gemma]"
        
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(textToUser, true, imagePath = imagePath, documentId = docId))
        _messages.value = currentMessages

        _state.value = ChatState.Generating

        val systemPrompt = "INSTRUÇÃO DO SISTEMA: Você é a 'Gemma Scan Assistant'. O profissional enviou um documento médico via OCR. Sua tarefa é: 1. Identificar tecnicamente o tipo de documento. 2. Extrair dados estruturados (valores de exames, nomes de medicamentos, datas). 3. Perguntar se há dados adicionais para completar a ficha. Seja técnica e não emita opiniões clínicas ou recomendações de saúde. Se o texto estiver vazio, informe que a captura falhou."
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
                }

                val doc = documentManager.getDocuments().find { it.id == docId }
                if (doc != null) {
                    val newContext = "Gemma: $fullResponse"
                    documentManager.saveDocument(doc.copy(context = newContext))
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
        
        val systemMessage = "Usando contexto do Documento ID: $docId"
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(ChatMessage(systemMessage, true, imagePath = doc.imagePath, documentId = docId))
        _messages.value = currentMessages

        sendMessage("Quero revisar o documento $docId. O que falta para enviarmos para o sync?")
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
