# 🏥 Gemma 4 Good: Mobile Emergency Assistant

**Gemma 4 Good** é um assistente médico offline desenvolvido para o Hackathon Gemma. Ele utiliza o modelo **Gemma 2B** rodando localmente no Android para fornecer triagem, análise de documentos e suporte a decisões em cenários sem conectividade.

## 🌟 Destaques

- **100% Offline**: Processamento local via LiteRT (TensorFlow Lite).
- **Visão Expandida**: Suporte a múltiplas fotos por documento para análise completa.
- **RAG Dinâmico**: Base de conhecimento offline (`KnowledgeManager`) que sincroniza automaticamente com o servidor para guiar diagnósticos e triagens.
- **Memória Persistente**: Histórico de chat preservado individualmente por sessão/documento.
- **Relatos Flexíveis**: Suporta triagem baseada apenas em texto ou múltiplos anexos visuais.
- **Ecossistema de Sincronização**: Backend em Python (FastAPI + Streamlit) para consolidar dados e gerenciar conhecimento.

## 🌟 Novidades Recentes

1. **📚 Knowledge Base**: Nova aba no backend para gerenciar protocolos médicos e indexar conhecimento para o app.
2. **🔄 Sincronização Inteligente**: O app agora baixa automaticamente a base de conhecimento mais recente ao clicar em "Sync".
3. **💬 RAG Offline**: Busca ultra-rápida de evidências locais que são injetadas no contexto do Gemma para respostas precisas.
4. **Sessões de Chat**: Cada documento mantém seu próprio histórico. Use "Use it" para retomar ou "New Chat" para começar do zero.


## 📲 Integração Android (Multi-Imagens)

Para sincronizar, o APK envia um JSON contendo o array de imagens em Base64:

```kotlin
val payload = JSONObject().apply {
    put("id", doc.id)
    put("extracted_text", doc.extractedText)
    put("gemma_diagnosis", doc.gemmaResponse)
    put("status", "READY")
    put("images", JSONArray(listOf(base64Image1, base64Image2))) 
}
```



- `/app`: Código-fonte da aplicação Android (Kotlin + Jetpack Compose).
- `/streamlit_backend`: Servidor de sincronização e painel administrativo (Python).
- `/knowledge_base.json`: Base de dados técnica para o RAG local.

## 🚀 Como Iniciar

1. **Android**: Abra a pasta raiz no Android Studio e execute no dispositivo.
2. **Backend**: Vá para `/streamlit_backend` e execute `run_api.bat` e `run_ui.bat`.

---
*Transformando o Edge AI em uma ferramenta vital para a saúde pública e resposta a desastres.*
