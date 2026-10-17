import logging

from fastapi import FastAPI

from app.config import settings
from app.routers import api
from app.rules.engine import load_procedures

logging.basicConfig(level=logging.INFO)

app = FastAPI(title="sd_2606 API", version="0.1.0")
app.include_router(api.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "procedures": len(load_procedures()), "llm_enabled": settings.llm_enabled}
