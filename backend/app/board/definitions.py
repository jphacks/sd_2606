"""掲示板の定義（data/board.yaml）の読み込み。"""

from functools import lru_cache
from pathlib import Path

import yaml

from app.board.schemas import BoardMeta

DATA_DIR = Path(__file__).resolve().parents[2] / "data"
META_FILE = DATA_DIR / "board.yaml"
SAMPLES_FILE = DATA_DIR / "board_samples.yaml"


@lru_cache
def load_meta() -> BoardMeta:
    return BoardMeta.model_validate(yaml.safe_load(META_FILE.read_text(encoding="utf-8")))


def tags_for_procedure(procedure_id: str) -> list[str]:
    """その手続きの画面に出す体験談のタグ。"""
    return [t.id for t in load_meta().tags if procedure_id in t.procedures]


def load_samples() -> list[dict]:
    if not SAMPLES_FILE.exists():
        return []
    return yaml.safe_load(SAMPLES_FILE.read_text(encoding="utf-8")) or []
