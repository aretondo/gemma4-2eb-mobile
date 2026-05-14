# 🏥 Gemma 4 Good: Mobile Emergency Assistant

**Gemma 4 Good** é um assistente médico offline desenvolvido para o Hackathon Gemma. Ele utiliza o modelo **Gemma 2B** rodando localmente no Android para fornecer triagem, análise de documentos e suporte a decisões em cenários sem conectividade.

## 🌟 Destaques

- **100% Offline**: Processamento local via LiteRT (TensorFlow Lite).
- **OCR Inteligente**: Digitalização de laudos e receitas com Google ML Kit.
- **RAG Local**: Base de conhecimento embutida para protocolos de emergência.
- **Ecossistema de Sincronização**: Backend em Python para consolidar dados coletados em campo.

## 🛠 Novidades Recentes

1. **Cópia de Texto**: Toque longo em qualquer mensagem do chat para copiar o conteúdo.
2. **Tela "See It"**: Edição estruturada de documentos para corrigir OCR ou análises da IA antes da sincronização.
3. **Atalhos no Chat**: Botão "Visualizar Metadados" integrado às respostas do Gemma para acesso rápido à edição.
4. **Local Sync Backend**: Servidor FastAPI + Streamlit para gerenciar documentos e prompts remotamente.
5. **Sync Multi-Imagens**: O backend agora suporta o recebimento de múltiplas imagens por documento via Base64.

## 📲 Integração Android (Multi-Imagens)

Para enviar as imagens do APK para o backend, use o campo `images` (array de strings Base64) no JSON de sincronização:

```kotlin
// Exemplo de payload JSON no Android
val payload = JSONObject().apply {
    put("id", doc.id)
    put("extractedText", doc.extractedText)
    put("syncStatus", "READY")
    // Array de imagens convertidas para Base64
    put("images", JSONArray(listOf(base64Image1, base64Image2))) 
}

// Chamada via OkHttp
val request = Request.Builder()
    .url("http://<IP_DO_PC>:8000/sync")
    .post(payload.toString().toRequestBody("application/json".toMediaType()))
    .build()
```


- `/app`: Código-fonte da aplicação Android (Kotlin + Jetpack Compose).
- `/streamlit_backend`: Servidor de sincronização e painel administrativo (Python).
- `/knowledge_base.json`: Base de dados técnica para o RAG local.

## 🚀 Como Iniciar

1. **Android**: Abra a pasta raiz no Android Studio e execute no dispositivo.
2. **Backend**: Vá para `/streamlit_backend` e execute `run_api.bat` e `run_ui.bat`.

---
*Transformando o Edge AI em uma ferramenta vital para a saúde pública e resposta a desastres.*
