"""
Gemma4Good – Streamlit UI
Manages synced documents and system prompt configurations.
"""

from __future__ import annotations

import json
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

    [data-testid="stSidebar"] {
        background: linear-gradient(180deg, #1a1f2e 0%, #0f1117 100%);
        border-right: 1px solid #2a2d3e;
    }

    [data-testid="metric-container"] {
        background: #1a1f2e;
        border: 1px solid #2a2d3e;
        border-radius: 12px;
        padding: 16px;
    }

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

    /* Badges */
    .badge-pending  { background:#f59e0b22; color:#f59e0b; border:1px solid #f59e0b44; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }
    .badge-ready    { background:#3b82f622; color:#3b82f6; border:1px solid #3b82f644; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }
    .badge-synced   { background:#10b98122; color:#10b981; border:1px solid #10b98144; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }
    .badge-error    { background:#ef444422; color:#ef4444; border:1px solid #ef444444; border-radius:6px; padding:2px 8px; font-size:0.75rem; font-weight:600; }

    /* API status */
    .api-online  { color:#10b981; font-weight:600; }
    .api-offline { color:#ef4444; font-weight:600; }

    hr { border-color: #2a2d3e; }
    textarea { font-family: 'Inter', monospace !important; font-size: 0.875rem !important; }

    /* Tip box */
    .tip-box {
        background: #1a1f2e;
        border-left: 3px solid #667eea;
        border-radius: 0 8px 8px 0;
        padding: 12px 16px;
        margin: 8px 0;
        font-size: 0.875rem;
    }
</style>
""", unsafe_allow_html=True)

# ── Constantes ────────────────────────────────────────────────────────────────
BASE_DIR    = Path(__file__).parent
SYNC_DIR    = BASE_DIR / "synced_docs"
PROMPT_FILE = BASE_DIR / "prompts.json"
KNOWLEDGE_FILE = BASE_DIR / "knowledge.json"
API_URL     = "http://127.0.0.1:8000"

SYNC_DIR.mkdir(exist_ok=True)

# Tag de controle reconhecida pelo app Android
SET_STATUS_TAG_INSTRUCTION = (
    " Sempre que o usuário confirmar que o dado está correto ou pedir para finalizar, "
    "use a tag [SET_STATUS:READY] no final da sua resposta para que o app atualize o status automaticamente. "
    "Se o usuário pedir para voltar ao pendente, use [SET_STATUS:PENDING]. "
    "Nunca mostre a tag ao usuário, ela é processada silenciosamente pelo sistema."
)

# ── Helpers ───────────────────────────────────────────────────────────────────
def load_prompts() -> dict:
    try:
        if PROMPT_FILE.exists():
            return json.loads(PROMPT_FILE.read_text(encoding="utf-8"))
    except:
        pass
    return {
        "chat_system_prompt": "You are 'Gemma Scan Assistant'. DIRECTLY ANSWER the user's question using the provided technical context. Do not make meta-analyses or explain your reasoning.",
        "ocr_system_prompt": "Extract data from OCR. Be technical."
    }

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
        return f'<span class="badge-pending">⏳ PENDING</span>'
    elif s == "READY":
        return f'<span class="badge-ready">🔵 READY</span>'
    elif s in ("SYNCED", "DONE"):
        return f'<span class="badge-synced">☁️ SYNCED</span>'
    return f'<span class="badge-error">❌ {s}</span>'

def guess_doc_type(doc: dict) -> str:
    """Infers document type from extracted text or context."""
    text = (doc.get("extracted_text", "") or "").lower()
    ctx  = (doc.get("context", "") or "").lower()
    combined = text + ctx
    if any(k in combined for k in ["blood count", "hemogram", "leukocytes", "platelets", "hematocrit", "exam", "test"]):
        return "Exam"
    if any(k in combined for k in ["prescription", "mg", "tablet", "dose", "prescri"]):
        return "Prescription"
    if any(k in combined for k in ["report", "pathology", "histopathol", "anatomy", "laudo"]):
        return "Report"
    return "Other"

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

    docs_all = load_docs()
    total   = len(docs_all)
    pending = sum(1 for d in docs_all if d.get("status", "").upper() == "PENDING")
    ready   = sum(1 for d in docs_all if d.get("status", "").upper() == "READY")
    synced  = sum(1 for d in docs_all if d.get("status", "").upper() in ("SYNCED", "DONE"))

    col1, col2 = st.columns(2)
    col1.metric("Total", total)
    col2.metric("Pending", pending)
    col1.metric("Ready", ready)
    col2.metric("Synced", synced)

    st.markdown("---")
    st.caption("Gemma4Good Backend v2.0")
    st.caption(f"🕒 {datetime.now().strftime('%H:%M:%S')}")

# ── Conteúdo principal ────────────────────────────────────────────────────────
st.markdown("# 🏥 Gemma4Good Backend")
st.markdown("Command center: receives documents from the app, manages prompts, and visualizes screened data.")
st.markdown("---")

tab1, tab2, tab3, tab4 = st.tabs(["📊 Synced Documents", "⚙️ Prompt Configuration", "📚 Knowledge Base", "🔌 API & Integration"])

# ══════════════════════════════════════════════════════════════════════════════
# TAB 1 – Documents
# ══════════════════════════════════════════════════════════════════════════════
with tab1:
    col_h1, col_h2 = st.columns([3, 1])
    with col_h1:
        st.subheader("Received Documents")
    with col_h2:
        if st.button("🔄 Refresh", key="refresh_docs"):
            st.rerun()

    docs = load_docs()

    if not docs:
        st.info("📭 No documents synced yet. Waiting for data from the Android app.", icon="ℹ️")
    else:
        # ── Filters ───────────────────────────────────────────────────────────
        filter_col1, filter_col2 = st.columns(2)

        with filter_col1:
            status_filter = st.selectbox(
                "Filter by status",
                ["All", "PENDING", "READY", "SYNCED"],
                key="status_filter"
            )

        # Enrich docs with guessed type
        for d in docs:
            d["_doc_type"] = guess_doc_type(d)

        available_types = sorted(set(d["_doc_type"] for d in docs))
        with filter_col2:
            tipo_filter = st.selectbox(
                "Filter by type",
                ["All"] + available_types,
                key="tipo_filter"
            )

        # Apply filters
        filtered = docs
        if status_filter != "All":
            filtered = [d for d in filtered if d.get("status", "").upper() == status_filter]
        if tipo_filter != "All":
            filtered = [d for d in filtered if d["_doc_type"] == tipo_filter]

        st.caption(f"Showing **{len(filtered)}** of **{len(docs)}** documents")
        st.markdown("---")

        for doc in filtered:
            doc_id   = doc.get("id", "Unknown")
            status   = doc.get("status", "PENDING")
            recv_at  = doc.get("received_at", "—")
            doc_type = doc.get("_doc_type", "Other")
            txt_prev = (doc.get("extracted_text", "") or "")[:120]

            label = f"📄 {doc_id}  ·  {doc_type}"
            with st.expander(label, expanded=False):
                cols = st.columns([2, 2, 1, 1])
                cols[0].markdown(f"**ID:** `{doc_id}`")
                cols[1].markdown(f"**Received:** {recv_at}")
                cols[2].markdown(f"**Type:** {doc_type}")
                cols[3].markdown(badge(status), unsafe_allow_html=True)

                if txt_prev:
                    st.markdown("**OCR text preview:**")
                    st.code(
                        txt_prev + ("..." if len(doc.get("extracted_text", "")) > 120 else ""),
                        language="text"
                    )

                if doc.get("context"):
                    st.markdown("**Chat context (including processed tags):**")
                    st.text_area(
                        label="context",
                        value=doc["context"],
                        height=100,
                        disabled=True,
                        key=f"ctx_{doc_id}",
                        label_visibility="collapsed"
                    )
                
                # Display received images
                saved_images = doc.get("saved_images", [])
                if saved_images:
                    st.markdown(f"**Captured images ({len(saved_images)}):**")
                    img_cols = st.columns(min(len(saved_images), 4))
                    for idx, img_rel_path in enumerate(saved_images):
                        with img_cols[idx % 4]:
                            img_abs_path = BASE_DIR / img_rel_path
                            if img_abs_path.exists():
                                st.image(str(img_abs_path), use_container_width=True)


                with st.expander("🔍 Full JSON"):
                    st.json(doc)

# ══════════════════════════════════════════════════════════════════════════════
# TAB 2 – Prompts
# ══════════════════════════════════════════════════════════════════════════════
with tab2:
    st.subheader("⚙️ System Prompt Configuration")
    st.markdown(
        "These prompts are downloaded by the Android app at startup. "
        "Edit here and save — the app will use the new values on the next sync."
    )

    st.markdown("""
<div class="tip-box">
    💡 <strong>Gemma Tool Use (Tag [SET_STATUS])</strong><br>
    The app intercepts the <code>[SET_STATUS:READY]</code> or <code>[SET_STATUS:PENDING]</code> tags in Gemma's responses
    and updates the local database silently — the tag is never displayed to the user.<br>
    The app now uses an Intent Parser to handle these, but keeping them in instructions helps the LLM understand state.
</div>
""", unsafe_allow_html=True)

    st.markdown("---")

    prompts = load_prompts()

    col_p1, col_p2 = st.columns(2)

    with col_p1:
        st.markdown("### 💬 Chat System Prompt")
        st.caption("Instructions for free chat with the healthcare professional.")
        chat_p = st.text_area(
            label="chat_system_prompt",
            value=prompts.get("chat_system_prompt", ""),
            height=300,
            key="chat_prompt_area",
            label_visibility="collapsed",
        )
        st.caption(f"📏 {len(chat_p)} characters")

    with col_p2:
        st.markdown("### 📷 OCR System Prompt")
        st.caption("Instructions for analyzing documents scanned via OCR.")
        ocr_p = st.text_area(
            label="ocr_system_prompt",
            value=prompts.get("ocr_system_prompt", ""),
            height=300,
            key="ocr_prompt_area",
            label_visibility="collapsed",
        )
        st.caption(f"📏 {len(ocr_p)} characters")

    st.markdown("---")

    col_save, col_reset, _ = st.columns([1, 1, 4])

    with col_save:
        if st.button("💾 Save Prompts", type="primary", key="save_prompts"):
            save_prompts({"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p})
            try:
                httpx.post(
                    f"{API_URL}/prompts",
                    json={"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p},
                    timeout=2
                )
            except Exception:
                pass
            st.success("✅ Prompts saved! The app will fetch the new values in the next sync.")

    with col_reset:
        if st.button("↩️ Reset", key="reset_prompts"):
            st.rerun()

# ══════════════════════════════════════════════════════════════════════════════
# TAB 3 – Knowledge Base
# ══════════════════════════════════════════════════════════════════════════════
with tab3:
    st.subheader("📚 Knowledge Base (Local RAG)")
    st.markdown(
        "Upload `.txt` or `.md` files to create an offline knowledge base. "
        "The Android app will download this data during Sync to answer technical questions."
    )

    uploaded_files = st.file_uploader(
        "Add documents to the base (.txt, .md)",
        type=["txt", "md"],
        accept_multiple_files=True
    )

    if uploaded_files:
        if st.button("🏗️ Process and Index Base"):
            all_chunks = []
            chunk_size = 800
            overlap = 100

            for uploaded_file in uploaded_files:
                text = uploaded_file.read().decode("utf-8")

                # Chunking robusto com overlap
                start = 0
                while start < len(text):
                    end = start + chunk_size
                    chunk_text = text[start:end]

                    if end < len(text):
                        last_space = chunk_text.rfind(' ')
                        if last_space != -1:
                            chunk_text = chunk_text[:last_space]
                            end = start + last_space

                    all_chunks.append({
                        "source": uploaded_file.name,
                        "text": chunk_text.strip(),
                        "timestamp": datetime.now().isoformat()
                    })

                    start = end - overlap
                    if start < 0: start = 0
                    if end >= len(text): break

            # Salva no arquivo knowledge.json
            KNOWLEDGE_FILE.write_text(json.dumps({"chunks": all_chunks}, ensure_ascii=False, indent=2), encoding="utf-8")
            st.success(f"✅ Base processed with {len(all_chunks)} chunks. The app will download it in the next Sync.")

    st.markdown("---")
    if KNOWLEDGE_FILE.exists():
        knowledge = json.loads(KNOWLEDGE_FILE.read_text(encoding="utf-8"))
        chunks = knowledge.get("chunks", [])
        st.markdown(f"**Current Base:** {len(chunks)} indexed chunks.")

        with st.expander("View Indexed Chunks"):
            for i, chunk in enumerate(chunks):
                st.markdown(f"**[{i+1}] Source: {chunk['source']}**")
                st.info(chunk["text"])
    else:
        st.info("The knowledge base is empty.")

# ══════════════════════════════════════════════════════════════════════════════
# TAB 4 – API & Integration
# ══════════════════════════════════════════════════════════════════════════════
with tab4:
    st.subheader("🔌 FastAPI & Android Integration")

    online = api_status()

    if online:
        st.success(f"✅ FastAPI is **online** at `{API_URL}`", icon="🟢")
    else:
        st.warning(
            f"⚠️ FastAPI is **offline**. "
            f"Run `run_api.bat` in the `streamlit_backend/` folder.",
            icon="🔴"
        )

    st.markdown("---")
    st.markdown("### 📡 Available Endpoints")

    endpoints = [
        ("GET",  "/health",  "API Health check"),
        ("POST", "/sync",    "Receives `DocumentState` JSON from Android app"),
        ("GET",  "/sync",    "Lists all received documents"),
        ("GET",  "/knowledge", "Serves the knowledge base chunks"),
        ("GET",  "/prompts", "Returns current prompts (app fetches at startup)"),
        ("POST", "/prompts", "Updates prompts (used by this UI)"),
    ]

    header_cols = st.columns([1, 2, 4])
    header_cols[0].markdown("**Método**")
    header_cols[1].markdown("**Rota**")
    header_cols[2].markdown("**Descrição**")
    st.markdown("---")

    for method, route, desc in endpoints:
        color = {"GET": "#10b981", "POST": "#667eea"}.get(method, "#aaa")
        cols = st.columns([1, 2, 4])
        cols[0].markdown(f'<span style="color:{color};font-weight:700">{method}</span>', unsafe_allow_html=True)
        cols[1].code(route, language="text")
        cols[2].markdown(desc)

    st.markdown("---")
    st.markdown("### 🤖 Gemma Tool Use — Tag [SET_STATUS]")
    st.markdown("""
O app Android implementa um mecanismo de **tool use simulado**:

- O Gemma pode incluir `[SET_STATUS:READY]` ou `[SET_STATUS:PENDING]` na resposta.
- O app intercepta a tag antes de exibir o texto, atualiza o `DocumentState` local e **nunca mostra a tag ao usuário**.
- Acionar frases como *"pode marcar como pronto"* ou *"voltar para pendente"* disparam esse comportamento.

Para que funcione corretamente, os prompts devem incluir a instrução de uso das tags. Use o botão **"Injetar instrução SET_STATUS"** na aba Prompts.
""")

    st.markdown("---")
    st.markdown("### 📲 Configuração no App Android")
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
        col_t1, col_t2 = st.columns(2)
        with col_t1:
            if st.button("📤 Enviar doc PENDING de teste"):
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
                        st.success(f"OK! ID: `{r.json()['id']}`")
                    else:
                        st.error(f"Erro: {r.status_code}")
                except Exception as e:
                    st.error(f"Falha: {e}")
        with col_t2:
            if st.button("📤 Enviar doc READY de teste"):
                try:
                    payload = {
                        "id": f"ready_{int(datetime.now().timestamp())}",
                        "extractedText": "RECEITA MÉDICA\nAmoxicilina 500mg — 1 comprimido 8/8h por 7 dias",
                        "syncStatus": "READY",
                        "context": "Gemma: [SET_STATUS:READY]",
                        "timestamp": datetime.now().isoformat()
                    }
                    r = httpx.post(f"{API_URL}/sync", json=payload, timeout=5)
                    if r.status_code == 201:
                        st.success(f"OK! ID: `{r.json()['id']}`")
                    else:
                        st.error(f"Erro: {r.status_code}")
                except Exception as e:
                    st.error(f"Falha: {e}")
