# Walkthrough Final: Gemma 4 Good – O Escrivão Digital

Este documento resume a arquitetura técnica e as funcionalidades implementadas no projeto **Gemma 4 Good**, uma solução de IA local para assistência médica em áreas remotas.

## 🌟 Visão Geral
O Gemma 4 Good transforma um dispositivo móvel em uma estação de trabalho inteligente autônoma. Ele combina processamento de linguagem natural (LLM), visão computacional (OCR) e recuperação de documentos (RAG) para auxiliar profissionais de saúde onde a internet não chega.

## 🏗️ Arquitetura Técnica

### 1. Cérebro Local (Gemma 2B)
- **Motor:** Google LiteRT (anteriormente TensorFlow Lite).
- **Modelo:** Gemma 2B Effective (aprox. 2.6GB), otimizado para execução em CPU/GPU móvel.
- **Diferencial:** Processamento 100% offline, garantindo privacidade de dados sensíveis e disponibilidade total.

### 2. RAG Local (Retrieval-Augmented Generation)
- **Mecanismo:** `KnowledgeManager`.
- **Fluxo:**
    1. Manuais técnicos (TXT/MD) são processados no backend Streamlit em *chunks* de 800 caracteres com overlap de 100.
    2. O app sincroniza e armazena a base localmente.
    3. Cada pergunta do usuário dispara uma busca por palavras-chave que injeta o contexto técnico relevante no prompt da IA.
- **Resultado:** Respostas fundamentadas em protocolos oficiais com citação de fontes.

### 3. Arquitetura de Dois Estágios (Intent Parsing)
Para evitar que o modelo se perca em contextos longos, implementamos um fluxo de inferência híbrido:
- **Estágio 1 (Classificação):** Uma micro-inferência identifica se o usuário quer executar um comando (ex: "marcar como pronto") ou fazer uma consulta clínica.
- **Estágio 2 (Execução):** O sistema executa ações de sistema instantâneas ou prossegue para a resposta técnica detalhada.

### 4. Visão Computacional e Multi-Imagem
- **OCR:** Integração com Google ML Kit para extração de dados de receitas e laudos.
- **Multi-Páginas:** Suporte para anexar múltiplas fotos a um único prontuário (JSON).
- **Codificação:** Conversão automática de imagens capturadas para Base64 para sincronização estruturada.

## 📱 Funcionalidades da Interface (Material 3)
- **Meus Arquivos:** Painel de gestão de prontuários com badges de status (`PENDING`, `READY`, `SYNCED`).
- **Chat Fluido:** Suporte a Markdown (negrito/listas) e histórico persistente por documento.
- **Sincronização Híbrida:** Botão de "Sync" que atualiza a base de conhecimento, prompts do sistema e faz o upload dos documentos processados para o servidor FastAPI.

## 🛠️ Stack Tecnológica
- **Mobile:** Kotlin, Jetpack Compose, LiteRT, ML Kit.
- **Backend:** Python, FastAPI, Streamlit, OkHttp.
- **Data:** JSON Local Persistence, Base64 Image Encoding.

---
**Gemma 4 Good:** Inteligência resiliente para quem salva vidas no limite da conectividade.
