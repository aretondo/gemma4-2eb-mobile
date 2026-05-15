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

# Garante que prompts.json existe com prompts melhorados
if not PROMPT_FILE.exists():
    PROMPT_FILE.write_text(json.dumps({
        "chat_system_prompt": "Você é a 'Gemma Scan Assistant'. Ajude o profissional a estruturar relatos clínicos e dados de OCR. Se o usuário fornecer um relato verbal/texto, extraia os pontos principais (estado geral, evolução, conduta). Se houver OCR, valide os dados técnicos. Quando as informações estiverem maduras, sugira o status [SET_STATUS:READY]. Seja técnica e objetiva.",
        "ocr_system_prompt": "Você recebeu dados de OCR. Extraia os campos técnicos. Considere que o usuário pode enviar múltiplas páginas/fotos para o mesmo caso; combine as novas informações com o contexto anterior. Pergunte se falta algo ou se pode marcar como pronto para sincronia."
    }, ensure_ascii=False, indent=2))

# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(
    title="Gemma4Good Backend API",
    version="1.1.0",
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
    """Recebe um DocumentState JSON com imagens em Base64 e persiste."""
    doc_id = doc.id or f"doc_{uuid.uuid4().hex[:8]}"
    doc_dict = doc.model_dump()
    doc_dict["id"] = doc_id
    doc_dict["received_at"] = datetime.utcnow().isoformat()
    
    # Processar array de imagens
    saved_image_paths = []
    for i, b64_str in enumerate(doc.images):
        try:
            # Remove header data:image/jpeg;base64, se existir
            if "," in b64_str:
                b64_str = b64_str.split(",")[1]
            
            img_data = base64.b64decode(b64_str)
            img_filename = f"{doc_id}_{i}.jpg"
            img_path = IMAGES_DIR / img_filename
            img_path.write_bytes(img_data)
            saved_image_paths.append(str(img_path.relative_to(BASE_DIR)))
        except Exception as e:
            print(f"Erro ao processar imagem {i}: {e}")

    # Atualiza o dicionário com os caminhos locais dos arquivos salvos (opcional, para UI)
    doc_dict["saved_images"] = saved_image_paths
    # Remove as strings base64 gigantes antes de salvar o JSON para não explodir o tamanho
    doc_dict.pop("images", None)

    dest = SYNC_DIR / f"{doc_id}.json"
    dest.write_text(json.dumps(doc_dict, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"status": "received", "id": doc_id, "images_count": len(saved_image_paths)}



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
