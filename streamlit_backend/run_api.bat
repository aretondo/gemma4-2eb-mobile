@echo off
title Gemma4Good API
cd /d "%~dp0"
echo Ativando ambiente virtual e iniciando API...
if not exist .venv (
    echo [ERRO] Ambiente virtual .venv nao encontrado!
    pause
    exit /b
)
call .venv\Scripts\activate.bat
uvicorn api:app --host 0.0.0.0 --port 8000 --reload
pause
