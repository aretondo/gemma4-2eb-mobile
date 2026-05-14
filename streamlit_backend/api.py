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

# ── Caminhos ──────────────────────────────────────────────────────────────────
BASE_DIR   = Path(__file__).parent
SYNC_DIR   = BASE_DIR / "synced_docs"
PROMPT_FILE = BASE_DIR / "prompts.json"

SYNC_DIR.mkdir(exist_ok=True)

# Garante que prompts.json existe
if not PROMPT_FILE.exists():
    PROMPT_FILE.write_text(json.dumps({
        "chat_system_prompt": "",
        "ocr_system_prompt": ""
    }, ensure_ascii=False, indent=2))

# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Gemma4Good Backend API",
    version="1.0.0",
    description="Sync sink e gerenciador de prompts para o app Gemma4Good."
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
    extractedText: str = ""
    imagePath: str | None = None
    context: str = ""
    syncStatus: str = "PENDING"
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
    """Recebe um DocumentState JSON do app e persiste em disco."""
    doc_id = doc.id or f"doc_{uuid.uuid4().hex[:8]}"
    doc_dict = doc.model_dump()
    doc_dict["id"] = doc_id
    doc_dict["received_at"] = datetime.utcnow().isoformat()

    dest = SYNC_DIR / f"{doc_id}.json"
    dest.write_text(json.dumps(doc_dict, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"status": "received", "id": doc_id}


@app.get("/sync")
def list_documents():
    """Lista todos os documentos sincronizados."""
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


@app.post("/prompts")
def update_prompts(payload: PromptsPayload):
    """Atualiza os prompts (chamado pela UI Streamlit)."""
    data = {
        "chat_system_prompt": payload.chat_system_prompt,
        "ocr_system_prompt": payload.ocr_system_prompt,
    }
    _save_prompts(data)
    return {"status": "updated"}
