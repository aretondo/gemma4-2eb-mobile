# Local RAG Implementation: GemmaKnowledge

This plan outlines the implementation of a local Knowledge Base (RAG - Retrieval Augmented Generation) for the Gemma4Good project. The goal is to allow users to upload technical documents via the Streamlit UI, sync them to the Android app, and use them as context for the local Gemma model.

## User Review Required

- **Retrieval Strategy:** Since we are running offline on mobile, we will use a simple Keyword/TF-IDF search or a lightweight vector search if available. For the initial phase, a structured JSON "Knowledge Pack" with text chunks will be used.
- **Sync Overhead:** Large knowledge bases might increase sync time. We'll start with text files (.txt, .md).
- **LLM Context Window:** We must ensure retrieved chunks fit within the Gemma 2B context window without displacing conversation history.

## Proposed Changes

---

### Backend & UI (Streamlit/FastAPI)

#### [api.py](file:///C:/repository/gemma4good/streamlit_backend/api.py)
- New endpoint `GET /knowledge` to serve the compiled knowledge base.
- New model `KnowledgeChunk` for structured sync.

#### [app.py](file:///C:/repository/gemma4good/streamlit_backend/app.py)
- New "Knowledge Base" tab.
- File uploader for `.txt` and `.md`.
- Basic chunking logic (e.g., by paragraph or fixed length).
- Persistence of knowledge base in `knowledge.json`.

---

### Android Application

#### [NEW] [KnowledgeManager.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/data/KnowledgeManager.kt)
- Handles downloading `knowledge.json` during sync.
- Logic to search through chunks based on user query.

#### [ChatViewModel.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/ui/ChatViewModel.kt)
- Integrate `KnowledgeManager` into the `sendMessage` flow.
- Inject relevant chunks into the prompt sent to Gemma.

#### [MainActivity.kt](file:///C:/repository/gemma4good/app/src/main/java/com/example/gemma4good/MainActivity.kt)
- Update UI to show sync status of the knowledge base.

## Verification Plan

### Automated Tests
- Test Python chunking logic via a standalone script.
- Unit test for `KnowledgeManager` retrieval logic (mocking `knowledge.json`).

### Manual Verification
1. Upload a file "test.txt" with unique info via UI.
2. Trigger Sync on Android.
3. Ask the app a question related to "test.txt" while offline.
4. Verify Gemma cites the info from the file.
