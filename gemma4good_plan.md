# Plano de Implementação - Gemma 4 Good Hackathon (Android)

## Visão Geral e Estratégia
O objetivo do hackathon é gerar impacto social usando modelos **Gemma 4**. Como a implementação será feita majoritariamente pelo **Gemini Flash**, a arquitetura do projeto precisa ser simples, modular e as tarefas devem ser atômicas. O Flash tem excelente velocidade, mas lida melhor com instruções de contexto menor e mais focado.

## Ideias Sugeridas para o Hackathon
A avaliação foca muito em **Impacto & Visão (40 pts)** e **Video Pitch (30 pts)**. Recomendo focarmos em casos de uso offline ou de baixo consumo de dados (Digital Equity / Resilience):

1.  **EduGemma Offline (Future of Education):**
    *   **O que é:** Um tutor de bolso que roda 100% *on-device*. Direcionado para alunos ou professores rurais em áreas com pouca conectividade.
    *   **Funcionalidade:** O usuário tira uma foto ou digita um conceito complexo e o Gemma explica de forma didática e simplificada.
2.  **Gemma Responder / FirstAid (Global Resilience / Health):**
    *   **O que é:** Assistente offline para voluntários em áreas de desastres naturais (enchentes, apagões).
    *   **Funcionalidade:** Triagem e sugestões de primeiros socorros utilizando RAG (Retrieval-Augmented Generation) com uma base de dados SQLite/Room de manuais de sobrevivência embutida no app.

## Arquitetura Recomendada (Otimizada para o Flash)
*   **UI:** Jetpack Compose (Declarativo, o Flash gera telas de forma muito eficiente e com menos boilerplate).
*   **Arquitetura:** MVVM (Model-View-ViewModel).
*   **Integração LLM:** Google MediaPipe LLM Inference API (para rodar os pesos menores do Gemma 4 diretamente no celular).
*   **Armazenamento Local:** Room Database (se optarmos pelo RAG local).

## Divisão de Tarefas para o Gemini Flash

Abaixo está o plano dividido em _chunks_ pequenos. Ao pedir para o Flash executar, passe **apenas uma fase de cada vez**.

### Fase 1: Setup do Projeto e UI Base
*   [ ] Inicializar um projeto Android básico com Jetpack Compose no diretório atual.
*   [ ] Adicionar dependências essenciais no `build.gradle` (Navigation Compose, Lifecycle, ViewModel, Material 3, Hilt se necessário).
*   [ ] Criar a estrutura de temas (`Color.kt`, `Theme.kt`, `Type.kt`) focando em alta acessibilidade (alto contraste, fontes legíveis).
*   [ ] Criar um esqueleto básico de navegação: Tela Principal (Home) e Tela de Interação (Chat/Tutor).

### Fase 2: Motor de Inferência (Gemma 4)
*   [x] Configurar as dependências do `mediapipe-tasks-genai` no Gradle.
*   [ ] **Conversão do Modelo:** Criar script Python e converter o modelo original (Safetensors) para o formato `.bin` compatível com MediaPipe Mobile.
*   [x] Criar um repositório isolado `GemmaInferenceManager` focado exclusivamente em gerenciar o ciclo de vida do modelo.
*   [ ] Integrar o `GemmaInferenceManager` no ViewModel da tela de Chat, gerenciando os estados da UI (Loading, Generating, Idle, Error).

### Fase 3: Features de Impacto (Diferenciais)
*   [ ] **Acessibilidade:** Integrar o Speech-to-Text (STT) nativo do Android para permitir que usuários façam perguntas por voz.
*   [ ] **System Prompt & Contexto:** Codificar *system prompts* fortes que limitem a atuação do Gemma ao escopo do app.
*   [ ] **(Opcional mas Recomendado) RAG Simples:** Criar uma mecânica para injetar informações locais (ex: leitura de um arquivo JSON estático de primeiros socorros) no prompt antes de enviá-lo ao Gemma.

### Fase 4: Entregáveis do Hackathon
*   [ ] Polimento da interface e geração de APK para testes.
*   [ ] Roteiro do vídeo: O Flash pode ajudar a escrever um roteiro emocionante de 3 minutos focando na história de uso real.
*   [ ] Preparar o Writeup Técnico de 1500 palavras e o README do GitHub para atender aos requisitos da submissão.
