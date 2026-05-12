package com.example.gemma4good.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gemma4good.data.GemmaInferenceManager
import com.example.gemma4good.data.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _state = MutableStateFlow<ChatState>(ChatState.LoadingModel)
    val state: StateFlow<ChatState> = _state

    private val _messages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val messages: StateFlow<List<Pair<String, Boolean>>> = _messages

    init {
        checkModelState()
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
                    // Adicionando tratamento para quando o ID não é encontrado ou cursor vazio
                    -1 -> {
                         // Se demorar muito a aparecer, pode ter falhado silenciosamente
                    }
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
        currentMessages.add(Pair(text, true)) // User message
        _messages.value = currentMessages

        _state.value = ChatState.Generating

        viewModelScope.launch {
            try {
                var firstToken = true
                inferenceManager.generateResponse(text).collect { token ->
                    val updatedMessages = _messages.value.toMutableList()
                    if (firstToken) {
                        updatedMessages.add(Pair(token, false))
                        firstToken = false
                    } else {
                        val lastMsg = updatedMessages.last()
                        updatedMessages[updatedMessages.size - 1] = Pair(lastMsg.first + token, false)
                    }
                    _messages.value = updatedMessages
                }
                _state.value = ChatState.Idle
            } catch (e: Exception) {
                _state.value = ChatState.Error(e.localizedMessage ?: "Erro desconhecido")
            }
        }
    }
}
