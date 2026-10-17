import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.board import db as board_db
from app.config import settings
from app.routers import api, board
from app.rules.engine import load_procedures

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    # 掲示板の DB を起動時に作る（書き込めない場所なら、ここで起動に失敗して気づける）
    with board_db.connect():
        pass
    yield


app = FastAPI(title="sd_2606 API", version="0.1.0", lifespan=lifespan)
app.include_router(api.router)
app.include_router(board.router)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "procedures": len(load_procedures()), "llm_enabled": settings.llm_enabled}
