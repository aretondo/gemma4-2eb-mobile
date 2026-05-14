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

### 3. Advanced OCR Pipeline (V2) & Multi-Image Support
The app features a sophisticated document scanning system:
- **Multi-Image Attachment**: Users can now attach multiple photos to a single case, allowing for comprehensive documentation of medical reports or patient conditions.
- **See It (Structured Editor)**: A dedicated screen where professionals can review and manually correct OCR text and AI-generated analysis.
- **Status Management**: Support for marking documents as `PENDING` or `READY` for sync.

### 4. Multi-Session Chat & State Management
- **Persistent Sessions**: `DocumentState` now stores its own list of messages, enabling per-document conversation history.
- **Context Switching**: Using "Use it" on a document restores its specific chat history, while "New Chat" allows starting fresh clinical reports.
- **Text-Only Reports**: Full support for clinical triage based on manual descriptions even without accompanying images.
- **Gemma Tool Use (Simulated)**: The model can emit `[SET_STATUS:READY]` tags to automate status updates based on natural language confirmation.

### 5. Local Backend & Sync (v1.1)
The backend has been optimized for field consistency and performance:
- **Aligned Data Model**: `DocumentState` now uses standardized field names (`extracted_text`, `gemma_diagnosis`, `status`) to match the Android client perfectly.
- **Base64 Multi-Image Upload**: The `SyncManager` serializes all attached images into a Base64 `JSONArray`. The backend decodes them, saves them as `.jpg` files, and cleans up the JSON to maintain low disk usage.
- **Prompt Manager**: Updated system prompts to guide Gemma in multi-image summarization and purely textual clinical reports.

## 🧪 Future Improvements
- Multi-user authentication for the backend.
- End-to-end encryption for the sync payloads.
- Integration with FHIR standards for medical data export.
- Extend Gemma Tool Use to support structured data extraction commands (e.g., `[EXTRACT:DATE]`, `[EXTRACT:MEDICATION]`).

---

*This project is a demonstration of how edge AI can provide critical assistance in humanitarian and emergency contexts.*

