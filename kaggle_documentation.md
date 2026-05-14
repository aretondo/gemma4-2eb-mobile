# Gemma 4 Good: Offline Emergency & Medical Assistant

This project aims to leverage **Gemma 4** (Effective 2B) to provide a robust, offline-first assistant for emergency situations, medical document triage, and survival guidance in off-grid scenarios.

## 🚀 Project Overview

**Gemma 4 Good** is an Android application designed for social impact. It combines on-device Large Language Models (LLM) with Optical Character Recognition (OCR) and a local knowledge base to assist healthcare workers and responders in environments without internet connectivity.

## 🛠 Tech Stack

- **Model**: Gemma 4 Effective 2B (quantized for mobile).
- **Engine**: Google LiteRT (formerly TensorFlow Lite) via `litertlm-android`.
- **OCR**: Google ML Kit Text Recognition V2 (Latin).
- **Framework**: Jetpack Compose (Material 3).
- **Memory**: Persistent JSON-based session memory and document batch tracking.
- **Language**: Kotlin.

## 📈 Key Features & Implementation Steps

### 1. Offline Model Deployment
We implemented a `ModelDownloader` that fetches the Gemma 2B model directly from Hugging Face. Once downloaded, the model runs entirely offline using the LiteRT SDK, ensuring data privacy and reliability in remote areas.

### 2. Local Knowledge Base (RAG-lite)
To improve survival guidance, we integrated a `LocalKnowledgeManager` that acts as a local reference. It matches user queries against a pre-defined JSON knowledge base (e.g., first aid protocols) and injects relevant context into the Gemma prompt.

### 3. Advanced OCR Pipeline (V2)
The app features a sophisticated document scanning system:
- **ML Kit V2 (Latin)**: Upgraded to the latest standalone SDK for higher accuracy.
- **Image Pre-processing**: We implemented an `enhanceContrast` helper that applies grayscale and contrast adjustment using `ColorMatrix` before OCR, significantly improving text extraction from low-light medical receipts or reports.
- **Persistence**: Every scan is saved as a `DocumentState` in a local batch file (`sync_batch.json`).

### 4. Smart Document Triage
When a document is scanned:
- A thumbnail is generated and displayed in the chat.
- Gemma analyzes the extracted text to identify the document type (e.g., prescription, lab result).
- The conversation context is saved per document, allowing the user to "Use it" later to resume a specific triage case.

### 5. Premium Material 3 Interface
- **Modern Navigation**: Fixed `TopAppBar` and a `ModalNavigationDrawer` for seamless switching between Chat and the Files history.
- **Responsive Layout**: A clean, adaptive chat interface that feels alive with auto-scrolling and real-time generation feedback.
- **Files Management**: A dedicada tela de "Arquivos" agora suporta a exclusão de documentos com atualização reativa da lista via `StateFlow`.

## 🧪 Future Improvements
- Full-screen OCR preview with highlighted text areas.
- Support for multiple languages in the knowledge base.
- Integration with external synchronization APIs when internet becomes available.

---

*This project is a demonstration of how edge AI can provide critical assistance in humanitarian and emergency contexts.*
