"""
Gemma4Good – Streamlit UI
Gerencia documentos sincronizados e configuração de prompts do sistema.
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
        return f'<span class="badge-pending">⏳ PENDING</span>'
    elif s == "READY":
        return f'<span class="badge-ready">🔵 READY</span>'
    elif s in ("SYNCED", "DONE"):
        return f'<span class="badge-synced">☁️ SYNCED</span>'
    return f'<span class="badge-error">❌ {s}</span>'

def guess_doc_type(doc: dict) -> str:
    """Infere o tipo de documento pelo texto extraído ou contexto."""
    text = (doc.get("extractedText", "") or "").lower()
    ctx  = (doc.get("context", "") or "").lower()
    combined = text + ctx
    if any(k in combined for k in ["hemograma", "leucócitos", "plaquetas", "hematócrito", "exame"]):
        return "Exame"
    if any(k in combined for k in ["receita", "mg", "comprimido", "dose", "prescri"]):
        return "Receita"
    if any(k in combined for k in ["laudo", "patologia", "histopatol", "anatomia"]):
        return "Laudo"
    return "Outro"

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
    pending = sum(1 for d in docs_all if d.get("syncStatus", "").upper() == "PENDING")
    ready   = sum(1 for d in docs_all if d.get("syncStatus", "").upper() == "READY")
    synced  = sum(1 for d in docs_all if d.get("syncStatus", "").upper() in ("SYNCED", "DONE"))

    col1, col2 = st.columns(2)
    col1.metric("Total", total)
    col2.metric("Pendentes", pending)
    col1.metric("Prontos", ready)
    col2.metric("Sincronizados", synced)

    st.markdown("---")
    st.caption("Gemma4Good Backend v2.0")
    st.caption(f"🕒 {datetime.now().strftime('%H:%M:%S')}")

# ── Conteúdo principal ────────────────────────────────────────────────────────
st.markdown("# 🏥 Gemma4Good Backend")
st.markdown("Central de comando: recebe documentos do app, gerencia prompts e visualiza dados triados.")
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
        # ── Filtros laterais ──────────────────────────────────────────────────
        filter_col1, filter_col2 = st.columns(2)

        with filter_col1:
            status_filter = st.selectbox(
                "Filtrar por status",
                ["Todos", "PENDING", "READY", "SYNCED"],
                key="status_filter"
            )

        # Enriquece docs com tipo inferido
        for d in docs:
            d["_doc_type"] = guess_doc_type(d)

        tipos_disponiveis = sorted(set(d["_doc_type"] for d in docs))
        with filter_col2:
            tipo_filter = st.selectbox(
                "Filtrar por tipo",
                ["Todos"] + tipos_disponiveis,
                key="tipo_filter"
            )

        # Aplica filtros
        filtered = docs
        if status_filter != "Todos":
            filtered = [d for d in filtered if d.get("syncStatus", "").upper() == status_filter]
        if tipo_filter != "Todos":
            filtered = [d for d in filtered if d["_doc_type"] == tipo_filter]

        st.caption(f"Exibindo **{len(filtered)}** de **{len(docs)}** documentos")
        st.markdown("---")

        for doc in filtered:
            doc_id   = doc.get("id", "Unknown")
            status   = doc.get("syncStatus", "PENDING")
            recv_at  = doc.get("received_at", "—")
            doc_type = doc.get("_doc_type", "Outro")
            txt_prev = (doc.get("extractedText", "") or "")[:120]

            label = f"📄 {doc_id}  ·  {doc_type}"
            with st.expander(label, expanded=False):
                cols = st.columns([2, 2, 1, 1])
                cols[0].markdown(f"**ID:** `{doc_id}`")
                cols[1].markdown(f"**Recebido:** {recv_at}")
                cols[2].markdown(f"**Tipo:** {doc_type}")
                cols[3].markdown(badge(status), unsafe_allow_html=True)

                if txt_prev:
                    st.markdown("**Prévia do texto OCR:**")
                    st.code(
                        txt_prev + ("..." if len(doc.get("extractedText", "")) > 120 else ""),
                        language="text"
                    )

                if doc.get("context"):
                    st.markdown("**Contexto da conversa (inclui tags processadas):**")
                    st.text_area(
                        label="context",
                        value=doc["context"],
                        height=100,
                        disabled=True,
                        key=f"ctx_{doc_id}",
                        label_visibility="collapsed"
                    )
                
                # Exibição das imagens recebidas
                saved_images = doc.get("saved_images", [])
                if saved_images:
                    st.markdown(f"**Imagens capturadas ({len(saved_images)}):**")
                    img_cols = st.columns(min(len(saved_images), 4))
                    for idx, img_rel_path in enumerate(saved_images):
                        with img_cols[idx % 4]:
                            # O Streamlit pode servir arquivos locais se o caminho for relativo ou via st.image
                            img_abs_path = BASE_DIR / img_rel_path
                            if img_abs_path.exists():
                                st.image(str(img_abs_path), use_container_width=True)


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

    # Aviso sobre a tag SET_STATUS
    st.markdown("""
