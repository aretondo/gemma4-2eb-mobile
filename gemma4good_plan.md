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
*   [x] Configurar as dependências do `mediapipe-tasks-genai` no Gradle. (Substituído por LiteRT-LM).
*   [x] **Conversão do Modelo:** Integrado via LiteRT com formato `.litertlm` direto do HuggingFace (sem necessidade de script Python).
*   [x] Criar um repositório isolado `GemmaInferenceManager` focado exclusivamente em gerenciar o ciclo de vida do modelo.
*   [x] Integrar o `GemmaInferenceManager` no ViewModel da tela de Chat, gerenciando os estados da UI (Loading, Generating, Idle, Error).

### Fase 3: Features de Impacto (Diferenciais)
*   [x] **Acessibilidade:** Integrar o Speech-to-Text (STT) nativo do Android para permitir que usuários façam perguntas por voz. (Microfone dinâmico adicionado na UI).
*   [x] **System Prompt & Contexto:** Codificar *system prompts* fortes que limitem a atuação do Gemma ao escopo do app. (Prompt do 'Gemma Responder' implementado no ChatViewModel).
*   [x] **(Opcional mas Recomendado) RAG Simples:** Criar uma mecânica para injetar informações locais (ex: leitura de um arquivo JSON estático de primeiros socorros) no prompt antes de enviá-lo ao Gemma. (Implementado LocalKnowledgeManager com knowledge_base.json).

### Fase 4: Ingestão de Documentos (OCR) e Gestão de Estado
*   [x] **Integração OCR:** Adicionar ML Kit Text Recognition. Implementar botão na UI para abrir a câmera, tirar foto de um documento médico e extrair o texto.
*   [x] **UI de Metadados:** Exibir o documento no chat como um bloco colapsável (ex: `File[Metadados]`), garantindo que o texto extraído seja enviado ao Gemma como contexto.
*   [x] **Processamento Padrão (Batch JSON):** Criar uma estrutura de dados (JSON) para acompanhar o status de cada documento ingerido (ex: `pendente`, `aguardando_complemento`, `pronto_sincronizacao`). O Gemma usará esse contexto para guiar a triagem ("é um laudo X, quer complementar?").
*   [x] **Memória Recente:** Implementar um mecanismo para resumir e salvar as interações recentes, permitindo retomada fluida do trabalho e manutenção de longo contexto.

### Fase 5: Sync Backend & Refinamentos de UI
*   [x] **Backend Streamlit/FastAPI:** Criada pasta `streamlit_backend` com servidor para receber documentos e gerenciar prompts remotamente.
*   [x] **Edição de Dados (See It):** Tela de edição com correção manual de OCR, metadados da IA e toggle de status (PENDING ↔ READY via FilterChip).
*   [x] **Gemma Tool Use (Simulado):** O Gemma emite tags `[SET_STATUS:READY]` / `[SET_STATUS:PENDING]` que o app intercepta silenciosamente para atualizar o banco local.
*   [x] **Copiar Texto:** Toque longo em mensagens do chat para copiar conteúdo.
*   [x] **Atalhos Reativos:** Botão "Visualizar Metadados" no balão de resposta do Gemma.
*   [x] **Prompts Atualizados:** Instrução da tag `[SET_STATUS]` injetada nos prompts base. O backend tem botão de injeção automática.
*   [x] **Filtro por Tipo no Backend:** Documentos classificados como Laudo, Receita ou Exame com filtro lateral.

### Fase 6: Entregáveis do Hackathon (Próximos Passos)
*   [ ] **Polimento Final:** Pequenos ajustes visuais.
*   [ ] **Video Pitch:** Gravar demonstração do fluxo offline -> sync local.
*   [ ] **Submissão:** Preparar repositório e documentação Kaggle.

