"""
Gemma4Good – Streamlit UI
Gerencia documentos sincronizados e configuração de prompts do sistema.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import threading
from pathlib import Path
from datetime import datetime

import streamlit as st
import httpx

# ── Configuração da página ────────────────────────────────────────────────────
st.set_page_config(
    page_title="Gemma4Good Backend",
    page_icon="🏥",
    layout="wide",
    initial_sidebar_state="expanded",
)

# ── CSS personalizado ─────────────────────────────────────────────────────────
st.markdown("""
<style>
    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap');

    html, body, [class*="css"] { font-family: 'Inter', sans-serif; }

    .main { background: #0f1117; }

    /* Sidebar */
    [data-testid="stSidebar"] {
        background: linear-gradient(180deg, #1a1f2e 0%, #0f1117 100%);
        border-right: 1px solid #2a2d3e;
    }

    /* Métricas */
    [data-testid="metric-container"] {
        background: #1a1f2e;
        border: 1px solid #2a2d3e;
        border-radius: 12px;
        padding: 16px;
    }

    /* Botão primário */
    .stButton > button {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        color: white;
        border: none;
        border-radius: 8px;
        padding: 8px 24px;
        font-weight: 600;
        transition: all 0.2s ease;
    }
    .stButton > button:hover {
        transform: translateY(-1px);
        box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
    }

    /* Cards de documento */
    .doc-card {
        background: #1a1f2e;
        border: 1px solid #2a2d3e;
        border-radius: 12px;
        padding: 16px;
        margin-bottom: 12px;
        transition: border-color 0.2s ease;
    }
    .doc-card:hover { border-color: #667eea; }

    /* Badge de status */
    .badge-pending  { background:#f59e0b22; color:#f59e0b; border:1px solid #f59e0b44; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }
    .badge-synced   { background:#10b98122; color:#10b981; border:1px solid #10b98144; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }
    .badge-error    { background:#ef444422; color:#ef4444; border:1px solid #ef444444; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }

    /* Status da API */
    .api-online  { color:#10b981; font-weight:600; }
    .api-offline { color:#ef4444; font-weight:600; }

    /* Separador */
    hr { border-color: #2a2d3e; }

    /* Text area */
    textarea { font-family: 'Inter', monospace !important; font-size: 0.875rem !important; }
</style>
""", unsafe_allow_html=True)

# ── Constantes ────────────────────────────────────────────────────────────────
BASE_DIR    = Path(__file__).parent
SYNC_DIR    = BASE_DIR / "synced_docs"
PROMPT_FILE = BASE_DIR / "prompts.json"
API_URL     = "http://127.0.0.1:8000"

SYNC_DIR.mkdir(exist_ok=True)

# ── Helpers ───────────────────────────────────────────────────────────────────
def load_prompts() -> dict:
    if PROMPT_FILE.exists():
        return json.loads(PROMPT_FILE.read_text(encoding="utf-8"))
    return {"chat_system_prompt": "", "ocr_system_prompt": ""}

def save_prompts(data: dict) -> None:
    PROMPT_FILE.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

def load_docs() -> list[dict]:
    docs = []
    for f in sorted(SYNC_DIR.glob("*.json"), reverse=True):
        try:
            docs.append(json.loads(f.read_text(encoding="utf-8")))
        except Exception:
            pass
    return docs

def api_status() -> bool:
    try:
        r = httpx.get(f"{API_URL}/health", timeout=2)
        return r.status_code == 200
    except Exception:
        return False

def badge(status: str) -> str:
    s = status.upper()
    if s == "PENDING":
        return f'<span class="badge-pending">⏳ {s}</span>'
    elif s in ("SYNCED", "DONE"):
        return f'<span class="badge-synced">✅ {s}</span>'
    return f'<span class="badge-error">❌ {s}</span>'

# ── Sidebar ───────────────────────────────────────────────────────────────────
with st.sidebar:
    st.markdown("## 🏥 Gemma4Good")
    st.markdown("**Backend & Prompt Manager**")
    st.markdown("---")

    online = api_status()
    status_class = "api-online" if online else "api-offline"
    status_text  = "🟢 API Online" if online else "🔴 API Offline"
    st.markdown(f'<span class="{status_class}">{status_text}</span>', unsafe_allow_html=True)
    st.caption(f"Endpoint: `{API_URL}`")

    st.markdown("---")

    docs = load_docs()
    total    = len(docs)
    pending  = sum(1 for d in docs if d.get("syncStatus", "").upper() == "PENDING")
    synced   = total - pending

    col1, col2 = st.columns(2)
    col1.metric("Total Docs", total)
    col2.metric("Pendentes",  pending)

    st.markdown("---")
    st.caption("Gemma4Good Backend v1.0")
    st.caption(f"🕒 {datetime.now().strftime('%H:%M:%S')}")

# ── Conteúdo principal ────────────────────────────────────────────────────────
st.markdown("# 🏥 Gemma4Good Backend")
st.markdown("Painel central para gerenciar documentos sincronizados e configurar prompts do sistema.")
st.markdown("---")

tab1, tab2, tab3 = st.tabs(["📊 Documentos Sincronizados", "⚙️ Configuração de Prompts", "🔌 API & Integração"])

# ══════════════════════════════════════════════════════════════════════════════
# TAB 1 – Documentos
# ══════════════════════════════════════════════════════════════════════════════
with tab1:
    col_h1, col_h2 = st.columns([3, 1])
    with col_h1:
        st.subheader("Documentos Recebidos")
    with col_h2:
        if st.button("🔄 Atualizar", key="refresh_docs"):
            st.rerun()

    docs = load_docs()

    if not docs:
        st.info("📭 Nenhum documento sincronizado ainda. Aguardando dados do app Android.", icon="ℹ️")
    else:
        # Filtro de status
        status_filter = st.selectbox(
            "Filtrar por status",
            ["Todos", "PENDING", "SYNCED"],
            key="status_filter"
        )
        filtered = docs if status_filter == "Todos" else [
            d for d in docs if d.get("syncStatus", "").upper() == status_filter
        ]

        st.caption(f"Exibindo **{len(filtered)}** de **{len(docs)}** documentos")
        st.markdown("---")

        for doc in filtered:
            doc_id   = doc.get("id", "Unknown")
            status   = doc.get("syncStatus", "PENDING")
            recv_at  = doc.get("received_at", "—")
            txt_prev = (doc.get("extractedText", "") or "")[:120]

            with st.expander(f"📄 {doc_id}  —  {badge(status)}", expanded=False):
                cols = st.columns([2, 2, 1])
                cols[0].markdown(f"**ID:** `{doc_id}`")
                cols[1].markdown(f"**Recebido:** {recv_at}")
                cols[2].markdown(badge(status), unsafe_allow_html=True)

                if txt_prev:
                    st.markdown("**Prévia do texto OCR:**")
                    st.code(txt_prev + ("..." if len(doc.get("extractedText", "")) > 120 else ""), language="text")

                if doc.get("context"):
                    st.markdown("**Contexto da conversa:**")
                    st.text_area(
                        label="context",
                        value=doc["context"],
                        height=100,
                        disabled=True,
                        key=f"ctx_{doc_id}",
                        label_visibility="collapsed"
                    )

                with st.expander("🔍 JSON completo"):
                    st.json(doc)

# ══════════════════════════════════════════════════════════════════════════════
# TAB 2 – Prompts
# ══════════════════════════════════════════════════════════════════════════════
with tab2:
    st.subheader("⚙️ Configuração de Prompts do Sistema")
    st.markdown(
        "Estes prompts são baixados pelo app Android no startup. "
        "Edite aqui e salve — na próxima sincronização o app usará os novos valores."
    )
    st.markdown("---")

    prompts = load_prompts()

    col_p1, col_p2 = st.columns(2)

    with col_p1:
        st.markdown("### 💬 Chat System Prompt")
        st.caption("Instrução do sistema para o chat livre com o profissional de saúde.")
        chat_p = st.text_area(
            label="chat_system_prompt",
            value=prompts.get("chat_system_prompt", ""),
            height=320,
            key="chat_prompt_area",
            label_visibility="collapsed",
            help="Prompt injetado antes de cada mensagem do chat."
        )
        st.caption(f"📏 {len(chat_p)} caracteres")

    with col_p2:
        st.markdown("### 📷 OCR System Prompt")
        st.caption("Instrução do sistema para análise de documentos digitalizados via OCR.")
        ocr_p = st.text_area(
            label="ocr_system_prompt",
            value=prompts.get("ocr_system_prompt", ""),
            height=320,
            key="ocr_prompt_area",
            label_visibility="collapsed",
            help="Prompt injetado quando o app envia texto de OCR para análise."
        )
        st.caption(f"📏 {len(ocr_p)} caracteres")

    st.markdown("---")

    col_save, col_reset, _ = st.columns([1, 1, 4])

    with col_save:
        if st.button("💾 Salvar Prompts", type="primary", key="save_prompts"):
            save_prompts({"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p})
            # Tenta atualizar via API também (se estiver rodando)
            try:
                httpx.post(
                    f"{API_URL}/prompts",
                    json={"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p},
                    timeout=2
                )
            except Exception:
                pass
            st.success("✅ Prompts salvos com sucesso! O app buscará os novos valores no próximo sync.")

    with col_reset:
        if st.button("↩️ Resetar", key="reset_prompts"):
            st.rerun()

# ══════════════════════════════════════════════════════════════════════════════
# TAB 3 – API & Integração
# ══════════════════════════════════════════════════════════════════════════════
with tab3:
    st.subheader("🔌 API FastAPI & Integração Android")

    online = api_status()

    if online:
        st.success(f"✅ API FastAPI está **online** em `{API_URL}`", icon="🟢")
    else:
        st.warning(
            f"⚠️ API FastAPI está **offline**. "
            f"Execute `python -m uvicorn api:app --reload` na pasta `streamlit_backend/`.",
            icon="🔴"
        )

    st.markdown("---")
    st.markdown("### 📡 Endpoints disponíveis")

    endpoints = [
        ("GET",  "/health",  "Health check da API"),
        ("POST", "/sync",    "Recebe um `DocumentState` JSON do app Android"),
        ("GET",  "/sync",    "Lista todos os documentos recebidos"),
        ("GET",  "/prompts", "Retorna os prompts atuais (app busca no startup)"),
        ("POST", "/prompts", "Atualiza os prompts (usado por esta UI)"),
    ]

    header_cols = st.columns([1, 2, 4])
    header_cols[0].markdown("**Método**")
    header_cols[1].markdown("**Rota**")
    header_cols[2].markdown("**Descrição**")
    st.markdown("---")

    for method, route, desc in endpoints:
        color = {"GET": "#10b981", "POST": "#667eea"}.get(method, "#gray")
        cols = st.columns([1, 2, 4])
        cols[0].markdown(f'<span style="color:{color};font-weight:700">{method}</span>', unsafe_allow_html=True)
        cols[1].code(route, language="text")
        cols[2].markdown(desc)

    st.markdown("---")
    st.markdown("### 📲 Configuração no App Android")
    st.markdown(
        "No `SyncManager` do app, configure o IP local desta máquina "
        "(ex: `192.168.x.x`) como base URL da API:"
    )
    st.code(
        """// SyncManager.kt
const val BACKEND_BASE_URL = "http://192.168.x.x:8000"

// Sincronizar documento
okHttpClient.newCall(Request.Builder()
    .url("$BACKEND_BASE_URL/sync")
    .post(docJson.toRequestBody("application/json".toMediaType()))
    .build()
).execute()

// Buscar prompts no startup
okHttpClient.newCall(Request.Builder()
    .url("$BACKEND_BASE_URL/prompts")
    .get()
    .build()
).execute()""",
        language="kotlin"
    )

    if online:
        st.markdown("---")
        st.markdown("### 🧪 Teste rápido")
        if st.button("📤 Enviar documento de teste para a API"):
            try:
                payload = {
                    "id": f"test_{int(datetime.now().timestamp())}",
                    "extractedText": "HEMOGRAMA COMPLETO\nHemoglobina: 13.2 g/dL\nLeucócitos: 7.800/mm³",
                    "syncStatus": "PENDING",
                    "context": "",
                    "timestamp": datetime.now().isoformat()
                }
                r = httpx.post(f"{API_URL}/sync", json=payload, timeout=5)
                if r.status_code == 201:
                    st.success(f"✅ Documento de teste enviado! ID: `{r.json()['id']}`")
                    st.json(r.json())
                else:
                    st.error(f"Erro: {r.status_code} – {r.text}")
            except Exception as e:
                st.error(f"Falha ao conectar: {e}")