<div class="tip-box">
    💡 <strong>Gemma Tool Use (Tag [SET_STATUS])</strong><br>
    O app intercepta a tag <code>[SET_STATUS:READY]</code> ou <code>[SET_STATUS:PENDING]</code> nas respostas do Gemma
    e atualiza o banco de dados local silenciosamente — a tag nunca é exibida ao usuário.<br>
    Certifique-se de que seus prompts incluam a instrução de uso desta tag (o botão abaixo insere automaticamente).
</div>
""", unsafe_allow_html=True)

    st.markdown("---")

    prompts = load_prompts()

    col_p1, col_p2 = st.columns(2)

    with col_p1:
        st.markdown("### 💬 Chat System Prompt")
        st.caption("Instrução para o chat livre com o profissional de saúde.")
        chat_p = st.text_area(
            label="chat_system_prompt",
            value=prompts.get("chat_system_prompt", ""),
            height=300,
            key="chat_prompt_area",
            label_visibility="collapsed",
        )
        st.caption(f"📏 {len(chat_p)} caracteres")
        if st.button("➕ Injetar instrução SET_STATUS", key="inject_chat"):
            if "[SET_STATUS" not in chat_p:
                chat_p = chat_p.rstrip() + SET_STATUS_TAG_INSTRUCTION
            st.rerun()

    with col_p2:
        st.markdown("### 📷 OCR System Prompt")
        st.caption("Instrução para análise de documentos digitalizados via OCR.")
        ocr_p = st.text_area(
            label="ocr_system_prompt",
            value=prompts.get("ocr_system_prompt", ""),
            height=300,
            key="ocr_prompt_area",
            label_visibility="collapsed",
        )
        st.caption(f"📏 {len(ocr_p)} caracteres")
        if st.button("➕ Injetar instrução SET_STATUS", key="inject_ocr"):
            if "[SET_STATUS" not in ocr_p:
                ocr_p = ocr_p.rstrip() + SET_STATUS_TAG_INSTRUCTION
            st.rerun()

    st.markdown("---")

    col_save, col_reset, _ = st.columns([1, 1, 4])

    with col_save:
        if st.button("💾 Salvar Prompts", type="primary", key="save_prompts"):
            save_prompts({"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p})
            try:
                httpx.post(
                    f"{API_URL}/prompts",
                    json={"chat_system_prompt": chat_p, "ocr_system_prompt": ocr_p},
                    timeout=2
                )
            except Exception:
                pass
            st.success("✅ Prompts salvos! O app buscará os novos valores no próximo sync.")

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
            f"Execute `run_api.bat` na pasta `streamlit_backend/`.",
            icon="🔴"
        )

    st.markdown("---")
    st.markdown("### 📡 Endpoints disponíveis")

    endpoints = [
        ("GET",  "/health",  "Health check da API"),
        ("POST", "/sync",    "Recebe `DocumentState` JSON do app Android"),
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
