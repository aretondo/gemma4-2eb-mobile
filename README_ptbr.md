# 🏥 Gemma 4 Good: Mobile Emergency Assistant

**Gemma 4 Good** is an offline medical assistant built for the Gemma Hackathon. It uses a local **Gemma 2B** model on Android to provide triage, document analysis, and decision support in environments without internet access.

## 🌟 Highlights

- **100% Offline**: Local inference using LiteRT and TensorFlow Lite.
- **Expanded Vision**: Support for multiple images per document for more complete analysis.
- **Dynamic RAG**: Offline knowledge base (`KnowledgeManager`) that syncs with the remote backend to guide diagnostics and triage.
- **Persistent Memory**: Chat history is stored per session/document.
- **Flexible Reports**: Supports text-only triage as well as multiple image attachments.
- **Sync Ecosystem**: Python backend with FastAPI + Streamlit for data consolidation and content management.

## 🚧 Current Project Status

- The app is currently running and the model download flow is working.
- The locally downloaded model was tested and produced a response successfully.
- Project Phase 2 is validated: the core model pipeline is active and responding.
- No application code changes have been made beyond Git cleanup and repository updates.

## 🌟 Recent Improvements

1. **📚 Knowledge Base**: New backend panel to manage medical protocols and index knowledge for the app, with smart chunking (800 chars / 100 overlap).
2. **🔄 Smart Sync**: The app now downloads the latest knowledge base automatically when "Sync" is pressed.
3. **💬 Sensitive Offline RAG**: Fast local evidence search that handles short phrases and returns source references.
4. **Persistent Chat Sessions**: Each document keeps its own history and remembers sources after closing and reopening.
5. **File Manager UI**: Material 3 cards with rounded corners, colored status badges, and quick action buttons.
6. **Two-stage Prompt Flow**: Gemma first detects whether the input is a question or command, then responds with concise, direct prompts.
7. **Clean Output Rendering**: Native Markdown parsing in chat plus a regex filter that hides technical instructions like `[SET_STATUS]`.

## 📲 Android Integration (Multi-Image)

The APK sends a JSON payload with a Base64 image array to sync:

```kotlin
val payload = JSONObject().apply {
    put("id", doc.id)
    put("extracted_text", doc.extractedText)
    put("gemma_diagnosis", doc.gemmaResponse)
    put("status", "READY")
    put("images", JSONArray(listOf(base64Image1, base64Image2)))
}
```

- `/app`: Android source code (Kotlin + Jetpack Compose).
- `/streamlit_backend`: Sync backend and admin dashboard (Python).
- `/knowledge_base.json`: Offline knowledge store used by the local RAG.

## 🚀 Getting Started

### 📱 Android (App)

Para rodar o aplicativo Android:

1. **Requisitos**: Android Studio Jellyfish ou superior, dispositivo físico Android com pelo menos 6GB de RAM (recomendado 8GB+ para o modelo de 2.6GB).
2. **Instalação**: 
   - Abra a pasta raiz no Android Studio.
   - Compile e instale no dispositivo.
   - Alternativamente, você pode baixar o APK de debug diretamente do repositório em [`.artifacts/app-debug.apk`](.artifacts/app-debug.apk).
3. **Modelo de IA**: Ao abrir o app pela primeira vez, ele solicitará o download do modelo **Gemma 4 E2B** (~2.6GB) do Hugging Face. Certifique-se de estar em uma conexão Wi-Fi estável.
4. **Permissões**: O app solicitará permissão de Câmera e Microfone para análise de documentos e comandos de voz.

### 🖥️ Backend (Sincronização e Admin)

O backend gerencia a base de conhecimento e recebe os dados sincronizados do celular.

1. **Requisitos**: Python 3.10+.
2. **Configuração**:
   - Vá para a pasta `/streamlit_backend`.
   - Crie um ambiente virtual: `python -m venv .venv`.
   - Ative o ambiente: `.venv\Scripts\activate` (Windows) ou `source .venv/bin/activate` (Mac/Linux).
   - Instale as dependências: `pip install -r requirements.txt`.
3. **Execução**:
   - Execute `run_api.bat` para iniciar a API FastAPI (porta 8000).
   - Execute `run_ui.bat` para iniciar o Dashboard Streamlit (porta 8501).
4. **Configuração no App**: No aplicativo Android, vá em Configurações e aponte o IP do servidor para o IP da sua máquina na rede local para habilitar o "Sync".

---
*Turning edge AI into a vital healthcare and disaster response assistant.*
