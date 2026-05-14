@echo off
title Gemma4Good UI (Streamlit)
cd /d "%~dp0"
echo Ativando ambiente virtual e iniciando Streamlit...
if not exist .venv (
    echo [ERRO] Ambiente virtual .venv nao encontrado!
    pause
    exit /b
)
call .venv\Scripts\activate.bat
streamlit run app.py --server.port 8501
pause
