# 🏥 Gemma4Good – Streamlit Backend

Backend local para o app Gemma4Good. Composto por dois serviços:

| Serviço | Porta | Função |
|---|---|---|
| **FastAPI** (`api.py`) | `8000` | REST API consumida pelo app Android |
| **Streamlit** (`app.py`) | `8501` | Painel de gerenciamento e configuração |

---

## Pré-requisitos

- Python 3.10+
- O venv já foi criado em `.venv/` (com `python -m venv .venv`)

---

## Como executar (Windows)

Execute cada serviço em uma janela separada:

1. **Rodar a API:** Execute `run_api.bat`
2. **Rodar a Interface:** Execute `run_ui.bat`

Estes scripts ativam o ambiente virtual automaticamente e mantêm a janela aberta caso ocorra algum erro.


---

## Endpoints da API

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/health` | Health check |
| `POST` | `/sync` | Recebe `DocumentState` do app Android |
| `GET` | `/sync` | Lista documentos recebidos |
| `GET` | `/prompts` | Retorna prompts atuais (app busca no startup) |
| `POST` | `/prompts` | Atualiza prompts via UI Streamlit |

Swagger UI disponível em: **http://localhost:8000/docs**

---

## Configuração no App Android

No `SyncManager.kt`, configure o IP local desta máquina:

```kotlin
const val BACKEND_BASE_URL = "http://192.168.x.x:8000"
```

---

## Estrutura

```
streamlit_backend/
├── .venv/              # Ambiente virtual Python (não versionado)
├── synced_docs/        # Documentos recebidos do app (não versionados)
├── app.py              # UI Streamlit
├── api.py              # FastAPI REST backend
├── prompts.json        # Prompts editáveis pela UI
├── requirements.txt    # Dependências Python
├── start.bat           # Script de inicialização (Windows)
└── start.sh            # Script de inicialização (Linux/macOS)
```
