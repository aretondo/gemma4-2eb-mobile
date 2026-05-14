# Backend Blueprint & Streamlit Guide

This document provides the roadmap for building a local backend using Streamlit and FastAPI (or just Streamlit) to receive data from the Gemma4Good app and manage dynamic prompts.

## 1. Backend Architecture (Local)

The backend will serve two purposes:
1.  **Sync Sink**: Receive JSON payloads from the mobile app (scanned data + LLM analysis).
2.  **Prompt Manager**: Provide a UI to edit the system prompts that the app uses.

### Proposed API Endpoints

| Endpoint | Method | Description |
| :--- | :--- | :--- |
| `/sync` | `POST` | Receives a `DocumentState` JSON object. |
| `/prompts` | `GET` | Returns current system prompts for the app to download. |
| `/prompts` | `POST` | Updates prompts via the Streamlit UI. |

## 2. Streamlit Implementation (Starter Code)

Save this as `app.py` and run with `streamlit run app.py`.

```python
import streamlit as st
import json
import os

# Storage for synced data
SYNC_DIR = "synced_docs"
PROMPT_FILE = "prompts.json"

if not os.path.exists(SYNC_DIR):
    os.makedirs(SYNC_DIR)

if not os.path.exists(PROMPT_FILE):
    with open(PROMPT_FILE, "w") as f:
        json.dump({
            "chat_system_prompt": "Você é a 'Gemma Scan Assistant'...",
            "ocr_system_prompt": "Você é a 'Gemma Scan Assistant'. O profissional enviou um documento..."
        }, f)

st.title("🏥 Gemma4Good Backend")

tab1, tab2 = st.tabs(["📊 Synced Data", "⚙️ Prompt Settings"])

with tab1:
    st.header("Received Documents")
    files = os.listdir(SYNC_DIR)
    if not files:
        st.info("No documents synced yet.")
    for file in files:
        with open(os.path.join(SYNC_DIR, file), "r") as f:
            data = json.load(f)
            with st.expander(f"Document {data.get('id', 'Unknown')}"):
                st.json(data)

with tab2:
    st.header("System Prompts Configuration")
    with open(PROMPT_FILE, "r") as f:
        prompts = json.load(f)

    chat_p = st.text_area("Chat System Prompt", value=prompts["chat_system_prompt"], height=200)
    ocr_p = st.text_area("OCR System Prompt", value=prompts["ocr_system_prompt"], height=200)

    if st.button("Save Prompts"):
        with open(PROMPT_FILE, "w") as f:
            json.dump({"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p}, f)
        st.success("Prompts updated! The app will fetch these on next sync.")

# Note: To receive data from Android, you'd need an endpoint.
# You can use 'st.query_params' for simple GET or a side FastAPI app.
```

## 3. Integration Plan for the App (Next Phase)

1.  **Sync Worker**: Implement a `SyncManager` using `OkHttp` to send documents that are not in "PENDING" status.
2.  **Prompt Fetching**: At app startup, try to fetch `prompts.json` from the local IP of the backend.
3.  **Conflict Resolution**: If the backend is offline, keep data in the local `sync_batch.json` until connection is restored.
