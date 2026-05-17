"""
Gemma4Good – FastAPI REST Backend
Roda em http://0.0.0.0:8000 e expõe os endpoints que o app Android consome.
"""

from __future__ import annotations

import json
import os
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

import base64

# ── Caminhos ──────────────────────────────────────────────────────────────────
BASE_DIR   = Path(__file__).parent
SYNC_DIR   = BASE_DIR / "synced_docs"
IMAGES_DIR = BASE_DIR / "received_images"
PROMPT_FILE = BASE_DIR / "prompts.json"
KNOWLEDGE_FILE = BASE_DIR / "knowledge.json"

SYNC_DIR.mkdir(exist_ok=True)
IMAGES_DIR.mkdir(exist_ok=True)

# Ensure prompts.json exists with high-performance English prompts (Final Version)
def ensure_default_prompts():
    default_data = {
        "chat_system_prompt": "You are 'Gemma Scan Assistant', an AI technical specialist. Your mission is to answer technical questions using the provided reference data and maintain an updated summary in the context field. Be objective, technical, and direct. Do not explain your reasoning.",
        "ocr_system_prompt": "Analyze the OCR text and extract technical parameters, values and instructions. Be brief and structured."
    }
    # Force creation/overwrite to ensure the final English version
    PROMPT_FILE.write_text(json.dumps(default_data, ensure_ascii=False, indent=2), encoding="utf-8")

ensure_default_prompts()

# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Gemma4Good Backend API",
    version="1.1.0",
    description="Sync sink and prompt manager for the Gemma4Good app."
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Modelos Pydantic ──────────────────────────────────────────────────────────
class DocumentState(BaseModel):
    id: str | None = None
    extracted_text: str = ""       # Nome do campo vindo do Android
    gemma_diagnosis: str = ""      # Nome do campo vindo do Android
    status: str = "PENDING"        # Nome do campo vindo do Android
    context: str = ""
    images: list[str] = []         # Array de imagens em Base64
    timestamp: str | None = None
    extra: dict[str, Any] = {}

class PromptsPayload(BaseModel):
    chat_system_prompt: str
    ocr_system_prompt: str

# ── Helpers ───────────────────────────────────────────────────────────────────
def _load_prompts() -> dict:
    return json.loads(PROMPT_FILE.read_text(encoding="utf-8"))

def _save_prompts(data: dict) -> None:
    PROMPT_FILE.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok", "timestamp": datetime.utcnow().isoformat()}


@app.post("/sync", status_code=201)
def receive_document(doc: DocumentState):
    """Receives a DocumentState JSON with Base64 images and persists it."""
    doc_id = doc.id or f"doc_{uuid.uuid4().hex[:8]}"
    doc_dict = doc.model_dump()
    doc_dict["id"] = doc_id
    doc_dict["received_at"] = datetime.utcnow().isoformat()
    
    # Process array of images
    saved_image_paths = []
    for i, b64_str in enumerate(doc.images):
        try:
            # Remove header data:image/jpeg;base64, if exists
            if "," in b64_str:
                b64_str = b64_str.split(",")[1]
            
            img_data = base64.b64decode(b64_str)
            img_filename = f"{doc_id}_{i}.jpg"
            img_path = IMAGES_DIR / img_filename
            img_path.write_bytes(img_data)
            saved_image_paths.append(str(img_path.relative_to(BASE_DIR)))
        except Exception as e:
            print(f"Error processing image {i}: {e}")

    # Update dictionary with local paths (optional, for UI)
    doc_dict["saved_images"] = saved_image_paths
    # Remove giant base64 strings before saving JSON
    doc_dict.pop("images", None)

    dest = SYNC_DIR / f"{doc_id}.json"
    dest.write_text(json.dumps(doc_dict, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"status": "received", "id": doc_id, "images_count": len(saved_image_paths)}



@app.get("/sync")
def list_documents():
    """Lists all synced documents."""
    docs = []
    for f in sorted(SYNC_DIR.glob("*.json")):
        try:
            docs.append(json.loads(f.read_text(encoding="utf-8")))
        except Exception:
            pass
    return docs


@app.get("/prompts")
def get_prompts():
    """Retorna os prompts atuais (o app baixa isso no startup)."""
    return _load_prompts()


@app.get("/knowledge")
def get_knowledge():
    """Retorna a base de conhecimento indexada."""
    if KNOWLEDGE_FILE.exists():
        return json.loads(KNOWLEDGE_FILE.read_text(encoding="utf-8"))
    return {"chunks": []}


@app.post("/prompts")
def update_prompts(payload: PromptsPayload):
    """Atualiza os prompts (chamado pela UI Streamlit)."""
    data = {
        "chat_system_prompt": payload.chat_system_prompt,
        "ocr_system_prompt": payload.ocr_system_prompt,
    }
    _save_prompts(data)
    return {"status": "updated"}
