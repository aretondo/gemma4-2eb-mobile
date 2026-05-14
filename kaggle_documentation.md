# Gemma 4 Good: Offline Emergency & Medical Assistant

This project aims to leverage **Gemma 4** (Effective 2B) to provide a robust, offline-first assistant for emergency situations, medical document triage, and survival guidance in off-grid scenarios.

## 🚀 Project Overview

**Gemma 4 Good** is an Android application designed for social impact. It combines on-device Large Language Models (LLM) with Optical Character Recognition (OCR) and a local knowledge base to assist healthcare workers and responders in environments without internet connectivity.

## 🛠 Tech Stack

- **Model**: Gemma 4 Effective 2B (quantized for mobile).
- **Engine**: Google LiteRT (formerly TensorFlow Lite) via `litertlm-android`.
- **OCR**: Google ML Kit Text Recognition V2 (Latin).
- **Framework**: Jetpack Compose (Material 3).
- **Backend**: Python (FastAPI + Streamlit) for local data synchronization and prompt management.
- **Persistence**: Persistent JSON-based session memory, document batch tracking, and local file storage for scanned images.
- **Language**: Kotlin (Android) and Python (Backend).

## 📈 Key Features & Implementation Steps

### 1. Offline Model Deployment
We implemented a `ModelDownloader` that fetches the Gemma 2B model directly from Hugging Face. Once downloaded, the model runs entirely offline using the LiteRT SDK, ensuring data privacy and reliability in remote areas.

### 2. Local Knowledge Base (RAG-lite)
To improve survival guidance, we integrated a `LocalKnowledgeManager` that acts as a local reference. It matches user queries against a pre-defined JSON knowledge base (e.g., first aid protocols) and injects relevant context into the Gemma prompt.

### 3. Advanced OCR Pipeline (V2) & Editing
The app features a sophisticated document scanning system:
- **ML Kit V2 (Latin)**: Upgraded to the latest standalone SDK for higher accuracy.
- **See It (Structured Editor)**: A dedicated screen where professionals can review and manually correct OCR text and AI-generated analysis.
- **Status Management**: Support for marking documents as `PENDING` or `READY` for sync.

### 4. Smart Document Triage & UI Shortcuts
When a document is scanned:
- Gemma analyzes and classifies the document type (Laudo, Receita, Exame).
- **"Visualizar Metadados" Shortcut**: Tap the button directly in the chat bubble to jump to the structured data editor.
- **Interactive Chat**: Long-press any message to copy it, enabling quick data reuse.

### 4.1 Gemma Tool Use (Simulated)
Implemented a lightweight command protocol inside the LLM response stream:
- The model can emit invisible control tags like `[SET_STATUS:READY]` or `[SET_STATUS:PENDING]`.
- The app intercepts and strips the tag before rendering, silently updating the local `DocumentState`.
- Natural language triggers: *"pode marcar como pronto"* or *"marcar como pendente"* reliably activate the behavior.
- The Streamlit backend's Prompt Manager includes an **Inject SET_STATUS** button to append the tag instruction to any prompt without manual editing.

### 4.2 "See It" Status Control
- In the Files screen, the **See It** button opens a structured editor for OCR text and AI analysis.
- A `FilterChip` displays the current status and **toggles between PENDING ↔ READY** on tap.
- Synced documents show a green cloud icon (☁️) and the status field becomes read-only.

### 5. Local Backend & Sync (Streamlit)
To bridge the gap between offline collection and centralized data:
- **FastAPI Sync Sink**: A local server that receives synced batches from the app.
- **Prompt Manager**: A Streamlit dashboard to edit the system prompts (Chat and OCR) dynamically without rebuilding the Android app.
- **Visual Analytics**: Real-time view of all received documents and their structured data.

## 🧪 Future Improvements
- Multi-user authentication for the backend.
- End-to-end encryption for the sync payloads.
- Integration with FHIR standards for medical data export.
- Extend Gemma Tool Use to support structured data extraction commands (e.g., `[EXTRACT:DATE]`, `[EXTRACT:MEDICATION]`).


---

*This project is a demonstration of how edge AI can provide critical assistance in humanitarian and emergency contexts.*
