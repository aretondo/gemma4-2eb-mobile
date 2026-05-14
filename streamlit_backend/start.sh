#!/usr/bin/env bash
# start.sh – Ativa o venv e sobe API + Streamlit (Linux/macOS)
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/.venv/Scripts/activate" 2>/dev/null || source "$SCRIPT_DIR/.venv/bin/activate"
echo "🚀 Subindo API FastAPI na porta 8000..."
uvicorn api:app --host 0.0.0.0 --port 8000 --reload &
API_PID=$!
sleep 1
echo "🎨 Subindo Streamlit na porta 8501..."
streamlit run app.py --server.port 8501
kill $API_PID 2>/dev/null || true
